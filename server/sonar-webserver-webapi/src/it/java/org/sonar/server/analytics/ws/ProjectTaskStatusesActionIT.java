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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.test.JsonAssert.assertJson;

class ProjectTaskStatusesActionIT {

  // the latest finished task is the one recorded last, as api/ce/component reads it
  private final TestSystem2 system2 = new TestSystem2().setNow(1_000L);
  @RegisterExtension
  public final DbTester db = DbTester.create(system2);

  private final UserSession userSession = mock(UserSession.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new ProjectTaskStatusesAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession)));

  private OrganizationDto organization;

  @BeforeEach
  void setUp() {
    organization = testing.insertOrganization("analytics-org");
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("project_task_statuses");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization");
  }

  @Test
  void latest_finished_task_whatever_its_type_and_branch() {
    ProjectData project = testing.insertProject(organization);
    testing.insertActivity(testing.newTask(project, project.getMainBranchDto(), 1_000L), CeActivityDto.Status.SUCCESS, 1_000L, 10L);
    system2.setNow(3_000L);
    CeQueueDto otherType = testing.newTask(project, project.getMainBranchDto(), 3_000L).setTaskType(CeTaskTypes.AUDIT_PURGE);
    testing.insertActivity(otherType, CeActivityDto.Status.FAILED, 3_000L, 10L);
    // a canceled task never becomes the current one
    system2.setNow(5_000L);
    testing.insertActivity(testing.newTask(project, project.getMainBranchDto(), 5_000L), CeActivityDto.Status.CANCELED, null, null);

    assertJson(call().execute().getInput()).isSimilarTo(
      "{\"projects\": [{\"key\": \"" + project.projectKey() + "\", \"status\": \"FAILED\"}]}");
  }

  @Test
  void queued_task_wins_over_finished_ones_and_running_over_pending() {
    ProjectData pending = testing.insertProject(organization);
    testing.insertActivity(testing.newTask(pending, pending.getMainBranchDto(), 1_000L), CeActivityDto.Status.SUCCESS, 1_000L, 10L);
    testing.insertQueued(testing.newTask(pending, pending.getMainBranchDto(), 2_000L), CeQueueDto.Status.PENDING);
    ProjectData running = testing.insertProject(organization);
    testing.insertQueued(testing.newTask(running, running.getMainBranchDto(), 1_000L), CeQueueDto.Status.IN_PROGRESS);
    testing.insertQueued(testing.newTask(running, running.getMainBranchDto(), 2_000L), CeQueueDto.Status.PENDING);

    String json = call().execute().getInput();

    assertJson(json).isSimilarTo("{\"projects\": [" +
      "{\"key\": \"" + pending.projectKey() + "\", \"status\": \"PENDING\"}," +
      "{\"key\": \"" + running.projectKey() + "\", \"status\": \"IN_PROGRESS\"}]}");
  }

  @Test
  void leave_out_projects_without_task_and_projects_of_other_organizations() {
    testing.insertProject(organization);
    ProjectData otherProject = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    testing.insertActivity(testing.newTask(otherProject, otherProject.getMainBranchDto(), 1_000L), CeActivityDto.Status.SUCCESS, 1_000L, 10L);
    testing.insertQueued(testing.newTask(otherProject, otherProject.getMainBranchDto(), 2_000L), CeQueueDto.Status.PENDING);

    assertThat(call().execute().getInput()).isEqualTo("{\"projects\":[]}");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private TestRequest call() {
    return ws.newRequest().setParam("organization", organization.getKey());
  }
}
