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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ProjectData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class RetainedIssueDaoIT {

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final RetainedIssueDao underTest = new RetainedIssueDao();

  private long fixedAt = 0;

  @Test
  void insertIssue_thenScroll_returnsEveryStoredField() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    underTest.insertIssue(db.getSession(), issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED)
      .setMessage("Avoid the global modifier")
      .setSeverity("MAJOR")
      .setIssueType(1)
      .setTags("design,apex")
      .setAssigneeUuid("user-7")
      .setStatusMarkedByUuid("user-9")
      .setIssueCreationDate(1_000L)
      .setFixedAt(2_000L));
    db.getSession().commit();

    IssueTrackForRetainDto stored = scroll("project-1", IssueStatus.FIXED, null).get(0);

    assertThat(stored.getUuid()).isEqualTo("t1");
    assertThat(stored.getBranchUuid()).isEqualTo("branch-1");
    assertThat(stored.getIssueKey()).isEqualTo("ISSUE-1");
    assertThat(stored.getRetainedStatus()).isEqualTo(IssueStatus.FIXED);
    assertThat(stored.getRuleUuid()).isEqualTo("rule-1");
    assertThat(stored.getComponentKey()).isEqualTo("project-1:src/Foo.cls");
    assertThat(stored.getMessage()).isEqualTo("Avoid the global modifier");
    assertThat(stored.getSeverity()).isEqualTo("MAJOR");
    assertThat(stored.getIssueType()).isEqualTo(1);
    assertThat(stored.getTags()).isEqualTo("design,apex");
    assertThat(stored.getAssigneeUuid()).isEqualTo("user-7");
    assertThat(stored.getStatusMarkedByUuid()).isEqualTo("user-9");
    assertThat(stored.getIssueCreationDate()).isEqualTo(1_000L);
    assertThat(stored.getFixedAt()).isEqualTo(2_000L);
  }

  @Test
  void upsertBranch_insertsOnceThenRefreshesTheNameOfARenamedBranch() {
    branch("branch-1", "project-1", "feature-x", BranchType.BRANCH, null);
    branch("branch-1", "project-1", "feature-x", BranchType.BRANCH, null);
    branch("branch-1", "project-1", "feature-y", BranchType.BRANCH, null);

    assertThat(underTest.selectBranches(db.getSession(), "project-1"))
      .extracting(RetainedBranchDto::getBranchUuid, RetainedBranchDto::getBranchKey)
      .containsExactly(tuple("branch-1", "feature-y"));
  }

  /**
   * A branch is shown as deleted when its uuid has no 'project_branches' row. A branch deleted and re-created with the
   * same name has a new uuid, so the two are listed separately.
   */
  @Test
  void selectBranches_flagsDeletedBranchesAndKeepsAReCreatedBranchSeparate() {
    ProjectData project = db.components().insertPrivateProject();
    String projectUuid = project.projectUuid();
    BranchDto liveBranch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setKey("feature-x"));

    branch(liveBranch.getUuid(), projectUuid, "feature-x", BranchType.BRANCH, null);
    branch("deleted-feature-x", projectUuid, "feature-x", BranchType.BRANCH, null);
    branch("deleted-pr", projectUuid, "42", BranchType.PULL_REQUEST, "main");
    insert(issue("t1", liveBranch.getUuid(), "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", liveBranch.getUuid(), "ISSUE-2", IssueStatus.FIXED));
    insert(issue("t3", "deleted-feature-x", "ISSUE-3", IssueStatus.FIXED));
    insert(issue("t4", "deleted-pr", "ISSUE-4", IssueStatus.FIXED));

    assertThat(underTest.selectBranches(db.getSession(), projectUuid))
      .extracting(RetainedBranchDto::getBranchUuid, RetainedBranchDto::getBranchKey, RetainedBranchDto::getBranchType,
        RetainedBranchDto::getTargetBranchKey, RetainedBranchDto::isActive, RetainedBranchDto::getIssueCount)
      .containsExactlyInAnyOrder(
        tuple(liveBranch.getUuid(), "feature-x", BranchType.BRANCH, null, true, 2L),
        tuple("deleted-feature-x", "feature-x", BranchType.BRANCH, null, false, 1L),
        tuple("deleted-pr", "42", BranchType.PULL_REQUEST, "main", false, 1L));
  }

  @Test
  void selectRetainedIssueKeys_returnsOnlyKeysHeldInThatStatus() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", "branch-1", "ISSUE-2", IssueStatus.ACCEPTED));

    assertThat(underTest.selectRetainedIssueKeys(db.getSession(), IssueStatus.FIXED, List.of("ISSUE-1", "ISSUE-2", "ISSUE-9")))
      .containsExactly("ISSUE-1");
    assertThat(underTest.selectRetainedIssueKeys(db.getSession(), IssueStatus.FIXED, List.of())).isEmpty();
  }

  /**
   * An issue that leaves Fixed was not fixed, so its row goes; if it is fixed again later it is captured again, with
   * the later fix time.
   */
  @Test
  void deleteRetainedIssues_removesOnlyThatStatusAndLetsALaterFixBeCapturedAgain() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", "branch-1", "ISSUE-1", IssueStatus.ACCEPTED));
    insert(issue("t3", "branch-1", "ISSUE-2", IssueStatus.FIXED));

    underTest.deleteRetainedIssues(db.getSession(), IssueStatus.FIXED, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(scroll("project-1", IssueStatus.FIXED, null)).extracting(IssueTrackForRetainDto::getUuid).containsExactly("t3");
    assertThat(scroll("project-1", IssueStatus.ACCEPTED, null)).extracting(IssueTrackForRetainDto::getUuid).containsExactly("t2");

    insert(issue("t4", "branch-1", "ISSUE-1", IssueStatus.FIXED));

    assertThat(scroll("project-1", IssueStatus.FIXED, null)).extracting(IssueTrackForRetainDto::getUuid).containsExactly("t3", "t4");
  }

  @Test
  void scrollIssues_filtersByProjectStatusAndBranch() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    branch("pr-7", "project-1", "7", BranchType.PULL_REQUEST, "main");
    branch("branch-2", "project-2", "main", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", "pr-7", "ISSUE-2", IssueStatus.FIXED));
    insert(issue("t3", "branch-2", "ISSUE-3", IssueStatus.FIXED));

    assertThat(scroll("project-1", IssueStatus.FIXED, null)).extracting(IssueTrackForRetainDto::getUuid).containsExactly("t1", "t2");
    assertThat(scroll("project-1", IssueStatus.FIXED, List.of("pr-7"))).extracting(IssueTrackForRetainDto::getUuid).containsExactly("t2");
    assertThat(underTest.countIssues(db.getSession(), "project-1")).isEqualTo(2);
  }

  /**
   * Only comments are copied, only for retained issues, and copying twice adds nothing.
   */
  @Test
  void copyCommentsOfIssues_copiesCommentsOfRetainedIssuesOnlyAndOnce() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    issueChange("c1", "ISSUE-1", "branch-1", "comment", "first comment", 10L);
    issueChange("c2", "ISSUE-1", "branch-1", "comment", "second comment", 20L);
    issueChange("d1", "ISSUE-1", "branch-1", "diff", "status=OPEN|CLOSED", 30L);
    issueChange("c3", "ISSUE-2", "branch-1", "comment", "not retained", 40L);

    underTest.copyCommentsOfIssues(db.getSession(), List.of("ISSUE-1", "ISSUE-2"));
    underTest.copyCommentsOfIssues(db.getSession(), List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(underTest.selectComments(db.getSession(), List.of("ISSUE-1", "ISSUE-2")))
      .extracting(RetainIssueCommentDto::getUuid, RetainIssueCommentDto::getIssueKey, RetainIssueCommentDto::getChangeData,
        RetainIssueCommentDto::getUserUuid, RetainIssueCommentDto::getIssueChangeCreationDate)
      .containsExactly(
        tuple("c1", "ISSUE-1", "first comment", "user-1", 10L),
        tuple("c2", "ISSUE-1", "second comment", "user-1", 20L));
  }

  @Test
  void copyCommentsOfBranch_copiesCommentsOfThatBranchsRetainedIssues() {
    branch("branch-1", "project-1", "feature-x", BranchType.BRANCH, null);
    branch("branch-2", "project-1", "feature-y", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", "branch-2", "ISSUE-2", IssueStatus.FIXED));
    issueChange("c1", "ISSUE-1", "branch-1", "comment", "on branch 1", 10L);
    issueChange("c2", "ISSUE-2", "branch-2", "comment", "on branch 2", 20L);
    issueChange("c3", "ISSUE-3", "branch-1", "comment", "not retained", 30L);

    underTest.copyCommentsOfBranch(db.getSession(), "branch-1");
    db.getSession().commit();

    assertThat(underTest.selectComments(db.getSession(), List.of("ISSUE-1", "ISSUE-2", "ISSUE-3")))
      .extracting(RetainIssueCommentDto::getUuid).containsExactly("c1");
  }

  @Test
  void deleteByProjectUuid_removesBranchesIssuesAndCommentsOfThatProjectOnly() {
    branch("branch-1", "project-1", "main", BranchType.BRANCH, null);
    branch("deleted-pr", "project-1", "42", BranchType.PULL_REQUEST, "main");
    branch("branch-2", "project-2", "main", BranchType.BRANCH, null);
    insert(issue("t1", "branch-1", "ISSUE-1", IssueStatus.FIXED));
    insert(issue("t2", "deleted-pr", "ISSUE-2", IssueStatus.FIXED));
    insert(issue("t3", "branch-2", "ISSUE-3", IssueStatus.FIXED));
    issueChange("c1", "ISSUE-1", "branch-1", "comment", "project 1", 10L);
    issueChange("c3", "ISSUE-3", "branch-2", "comment", "project 2", 30L);
    underTest.copyCommentsOfIssues(db.getSession(), List.of("ISSUE-1", "ISSUE-3"));
    db.getSession().commit();

    underTest.deleteByProjectUuid(db.getSession(), "project-1");
    db.getSession().commit();

    assertThat(underTest.selectBranches(db.getSession(), "project-1")).isEmpty();
    assertThat(underTest.countIssues(db.getSession(), "project-1")).isZero();
    assertThat(underTest.selectComments(db.getSession(), List.of("ISSUE-1", "ISSUE-3")))
      .extracting(RetainIssueCommentDto::getUuid).containsExactly("c3");
    assertThat(underTest.selectBranches(db.getSession(), "project-2")).hasSize(1);
    assertThat(underTest.countIssues(db.getSession(), "project-2")).isEqualTo(1);
  }

  private void branch(String branchUuid, String projectUuid, String branchKey, BranchType branchType, String targetBranchKey) {
    underTest.upsertBranch(db.getSession(), new RetainDetailsDto()
      .setBranchUuid(branchUuid)
      .setProjectUuid(projectUuid)
      .setBranchKey(branchKey)
      .setBranchType(branchType)
      .setTargetBranchKey(targetBranchKey));
    db.getSession().commit();
  }

  private void insert(IssueTrackForRetainDto dto) {
    underTest.insertIssue(db.getSession(), dto);
    db.getSession().commit();
  }

  private IssueTrackForRetainDto issue(String uuid, String branchUuid, String issueKey, IssueStatus retainedStatus) {
    return new IssueTrackForRetainDto()
      .setUuid(uuid)
      .setBranchUuid(branchUuid)
      .setIssueKey(issueKey)
      .setRetainedStatus(retainedStatus)
      .setRuleUuid("rule-1")
      .setComponentKey("project-1:src/Foo.cls")
      .setFixedAt(++fixedAt);
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

  private List<IssueTrackForRetainDto> scroll(String projectUuid, IssueStatus retainedStatus, List<String> branchUuids) {
    List<IssueTrackForRetainDto> collected = new ArrayList<>();
    underTest.scrollIssues(db.getSession(), projectUuid, retainedStatus, branchUuids, context -> collected.add(context.getResultObject()));
    return collected;
  }
}
