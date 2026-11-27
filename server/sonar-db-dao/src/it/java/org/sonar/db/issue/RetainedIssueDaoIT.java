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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.sonar.db.issue.RetainFixtures.retainedBranches;
import static org.sonar.db.issue.RetainFixtures.retainedCommentUuids;
import static org.sonar.db.issue.RetainFixtures.retainedIssueKeys;
import static org.sonar.db.issue.RetainFixtures.retainedIssues;

class RetainedIssueDaoIT {

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final RetainedIssueDao underTest = new RetainedIssueDao();

  private long statusChangedAt = 0;

  /**
   * An issue that leaves Fixed was not fixed, so its row goes; if it is fixed again later it is captured again, with
   * the later fix time.
   */
  @Test
  void deleteRetainedIssues_removesOnlyThatStatusAndLetsALaterFixBeCapturedAgain() {
    RetainFixtures.retainedBranch(db, "branch-1", "project-1", "main", "BRANCH");
    retained("branch-1", "ISSUE-1", IssueStatus.FIXED);
    retained("branch-1", "ISSUE-2", IssueStatus.FIXED);
    retained("branch-1", "ISSUE-3", IssueStatus.ACCEPTED);

    underTest.deleteRetainedIssues(db.getSession(), IssueStatus.FIXED, List.of("ISSUE-1", "ISSUE-3"));
    db.getSession().commit();

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("RETAINED_STATUS"))
      .containsExactly(tuple("ISSUE-2", "FIXED"), tuple("ISSUE-3", "ACCEPTED"));

    retained("branch-1", "ISSUE-1", IssueStatus.FIXED);

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("STATUS_CHANGED_AT"))
      .containsExactly(tuple("ISSUE-2", 2L), tuple("ISSUE-3", 3L), tuple("ISSUE-1", 4L));
  }

  /** A row whose issue is still Fixed stays, so the delete can be written again after a rolled-back reopen. */
  @Test
  void deleteRetainedIssues_keepsTheRowOfAnIssueThatIsStillFixed() {
    RetainFixtures.retainedBranch(db, "branch-1", "project-1", "main", "BRANCH");
    RetainFixtures.closedIssue(db, "STILL-FIXED", "branch-1", "file-1", 1, 5_000L);
    RetainFixtures.issue(db, "REOPENED", "branch-1", "file-1", 1);
    retained("branch-1", "STILL-FIXED", IssueStatus.FIXED);
    retained("branch-1", "REOPENED", IssueStatus.FIXED);

    underTest.deleteRetainedIssues(db.getSession(), IssueStatus.FIXED, List.of("STILL-FIXED", "REOPENED"));
    db.getSession().commit();

    assertThat(retainedIssueKeys(db)).containsExactly("STILL-FIXED");
  }

  /** Only comments are copied, only for issues retained on that branch, and copying twice adds nothing. */
  @Test
  void copyCommentsOfBranch_copiesCommentsOfThatBranchsRetainedIssuesOnlyAndOnce() {
    RetainFixtures.retainedBranch(db, "branch-1", "project-1", "feature-x", "BRANCH");
    RetainFixtures.retainedBranch(db, "branch-2", "project-1", "feature-y", "BRANCH");
    retained("branch-1", "ISSUE-1", IssueStatus.FIXED);
    retained("branch-2", "ISSUE-2", IssueStatus.FIXED);
    issueChange("c1", "ISSUE-1", "branch-1", "comment", "first comment", 10L);
    issueChange("c2", "ISSUE-1", "branch-1", "comment", "second comment", 20L);
    issueChange("d1", "ISSUE-1", "branch-1", "diff", "status=OPEN|CLOSED", 30L);
    issueChange("c3", "ISSUE-2", "branch-2", "comment", "on branch 2", 40L);
    issueChange("c4", "ISSUE-3", "branch-1", "comment", "not retained", 50L);

    underTest.copyCommentsOfBranch(db.getSession(), "branch-1");
    underTest.copyCommentsOfBranch(db.getSession(), "branch-1");
    db.getSession().commit();

    assertThat(db.select("select * from cs_retain_issue_comments order by uuid"))
      .extracting(row -> row.get("UUID"), row -> row.get("ISSUE_KEE"), row -> row.get("CHANGE_DATA"), row -> row.get("USER_UUID"),
        row -> row.get("ISSUE_CHANGE_CREATION_DATE"))
      .containsExactly(
        tuple("c1", "ISSUE-1", "first comment", "user-1", 10L),
        tuple("c2", "ISSUE-1", "second comment", "user-1", 20L));
  }

  @Test
  void deleteByProjectUuid_removesBranchesIssuesAndCommentsOfThatProjectOnly() {
    RetainFixtures.retainedBranch(db, "branch-1", "project-1", "main", "BRANCH");
    RetainFixtures.retainedBranch(db, "deleted-pr", "project-1", "42", "PULL_REQUEST");
    RetainFixtures.retainedBranch(db, "branch-2", "project-2", "main", "BRANCH");
    retained("branch-1", "ISSUE-1", IssueStatus.FIXED);
    retained("deleted-pr", "ISSUE-2", IssueStatus.FIXED);
    retained("branch-2", "ISSUE-3", IssueStatus.FIXED);
    issueChange("c1", "ISSUE-1", "branch-1", "comment", "project 1", 10L);
    issueChange("c3", "ISSUE-3", "branch-2", "comment", "project 2", 30L);
    underTest.copyCommentsOfBranch(db.getSession(), "branch-1");
    underTest.copyCommentsOfBranch(db.getSession(), "branch-2");
    db.getSession().commit();

    underTest.deleteByProjectUuid(db.getSession(), "project-1");
    db.getSession().commit();

    assertThat(retainedBranches(db)).extracting(row -> row.get("BRANCH_UUID")).containsExactly("branch-2");
    assertThat(retainedIssueKeys(db)).containsExactly("ISSUE-3");
    assertThat(retainedCommentUuids(db)).containsExactly("c3");
  }

  private void retained(String branchUuid, String issueKey, IssueStatus retainedStatus) {
    RetainFixtures.retainedIssue(db, branchUuid, issueKey, retainedStatus.name(), ++statusChangedAt);
  }

  private void issueChange(String uuid, String issueKey, String branchUuid, String changeType, String changeData, long creationDate) {
    db.executeInsert("issue_changes",
      "uuid", uuid,
      "kee", "kee-" + uuid,
      "issue_key", issueKey,
      "user_login", "user-1",
      "change_type", changeType,
      "change_data", changeData,
      "created_at", creationDate,
      "updated_at", creationDate,
      "issue_change_creation_date", creationDate,
      "project_uuid", branchUuid);
  }
}
