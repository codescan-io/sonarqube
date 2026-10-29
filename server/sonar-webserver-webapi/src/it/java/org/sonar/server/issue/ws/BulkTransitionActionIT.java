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
package org.sonar.server.issue.ws;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.TestIssueChangePostProcessor;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.api.issue.Issue.RESOLUTION_EXCEPTION;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.issue.IssueChangeDto.TYPE_COMMENT;
import static org.sonar.db.issue.IssueChangeDto.TYPE_EXCEPTION_REASON;
import static org.sonar.db.issue.IssueChangeDto.TYPE_FIELD_CHANGE;

/** CSN-82: the exception transition of {@code api/issues/bulk_transition}, copied from the source each issue's transfer paired it with. */
public class BulkTransitionActionIT {

  private static final long NOW = 2_000_000_000_000L;
  private static final String REASON = "Approved until the vendor patch";
  private static final String OTHER_REASON = "Accepted risk for the legacy module";
  private static final String COMMENT = "Resolution transferred from src (branch- main)";

  @Rule
  public DbTester db = DbTester.create();
  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = db.getDbClient();
  private final IssueIndexer issueIndexer = new IssueIndexer(es.client(), dbClient, new IssueIteratorFactory(dbClient),
    null);
  private final WsActionTester ws = new WsActionTester(new BulkTransitionAction(dbClient, userSession, issueIndexer,
    new SequenceUuidFactory(), new TestSystem2().setNow(NOW), new TestIssueChangePostProcessor()));

  private final String transferUuid = UUID.randomUUID().toString();
  private ComponentDto project;
  private ComponentDto file;
  private RuleDto rule;

  @Test
  public void eachIssueTakesTheExpiryAndReasonOfItsOwnSource() {
    givenAProject();
    IssueDto firstSource = insertException(utcMidnight("2026-12-31"), REASON);
    IssueDto secondSource = insertException(utcMidnight("2027-03-31"), OTHER_REASON);
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    String json = exceptionRequest(List.of(first, second), List.of(firstSource, secondSource)).execute().getInput();

    assertThat(json).contains("\"transitioned\":2");
    assertException(first, utcMidnight("2026-12-31"), REASON);
    assertException(second, utcMidnight("2027-03-31"), OTHER_REASON);
    for (IssueDto issue : List.of(first, second)) {
      assertThat(changeData(issue, TYPE_COMMENT)).containsExactly(COMMENT);
      assertThat(changeData(issue, TYPE_FIELD_CHANGE))
        .containsExactly("resolution=EXCEPTION,status=OPEN|RESOLVED,issueStatus=OPEN|EXCEPTION");
    }
  }

  @Test
  public void issuesSharingASourceValueAreWrittenTogether() {
    givenAProject();
    IssueDto firstSource = insertException(utcMidnight("2026-12-31"), REASON);
    IssueDto secondSource = insertException(utcMidnight("2026-12-31"), REASON);
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    exceptionRequest(List.of(first, second), List.of(firstSource, secondSource)).execute();

    assertException(first, utcMidnight("2026-12-31"), REASON);
    assertException(second, utcMidnight("2026-12-31"), REASON);
  }

  @Test
  public void theSourcesLatestReasonIsTheOneCopied() {
    givenAProject();
    IssueDto source = insertException(null, OTHER_REASON);
    insertReason(source, REASON, NOW - 1_000);
    IssueDto issue = insertOpenIssue();

    exceptionRequest(List.of(issue), List.of(source)).execute();

    assertException(issue, null, REASON);
  }

  @Test
  public void aSourceThatNeverExpiresGivesAnExceptionThatNeverExpires() {
    givenAProject();
    IssueDto source = insertException(null, REASON);
    IssueDto issue = insertOpenIssue();

    exceptionRequest(List.of(issue), List.of(source)).execute();

    assertException(issue, null, REASON);
  }

  @Test
  public void aSourceWithoutAReasonGivesAnExceptionWithoutOneButKeepsTheComment() {
    givenAProject();
    IssueDto source = insertException(utcMidnight("2026-12-31"));
    IssueDto blankSource = insertException(utcMidnight("2026-12-31"), "  ");
    IssueDto issue = insertOpenIssue();
    IssueDto other = insertOpenIssue();

    exceptionRequest(List.of(issue, other), List.of(source, blankSource)).execute();

    for (IssueDto moved : List.of(issue, other)) {
      assertException(moved, utcMidnight("2026-12-31"), null);
      assertThat(changeData(moved, TYPE_COMMENT)).containsExactly(COMMENT);
    }
  }

  @Test
  public void aSourceThatIsNoLongerAnExceptionGivesNeitherExpiryNorReason() {
    givenAProject();
    IssueDto source = insertException(utcMidnight("2026-12-31"), REASON);
    dbClient.issueDao().update(db.getSession(), source.setStatus(STATUS_OPEN).setResolution(null));
    db.commit();
    IssueDto issue = insertOpenIssue();

    exceptionRequest(List.of(issue), List.of(source)).execute();

    assertException(issue, null, null);
  }

  @Test
  public void perRowChangelogWritesTheSameExceptionRows() {
    givenAProject();
    IssueDto withReason = insertException(utcMidnight("2027-03-31"), REASON);
    IssueDto withoutReason = insertException(null);
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    exceptionRequest(List.of(first, second), List.of(withReason, withoutReason))
      .setParam("setBasedChangelog", "false").execute();

    assertException(first, utcMidnight("2027-03-31"), REASON);
    assertException(second, null, null);
    for (IssueDto issue : List.of(first, second)) {
      assertThat(changeData(issue, TYPE_COMMENT)).containsExactly(COMMENT);
      assertThat(changeData(issue, TYPE_FIELD_CHANGE))
        .containsExactly("resolution=EXCEPTION,status=OPEN|RESOLVED,issueStatus=OPEN|EXCEPTION");
    }
  }

  @Test
  public void anIssuePairedWithSeveralSourcesTakesTheSourceWithTheLowestKey() {
    givenAProject();
    IssueDto first = insertException(utcMidnight("2026-12-31"), REASON);
    IssueDto second = insertException(utcMidnight("2027-03-31"), OTHER_REASON);
    IssueDto issue = insertOpenIssue();
    pair(transferUuid, second, issue);

    exceptionRequest(List.of(issue), List.of(first)).execute();

    IssueDto lowest = first.getKey().compareTo(second.getKey()) < 0 ? first : second;
    assertException(issue, lowest.getIssueResolutionExpiresAt(), lowest == first ? REASON : OTHER_REASON);
  }

  @Test
  public void onlyTheNamedTransfersPairingIsRead() {
    givenAProject();
    IssueDto source = insertException(utcMidnight("2026-12-31"), REASON);
    IssueDto issue = insertOpenIssue();
    pair(UUID.randomUUID().toString(), source, issue);

    baseRequest(List.of(issue), "exception").setParam("transferUuid", transferUuid).execute();

    assertException(issue, null, null);
  }

  @Test
  public void exceptionWithoutATransferIsRejected() {
    givenAProject();
    IssueDto issue = insertOpenIssue();
    TestRequest request = baseRequest(List.of(issue), "exception");

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'transferUuid' is required with the 'exception' transition");
    assertThat(reload(issue).getStatus()).isEqualTo(STATUS_OPEN);
  }

  @Test
  public void aTransferIsRejectedWithAnotherTransition() {
    givenAProject();
    TestRequest request = baseRequest(List.of(insertOpenIssue()), "falsepositive")
      .setParam("transferUuid", transferUuid);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'transferUuid' applies only to the 'exception' transition");
  }

  @Test
  public void falsePositiveAndAcceptLeaveNoExpiryAndNoReason() {
    givenAProject();
    IssueDto falsePositive = insertOpenIssue();
    IssueDto accepted = insertOpenIssue();

    baseRequest(List.of(falsePositive), "falsepositive").execute();
    baseRequest(List.of(accepted), "accept").execute();

    assertThat(reload(falsePositive).getResolution()).isEqualTo(RESOLUTION_FALSE_POSITIVE);
    assertThat(reload(accepted).getResolution()).isEqualTo(RESOLUTION_WONT_FIX);
    for (IssueDto issue : List.of(falsePositive, accepted)) {
      assertThat(reload(issue).getIssueResolutionExpiresAt()).isNull();
      assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).isEmpty();
    }
  }

  private void givenAProject() {
    // The resolution transfer report lives in CodeScan's schema, so the test database only gets the columns read.
    db.executeDdl("create table if not exists cs_resolution_transfer_issue (uuid varchar(40) not null primary key, "
      + "transfer_uuid varchar(40) not null, issue_key varchar(50) not null, destination_issue_key varchar(50))");
    userSession.logIn().setSystemAdministrator();
    project = db.components().insertPrivateProject().getMainBranchComponent();
    file = db.components().insertComponent(newFileDto(project));
    rule = db.rules().insertIssueRule();
  }

  private IssueDto insertOpenIssue() {
    return db.issues().insertIssue(rule, project, file, i -> i.setStatus(STATUS_OPEN).setResolution(null));
  }

  /** A resolved exception with the given expiry and, when one is given, a reason recorded before {@link #NOW}. */
  private IssueDto insertException(@Nullable Long expiresAt, String... reasons) {
    IssueDto source = db.issues().insertIssue(rule, project, file, i -> i.setStatus(STATUS_RESOLVED)
      .setResolution(RESOLUTION_EXCEPTION).setIssueResolutionExpiresAt(expiresAt));
    for (String reason : reasons) {
      insertReason(source, reason, NOW - 10_000);
    }
    return source;
  }

  private void insertReason(IssueDto issue, String reason, long createdAt) {
    db.issues().insertChange(issue, c -> c.setChangeType(TYPE_EXCEPTION_REASON).setChangeData(reason)
      .setIssueChangeCreationDate(createdAt).setCreatedAt(createdAt).setUpdatedAt(createdAt));
  }

  private void assertException(IssueDto issue, @Nullable Long expiresAt, @Nullable String reason) {
    IssueDto reloaded = reload(issue);
    assertThat(reloaded.getStatus()).isEqualTo(STATUS_RESOLVED);
    assertThat(reloaded.getResolution()).isEqualTo(RESOLUTION_EXCEPTION);
    assertThat(reloaded.getIssueResolutionExpiresAt()).isEqualTo(expiresAt);
    if (reason == null) {
      assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).isEmpty();
    } else {
      assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).containsExactly(reason);
    }
  }

  /** Pairs each issue with the source in the same position, as the transfer's report would, and requests it. */
  private TestRequest exceptionRequest(List<IssueDto> issues, List<IssueDto> sources) {
    for (int i = 0; i < issues.size(); i++) {
      pair(transferUuid, sources.get(i), issues.get(i));
    }
    return baseRequest(issues, "exception").setParam("transferUuid", transferUuid);
  }

  private void pair(String transfer, IssueDto source, IssueDto issue) {
    db.executeUpdateSql("insert into cs_resolution_transfer_issue (uuid, transfer_uuid, issue_key, "
      + "destination_issue_key) values (?, ?, ?, ?)", UUID.randomUUID().toString(), transfer, source.getKey(),
      issue.getKey());
  }

  private TestRequest baseRequest(List<IssueDto> issues, String transition) {
    return ws.newRequest()
      .setMethod("POST")
      .setParam("issues", String.join(",", issues.stream().map(IssueDto::getKey).toList()))
      .setParam("transition", transition)
      .setParam("branchUuid", project.uuid())
      .setParam("comment", COMMENT)
      .setParam("refreshMeasures", "false");
  }

  private IssueDto reload(IssueDto issue) {
    return dbClient.issueDao().selectOrFailByKey(db.getSession(), issue.getKey());
  }

  private List<String> changeData(IssueDto issue, String changeType) {
    return dbClient.issueChangeDao().selectByTypeAndIssueKeys(db.getSession(), List.of(issue.getKey()), changeType)
      .stream().map(IssueChangeDto::getChangeData).toList();
  }

  private static long utcMidnight(String date) {
    return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
