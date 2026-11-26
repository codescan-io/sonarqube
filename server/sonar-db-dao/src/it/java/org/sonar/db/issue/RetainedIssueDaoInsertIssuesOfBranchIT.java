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
package org.sonar.db.issue;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.sonar.db.issue.RetainFixtures.retainedBranches;
import static org.sonar.db.issue.RetainFixtures.retainedIssueKeys;
import static org.sonar.db.issue.RetainFixtures.retainedIssues;

class RetainedIssueDaoInsertIssuesOfBranchIT {

  private static final int CODE_SMELL = 1;
  private static final int SECURITY_HOTSPOT = 4;

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final RetainedIssueDao underTest = new RetainedIssueDao();

  @Test
  void copiesTheIssueRowAndWritesTheBranch_whenTheOrganizationRetains() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.closedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL, 5_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, "user-9", 9_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    Map<String, Object> stored = retainedIssues(db).get(0);
    assertThat(stored)
      .containsEntry("BRANCH_UUID", "branch-1")
      .containsEntry("ISSUE_KEE", "ISSUE-1")
      .containsEntry("RETAINED_STATUS", "FIXED")
      .containsEntry("RULE_UUID", "rule-1")
      .containsEntry("COMPONENT_KEY", "project-1:src/Foo.cls")
      .containsEntry("MESSAGE", "message of ISSUE-1")
      .containsEntry("SEVERITY", "MAJOR")
      .containsEntry("TAGS", "design,apex")
      .containsEntry("ASSIGNEE_UUID", "user-7")
      .containsEntry("STATUS_MARKED_BY_UUID", "user-9")
      .containsEntry("ISSUE_CREATION_DATE", 1_000L)
      .containsEntry("STATUS", "CLOSED")
      .containsEntry("RESOLUTION", "FIXED")
      .as("copied when the branch is deleted")
      .containsEntry("ASSIGNED_AT", null);
    assertThat(((Number) stored.get("LINE")).intValue()).isEqualTo(12);
    assertThat(((Number) stored.get("ISSUE_TYPE")).intValue()).isEqualTo(CODE_SMELL);
    assertThat(stored.get("STATUS_CHANGED_AT")).as("the close date of the issue").isEqualTo(5_000L);
    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_UUID"), row -> row.get("PROJECT_UUID"), row -> row.get("BRANCH_KEY"), row -> row.get("BRANCH_TYPE"),
        row -> row.get("BRANCH_NAME"))
      .containsExactly(tuple("branch-1", "project-1", "main", "BRANCH", "main"));
  }

  /** A user's resolution leaves the close date empty, so the issue became Fixed at the time of the change. */
  @Test
  void usesTheTimeOfTheChange_forAnIssueWithoutCloseDate() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.resolvedFixedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, "user-9", 9_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedIssues(db)).extracting(row -> row.get("STATUS_CHANGED_AT"), row -> row.get("STATUS"), row -> row.get("RESOLUTION"))
      .containsExactly(tuple(9_000L, "RESOLVED", "FIXED"));
  }

  /** Nothing in the schema ties an issue to an existing file row: such an issue is still retained, without a file key. */
  @Test
  void retainsAnIssueWithoutFileKey_whenItsFileRowIsMissing() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.closedIssue(db, "ISSUE-1", "branch-1", "missing-file", CODE_SMELL, 5_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 9_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("COMPONENT_KEY"))
      .containsExactly(tuple("ISSUE-1", null));
  }

  @Test
  void writesNothing_whenTheOrganizationDoesNotRetain() {
    branchWithFile(false, "branch-1", "main", "BRANCH", null);
    RetainFixtures.closedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL, 5_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 9_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(db.countRowsOfTable("cs_issue_track_for_retain")).isZero();
    assertThat(db.countRowsOfTable("cs_retain_details")).isZero();
  }

  /** RESOLVED/FIXED then CLOSED/FIXED is one fix: the first time stays. Hotspots are never retained. */
  @Test
  void keepsTheFirstRowOfAnAlreadyRetainedIssueAndSkipsHotspots() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.resolvedFixedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL);
    RetainFixtures.closedIssue(db, "HOTSPOT-1", "branch-1", "file-1", SECURITY_HOTSPOT, 2_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, "user-1", 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 2_000L, List.of("ISSUE-1", "HOTSPOT-1"));
    db.getSession().commit();

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("STATUS_MARKED_BY_UUID"), row -> row.get("STATUS_CHANGED_AT"))
      .containsExactly(tuple("ISSUE-1", "user-1", 1_000L));
  }

  /** The status check in SQL: a key whose issue row is not Fixed, for example because it reopened since, is skipped. */
  @Test
  void skipsAnIssueWhoseRowIsNotFixed() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.issue(db, "OPEN-1", "branch-1", "file-1", CODE_SMELL);
    RetainFixtures.issue(db, "ACCEPTED-1", "branch-1", "file-1", CODE_SMELL, "RESOLVED", "WONTFIX", null);
    RetainFixtures.issue(db, "REMOVED-1", "branch-1", "file-1", CODE_SMELL, "CLOSED", "REMOVED", 3_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 9_000L,
      List.of("OPEN-1", "ACCEPTED-1", "REMOVED-1"));
    db.getSession().commit();

    assertThat(retainedIssueKeys(db)).containsExactly("REMOVED-1");
  }

  /** More keys than one statement takes are written in several statements. */
  @Test
  void insertsEveryKey_whenThereAreMoreThanOnePartition() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    List<String> keys = IntStream.range(0, 1_200).mapToObj(i -> "ISSUE-" + i).toList();
    keys.forEach(key -> RetainFixtures.closedIssue(db, key, "branch-1", "file-1", CODE_SMELL, 5_000L));

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 9_000L, keys);
    db.getSession().commit();

    assertThat(db.countRowsOfTable("cs_issue_track_for_retain")).isEqualTo(1_200);
  }

  /** A row another transaction wrote meanwhile is kept, and the insert does not fail. */
  @Test
  void insertIssues_keepsARowWrittenConcurrently() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainFixtures.closedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL, 1_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, "user-1", 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    underTest.insertIssues(db.getSession(), "branch-1", IssueStatus.FIXED, "user-2", 2_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("STATUS_MARKED_BY_UUID"), row -> row.get("STATUS_CHANGED_AT"))
      .containsExactly(tuple("user-1", 1_000L));
  }

  /**
   * Two writers retain the first issues of a branch at the same moment: both read the branch before either wrote its
   * row, so both insert it. The second insert keeps the first row and does not fail.
   */
  @Test
  void writeBranch_keepsTheRowOfAConcurrentWriter() {
    branchWithFile(true, "branch-1", "main", "BRANCH", null);
    RetainBranchContextDto firstWriter = underTest.selectBranchContext(db.getSession(), "branch-1").orElseThrow();
    RetainBranchContextDto secondWriter = underTest.selectBranchContext(db.getSession(), "branch-1").orElseThrow();

    underTest.writeBranch(db.getSession(), firstWriter);
    db.getSession().commit();
    underTest.writeBranch(db.getSession(), secondWriter);
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_UUID"), row -> row.get("BRANCH_KEY"))
      .containsExactly(tuple("branch-1", "main"));
  }

  @Test
  void storesNoTargetForABranchWhoseReferenceIsItself() {
    branchWithFile(true, "branch-1", "feature-x", "BRANCH", "branch-1");
    RetainFixtures.closedIssue(db, "ISSUE-1", "branch-1", "file-1", CODE_SMELL, 1_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "branch-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_KEY"), row -> row.get("TARGET_BRANCH_KEY"), row -> row.get("TARGET_BRANCH_UUID"))
      .containsExactly(tuple("feature-x", null, null));
  }

  @Test
  void storesThePullRequestTargetAndRefreshesARenamedBranch() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "pr-1", "project-1", "42", "PULL_REQUEST", "branch-main");
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);
    RetainFixtures.closedIssue(db, "ISSUE-2", "pr-1", "file-1", CODE_SMELL, 2_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();
    db.executeUpdateSql("update project_branches set kee = '43' where uuid = 'pr-1'");

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 2_000L, List.of("ISSUE-2"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_KEY"), row -> row.get("BRANCH_TYPE"), row -> row.get("TARGET_BRANCH_KEY"))
      .containsExactly(tuple("43", "PULL_REQUEST", "main"));
    assertThat(retainedIssueKeys(db)).containsExactly("ISSUE-1", "ISSUE-2");
  }

  @Test
  void storesThePullRequestTargetUuidAndRefreshesItAfterARetarget() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "branch-dev", "project-1", "develop", "BRANCH", "branch-main");
    RetainFixtures.branch(db, "pr-1", "project-1", "42", "PULL_REQUEST", "branch-dev");
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);
    RetainFixtures.closedIssue(db, "ISSUE-2", "pr-1", "file-1", CODE_SMELL, 2_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();
    assertThat(retainedBranches(db)).extracting(row -> row.get("TARGET_BRANCH_UUID")).containsExactly("branch-dev");
    db.executeUpdateSql("update project_branches set merge_branch_uuid = 'branch-main' where uuid = 'pr-1'");

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 2_000L, List.of("ISSUE-2"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("TARGET_BRANCH_KEY"), row -> row.get("TARGET_BRANCH_UUID"))
      .containsExactly(tuple("main", "branch-main"));
  }

  /** The target's row is gone once it is deleted; the pull request still points at it, so the stored name is kept. */
  @Test
  void keepsTheTargetNameOfAPullRequestWhoseTargetIsDeleted() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "branch-dev", "project-1", "develop", "BRANCH", "branch-main");
    RetainFixtures.branch(db, "pr-1", "project-1", "42", "PULL_REQUEST", "branch-dev");
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);
    RetainFixtures.closedIssue(db, "ISSUE-2", "pr-1", "file-1", CODE_SMELL, 2_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();
    db.executeUpdateSql("delete from project_branches where uuid = 'branch-dev'");

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 2_000L, List.of("ISSUE-2"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("TARGET_BRANCH_KEY"), row -> row.get("TARGET_BRANCH_UUID"))
      .containsExactly(tuple("develop", "branch-dev"));
    assertThat(retainedIssueKeys(db)).containsExactly("ISSUE-1", "ISSUE-2");
  }

  /** A pull request's key is its id; its name is the source branch held in its data. */
  @Test
  void storesThePullRequestSourceBranchAsItsNameAndRefreshesIt() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.pullRequest(db, "pr-1", "project-1", "42", "branch-main", "feature/login");
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);
    RetainFixtures.closedIssue(db, "ISSUE-2", "pr-1", "file-1", CODE_SMELL, 2_000L);
    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();
    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_KEY"), row -> row.get("BRANCH_NAME"), row -> row.get("TARGET_BRANCH_KEY"))
      .containsExactly(tuple("42", "feature/login", "main"));
    RetainFixtures.setSourceBranch(db, "pr-1", "feature/sign-in");

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 2_000L, List.of("ISSUE-2"));
    db.getSession().commit();

    assertThat(retainedBranches(db)).extracting(row -> row.get("BRANCH_NAME")).containsExactly("feature/sign-in");
  }

  /** As api/project_pull_requests/list: without readable data, a pull request is named by its key. */
  @Test
  void namesAPullRequestByItsKey_whenItsDataIsMissing() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "pr-1", "project-1", "42", "PULL_REQUEST", "branch-main");
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_KEY"), row -> row.get("BRANCH_NAME"))
      .containsExactly(tuple("42", "42"));
  }

  @Test
  void namesAPullRequestByItsKey_whenItsDataIsUnreadable() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-main", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "pr-1", "project-1", "42", "PULL_REQUEST", "branch-main");
    db.executeUpdateSql("update project_branches set pull_request_binary = ? where uuid = 'pr-1'", new byte[] {(byte) 0xFF, 0x01});
    RetainFixtures.file(db, "file-1", "pr-1", "project-1:src/Foo.cls");
    RetainFixtures.closedIssue(db, "ISSUE-1", "pr-1", "file-1", CODE_SMELL, 1_000L);

    RetainFixtures.retainIssues(db, db.getSession(), "pr-1", IssueStatus.FIXED, null, 1_000L, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(retainedBranches(db))
      .extracting(row -> row.get("BRANCH_NAME"))
      .as("the issue is still retained")
      .containsExactly("42");
    assertThat(retainedIssueKeys(db)).containsExactly("ISSUE-1");
  }

  private void branchWithFile(boolean retainEnabled, String branchUuid, String key, String type, String mergeBranchUuid) {
    RetainFixtures.project(db, retainEnabled, "project-1");
    RetainFixtures.branch(db, branchUuid, "project-1", key, type, mergeBranchUuid);
    RetainFixtures.file(db, "file-1", branchUuid, "project-1:src/Foo.cls");
  }
}
