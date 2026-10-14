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
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ProjectData;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.metric.MetricDto;
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

class QualityTrendActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new QualityTrendAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private MetricDto alertStatus;
  private MetricDto violations;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    alertStatus = db.measures().insertMetric(m -> m.setKey("alert_status").setValueType("LEVEL"));
    violations = db.measures().insertMetric(m -> m.setKey("violations").setValueType("INT"));
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("quality_trend");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "from", "to", "timeZone", "interval");
  }

  @Test
  void state_of_main_branches_at_the_end_of_every_bucket() {
    ProjectData improving = testing.insertProject(organization);
    analysis(improving.getMainBranchDto(), "2026-10-01", "ERROR", 30);
    analysis(improving.getMainBranchDto(), "2026-10-13", "OK", 12);
    ProjectData steady = testing.insertProject(organization);
    analysis(steady.getMainBranchDto(), "2026-10-12", "OK", 5);
    // ignored: other branches, other organizations
    BranchDto branch = db.components().insertProjectBranch(steady.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    analysis(branch, "2026-10-13", "ERROR", 999);
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    analysis(other.getMainBranchDto(), "2026-10-13", "ERROR", 999);

    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "period": {"from": "2026-10-12", "to": "2026-10-14", "interval": "day"},
        "timeline": [
          {"date": "2026-10-12", "projects": 2, "passed": 1, "failed": 1, "openIssues": 35},
          {"date": "2026-10-13", "projects": 2, "passed": 2, "failed": 0, "openIssues": 17},
          {"date": "2026-10-14", "projects": 2, "passed": 2, "failed": 0, "openIssues": 17}
        ]
      }
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
      .setParam("from", "2026-10-12")
      .setParam("to", "2026-10-14")
      .setParam("timeZone", "Z")
      .setParam("interval", "day");
  }

  private void analysis(BranchDto branch, String day, String gate, double issues) {
    long date = day(day) + 3_600_000L;
    SnapshotDto snapshot = db.components().insertSnapshot(branch, s -> s.setCreatedAt(date).setAnalysisDate(date).setStatus("P"));
    db.measures().insertProjectMeasure(branch, snapshot, alertStatus, m -> m.setValue(null).setData(gate));
    db.measures().insertProjectMeasure(branch, snapshot, violations, m -> m.setValue(issues));
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
