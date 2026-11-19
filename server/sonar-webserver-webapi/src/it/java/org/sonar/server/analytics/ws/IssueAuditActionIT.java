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
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.user.UserDto;
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

class IssueAuditActionIT {

  private static final long NOW = day("2026-10-14") + 12 * 3_600_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new IssueAuditAction(db.getDbClient(),
    new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData project;
  private ComponentDto file;
  private RuleDto rule;
  private UserDto alice;
  private UserDto bob;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    file = db.components().insertFile(project.getMainBranchDto());
    rule = db.rules().insertIssueRule();
    alice = testing.insertUser("alice");
    bob = testing.insertUser("bob");
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("issue_audit");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "from", "to", "timeZone", "categories", "user", "project", "includeAnalysisChanges", "p", "ps");
  }

  @Test
  void list_changes_most_recent_first_with_their_users() {
    IssueDto issue = issue(project.getMainBranchDto());
    diff(issue, "2026-10-12", "resolution", "", "WONTFIX", alice);
    diff(issue, "2026-10-13", "resolution", "WONTFIX", "", bob);
    diff(issue, "2026-10-13", 1, "severity", "MAJOR", "BLOCKER", bob);
    db.issues().insertChange(issue, c -> c.setChangeType(IssueChangeDto.TYPE_COMMENT).setChangeData("Not in this release")
      .setUserUuid(bob.getUuid()).setIssueChangeCreationDate(day("2026-10-14") + 3_600_000L));
    db.issues().insertChange(issue, c -> c.setChangeType(IssueChangeDto.TYPE_EXCEPTION_REASON).setChangeData("Mitigated by WAF")
      .setUserUuid(alice.getUuid()).setIssueChangeCreationDate(day("2026-10-12") + 3_600_000L));
    // left out: made by the analysis, before the period, on another branch
    diff(issue, "2026-10-13", "resolution", "", "FIXED", null);
    diff(issue, "2026-10-01", "resolution", "", "FALSE-POSITIVE", alice);
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    diff(issue(branch), "2026-10-13", "resolution", "", "WONTFIX", alice);

    String json = call().execute().getInput();

    assertJson(json).withStrictArrayOrder().isSimilarTo("""
      {
        "paging": {"total": 5},
        "changes": [
          {"action": "COMMENTED", "text": "Not in this release", "user": {"login": "bob"}, "issueKey": "%1$s"},
          {"action": "REOPENED", "from": "WONTFIX", "user": {"login": "bob"}},
          {"action": "SEVERITY_CHANGED", "from": "MAJOR", "to": "BLOCKER", "user": {"login": "bob"}},
          {"action": "ACCEPTED", "to": "WONTFIX", "user": {"login": "alice"}, "projectKey": "%2$s"},
          {"action": "EXCEPTION_REASON", "text": "Mitigated by WAF", "user": {"login": "alice"}}
        ],
        "users": [
          {"login": "bob", "total": 3, "actions": {"commented": 1, "reopened": 1, "severity_changed": 1}},
          {"login": "alice", "total": 2, "actions": {"accepted": 1, "exception_reason": 1}}
        ]
      }
      """.formatted(issue.getKey(), project.projectKey()));
  }

  @Test
  void filter_by_category_user_and_project() {
    IssueDto issue = issue(project.getMainBranchDto());
    diff(issue, "2026-10-12", "resolution", "", "WONTFIX", alice);
    diff(issue, "2026-10-13", "severity", "MAJOR", "BLOCKER", alice);
    diff(issue, "2026-10-13", "severity", "BLOCKER", "MAJOR", bob);
    ProjectData other = testing.insertProject(organization);
    diff(issue(other.getMainBranchDto()), "2026-10-13", "severity", "MAJOR", "BLOCKER", alice);

    String json = call().setParam("categories", "SEVERITY").setParam("user", "alice").setParam("project", project.projectKey())
      .execute().getInput();

    assertJson(json).isSimilarTo("{\"paging\": {\"total\": 1}, \"changes\": [{\"action\": \"SEVERITY_CHANGED\", \"to\": \"BLOCKER\"}]}");
  }

  @Test
  void include_changes_made_by_the_analysis_when_asked() {
    diff(issue(project.getMainBranchDto()), "2026-10-13", "resolution", "", "FIXED", null);

    String json = call().setParam("includeAnalysisChanges", "true").execute().getInput();

    assertJson(json).isSimilarTo("{\"paging\": {\"total\": 1}, \"changes\": [{\"action\": \"FIXED\"}], \"users\": [{\"total\": 1}]}");
    assertThat(json).doesNotContain("\"user\":");
  }

  @Test
  void fail_when_user_is_unknown() {
    TestRequest request = call().setParam("user", "nobody");

    assertThatThrownBy(request::execute).isInstanceOf(NotFoundException.class);
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

  private IssueDto issue(BranchDto branch) {
    ComponentDto branchFile = branch.getUuid().equals(project.getMainBranchDto().getUuid()) ? file : db.components().insertFile(branch);
    return db.issues().insert(rule, branch, branchFile, i -> i.setType(2).setSeverity("MAJOR"));
  }

  private void diff(IssueDto issue, String on, String field, String from, String to, UserDto user) {
    diff(issue, on, 2, field, from, to, user);
  }

  private void diff(IssueDto issue, String on, int hour, String field, String from, String to, UserDto user) {
    db.issues().insertFieldDiffs(issue, new FieldDiffs()
      .setCreationDate(new Date(day(on) + hour * 3_600_000L))
      .setUserUuid(user == null ? null : user.getUuid())
      .setDiff(field, from, to));
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
