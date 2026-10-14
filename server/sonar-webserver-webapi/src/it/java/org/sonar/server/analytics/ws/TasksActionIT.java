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

class TasksActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new TasksAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

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

    assertThat(def.key()).isEqualTo("tasks");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "from", "to", "timeZone", "statuses", "type", "s", "p", "ps");
  }

  @Test
  void list_failed_analyses_of_the_period_most_recent_first() {
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    insert(project.getMainBranchDto(), "2026-10-13", 1, Status.FAILED, 100L, 50L, "first failure");
    insert(branch, "2026-10-14", 1, Status.FAILED, 100L, 2_000L, "Analysis report is corrupted");
    insert(project.getMainBranchDto(), "2026-10-14", 2, Status.SUCCESS, 100L, 50L, null);
    // outside the period
    insert(project.getMainBranchDto(), "2026-10-01", 1, Status.FAILED, 100L, 50L, null);

    String json = call().setParam("statuses", "FAILED").execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "paging": {"pageIndex": 1, "pageSize": 20, "total": 2},
        "tasks": [
          {
            "type": "REPORT", "status": "FAILED", "projectKey": "%s", "branch": "feature", "target": "BRANCH",
            "executionTimeMs": 100, "queueTimeMs": 2000, "errorMessage": "Analysis report is corrupted"
          },
          {"status": "FAILED", "target": "MAIN", "errorMessage": "first failure"}
        ]
      }
      """.formatted(project.getProjectDto().getKey()));
  }

  @Test
  void sort_by_execution_time_or_queue_time() {
    insert(project.getMainBranchDto(), "2026-10-14", 1, Status.SUCCESS, 300L, 10L, null);
    insert(project.getMainBranchDto(), "2026-10-14", 2, Status.SUCCESS, 900L, 5L, null);
    insert(project.getMainBranchDto(), "2026-10-14", 3, Status.SUCCESS, 100L, 70L, null);

    assertJson(call().setParam("s", "executionTime").execute().getInput()).withStrictArrayOrder().isSimilarTo("""
      {"tasks": [{"executionTimeMs": 900}, {"executionTimeMs": 300}, {"executionTimeMs": 100}]}
      """);
    assertJson(call().setParam("s", "queueTime").execute().getInput()).withStrictArrayOrder().isSimilarTo("""
      {"tasks": [{"queueTimeMs": 70}, {"queueTimeMs": 10}, {"queueTimeMs": 5}]}
      """);
  }

  @Test
  void only_analyses_by_default_and_other_types_on_request() {
    insert(project.getMainBranchDto(), "2026-10-14", 1, Status.SUCCESS, 1L, 1L, null);
    testing.insertActivity(testing.newTask(project, project.getMainBranchDto(), day("2026-10-14") + 5).setTaskType(CeTaskTypes.AUDIT_PURGE),
      Status.SUCCESS, null, null);

    assertJson(call().execute().getInput()).isSimilarTo("""
      {"paging": {"total": 1}, "tasks": [{"type": "REPORT"}]}
      """);
    assertJson(call().setParam("type", CeTaskTypes.AUDIT_PURGE).execute().getInput()).isSimilarTo("""
      {"paging": {"total": 1}, "tasks": [{"type": "AUDIT_PURGE"}]}
      """);
  }

  @Test
  void ignore_projects_of_other_organizations() {
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    testing.insertActivity(testing.newTask(other, other.getMainBranchDto(), day("2026-10-14") + 1), Status.SUCCESS, null, null);

    assertJson(call().execute().getInput()).isSimilarTo("""
      {"paging": {"total": 0}, "tasks": []}
      """);
  }

  @Test
  void paginate() {
    for (int i = 1; i <= 3; i++) {
      insert(project.getMainBranchDto(), "2026-10-14", i, Status.SUCCESS, i * 100L, 1L, null);
    }

    assertJson(call().setParam("p", "2").setParam("ps", "2").execute().getInput()).isSimilarTo("""
      {"paging": {"pageIndex": 2, "pageSize": 2, "total": 3}, "tasks": [{"executionTimeMs": 100}]}
      """);
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

  /** {@code minute} spreads the tasks of a day so their order is known. */
  private void insert(BranchDto branch, String day, int minute, Status status, long executionTimeMs, long queueTimeMs, String errorMessage) {
    long submittedAt = day(day) + minute * 60_000L;
    testing.insertActivity(testing.newTask(project, branch, submittedAt), status, submittedAt + queueTimeMs, executionTimeMs, errorMessage);
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
