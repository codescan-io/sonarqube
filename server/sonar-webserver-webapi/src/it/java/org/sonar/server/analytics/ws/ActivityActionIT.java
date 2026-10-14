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
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
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

class ActivityActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new ActivityAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData project;
  private BranchDto main;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    main = project.getMainBranchDto();
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("activity");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.isPost()).isFalse();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "from", "to", "timeZone", "interval");
  }

  @Test
  void summary_compares_with_previous_period() {
    // current period: 2026-10-12..2026-10-14, previous: 2026-10-09..2026-10-11
    insert(main, "2026-10-12", Status.SUCCESS, 1_000L, 100L);
    insert(main, "2026-10-13", Status.SUCCESS, 3_000L, 300L);
    insert(main, "2026-10-14", Status.FAILED, 500L, 200L);
    insert(main, "2026-10-10", Status.SUCCESS, 2_000L, 100L);
    // outside both periods
    insert(main, "2026-10-01", Status.SUCCESS, 9_000L, 100L);
    testing.insertProject(organization);

    String json = call("2026-10-12", "2026-10-14").execute().getInput();

    assertJson(json).isSimilarTo("""
      {
        "period": {"from": "2026-10-12", "to": "2026-10-14", "timeZone": "Z", "interval": "day"},
        "totalProjects": 2,
        "analyses": {
          "total": 3, "success": 2, "failed": 1, "canceled": 0, "projects": 1,
          "avgExecutionTimeMs": 2000, "medianExecutionTimeMs": 1000, "p90ExecutionTimeMs": 3000,
          "avgQueueTimeMs": 200, "medianQueueTimeMs": 200
        },
        "previous": {
          "from": "2026-10-09", "to": "2026-10-11",
          "analyses": {"total": 1, "success": 1, "medianExecutionTimeMs": 2000}
        },
        "timeline": [
          {"date": "2026-10-12", "total": 1, "success": 1, "medianExecutionTimeMs": 1000},
          {"date": "2026-10-13", "total": 1, "success": 1, "medianExecutionTimeMs": 3000},
          {"date": "2026-10-14", "total": 1, "failed": 1}
        ]
      }
      """);
  }

  @Test
  void split_by_task_type_and_target() {
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    BranchDto pr = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.PULL_REQUEST).setKey("42"));
    insert(main, "2026-10-14", Status.SUCCESS, 1L, 1L);
    insert(branch, "2026-10-14", Status.SUCCESS, 1L, 1L);
    insert(pr, "2026-10-14", Status.FAILED, 1L, 1L);
    testing.insertActivity(testing.newTask(project, main, day("2026-10-14")).setTaskType(CeTaskTypes.AUDIT_PURGE), Status.SUCCESS, null, null);

    String json = call("2026-10-14", "2026-10-14").execute().getInput();

    assertJson(json).isSimilarTo("""
      {
        "analyses": {"total": 3},
        "taskTypes": [
          {"type": "AUDIT_PURGE", "total": 1, "success": 1},
          {"type": "REPORT", "total": 3, "success": 2, "failed": 1}
        ],
        "targets": [
          {"target": "MAIN", "total": 1},
          {"target": "BRANCH", "total": 1},
          {"target": "PULL_REQUEST", "total": 1, "failed": 1}
        ]
      }
      """);
  }

  @Test
  void ignore_projects_of_other_organizations() {
    OrganizationDto other = testing.insertOrganization("analytics-other-org");
    ProjectData otherProject = testing.insertProject(other);
    insert(otherProject.getMainBranchDto(), otherProject, "2026-10-14", Status.SUCCESS, 1L, 1L);

    String json = call("2026-10-14", "2026-10-14").execute().getInput();

    assertJson(json).isSimilarTo("""
      {"totalProjects": 1, "analyses": {"total": 0, "projects": 0}, "taskTypes": [], "targets": []}
      """);
  }

  @Test
  void weekly_timeline() {
    insert(main, "2026-10-12", Status.SUCCESS, 1L, 1L);

    String json = call("2026-10-01", "2026-10-14").setParam("interval", "week").execute().getInput();

    assertJson(json).isSimilarTo("""
      {"timeline": [
        {"date": "2026-10-01", "total": 0},
        {"date": "2026-10-05", "total": 0},
        {"date": "2026-10-12", "total": 1}
      ]}
      """);
  }

  @Test
  void to_defaults_to_today() {
    String json = ws.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("from", "2026-10-13")
      .setParam("timeZone", "UTC")
      .execute().getInput();

    assertJson(json).isSimilarTo("""
      {"period": {"from": "2026-10-13", "to": "2026-10-14", "timeZone": "UTC"}}
      """);
  }

  @Test
  void fail_on_invalid_parameters() {
    assertThatThrownBy(() -> call("2026-10-14", "2026-10-01").execute())
      .isInstanceOf(BadRequestException.class)
      .hasMessage("'from' must be before or equal to 'to'");
    assertThatThrownBy(() -> call("2026-01-01", "2026-10-14").execute())
      .isInstanceOf(BadRequestException.class)
      .hasMessage("The period cannot be longer than 180 days");
    assertThatThrownBy(() -> call("yesterday", "2026-10-14").execute())
      .isInstanceOf(BadRequestException.class)
      .hasMessage("'from' must be a date (yyyy-MM-dd), got 'yesterday'");
    assertThatThrownBy(() -> call("2026-10-13", "2026-10-14").setParam("timeZone", "Mars/Olympus").execute())
      .isInstanceOf(BadRequestException.class)
      .hasMessage("'Mars/Olympus' is not a valid time zone");
  }

  @Test
  void fail_when_organization_does_not_exist() {
    TestRequest request = ws.newRequest().setParam("organization", "unknown").setParam("from", "2026-10-13");

    assertThatThrownBy(request::execute)
      .isInstanceOf(NotFoundException.class)
      .hasMessage("No organization with key 'unknown'");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call("2026-10-13", "2026-10-14");

    assertThatThrownBy(request::execute)
      .isInstanceOf(ForbiddenException.class);
  }

  private TestRequest call(String from, String to) {
    return ws.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("from", from)
      .setParam("to", to)
      .setParam("timeZone", "Z");
  }

  private void insert(BranchDto branch, String day, Status status, long executionTimeMs, long queueTimeMs) {
    insert(branch, project, day, status, executionTimeMs, queueTimeMs);
  }

  private void insert(BranchDto branch, ProjectData project, String day, Status status, long executionTimeMs, long queueTimeMs) {
    long submittedAt = day(day) + 3_600_000L;
    testing.insertActivity(testing.newTask(project, branch, submittedAt), status, submittedAt + queueTimeMs, executionTimeMs);
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
