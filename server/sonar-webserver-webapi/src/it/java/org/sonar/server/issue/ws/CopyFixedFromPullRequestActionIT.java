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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.System2;
import org.sonar.core.component.ComponentKeys;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.api.web.UserRole.ADMIN;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.BranchType.PULL_REQUEST;
import static org.sonar.db.component.ComponentTesting.newFileDto;

class CopyFixedFromPullRequestActionIT {

  @RegisterExtension
  private final UserSessionRule userSession = UserSessionRule.standalone();
  @RegisterExtension
  private final DbTester db = DbTester.create();

  private final DbClient dbClient = db.getDbClient();
  /** Commits like the real indexer, without needing Elasticsearch. */
  private final IssueIndexer issueIndexer = mock(IssueIndexer.class);
  private final WsActionTester ws = new WsActionTester(
    new CopyFixedFromPullRequestAction(dbClient, userSession, issueIndexer, new SequenceUuidFactory(), System2.INSTANCE));

  private ProjectData projectData;
  private ComponentDto mainBranch;
  private ComponentDto mainFile;
  private ComponentDto pullRequest;
  private ComponentDto pullRequestFile;
  private RuleDto rule;

  @BeforeEach
  void commitOnIndex() {
    doAnswer(invocation -> {
      ((DbSession) invocation.getArgument(0)).commit();
      return null;
    }).when(issueIndexer).commitAndIndexIssues(any(), any());
  }

  private void setUpProjectWithPullRequest() {
    projectData = db.components().insertPrivateProject();
    mainBranch = projectData.getMainBranchComponent();
    mainFile = db.components().insertComponent(newFileDto(mainBranch));
    pullRequest = db.components().insertProjectBranch(mainBranch, b -> b.setBranchType(PULL_REQUEST).setKey("42"));
    pullRequestFile = db.components().insertComponent(newFileDto(pullRequest, mainBranch.uuid()).setKey(mainFile.getKey()));
    rule = db.rules().insertIssueRule();
  }

  @Test
  void copies_a_fixed_pull_request_issue_to_the_branch_as_fixed() {
    setUpProjectWithPullRequest();
    IssueDto fixed = db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-1").setLine(null));
    logInAsAdmin();

    String json = call().execute().getInput();

    assertThat(json).contains("\"copied\":1");
    verify(issueIndexer).commitAndIndexIssues(any(), argThat(issues -> issues.size() == 1));
    List<IssueDto> onMain = issuesOf(mainFile);
    assertThat(onMain).hasSize(1);
    IssueDto copy = onMain.get(0);
    assertThat(copy.getKey()).isNotEqualTo(fixed.getKey());
    assertThat(copy.getProjectUuid()).isEqualTo(mainBranch.uuid());
    assertThat(copy.getComponentUuid()).isEqualTo(mainFile.uuid());
    assertThat(copy.getStatus()).isEqualTo(STATUS_CLOSED);
    assertThat(copy.getResolution()).isEqualTo(RESOLUTION_FIXED);
    assertThat(copy.getRuleUuid()).isEqualTo(rule.getUuid());
    assertThat(copy.getChecksum()).isEqualTo("hash-1");
    assertThat(copy.getLine()).isNull();
    assertThat(copy.getIssueCloseDate()).isNotNull();

    try (DbSession session = dbClient.openSession(false)) {
      List<IssueChangeDto> changes = dbClient.issueChangeDao().selectByIssueKeys(session, List.of(copy.getKey()));
      // A single system changelog entry, no comment and no author.
      assertThat(changes).extracting(IssueChangeDto::getChangeType).containsExactly(IssueChangeDto.TYPE_FIELD_CHANGE);
      assertThat(changes.get(0).getUserUuid()).isNull();
      FieldDiffs diffs = FieldDiffs.parse(changes.get(0).getChangeData());
      assertThat(diffs.get("from_branch").oldValue()).isEqualTo("#42");
      assertThat(diffs.get("from_branch").newValue()).isEqualTo("main");
      assertThat(diffs.get("resolution").newValue()).isEqualTo(RESOLUTION_FIXED);
      assertThat(diffs.get("status").oldValue()).isEqualTo(STATUS_OPEN);
      assertThat(diffs.get("status").newValue()).isEqualTo(STATUS_CLOSED);
    }
    // The pull request issue itself is left untouched.
    assertThat(dbClient.issueDao().selectOrFailByKey(db.getSession(), fixed.getKey()).getProjectUuid()).isEqualTo(pullRequest.uuid());
  }

  @Test
  void calling_twice_does_not_copy_twice() {
    setUpProjectWithPullRequest();
    db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-1"));
    logInAsAdmin();

    call().execute();
    String json = call().execute().getInput();

    assertThat(json).contains("\"copied\":0").contains("\"alreadyOnBranch\":1");
    assertThat(issuesOf(mainFile)).hasSize(1);
  }

  @Test
  void skips_an_issue_the_branch_already_has() {
    setUpProjectWithPullRequest();
    db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-1"));
    IssueDto openOnMain = db.issues().insertIssue(rule, mainBranch, mainFile,
      i -> i.setStatus(STATUS_OPEN).setResolution(null).setChecksum("hash-1"));
    logInAsAdmin();

    String json = call().execute().getInput();

    assertThat(json).contains("\"copied\":0").contains("\"alreadyOnBranch\":1");
    assertThat(issuesOf(mainFile)).extracting(IssueDto::getKey).containsExactly(openOnMain.getKey());
    assertThat(issuesOf(mainFile).get(0).getStatus()).isEqualTo(STATUS_OPEN);
  }

  @Test
  void only_copies_fixed_issues() {
    setUpProjectWithPullRequest();
    db.issues().insertIssue(rule, pullRequest, pullRequestFile, i -> i.setStatus(STATUS_OPEN).setResolution(null));
    db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_RESOLVED).setResolution(RESOLUTION_FALSE_POSITIVE));
    logInAsAdmin();

    String json = call().execute().getInput();

    assertThat(json).contains("\"copied\":0");
    assertThat(issuesOf(mainFile)).isEmpty();
  }

  @Test
  void keeps_a_file_that_only_existed_in_the_pull_request_as_a_disabled_file_on_the_branch() {
    setUpProjectWithPullRequest();
    ComponentDto fileOnlyInPr = db.components().insertComponent(newFileDto(pullRequest, mainBranch.uuid()).setEnabled(false));
    db.issues().insertIssue(rule, pullRequest, fileOnlyInPr,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-2"));
    logInAsAdmin();

    String json = call().execute().getInput();
    // A second call reuses the disabled file instead of adding it again.
    String secondJson = call().execute().getInput();

    assertThat(json).contains("\"copied\":1").contains("\"deletedFilesAdded\":1");
    assertThat(secondJson).contains("\"copied\":0").contains("\"alreadyOnBranch\":1").contains("\"deletedFilesAdded\":0");
    try (DbSession session = dbClient.openSession(false)) {
      ComponentDto fileOnMain = dbClient.componentDao().selectByKeyAndBranch(session, fileOnlyInPr.getKey(), "main").orElseThrow();
      assertThat(fileOnMain.uuid()).isNotEqualTo(fileOnlyInPr.uuid());
      assertThat(fileOnMain.branchUuid()).isEqualTo(mainBranch.uuid());
      assertThat(fileOnMain.isEnabled()).isFalse();
      assertThat(fileOnMain.path()).isEqualTo(fileOnlyInPr.path());
      assertThat(issuesOf(fileOnMain)).extracting(IssueDto::getStatus, IssueDto::getResolution)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(STATUS_CLOSED, RESOLUTION_FIXED));
    }
  }

  @Test
  void requires_administer_permission_on_the_project() {
    setUpProjectWithPullRequest();
    userSession.logIn().addProjectPermission(USER, projectData.getProjectDto());

    TestRequest request = call();
    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void copies_to_a_pull_request_of_another_project_matching_the_file_by_path() {
    setUpProjectWithPullRequest();
    IssueDto fixed = db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-1"));
    ProjectData otherProject = db.components().insertPrivateProject();
    ComponentDto otherPullRequest = db.components().insertProjectBranch(otherProject.getMainBranchComponent(),
      b -> b.setBranchType(PULL_REQUEST).setKey("25"));
    ComponentDto otherPullRequestFile = db.components().insertComponent(newFileDto(otherPullRequest, otherProject.getMainBranchComponent().uuid())
      .setKey(ComponentKeys.createEffectiveKey(otherProject.getProjectDto().getKey(), pullRequestFile.path()))
      .setPath(pullRequestFile.path()));
    logInAsAdmin();
    userSession.addProjectPermission(ADMIN, otherProject.getProjectDto());

    String json = ws.newRequest()
      .setParam("project", projectData.getProjectDto().getKey())
      .setParam("pullRequest", "42")
      .setParam("targetProject", otherProject.getProjectDto().getKey())
      .setParam("targetPullRequest", "25")
      .execute().getInput();

    assertThat(json).contains("\"copied\":1").contains("\"deletedFilesAdded\":0");
    List<IssueDto> onOtherPullRequest = issuesOf(otherPullRequestFile);
    assertThat(onOtherPullRequest).hasSize(1);
    IssueDto copy = onOtherPullRequest.get(0);
    assertThat(copy.getProjectUuid()).isEqualTo(otherPullRequest.uuid());
    assertThat(copy.getStatus()).isEqualTo(STATUS_CLOSED);
    assertThat(copy.getResolution()).isEqualTo(RESOLUTION_FIXED);
    assertThat(copy.getChecksum()).isEqualTo("hash-1");
    // Nothing is copied to the source project.
    assertThat(issuesOf(mainFile)).isEmpty();
    assertThat(dbClient.issueDao().selectOrFailByKey(db.getSession(), fixed.getKey()).getProjectUuid()).isEqualTo(pullRequest.uuid());
  }

  @Test
  void adds_a_missing_file_to_the_target_pull_request_under_the_target_project_key() {
    setUpProjectWithPullRequest();
    db.issues().insertIssue(rule, pullRequest, pullRequestFile,
      i -> i.setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED).setChecksum("hash-1"));
    ProjectData otherProject = db.components().insertPrivateProject();
    db.components().insertProjectBranch(otherProject.getMainBranchComponent(), b -> b.setBranchType(PULL_REQUEST).setKey("25"));
    logInAsAdmin();
    userSession.addProjectPermission(ADMIN, otherProject.getProjectDto());

    String json = ws.newRequest()
      .setParam("project", projectData.getProjectDto().getKey())
      .setParam("pullRequest", "42")
      .setParam("targetProject", otherProject.getProjectDto().getKey())
      .setParam("targetPullRequest", "25")
      .execute().getInput();

    assertThat(json).contains("\"copied\":1").contains("\"deletedFilesAdded\":1");
    try (DbSession session = dbClient.openSession(false)) {
      String expectedKey = ComponentKeys.createEffectiveKey(otherProject.getProjectDto().getKey(), pullRequestFile.path());
      ComponentDto added = dbClient.componentDao().selectByKeyAndPullRequest(session, expectedKey, "25").orElseThrow();
      assertThat(added.isEnabled()).isFalse();
      assertThat(issuesOf(added)).hasSize(1);
    }
  }

  @Test
  void requires_administer_permission_on_the_target_project() {
    setUpProjectWithPullRequest();
    ProjectData otherProject = db.components().insertPrivateProject();
    db.components().insertProjectBranch(otherProject.getMainBranchComponent(), b -> b.setBranchType(PULL_REQUEST).setKey("25"));
    logInAsAdmin();

    TestRequest request = ws.newRequest()
      .setParam("project", projectData.getProjectDto().getKey())
      .setParam("pullRequest", "42")
      .setParam("targetProject", otherProject.getProjectDto().getKey())
      .setParam("targetPullRequest", "25");
    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  @Test
  void requires_exactly_one_of_branch_and_target_pull_request() {
    setUpProjectWithPullRequest();
    logInAsAdmin();

    TestRequest neither = ws.newRequest()
      .setParam("project", projectData.getProjectDto().getKey())
      .setParam("pullRequest", "42");
    TestRequest both = call().setParam("targetPullRequest", "25");
    assertThatThrownBy(neither::execute).isInstanceOf(BadRequestException.class);
    assertThatThrownBy(both::execute).isInstanceOf(BadRequestException.class);
  }

  private void logInAsAdmin() {
    userSession.logIn().addProjectPermission(ADMIN, projectData.getProjectDto());
  }

  private TestRequest call() {
    return ws.newRequest()
      .setParam("project", projectData.getProjectDto().getKey())
      .setParam("pullRequest", "42")
      .setParam("branch", "main");
  }

  private List<IssueDto> issuesOf(ComponentDto file) {
    try (DbSession session = dbClient.openSession(false)) {
      return dbClient.issueDao().selectByKeys(session, dbClient.issueDao().selectIssueKeysByFileUuid(session, file.uuid()));
    }
  }
}
