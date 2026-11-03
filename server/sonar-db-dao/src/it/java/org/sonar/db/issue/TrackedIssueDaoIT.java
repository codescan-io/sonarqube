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

class TrackedIssueDaoIT {

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final TrackedIssueDao underTest = new TrackedIssueDao();

  private long capturedAt = 0;

  @Test
  void insert_thenScroll_returnsEveryStoredField() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED)
      .setBaseBranchKey("develop")
      .setRuleKey("apex:AvoidGlobalModifier")
      .setComponentKey("project-1:src/Foo.cls")
      .setLine(12)
      .setMessage("Avoid the global modifier")
      .setSeverity("MAJOR")
      .setIssueType("CODE_SMELL")
      .setCleanCodeAttribute("CLEAR")
      .setImpacts("[{\"softwareQuality\":\"MAINTAINABILITY\",\"severity\":\"MEDIUM\"}]")
      .setTags("design,apex")
      .setAuthorLogin("author@example.com")
      .setAssigneeUuid("user-7")
      .setEffort(15)
      .setStatus("CLOSED")
      .setResolution("FIXED")
      .setIssueCreationDate(1_000L)
      .setIssueCloseDate(2_000L)
      .setCapturedByUuid("user-9"));

    TrackedIssueDto stored = scroll("project-1", IssueStatus.FIXED, null).get(0);

    assertThat(stored.getUuid()).isEqualTo("h1");
    assertThat(stored.getOrganizationUuid()).isEqualTo("org-1");
    assertThat(stored.getProjectUuid()).isEqualTo("project-1");
    assertThat(stored.getBranchKey()).isEqualTo("main");
    assertThat(stored.getBranchType()).isEqualTo(BranchType.BRANCH);
    assertThat(stored.getBaseBranchKey()).isEqualTo("develop");
    assertThat(stored.getIssueKey()).isEqualTo("ISSUE-1");
    assertThat(stored.getRuleKey()).isEqualTo("apex:AvoidGlobalModifier");
    assertThat(stored.getComponentKey()).isEqualTo("project-1:src/Foo.cls");
    assertThat(stored.getLine()).isEqualTo(12);
    assertThat(stored.getMessage()).isEqualTo("Avoid the global modifier");
    assertThat(stored.getSeverity()).isEqualTo("MAJOR");
    assertThat(stored.getIssueType()).isEqualTo("CODE_SMELL");
    assertThat(stored.getCleanCodeAttribute()).isEqualTo("CLEAR");
    assertThat(stored.getImpacts()).isEqualTo("[{\"softwareQuality\":\"MAINTAINABILITY\",\"severity\":\"MEDIUM\"}]");
    assertThat(stored.getTags()).isEqualTo("design,apex");
    assertThat(stored.getAuthorLogin()).isEqualTo("author@example.com");
    assertThat(stored.getAssigneeUuid()).isEqualTo("user-7");
    assertThat(stored.getEffort()).isEqualTo(15);
    assertThat(stored.getIssueStatus()).isEqualTo(IssueStatus.FIXED);
    assertThat(stored.getStatus()).isEqualTo("CLOSED");
    assertThat(stored.getResolution()).isEqualTo("FIXED");
    assertThat(stored.getIssueCreationDate()).isEqualTo(1_000L);
    assertThat(stored.getIssueCloseDate()).isEqualTo(2_000L);
    assertThat(stored.getCapturedBy()).isEqualTo(TrackedIssueDto.CaptureSource.ANALYSIS);
    assertThat(stored.getCapturedByUuid()).isEqualTo("user-9");
  }

  @Test
  void insert_keepsARowWhoseBranchWasNeverRecordedAsAUuid() {
    insert(row("h1", "project-1", "deleted-pr", BranchType.PULL_REQUEST, "ISSUE-1", IssueStatus.FIXED).setBranchUuid(null));

    assertThat(scroll("project-1", IssueStatus.FIXED, null))
      .extracting(TrackedIssueDto::getBranchUuid, TrackedIssueDto::getBranchKey)
      .containsExactly(tuple(null, "deleted-pr"));
  }

  @Test
  void scroll_filtersByStatusAndByBranch() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    insert(row("h2", "project-1", "pr-7", BranchType.PULL_REQUEST, "ISSUE-2", IssueStatus.FIXED));
    insert(row("h3", "project-1", "main", BranchType.BRANCH, "ISSUE-3", IssueStatus.ACCEPTED));
    insert(row("h4", "project-2", "main", BranchType.BRANCH, "ISSUE-4", IssueStatus.FIXED));

    assertThat(scroll("project-1", IssueStatus.FIXED, null))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h1", "h2");
    assertThat(scroll("project-1", IssueStatus.FIXED, List.of("pr-7")))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h2");
    assertThat(scroll("project-1", IssueStatus.ACCEPTED, null))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h3");
  }

  @Test
  void selectCapturedIssueKeys_returnsOnlyTheKeysAlreadyHeldForThatBranchAndStatus() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    insert(row("h2", "project-1", "main", BranchType.BRANCH, "ISSUE-2", IssueStatus.ACCEPTED));
    insert(row("h3", "project-1", "pr-7", BranchType.PULL_REQUEST, "ISSUE-3", IssueStatus.FIXED));

    assertThat(underTest.selectCapturedIssueKeys(db.getSession(), "project-1", "main", IssueStatus.FIXED,
      List.of("ISSUE-1", "ISSUE-2", "ISSUE-3", "ISSUE-4")))
      .containsExactly("ISSUE-1");
  }

  @Test
  void selectCapturedIssueKeys_whenNoKeyIsAsked_doesNotQuery() {
    assertThat(underTest.selectCapturedIssueKeys(db.getSession(), "project-1", "main", IssueStatus.FIXED, List.of())).isEmpty();
  }

  @Test
  void selectBranches_marksABranchThatStillExistsAsActiveAndADeletedOneAsNot() {
    ProjectData project = db.components().insertPrivateProject();
    String projectUuid = project.projectUuid();
    BranchDto liveBranch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setKey("feature/live"));

    insert(row("h1", projectUuid, liveBranch.getKey(), BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    insert(row("h2", projectUuid, liveBranch.getKey(), BranchType.BRANCH, "ISSUE-2", IssueStatus.FIXED));
    insert(row("h3", projectUuid, "pr-42", BranchType.PULL_REQUEST, "ISSUE-3", IssueStatus.FIXED).setBaseBranchKey("main"));

    assertThat(underTest.selectBranches(db.getSession(), projectUuid))
      .extracting(TrackedIssueBranchDto::branchKey, TrackedIssueBranchDto::branchType,
        TrackedIssueBranchDto::baseBranchKey, TrackedIssueBranchDto::active, TrackedIssueBranchDto::issueCount)
      .containsExactlyInAnyOrder(
        tuple("feature/live", BranchType.BRANCH, null, true, 2L),
        tuple("pr-42", BranchType.PULL_REQUEST, "main", false, 1L));
  }

  /**
   * An issue that reopens was never fixed, so its row goes. Only that issue, on that branch, in that status.
   */
  @Test
  void deleteCaptured_removesOnlyTheGivenIssuesOfThatBranchAndStatus() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    insert(row("h2", "project-1", "main", BranchType.BRANCH, "ISSUE-2", IssueStatus.FIXED));
    insert(row("h3", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.ACCEPTED));
    insert(row("h4", "project-1", "pr-7", BranchType.PULL_REQUEST, "ISSUE-1", IssueStatus.FIXED));

    underTest.deleteCaptured(db.getSession(), "project-1", "main", IssueStatus.FIXED, List.of("ISSUE-1", "ISSUE-9"));
    db.getSession().commit();

    assertThat(scroll("project-1", IssueStatus.FIXED, null))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h2", "h4");
    assertThat(scroll("project-1", IssueStatus.ACCEPTED, null))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h3");
  }

  /**
   * The row is dropped on reopen and written again if the issue is genuinely fixed later, so a fix that stuck
   * counts once and a fix that did not counts zero times.
   */
  @Test
  void deleteCaptured_thenInsert_letsALaterFixBeCapturedAgain() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    underTest.deleteCaptured(db.getSession(), "project-1", "main", IssueStatus.FIXED, List.of("ISSUE-1"));
    db.getSession().commit();

    assertThat(underTest.selectCapturedIssueKeys(db.getSession(), "project-1", "main", IssueStatus.FIXED,
      List.of("ISSUE-1"))).isEmpty();

    insert(row("h2", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));

    assertThat(scroll("project-1", IssueStatus.FIXED, null))
      .extracting(TrackedIssueDto::getUuid).containsExactly("h2");
  }

  @Test
  void deleteCaptured_whenNoKeyIsGiven_doesNothing() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));

    underTest.deleteCaptured(db.getSession(), "project-1", "main", IssueStatus.FIXED, List.of());

    assertThat(underTest.countByProjectUuid(db.getSession(), "project-1")).isEqualTo(1);
  }

  @Test
  void deleteByProjectUuid_removesOnlyThatProjectIncludingItsDeletedBranches() {
    insert(row("h1", "project-1", "main", BranchType.BRANCH, "ISSUE-1", IssueStatus.FIXED));
    insert(row("h2", "project-1", "deleted-pr", BranchType.PULL_REQUEST, "ISSUE-2", IssueStatus.FIXED));
    insert(row("h3", "project-2", "main", BranchType.BRANCH, "ISSUE-3", IssueStatus.FIXED));

    underTest.deleteByProjectUuid(db.getSession(), "project-1");
    db.getSession().commit();

    assertThat(underTest.countByProjectUuid(db.getSession(), "project-1")).isZero();
    assertThat(underTest.countByProjectUuid(db.getSession(), "project-2")).isEqualTo(1);
  }

  private void insert(TrackedIssueDto dto) {
    underTest.insert(db.getSession(), dto);
    db.getSession().commit();
  }

  private List<TrackedIssueDto> scroll(String projectUuid, IssueStatus issueStatus, List<String> branchKeys) {
    List<TrackedIssueDto> collected = new ArrayList<>();
    underTest.scroll(db.getSession(), projectUuid, issueStatus, branchKeys, context -> collected.add(context.getResultObject()));
    return collected;
  }

  private TrackedIssueDto row(String uuid, String projectUuid, String branchKey, BranchType branchType,
    String issueKey, IssueStatus issueStatus) {
    return new TrackedIssueDto()
      .setUuid(uuid)
      .setOrganizationUuid("org-1")
      .setProjectUuid(projectUuid)
      .setBranchUuid("branch-of-" + branchKey)
      .setBranchKey(branchKey)
      .setBranchType(branchType)
      .setIssueKey(issueKey)
      .setIssueStatus(issueStatus)
      .setCapturedAt(++capturedAt)
      .setCapturedBy(TrackedIssueDto.CaptureSource.ANALYSIS);
  }
}
