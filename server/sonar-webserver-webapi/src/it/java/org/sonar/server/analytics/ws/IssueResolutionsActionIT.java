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
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueDto;
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

class IssueResolutionsActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final MapSettings settings = new MapSettings();
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new IssueResolutionsAction(db.getDbClient(),
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

    assertThat(def.key()).isEqualTo("issue_resolutions");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "from", "to", "timeZone", "interval");
  }

  @Test
  void introduced_and_resolved_issues_of_main_branches() {
    IssueDto fixed = issue(project.getMainBranchDto(), "2026-10-12");
    resolve(fixed, "2026-10-13", "FIXED", null);
    IssueDto accepted = issue(project.getMainBranchDto(), "2026-10-01");
    resolve(accepted, "2026-10-14", "WONTFIX", "alice-uuid");
    IssueDto reopened = issue(project.getMainBranchDto(), "2026-10-01");
    resolve(reopened, "2026-10-12", "FALSE-POSITIVE", "bob-uuid");
    resolveFrom(reopened, "2026-10-13", "FALSE-POSITIVE", "");
    // ignored: not a main branch, another organization
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    resolve(issue(branch, "2026-10-12"), "2026-10-13", "FIXED", null);
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    resolve(issue(other.getMainBranchDto(), "2026-10-12"), "2026-10-13", "FIXED", null);
    settings.setProperty("sonar.dbcleaner.daysBeforeDeletingClosedIssues", 60);

    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "retentionDays": 60,
        "completeFrom": "2026-08-15",
        "totals": {
          "introduced": 1, "fixed": 1, "accepted": 1, "falsePositive": 1, "removed": 0, "reopened": 1,
          "resolved": 3, "fixedByAnalysis": 1, "netChange": -1
        },
        "timeline": [
          {"date": "2026-10-12", "introduced": 1, "falsePositive": 1},
          {"date": "2026-10-13", "fixed": 1, "reopened": 1},
          {"date": "2026-10-14", "accepted": 1}
        ]
      }
      """);
  }

  @Test
  void retention_defaults_to_30_days() {
    assertJson(call().execute().getInput()).isSimilarTo("""
      {"retentionDays": 30, "completeFrom": "2026-09-14"}
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
      .setParam("timeZone", "Z");
  }

  private IssueDto issue(BranchDto branch, String createdOn) {
    ComponentDto file = db.components().insertFile(branch);
    return db.issues().insert(rule, branch, file, i -> i.setIssueCreationTime(day(createdOn) + 3_600_000L).setType(1));
  }

  private void resolve(IssueDto issue, String on, String resolution, String user) {
    resolveFrom(issue, on, "", resolution, user);
  }

  private void resolveFrom(IssueDto issue, String on, String from, String to) {
    resolveFrom(issue, on, from, to, null);
  }

  private void resolveFrom(IssueDto issue, String on, String from, String to, String user) {
    db.issues().insertFieldDiffs(issue, new FieldDiffs()
      .setCreationDate(new Date(day(on) + 7_200_000L))
      .setUserUuid(user)
      .setDiff("resolution", from, to));
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
