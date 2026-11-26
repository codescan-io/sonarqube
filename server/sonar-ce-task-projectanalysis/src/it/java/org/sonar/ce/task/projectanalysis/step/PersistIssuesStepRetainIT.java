/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.task.projectanalysis.step;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.projectanalysis.analysis.AnalysisMetadataHolder;
import org.sonar.ce.task.projectanalysis.issue.AdHocRuleCreator;
import org.sonar.ce.task.projectanalysis.issue.ProtoIssueCache;
import org.sonar.ce.task.projectanalysis.issue.RuleRepositoryImpl;
import org.sonar.ce.task.projectanalysis.issue.UpdateConflictResolver;
import org.sonar.ce.task.projectanalysis.period.Period;
import org.sonar.ce.task.projectanalysis.period.PeriodHolderRule;
import org.sonar.ce.task.projectanalysis.util.cache.DiskCache.CacheAppender;
import org.sonar.ce.task.step.TestComputationStepContext;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.core.util.UuidFactory;
import org.sonar.core.util.UuidFactoryImpl;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeMapper;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.newcodeperiod.NewCodePeriodType;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.issue.IssueStorage;
import org.sonar.server.issue.retain.IssueResolutionListener;
import org.sonar.server.issue.retain.IssueResolutionListeners;
import org.sonar.server.issue.retain.RetainIssueResolutionListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_REOPENED;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;

/**
 * The analysis collects Fixed changes across its persist batches and writes them per 500, plus what is left before the
 * commit. See CSN-254.
 */
public class PersistIssuesStepRetainIT {

  private static final long NOW = 1_400_000_000_000L;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public PeriodHolderRule periodHolder = new PeriodHolderRule();

  private final System2 system2 = mock(System2.class);
  private final DbSession session = db.getSession();
  private ProtoIssueCache protoIssueCache;
  private PersistIssuesStep underTest;
  private ComponentDto project;
  private ComponentDto file;
  private RuleDto rule;

  @Before
  public void setup() throws Exception {
    periodHolder.setPeriod(new Period(NewCodePeriodType.NUMBER_OF_DAYS.name(), "10", 1000L));
    protoIssueCache = new ProtoIssueCache(temp.newFile(), System2.INSTANCE);
    underTest = newStep(db.getDbClient(), new IssueStorage());
    when(system2.now()).thenReturn(NOW);
    project = db.components().insertPrivateProject().getMainBranchComponent();
    file = db.components().insertComponent(newFileDto(project));
    rule = db.rules().insert();
  }

  @After
  public void tearDown() {
    session.close();
  }

  /** 1,200 changed issues are three persist batches; the three that became Fixed are written once, before the commit. */
  @Test
  public void retainsFixedChangesSpreadAcrossBatches() {
    setRetain(true);
    List<IssueDto> issues = insertOpenIssues(1_200);

    analyse(issues, i -> i == 10 || i == 600 || i == 1_100);

    assertThat(retainedKeys()).containsExactlyInAnyOrder(issues.get(10).getKey(), issues.get(600).getKey(), issues.get(1_100).getKey());
  }

  /** More Fixed changes than one write takes: written once full, and the rest before the commit. */
  @Test
  public void retainsEveryFixedChange_whenThereAreMoreThanOneWrite() {
    setRetain(true);
    List<IssueDto> issues = insertOpenIssues(700);

    analyse(issues, i -> true);

    assertThat(retainedKeys()).hasSize(700);
  }

  @Test
  public void retainsNothing_whenTheOrganizationDoesNotRetain() {
    setRetain(false);
    List<IssueDto> issues = insertOpenIssues(20);

    analyse(issues, i -> true);

    assertThat(retainedKeys()).isEmpty();
  }

  /** A user-resolved issue that the analysis then closes stays Fixed: no row, even one it never had. */
  @Test
  public void retainsNothing_whenAFixedIssueOnlyMovesFromResolvedToClosed() {
    setRetain(true);
    IssueDto issue = db.issues().insert(rule, project, file, i -> i.setStatus(STATUS_RESOLVED).setResolution(RESOLUTION_FIXED)
      .setCreatedAt(NOW - 1_000_000_000L).setUpdatedAt(NOW - 1_000_000_000L));

    CacheAppender appender = protoIssueCache.newAppender();
    appender.append(changeStatus(issue.toDefaultIssue(), STATUS_RESOLVED, STATUS_CLOSED, RESOLUTION_FIXED, RESOLUTION_FIXED)).close();
    underTest.execute(new TestComputationStepContext());

    assertThat(retainedKeys()).isEmpty();
  }

  /** An issue that reopens was not fixed: its row goes even when the toggle is off. */
  @Test
  public void removesTheRowOfAReopenedIssue_evenWhenTheOrganizationDoesNotRetain() {
    setRetain(false);
    IssueDto issue = db.issues().insert(rule, project, file, i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED)
      .setIssueCloseDate(new Date(NOW - 10_000L)).setCreatedAt(NOW - 1_000_000_000L).setUpdatedAt(NOW - 1_000_000_000L));
    db.executeInsert("cs_issue_track_for_retain",
      "branch_uuid", project.uuid(),
      "issue_kee", issue.getKey(),
      "retained_status", IssueStatus.FIXED.name(),
      "rule_uuid", rule.getUuid(),
      "component_key", file.getKey(),
      "status_changed_at", NOW - 10_000L);

    CacheAppender appender = protoIssueCache.newAppender();
    appender.append(changeStatus(issue.toDefaultIssue(), STATUS_CLOSED, STATUS_REOPENED, RESOLUTION_FIXED, null)).close();
    underTest.execute(new TestComputationStepContext());

    assertThat(retainedKeys()).isEmpty();
  }

  /**
   * The session commits every 250 statements, so when the save fails in the second batch the issues of the first are
   * committed while the 300 Fixed keys held for them, below one write, are not. Those are written after the failure.
   */
  @Test
  public void retainsTheHeldFixedIssues_whenTheSaveFailsPartWay() {
    setRetain(true);
    List<IssueDto> issues = insertOpenIssues(700);
    underTest = newStep(committingInBatches(), new FailingIssueStorage(600));

    assertThatThrownBy(() -> analyse(issues, i -> i < 300)).hasMessage("injected failure");

    Set<String> heldForFirstBatch = issues.subList(0, 300).stream().map(IssueDto::getKey).collect(Collectors.toSet());
    assertThat(retainedKeys()).isEqualTo(heldForFirstBatch);
  }

  /**
   * The branch row is written once the first batch reached the listener; a second batch of 50 issues queues too few
   * statements to commit before the failure, so the row is rolled back with it and must be written again.
   */
  @Test
  public void writesTheBranchRowAgain_whenItWasRolledBackWithTheFailure() {
    setRetain(true);
    List<IssueDto> issues = insertOpenIssues(550);
    underTest = newStep(committingInBatches(), new FailingIssueStorage(510));

    assertThatThrownBy(() -> analyse(issues, i -> i < 300)).hasMessage("injected failure");

    assertThat(retainedKeys()).hasSize(300);
    assertThat(db.select("select branch_uuid as \"uuid\" from cs_retain_details")).extracting(row -> row.get("uuid"))
      .containsExactly(project.uuid());
  }

  private PersistIssuesStep newStep(DbClient dbClient, IssueStorage issueStorage) {
    IssueResolutionListeners listeners = new IssueResolutionListeners(
      new IssueResolutionListener[] {new RetainIssueResolutionListener(dbClient)});
    return new PersistIssuesStep(dbClient, system2, new UpdateConflictResolver(),
      new RuleRepositoryImpl(mock(AdHocRuleCreator.class), dbClient, mock(AnalysisMetadataHolder.class)), periodHolder, protoIssueCache, issueStorage,
      UuidFactoryImpl.INSTANCE, listeners);
  }

  /** The test database never batches; the analysis's session must, so that it commits on its own as in production. */
  private DbClient committingInBatches() {
    DbClient dbClient = spy(db.getDbClient());
    doAnswer(invocation -> dbClient.getMyBatis().openSession(true)).when(dbClient).openSession(true);
    return dbClient;
  }

  /** Fails the save when the changes of the {@code failAt}-th issue are written, after its issue row is queued. */
  private static class FailingIssueStorage extends IssueStorage {
    private final int failAt;
    private int calls;

    FailingIssueStorage(int failAt) {
      this.failAt = failAt;
    }

    @Override
    public void insertChanges(IssueChangeMapper mapper, DefaultIssue issue, UuidFactory uuidFactory) {
      if (++calls == failAt) {
        throw new IllegalStateException("injected failure");
      }
      super.insertChanges(mapper, issue, uuidFactory);
    }
  }

  /** The setting is read through the project's organization, which the component fixtures leave unset. */
  private void setRetain(boolean enabled) {
    db.executeUpdateSql("update organizations set retain_issue_history_enabled = " + enabled);
    db.executeUpdateSql("update projects set organization_uuid = (select min(uuid) from organizations)");
  }

  private List<IssueDto> insertOpenIssues(int count) {
    return IntStream.range(0, count)
      .mapToObj(i -> db.issues().insert(rule, project, file, issue -> issue.setStatus(STATUS_OPEN).setResolution(null)
        .setCreatedAt(NOW - 1_000_000_000L).setUpdatedAt(NOW - 1_000_000_000L)))
      .toList();
  }

  /** Every issue is changed; those matching {@code fixed} are closed, the others only change severity. */
  private void analyse(List<IssueDto> issues, IntPredicate fixed) {
    CacheAppender appender = protoIssueCache.newAppender();
    for (int i = 0; i < issues.size(); i++) {
      DefaultIssue issue = issues.get(i).toDefaultIssue();
      if (fixed.test(i)) {
        appender.append(changeStatus(issue, STATUS_OPEN, STATUS_CLOSED, null, RESOLUTION_FIXED));
      } else {
        issue.setFieldChange(context(), "severity", issue.severity(), "BLOCKER");
        appender.append(issue.setSeverity("BLOCKER").setSelectedAt(NOW).setNew(false).setChanged(true));
      }
    }
    appender.close();
    underTest.execute(new TestComputationStepContext());
  }

  private static DefaultIssue changeStatus(DefaultIssue issue, String fromStatus, String toStatus, String fromResolution,
    String toResolution) {
    issue.setFieldChange(context(), "status", fromStatus, toStatus);
    issue.setFieldChange(context(), "resolution", fromResolution, toResolution);
    return issue.setStatus(toStatus).setResolution(toResolution)
      .setCloseDate(STATUS_CLOSED.equals(toStatus) ? new Date(NOW) : null)
      .setSelectedAt(NOW).setNew(false).setChanged(true);
  }

  private static IssueChangeContext context() {
    return IssueChangeContext.issueChangeContextByScanBuilder(new Date(NOW)).build();
  }

  private Set<String> retainedKeys() {
    return db.select("select issue_kee as \"key\" from cs_issue_track_for_retain").stream()
      .map(row -> (String) row.values().iterator().next())
      .collect(Collectors.toSet());
  }
}
