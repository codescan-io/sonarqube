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
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeQueueDto;
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
import static org.mockito.Mockito.when;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.test.JsonAssert.assertJson;

class QueuedTasksActionIT {

  private static final long NOW = 1_000_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new QueuedTasksAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData project;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("queued_tasks");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "p", "ps");
  }

  @Test
  void running_tasks_first_then_pending_ones_oldest_first() {
    testing.insertQueued(testing.newTask(project, project.getMainBranchDto(), 900_000L), CeQueueDto.Status.PENDING);
    testing.insertQueued(testing.newTask(project, project.getMainBranchDto(), 400_000L), CeQueueDto.Status.PENDING);
    testing.insertQueued(testing.newTask(project, project.getMainBranchDto(), 950_000L).setStartedAt(960_000L), CeQueueDto.Status.IN_PROGRESS);
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    testing.insertQueued(testing.newTask(other, other.getMainBranchDto(), 1L), CeQueueDto.Status.PENDING);

    String json = ws.newRequest().setParam("organization", organization.getKey()).execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "paging": {"pageIndex": 1, "pageSize": 20, "total": 3},
        "tasks": [
          {"status": "IN_PROGRESS", "target": "MAIN", "queueTimeMs": 10000},
          {"status": "PENDING", "queueTimeMs": 600000},
          {"status": "PENDING", "queueTimeMs": 100000}
        ]
      }
      """);
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = ws.newRequest().setParam("organization", organization.getKey());

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }
}
