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

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeActivityDto.Status;
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

class ProjectsActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new ProjectsAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData busy;
  private ProjectData quiet;
  private ProjectData stale;
  private ProjectData never;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    busy = testing.insertProject(organization);
    quiet = testing.insertProject(organization);
    stale = testing.insertProject(organization);
    never = testing.insertProject(organization);

    insert(busy, "2026-10-13", Status.SUCCESS, 1_000L);
    insert(busy, "2026-10-14", Status.SUCCESS, 3_000L);
    insert(busy, "2026-10-14", Status.FAILED, 99_000L);
    insert(quiet, "2026-10-14", Status.SUCCESS, 500L);
    // analyzed before the period only
    insert(stale, "2026-10-01", Status.SUCCESS, 500L);
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    insert(other, "2026-10-14", Status.SUCCESS, 500L);
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("projects");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "from", "to", "timeZone", "analyzed", "p", "ps");
  }

  @Test
  void analyzed_projects_most_analyzed_first() {
    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "paging": {"total": 2},
        "projects": [
          {"key": "%s", "analyses": 3, "success": 2, "failed": 1, "canceled": 0, "avgExecutionTimeMs": 2000},
          {"key": "%s", "analyses": 1, "success": 1, "avgExecutionTimeMs": 500}
        ]
      }
      """.formatted(busy.getProjectDto().getKey(), quiet.getProjectDto().getKey()));
  }

  @Test
  void projects_not_analyzed_in_the_period() {
    String json = call().setParam("analyzed", "false").execute().getInput();

    assertJson(json).isSimilarTo("""
      {
        "paging": {"total": 2},
        "projects": [
          {"key": "%s", "analyses": 0, "lastAnalysisAt": "2026-10-01T00:01:00+0000"},
          {"key": "%s", "analyses": 0}
        ]
      }
      """.formatted(stale.getProjectDto().getKey(), never.getProjectDto().getKey()));
    assertThat(json).containsOnlyOnce("lastAnalysisAt");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private TestRequest call() {
    return ws.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("from", "2026-10-13")
      .setParam("to", "2026-10-14")
      .setParam("timeZone", "Z");
  }

  private void insert(ProjectData project, String day, Status status, long executionTimeMs) {
    long submittedAt = day(day) + 60_000L;
    testing.insertActivity(testing.newTask(project, project.getMainBranchDto(), submittedAt), status, submittedAt, executionTimeMs);
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
