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

class ExceptionGovernanceActionIT {

  private static final long DAY = 24 * 3_600_000L;
  private static final long NOW = 1_790_000_000_000L;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new ExceptionGovernanceAction(db.getDbClient(),
    new AnalyticsWsSupport(db.getDbClient(), userSession), system2));

  private OrganizationDto organization;
  private ProjectData project;
  private ComponentDto file;
  private RuleDto rule;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    file = db.components().insertFile(project.getMainBranchDto());
    rule = db.rules().insertIssueRule();
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("exceptions");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "statuses", "project", "soonDays", "p", "ps");
  }

  @Test
  void list_exceptions_with_who_marked_them_and_why() {
    UserDto alice = testing.insertUser("alice");
    UserDto bob = testing.insertUser("bob");
    IssueDto accepted = exception("WONTFIX", NOW + 10 * DAY, 3);
    mark(accepted, "WONTFIX", alice, NOW - 5 * DAY);
    db.issues().insertComment(accepted, bob, "Looks fine");
    db.issues().insertChange(accepted, c -> c.setChangeType(IssueChangeDto.TYPE_EXCEPTION_REASON).setChangeData("Validated upstream")
      .setUserUuid(alice.getUuid()).setIssueChangeCreationDate(NOW - 5 * DAY));
    IssueDto falsePositive = exception("FALSE-POSITIVE", null, 2);
    mark(falsePositive, "FALSE-POSITIVE", bob, NOW - 3 * DAY);
    db.issues().insertComment(falsePositive, bob, "Generated code");
    exception("WONTFIX", NOW - DAY, 1);
    exception("WONTFIX", NOW + 60 * DAY, 0);
    // not counted: open issue, other branch, other organization
    db.issues().insert(rule, project.getMainBranchDto(), file, i -> i.setType(1).setStatus("OPEN").setResolution(null));
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setBranchType(BranchType.BRANCH).setKey("feature"));
    db.issues().insert(rule, branch, db.components().insertFile(branch), i -> i.setType(1).setStatus("RESOLVED").setResolution("WONTFIX"));
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    db.issues().insert(rule, other.getMainBranchDto(), db.components().insertFile(other.getMainBranchDto()),
      i -> i.setType(1).setStatus("RESOLVED").setResolution("WONTFIX"));
    db.issues().insertHotspot(db.rules().insertHotspotRule(), project.getMainBranchComponent(), file);

    String json = call().execute().getInput();

    assertJson(json).isSimilarTo("""
      {
        "summary": {"accepted": 3, "falsePositive": 1, "withoutExpiry": 1, "expired": 1, "expiringSoon": 1, "soonDays": 30,
          "hotspotsToReview": 1},
        "paging": {"total": 4}
      }
      """);
    assertJson(json).isSimilarTo("""
      {"exceptions": [
        {"issueKey": "%s", "issueStatus": "ACCEPTED", "expired": false, "markedBy": {"login": "alice"},
          "rationale": {"source": "EXCEPTION_REASON", "text": "Validated upstream", "user": {"login": "alice"}}, "comments": 1},
        {"issueKey": "%s", "issueStatus": "FALSE_POSITIVE", "markedBy": {"login": "bob"},
          "rationale": {"source": "COMMENT", "text": "Generated code", "user": {"login": "bob"}}, "comments": 1},
        {"issueStatus": "ACCEPTED", "expired": true, "comments": 0},
        {"issueStatus": "ACCEPTED", "expired": false, "comments": 0}
      ]}
      """.formatted(accepted.getKey(), falsePositive.getKey()));
  }

  @Test
  void filter_by_status_and_project() {
    exception("WONTFIX", null, 1);
    exception("FALSE-POSITIVE", null, 2);
    ProjectData other = testing.insertProject(organization);
    db.issues().insert(rule, other.getMainBranchDto(), db.components().insertFile(other.getMainBranchDto()),
      i -> i.setType(1).setStatus("RESOLVED").setResolution("FALSE-POSITIVE"));

    String json = call().setParam("statuses", "FALSE_POSITIVE").setParam("project", project.projectKey()).execute().getInput();

    assertJson(json).isSimilarTo("{\"summary\": {\"accepted\": 1, \"falsePositive\": 2}, \"paging\": {\"total\": 1}, " +
      "\"exceptions\": [{\"issueStatus\": \"FALSE_POSITIVE\", \"projectKey\": \"" + project.projectKey() + "\"}]}");
  }

  @Test
  void fail_when_project_is_of_another_organization() {
    ProjectData other = testing.insertProject(testing.insertOrganization("analytics-other-org"));
    TestRequest request = call().setParam("project", other.projectKey());

    assertThatThrownBy(request::execute).isInstanceOf(NotFoundException.class);
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private TestRequest call() {
    return ws.newRequest().setParam("organization", organization.getKey());
  }

  /** @param age the higher, the more recently updated */
  private IssueDto exception(String resolution, Long expiresAt, int age) {
    return db.issues().insert(rule, project.getMainBranchDto(), file, i -> i.setType(2).setSeverity("MAJOR").setStatus("RESOLVED")
      .setResolution(resolution).setIssueResolutionExpiresAt(expiresAt).setUpdatedAt(NOW - 100 * DAY + age * DAY));
  }

  private void mark(IssueDto issue, String resolution, UserDto user, long at) {
    db.issues().insertFieldDiffs(issue, new FieldDiffs()
      .setCreationDate(new Date(at))
      .setUserUuid(user.getUuid())
      .setDiff("resolution", "", resolution));
  }
}
