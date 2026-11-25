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
package org.sonar.server.issue;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.DefaultIssueComment;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.ProliferationChangeQuery;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.rule.ServerRuleFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The write, against a real database.
 *
 * <p>Every other test of this class mocks {@link org.sonar.db.issue.IssueChangeDao}, which cannot see what its
 * statements actually do. {@code insertProliferationDiffs} and {@code insertProliferationComments} are
 * INSERT ... SELECT over {@code issues}, filtered on the same statuses the update is guarded on - so whether a
 * changelog row appears at all depends on the state of the row at the moment the statement runs, and on the order
 * the two statements are issued in. A mock records the call and reports success either way.
 *
 * <p>That gap let a reordering ship in which the update ran first, leaving every set-based marking with no
 * changelog and no comment. These tests read the rows back.
 */
public class ProliferationIssueStorageIT {

  private static final long MARKED_AT = 1_700_000_000_000L;
  private static final Set<String> STATUSES = Set.of(Issue.STATUS_OPEN, Issue.STATUS_REOPENED);
  private static final String AUTHOR = "user-uuid";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final DbClient dbClient = db.getDbClient();
  private final DbSession dbSession = db.getSession();

  private final ProliferationIssueStorage underTest = new ProliferationIssueStorage(System2.INSTANCE, dbClient,
    mock(ServerRuleFinder.class), mock(IssueIndexer.class), UuidFactoryFast.getInstance());

  private ProjectData project;
  private RuleDto rule;

  /** An issue on the target branch as proliferation finds it, plus the DefaultIssue the workflow prepared from it. */
  private IssueDto givenOpenIssue(String key) {
    return givenIssueWithStatus(key, Issue.STATUS_OPEN);
  }

  private IssueDto givenIssueWithStatus(String key, String status) {
    if (project == null) {
      project = db.components().insertPrivateProject();
      rule = db.rules().insertIssueRule();
    }
    return db.issues().insertIssue(i -> i
      .setKee(key)
      .setStatus(status)
      .setResolution(Issue.STATUS_CLOSED.equals(status) ? Issue.RESOLUTION_FIXED : null)
      .setType(RuleType.CODE_SMELL)
      .setRuleUuid(rule.getUuid())
      .setProjectUuid(project.getMainBranchDto().getUuid())
      .setComponentUuid(project.getMainBranchComponent().uuid()));
  }

  /**
   * Built the way production builds it: from the persisted row, via {@code toDefaultIssue}. That matters beyond
   * tidiness - a bare {@code new DefaultIssue()} is isNew, and the per-issue changelog writer skips the field
   * diffs of a new issue, so a hand-rolled fixture would quietly test nothing on that path.
   */
  private DefaultIssue marked(IssueDto target, String resolution) {
    DefaultIssue issue = dbClient.issueDao().selectByKey(dbSession, target.getKey()).orElseThrow()
      .toDefaultIssue();
    issue.setStatus(Issue.STATUS_RESOLVED);
    issue.setResolution(resolution);
    issue.setRuleKey(RuleKey.of(rule.getRepositoryKey(), rule.getRuleKey()));
    issue.setType(RuleType.CODE_SMELL);
    issue.setCurrentChange(new FieldDiffs()
      .setDiff("resolution", null, resolution)
      .setDiff("status", Issue.STATUS_OPEN, Issue.STATUS_RESOLVED)
      .setDiff("issueStatus", "OPEN", "ACCEPTED")
      .setCreationDate(new Date(MARKED_AT)));
    return issue;
  }

  private List<IssueChangeDto> changesOf(String issueKey) {
    return dbClient.issueChangeDao().selectByIssueKeys(dbSession, List.of(issueKey));
  }

  private String statusOf(String issueKey) {
    return dbClient.issueDao().selectByKey(dbSession, issueKey).orElseThrow().getStatus();
  }

  /**
   * The regression test for the reordering: an Accepted marking must leave both the resolution and the history
   * behind. Asserting that the DAO was called is not enough - the statement selects from issues, and after the
   * update there is nothing left for it to select.
   */
  @Test
  public void writesTheChangelogForAnAcceptedMarking() {
    IssueDto target = givenOpenIssue("BBB-1");

    List<String> written = underTest.save(dbSession, List.of(marked(target, Issue.RESOLUTION_WONT_FIX)),
      target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(written).containsExactly("BBB-1");
    assertThat(statusOf("BBB-1")).isEqualTo(Issue.STATUS_RESOLVED);
    assertThat(changesOf("BBB-1"))
      .describedAs("the marking must carry a changelog row; without one nobody can see who marked it or why")
      .isNotEmpty()
      .anyMatch(c -> IssueChangeDto.TYPE_FIELD_CHANGE.equals(c.getChangeType()));
  }

  @Test
  public void writesTheChangelogForAFalsePositiveMarking() {
    IssueDto target = givenOpenIssue("BBB-2");

    underTest.save(dbSession, List.of(marked(target, Issue.RESOLUTION_FALSE_POSITIVE)),
      target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(statusOf("BBB-2")).isEqualTo(Issue.STATUS_RESOLVED);
    assertThat(changesOf("BBB-2")).isNotEmpty();
  }

  /** The comment travels with the marking, and by the same INSERT ... SELECT that the diff does. */
  @Test
  public void writesTheCommentThatCameWithTheMarking() {
    IssueDto target = givenOpenIssue("BBB-3");
    DefaultIssue issue = marked(target, Issue.RESOLUTION_WONT_FIX);
    issue.addComment(DefaultIssueComment.create("BBB-3", AUTHOR, "accepted upstream"));

    underTest.save(dbSession, List.of(issue), target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(changesOf("BBB-3"))
      .filteredOn(c -> IssueChangeDto.TYPE_COMMENT.equals(c.getChangeType()))
      .describedAs("the comment must reach the target branch")
      .hasSize(1)
      .allMatch(c -> "accepted upstream".equals(c.getChangeData()));
  }

  /** A REOPENED target is a candidate too, and must get the same treatment as an OPEN one. */
  @Test
  public void writesTheChangelogForAReopenedTarget() {
    IssueDto target = givenIssueWithStatus("BBB-4", Issue.STATUS_REOPENED);

    underTest.save(dbSession, List.of(marked(target, Issue.RESOLUTION_WONT_FIX)),
      target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(statusOf("BBB-4")).isEqualTo(Issue.STATUS_RESOLVED);
    assertThat(changesOf("BBB-4")).isNotEmpty();
  }

  /**
   * The guard, end to end: an issue that left the candidate statuses after it was read keeps its own state, gets
   * no changelog row describing a marking it never received, and is not reported as proliferated.
   */
  @Test
  public void leavesAnIssueThatIsNoLongerACandidateUntouched() {
    IssueDto target = givenIssueWithStatus("BBB-5", Issue.STATUS_CLOSED);

    List<String> written = underTest.save(dbSession, List.of(marked(target, Issue.RESOLUTION_WONT_FIX)),
      target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(written).isEmpty();
    assertThat(statusOf("BBB-5")).isEqualTo(Issue.STATUS_CLOSED);
    assertThat(changesOf("BBB-5")).isEmpty();
  }

  /** A mixed batch: the eligible issue is written and reported, the ineligible one is neither. */
  @Test
  public void reportsOnlyTheIssuesItActuallyWrote() {
    IssueDto eligible = givenOpenIssue("BBB-6");
    IssueDto gone = givenIssueWithStatus("BBB-7", Issue.STATUS_CLOSED);

    List<String> written = underTest.save(dbSession,
      List.of(marked(eligible, Issue.RESOLUTION_WONT_FIX), marked(gone, Issue.RESOLUTION_WONT_FIX)),
      eligible.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(written).containsExactly("BBB-6");
    assertThat(statusOf("BBB-6")).isEqualTo(Issue.STATUS_RESOLVED);
    assertThat(statusOf("BBB-7")).isEqualTo(Issue.STATUS_CLOSED);
    assertThat(changesOf("BBB-7")).isEmpty();
  }

  /**
   * The per-issue path, which an Exception marking always takes: it writes its changelog from the issue in memory
   * rather than by selecting rows, so it has the opposite ordering requirement. Both must end up with history.
   */
  @Test
  public void writesTheChangelogForAnExceptionMarking() {
    IssueDto target = givenOpenIssue("BBB-8");
    DefaultIssue issue = marked(target, Issue.RESOLUTION_EXCEPTION);

    List<String> written = underTest.save(dbSession, List.of(issue), target.getProjectUuid(), MARKED_AT, AUTHOR,
      STATUSES);

    assertThat(written).containsExactly("BBB-8");
    assertThat(statusOf("BBB-8")).isEqualTo(Issue.STATUS_RESOLVED);
    assertThat(changesOf("BBB-8")).isNotEmpty();
  }

  /** The same guard on the per-issue path. */
  @Test
  public void leavesANonCandidateUntouchedOnTheExceptionPath() {
    IssueDto target = givenIssueWithStatus("BBB-9", Issue.STATUS_CLOSED);

    List<String> written = underTest.save(dbSession, List.of(marked(target, Issue.RESOLUTION_EXCEPTION)),
      target.getProjectUuid(), MARKED_AT, AUTHOR, STATUSES);

    assertThat(written).isEmpty();
    assertThat(statusOf("BBB-9")).isEqualTo(Issue.STATUS_CLOSED);
    assertThat(changesOf("BBB-9")).isEmpty();
  }
  /**
   * The one case where the history and the resolution can come apart, pinned so nobody has to guess at it.
   *
   * <p>The three set-based statements run in sequence, each against its own snapshot. An issue that has already
   * left the candidate statuses when the write begins is passed over by all three - that is
   * {@link #leavesAnIssueThatIsNoLongerACandidateUntouched}. But a user who changes it in the gap between the
   * changelog statements and the update lands in between: the changelog and the comment are already written, and
   * the update then refuses the row. The issue keeps its own resolution and gains a note about one it never
   * received.
   *
   * <p>This reproduces that gap by changing the row between the statements. The window in production is the time
   * between two statements of one transaction, against seconds or minutes for the read-to-write window the status
   * guard exists to close, and the cost is a stray changelog row rather than lost data. The per-issue path has no
   * such gap: it guards first and writes history second - see
   * {@link #leavesANonCandidateUntouchedOnTheExceptionPath}.
   */
  @Test
  public void writesHistoryWithoutTheResolutionIfTheIssueChangesBetweenTheStatements() {
    IssueDto target = givenOpenIssue("BBB-10");
    ProliferationChangeQuery query = new ProliferationChangeQuery()
      .setKeys(List.of("BBB-10"))
      .setBranchUuid(target.getProjectUuid())
      .setChangeAuthor(AUTHOR)
      .setNow(MARKED_AT)
      .setMarkedAt(MARKED_AT)
      .setStatuses(STATUSES);

    // 1. the changelog statements, while the issue is still a candidate
    dbClient.issueChangeDao().insertProliferationDiffs(dbSession, query.copyFor(List.of("BBB-10"))
      .setResolution(Issue.RESOLUTION_WONT_FIX)
      .setIssueStatus("ACCEPTED"), UuidFactoryFast.getInstance());
    dbClient.issueChangeDao().insertProliferationComments(dbSession, query.copyFor(List.of("BBB-10"))
      .setComment("accepted upstream")
      .setChangeType(IssueChangeDto.TYPE_COMMENT), UuidFactoryFast.getInstance());

    // 2. a user closes it in the gap
    db.executeUpdateSql("update issues set status = ? where kee = ?", Issue.STATUS_CLOSED, "BBB-10");

    // 3. the update, which now refuses the row
    int updated = dbClient.issueDao().bulkTransitionWithExpiry(dbSession, List.of("BBB-10"),
      Issue.RESOLUTION_WONT_FIX, null, MARKED_AT, MARKED_AT, STATUSES);

    assertThat(updated).isZero();
    assertThat(statusOf("BBB-10")).isEqualTo(Issue.STATUS_CLOSED);
    assertThat(changesOf("BBB-10"))
      .describedAs("the history is already written by this point and is not rolled back")
      .isNotEmpty();
  }
}
