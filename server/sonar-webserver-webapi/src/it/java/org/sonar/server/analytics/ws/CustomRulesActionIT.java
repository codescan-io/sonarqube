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
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDto;
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

class CustomRulesActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new CustomRulesAction(db.getDbClient(),
    new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData project;
  private ComponentDto file;
  private RuleDto template;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    file = db.components().insertFile(project.getMainBranchDto());
    template = db.rules().insertIssueRule(r -> r.setIsTemplate(true));
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("custom_rules");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "from", "to", "timeZone", "interval");
  }

  @Test
  void count_findings_of_custom_rules() {
    RuleDto noisy = customRule("Noisy", null);
    issue(noisy, "MAJOR", null, "2026-10-12", null);
    issue(noisy, "BLOCKER", null, "2026-10-13", null);
    issue(noisy, "MAJOR", "FALSE-POSITIVE", "2026-09-01", null);
    issue(noisy, "MAJOR", "FIXED", "2026-09-01", "2026-10-14");
    RuleDto quiet = customRule("Quiet", organization.getUuid());
    // not custom, and custom rule of another organization without findings here
    RuleDto standard = db.rules().insertIssueRule();
    issue(standard, "MAJOR", null, "2026-10-12", null);
    customRule("Foreign", testing.insertOrganization("analytics-other-org").getUuid());

    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "rules": [
          {
            "key": "%s", "name": "Noisy", "template": "%s",
            "issues": {"total": 4, "open": 2, "accepted": 0, "falsePositive": 1, "fixed": 1, "openBySeverity": {"BLOCKER": 1, "MAJOR": 1}},
            "dismissalRate": 0.25,
            "introduced": 2, "closed": 1,
            "timeline": [
              {"date": "2026-10-12", "introduced": 1, "closed": 0},
              {"date": "2026-10-13", "introduced": 1, "closed": 0},
              {"date": "2026-10-14", "introduced": 0, "closed": 1}
            ]
          },
          {"name": "Quiet", "issues": {"total": 0, "open": 0}, "introduced": 0}
        ]
      }
      """.formatted(noisy.getKey().toString(), template.getKey().toString()));
    assertThat(json).doesNotContain("Foreign", standard.getKey().toString());
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

  private RuleDto customRule(String name, String organizationUuid) {
    return db.rules().insertIssueRule(r -> r.setName(name).setTemplateUuid(template.getUuid()).setOrganizationUuid(organizationUuid));
  }

  private void issue(RuleDto rule, String severity, String resolution, String createdOn, String closedOn) {
    db.issues().insert(rule, project.getMainBranchDto(), file, i -> i.setType(1).setSeverity(severity).setResolution(resolution)
      .setStatus(resolution == null ? "OPEN" : "CLOSED")
      .setIssueCreationTime(day(createdOn) + 3_600_000L)
      .setIssueCloseTime(closedOn == null ? null : day(closedOn) + 3_600_000L));
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
