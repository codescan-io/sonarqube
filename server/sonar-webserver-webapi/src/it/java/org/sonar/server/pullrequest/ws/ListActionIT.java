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
package org.sonar.server.pullrequest.ws;

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentTypes;
import org.sonar.server.component.ComponentTypesRule;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.permission.index.WebAuthorizationTypeSupport;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.ProjectPullRequests.ListWsResponse;
import org.sonarqube.ws.ProjectPullRequests.PullRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.BranchType.BRANCH;
import static org.sonar.db.component.BranchType.PULL_REQUEST;
import static org.sonar.db.component.ComponentQualifiers.PROJECT;

public class ListActionIT {

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final ComponentTypes componentTypes = new ComponentTypesRule().setRootQualifiers(PROJECT);
  private final IssueIndex issueIndex = new IssueIndex(es.client(), System2.INSTANCE, userSession,
    new WebAuthorizationTypeSupport(userSession), mock(Configuration.class));

  private final WsActionTester ws = new WsActionTester(
    new ListAction(db.getDbClient(), userSession, new ComponentFinder(db.getDbClient(), componentTypes), issueIndex));

  @Test
  public void returnsTheTargetBranchUuidOfEachPullRequest() {
    ProjectData projectData = db.components().insertPrivateProject();
    ProjectDto project = projectData.getProjectDto();
    BranchDto develop = db.components().insertProjectBranch(project, b -> b.setKey("develop").setBranchType(BRANCH));
    db.components().insertProjectBranch(project, b -> b.setKey("12").setBranchType(PULL_REQUEST)
      .setMergeBranchUuid(projectData.getMainBranchDto().getUuid()));
    db.components().insertProjectBranch(project, b -> b.setKey("13").setBranchType(PULL_REQUEST).setMergeBranchUuid(develop.getUuid()));
    userSession.logIn().addProjectPermission(USER, project);

    ListWsResponse response = ws.newRequest().setParam("project", project.getKey()).executeProtobuf(ListWsResponse.class);

    assertThat(response.getPullRequestsList())
      .extracting(PullRequest::getKey, PullRequest::getTargetBranchUuid, PullRequest::getBase, PullRequest::getIsOrphan)
      .containsExactlyInAnyOrder(
        tuple("12", projectData.getMainBranchDto().getUuid(), projectData.getMainBranchDto().getKey(), false),
        tuple("13", develop.getUuid(), "develop", false));
  }

  @Test
  public void keepsTheTargetBranchUuidOfAnOrphanPullRequest() {
    ProjectData projectData = db.components().insertPrivateProject();
    ProjectDto project = projectData.getProjectDto();
    db.components().insertProjectBranch(project, b -> b.setKey("14").setBranchType(PULL_REQUEST).setMergeBranchUuid("deleted-branch-uuid"));
    userSession.logIn().addProjectPermission(USER, project);

    ListWsResponse response = ws.newRequest().setParam("project", project.getKey()).executeProtobuf(ListWsResponse.class);

    PullRequest pullRequest = response.getPullRequests(0);
    assertThat(pullRequest.getIsOrphan()).isTrue();
    assertThat(pullRequest.hasBase()).isFalse();
    assertThat(pullRequest.getTargetBranchUuid()).isEqualTo("deleted-branch-uuid");
  }
}
