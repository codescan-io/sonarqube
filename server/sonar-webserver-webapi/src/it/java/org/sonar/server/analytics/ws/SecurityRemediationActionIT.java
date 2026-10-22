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
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.ImpactDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.exceptions.BadRequestException;
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

class SecurityRemediationActionIT {

  private static final long DAY = 24L * 3_600_000L;
  private static final long NOW = day("2026-10-14") + DAY / 2;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final MapSettings settings = new MapSettings();
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new SecurityRemediationAction(db.getDbClient(),
    new AnalyticsWsSupport(db.getDbClient(), userSession), system2, settings.asConfig()));

  private OrganizationDto organization;
  private ProjectData project;
  private RuleDto rule;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    rule = db.rules().insert();
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("security_remediation");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "from", "to", "timeZone", "interval", "slaDays");
  }

  @Test
  void time_to_fix_and_sla_compliance_against_the_previous_period() {
    BranchDto main = project.getMainBranchDto();
    fixed(main, Severity.HIGH, "2026-09-21", "2026-10-06");
    fixed(main, Severity.HIGH, "2026-08-01", "2026-10-08");
    fixed(main, Severity.MEDIUM, "2026-10-01", "2026-10-03");
    fixed(main, Severity.BLOCKER, "2026-09-01", "2026-09-20");
    open(main, Severity.HIGH, "2026-08-01");
    open(main, Severity.LOW, "2026-10-10");
    // ignored: fixed before the previous period, not on a main branch, not a security impact, another organization
    fixed(main, Severity.HIGH, "2026-08-01", "2026-09-01");
    BranchDto feature = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    fixed(feature, Severity.HIGH, "2026-10-01", "2026-10-02");
    issue(main, SoftwareQuality.MAINTAINABILITY, Severity.HIGH, "2026-10-01", "2026-10-02");
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    fixed(other.getMainBranchDto(), Severity.HIGH, "2026-10-01", "2026-10-02");
    open(other.getMainBranchDto(), Severity.HIGH, "2026-01-01");

    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "period": {"from": "2026-10-01", "to": "2026-10-14", "timeZone": "UTC", "interval": "week"},
        "retentionDays": 30,
        "completeFrom": "2026-09-14",
        "totals": {"fixed": 3, "fixedWithinSla": 2, "medianTimeToFixMs": 1296000000, "open": 2, "openPastSla": 1},
        "previous": {"from": "2026-09-17", "to": "2026-09-30", "complete": true, "fixed": 1, "fixedWithinSla": 1, "medianTimeToFixMs": 1641600000},
        "severities": [
          {"severity": "BLOCKER", "slaDays": 30, "fixed": 0, "open": 0},
          {"severity": "HIGH", "slaDays": 30, "fixed": 2, "fixedWithinSla": 1, "medianTimeToFixMs": 1296000000, "open": 1, "openPastSla": 1, "oldestOpenAgeMs": 6436800000},
          {"severity": "MEDIUM", "slaDays": 90, "fixed": 1, "fixedWithinSla": 1, "medianTimeToFixMs": 172800000, "open": 0},
          {"severity": "LOW", "slaDays": 180, "fixed": 0, "open": 1, "openPastSla": 0},
          {"severity": "INFO", "slaDays": 365, "fixed": 0, "open": 0}
        ],
        "timeline": [
          {"date": "2026-10-01", "fixed": 1, "fixedWithinSla": 1, "medianTimeToFixMs": 172800000},
          {"date": "2026-10-05", "fixed": 2, "fixedWithinSla": 1, "medianTimeToFixMs": 1296000000},
          {"date": "2026-10-12", "fixed": 0}
        ]
      }
      """);
  }

  @Test
  void sla_can_be_overridden_per_severity() {
    fixed(project.getMainBranchDto(), Severity.HIGH, "2026-09-21", "2026-10-06");

    String json = call().setParam("slaDays", "high:7").execute().getInput();

    assertJson(json).isSimilarTo("""
      {"totals": {"fixed": 1, "fixedWithinSla": 0}}
      """);
    assertThat(json).contains("{\"severity\":\"BLOCKER\",\"slaDays\":30,", "{\"severity\":\"HIGH\",\"slaDays\":7,");
  }

  @Test
  void fail_on_invalid_sla() {
    TestRequest request = call().setParam("slaDays", "HIGH:0");

    assertThatThrownBy(request::execute).isInstanceOf(BadRequestException.class);
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private void fixed(BranchDto branch, Severity severity, String createdOn, String closedOn) {
    issue(branch, SoftwareQuality.SECURITY, severity, createdOn, closedOn);
  }

  private void open(BranchDto branch, Severity severity, String createdOn) {
    issue(branch, SoftwareQuality.SECURITY, severity, createdOn, null);
  }

  private void issue(BranchDto branch, SoftwareQuality quality, Severity severity, String createdOn, @Nullable String closedOn) {
    ComponentDto file = db.components().insertFile(branch);
    db.issues().insert(rule, branch, file, i -> {
      i.setType(3).replaceAllImpacts(List.of(new ImpactDto(quality, severity))).setIssueCreationTime(day(createdOn));
      if (closedOn != null) {
        i.setStatus("CLOSED").setResolution("FIXED").setIssueCloseTime(day(closedOn)).setUpdatedAt(day(closedOn) + DAY);
      }
    });
  }

  private TestRequest call() {
    return ws.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("from", "2026-10-01")
      .setParam("to", "2026-10-14")
      .setParam("timeZone", "UTC");
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
