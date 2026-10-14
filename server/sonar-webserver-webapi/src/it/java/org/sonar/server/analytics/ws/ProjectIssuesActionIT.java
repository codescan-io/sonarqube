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
package org.sonar.server.analytics.ws;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.Configuration;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.permission.UserPermissionDto;
import org.sonar.db.user.UserIdDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.index.AsyncIssueIndexing;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.permission.index.WebAuthorizationTypeSupport;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.test.JsonAssert.assertJson;

class ProjectIssuesActionIT {

  @RegisterExtension
  public final DbTester db = DbTester.create();
  @RegisterExtension
  public final EsTester es = EsTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final IssueIndex issueIndex = new IssueIndex(es.client(), System2.INSTANCE, userSession,
    new WebAuthorizationTypeSupport(userSession), mock(Configuration.class));
  private final IssueIndexer issueIndexer = new IssueIndexer(es.client(), db.getDbClient(), new IssueIteratorFactory(db.getDbClient()),
    mock(AsyncIssueIndexing.class));
  private final PermissionIndexer permissionIndexer = new PermissionIndexer(db.getDbClient(), es.client(), issueIndexer);
  private final WsActionTester ws = new WsActionTester(
    new ProjectIssuesAction(db.getDbClient(), issueIndex, new AnalyticsWsSupport(db.getDbClient(), userSession)));

  private OrganizationDto organization;
  private UserIdDto user;
  private RuleDto rule;

  @BeforeEach
  void setUp() {
    organization = testing.insertOrganization("analytics-org");
    user = new UserIdDto(Uuids.createFast(), "analytics-admin");
    when(userSession.getUuid()).thenReturn(user.getUuid());
    when(userSession.getGroups()).thenReturn(List.of());
    rule = db.rules().insertIssueRule();
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("project_issues");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization");
  }

  @Test
  void count_unresolved_issues_of_main_branches_by_type_and_severity() {
    ProjectData project = insertBrowsableProject(organization);
    ComponentDto file = insertFile(project.getMainBranchComponent());
    insertIssue(project.getMainBranchComponent(), file, RuleType.BUG, "MAJOR");
    insertIssue(project.getMainBranchComponent(), file, RuleType.BUG, "MAJOR");
    insertIssue(project.getMainBranchComponent(), file, RuleType.VULNERABILITY, "BLOCKER");
    insertIssue(project.getMainBranchComponent(), file, RuleType.CODE_SMELL, "MINOR");
    db.issues().insertIssue(rule, project.getMainBranchComponent(), file,
      i -> i.setType(RuleType.CODE_SMELL).setSeverity("INFO").setStatus(STATUS_CLOSED).setResolution(RESOLUTION_FIXED));
    ComponentDto branch = db.components().insertProjectBranch(project.getMainBranchComponent())
      .setOrganizationUuid(organization.getUuid());
    insertIssue(branch, insertFile(branch), RuleType.BUG, "CRITICAL");
    index();

    String json = call().execute().getInput();

    assertJson(json).isSimilarTo("{\"projects\": [{\"key\": \"" + project.projectKey() + "\", \"issues\": {" +
      "\"BUG\": {\"MAJOR\": 2}, \"CODE_SMELL\": {\"MINOR\": 1}, \"VULNERABILITY\": {\"BLOCKER\": 1}}}]}");
    assertThat(json).doesNotContain("CRITICAL", "INFO");
  }

  @Test
  void leave_out_projects_the_user_cannot_browse_and_projects_of_other_organizations() {
    ProjectData notBrowsable = testing.insertProject(organization);
    insertIssue(notBrowsable.getMainBranchComponent(), insertFile(notBrowsable.getMainBranchComponent()), RuleType.BUG, "MAJOR");
    ProjectData otherOrganization = insertBrowsableProject(testing.insertOrganization("analytics-other-org"));
    insertIssue(otherOrganization.getMainBranchComponent(), insertFile(otherOrganization.getMainBranchComponent()), RuleType.BUG, "MAJOR");
    index();

    assertThat(call().execute().getInput()).isEqualTo("{\"projects\":[]}");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private ProjectData insertBrowsableProject(OrganizationDto organization) {
    ProjectData project = testing.insertProject(organization);
    // only the permission row is indexed, so no user row is needed
    db.getDbClient().userPermissionDao().insert(db.getSession(),
      new UserPermissionDto(Uuids.createFast(), organization.getUuid(), USER, user.getUuid(), project.projectUuid()),
      project.getProjectDto(), user, null);
    db.commit();
    return project;
  }

  /** As the analysis does, the file carries the organization, which issues are indexed with. */
  private ComponentDto insertFile(ComponentDto branch) {
    return db.components().insertComponent(newFileDto(branch).setOrganizationUuid(branch.getOrganizationUuid()));
  }

  private void insertIssue(ComponentDto branch, ComponentDto file, RuleType type, String severity) {
    db.issues().insertIssue(rule, branch, file, i -> i.setType(type).setSeverity(severity));
  }

  private void index() {
    issueIndexer.indexAllIssues();
    permissionIndexer.indexAll(permissionIndexer.getIndexTypes());
  }

  private TestRequest call() {
    return ws.newRequest().setParam("organization", organization.getKey());
  }
}
