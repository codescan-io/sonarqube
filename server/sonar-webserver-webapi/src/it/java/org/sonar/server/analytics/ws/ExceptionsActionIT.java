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
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueDto;
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

class ExceptionsActionIT {

  private static final long DAY = 24L * 60 * 60 * 1000;
  private static final long NOW = 1_000 * DAY;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(
    new ExceptionsAction(db.getDbClient(), new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

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

    assertThat(def.key()).isEqualTo("expiring_exceptions");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization", "withinDays", "p", "ps");
  }

  @Test
  void exceptions_expiring_soonest_first() {
    IssueDto soon = exception(project.getMainBranchDto(), "WONTFIX", NOW + 3 * DAY);
    IssueDto expired = exception(project.getMainBranchDto(), "FALSE-POSITIVE", NOW - DAY);
    // ignored: too far ahead, no expiry, not an exception, not the main branch, other organization
    exception(project.getMainBranchDto(), "WONTFIX", NOW + 60 * DAY);
    exception(project.getMainBranchDto(), "WONTFIX", null);
    exception(project.getMainBranchDto(), "FIXED", NOW + DAY);
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    exception(branch, "WONTFIX", NOW + DAY);
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    exception(other.getMainBranchDto(), "WONTFIX", NOW + DAY);

    String json = ws.newRequest().setParam("organization", organization.getKey()).execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "paging": {"total": 2},
        "exceptions": [
          {"issueKey": "%s", "issueStatus": "FALSE_POSITIVE", "expired": true, "rule": "%s", "projectKey": "%s"},
          {"issueKey": "%s", "issueStatus": "ACCEPTED", "expired": false}
        ]
      }
      """.formatted(expired.getKey(), rule.getKey().toString(), project.getProjectDto().getKey(), soon.getKey()));
  }

  @Test
  void fail_on_invalid_window() {
    TestRequest request = ws.newRequest().setParam("organization", organization.getKey()).setParam("withinDays", "0");

    assertThatThrownBy(request::execute).isInstanceOf(BadRequestException.class).hasMessage("'withinDays' must be at least 1");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = ws.newRequest().setParam("organization", organization.getKey());

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private IssueDto exception(BranchDto branch, String resolution, Long expiresAt) {
    ComponentDto file = db.components().insertFile(branch);
    return db.issues().insert(rule, branch, file, i -> i.setStatus("RESOLVED").setResolution(resolution).setIssueResolutionExpiresAt(expiresAt));
  }
}
