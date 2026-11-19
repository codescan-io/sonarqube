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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.DbTester;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.user.UserDto;
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

class ScanComparisonActionIT {

  private static final long HOUR = 3_600_000L;
  private static final long NOW = day("2026-10-10") + 12 * HOUR;

  @RegisterExtension
  public final DbTester db = DbTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final System2 system2 = mock(System2.class);
  private final MapSettings settings = new MapSettings();
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final WsActionTester ws = new WsActionTester(new ScanComparisonAction(db.getDbClient(),
    new AnalyticsWsSupport(db.getDbClient(), userSession), system2, settings.asConfig()));

  private OrganizationDto organization;
  private ProjectData project;
  private BranchDto branch;
  private ComponentDto file;
  private RuleDto rule;
  private SnapshotDto first;
  private SnapshotDto second;
  private SnapshotDto latest;

  @BeforeEach
  void setUp() {
    when(system2.now()).thenReturn(NOW);
    organization = testing.insertOrganization("analytics-org");
    project = testing.insertProject(organization);
    branch = project.getMainBranchDto();
    file = db.components().insertFile(branch);
    rule = db.rules().insertIssueRule();
    first = analysis("2026-10-01", false);
    second = analysis("2026-10-05", false);
    latest = analysis("2026-10-09", true);
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("scan_comparison");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key)
      .containsOnly("organization", "project", "baseAnalysis", "analysis", "list", "p", "ps");
  }

  @Test
  void compare_with_the_latest_analysis() {
    // raised by the base analysis: not new
    IssueDto old = issue(i -> i.setCreatedAt(processedAt(first) + 1000).setType(2).setSeverity("MAJOR"));
    // raised by the analyses after it
    issue(i -> i.setCreatedAt(processedAt(second) + 1000).setType(3).setSeverity("CRITICAL"));
    issue(i -> i.setCreatedAt(processedAt(latest) + 1000).setType(1).setSeverity("MINOR").setStatus("CLOSED").setResolution("FIXED"));
    // fixed by the latest analysis, and before the base one
    issue(i -> i.setType(3).setSeverity("BLOCKER").setStatus("CLOSED").setResolution("FIXED").setIssueCloseTime(latest.getCreatedAt()));
    issue(i -> i.setType(3).setSeverity("BLOCKER").setStatus("CLOSED").setResolution("FIXED").setIssueCloseTime(first.getCreatedAt()));
    UserDto alice = testing.insertUser("alice");
    UserDto bob = testing.insertUser("bob");
    change(old, "2026-10-03", "severity", "MAJOR", "CRITICAL", alice);
    IssueDto accepted = issue(i -> i.setType(1).setSeverity("INFO"));
    change(accepted, "2026-10-04", "resolution", "", "WONTFIX", bob);
    IssueDto reopened = issue(i -> i.setType(1).setSeverity("INFO"));
    change(reopened, "2026-10-06", "resolution", "FALSE-POSITIVE", "", bob);
    measure(first, "violations", 10);
    measure(latest, "violations", 7);

    String json = call().execute().getInput();

    assertJson(json).isSimilarTo("""
      {
        "project": {"key": "%s"},
        "base": {"key": "%s"},
        "analysis": {"key": "%s"},
        "measures": [{"metric": "violations", "base": 10, "analysis": 7, "change": -3}],
        "newIssues": {"total": 2, "open": 1, "byType": {"VULNERABILITY": {"CRITICAL": 1}, "CODE_SMELL": {"MINOR": 1}}},
        "remediated": {"complete": true, "retentionDays": 30, "total": 1, "byType": {"VULNERABILITY": {"BLOCKER": 1}}},
        "severityChanges": {"total": 1, "raised": 1, "lowered": 0},
        "exceptionChanges": {"accepted": 1, "falsePositive": 0, "reopened": 1}
      }
      """.formatted(project.projectKey(), first.getUuid(), latest.getUuid()));
    // the summary lists nothing: each list is paged on its own
    assertThat(json).doesNotContain("issueKey");

    assertJson(call().setParam("list", "new").execute().getInput())
      .isSimilarTo("{\"paging\": {\"pageIndex\": 1, \"total\": 2}, \"issues\": [{\"severity\": \"MINOR\"}, {\"severity\": \"CRITICAL\"}]}");
    assertJson(call().setParam("list", "remediated").execute().getInput())
      .isSimilarTo("{\"paging\": {\"total\": 1}, \"issues\": [{\"severity\": \"BLOCKER\", \"resolution\": \"FIXED\"}]}");
    assertJson(call().setParam("list", "severity_changes").execute().getInput()).isSimilarTo("""
      {"paging": {"total": 1}, "changes": [{"issueKey": "%s", "from": "MAJOR", "to": "CRITICAL", "user": {"login": "alice"}}]}
      """.formatted(old.getKey()));
    assertJson(call().setParam("list", "exception_changes").execute().getInput()).isSimilarTo("""
      {"paging": {"total": 2}, "changes": [
        {"issueKey": "%s", "outcome": "ACCEPTED", "user": {"login": "bob"}},
        {"issueKey": "%s", "outcome": "REOPENED", "user": {"login": "bob"}}
      ]}
      """.formatted(accepted.getKey(), reopened.getKey()));
  }

  @Test
  void compare_two_past_analyses() {
    issue(i -> i.setCreatedAt(processedAt(second) + 1000).setType(3).setSeverity("CRITICAL"));
    issue(i -> i.setCreatedAt(processedAt(latest) + 1000).setType(1).setSeverity("MINOR"));

    String json = call().setParam("analysis", second.getUuid()).execute().getInput();

    assertJson(json).isSimilarTo("{\"analysis\": {\"key\": \"" + second.getUuid() + "\"}, \"newIssues\": {\"total\": 1}}");
  }

  @Test
  void lists_are_paged() {
    for (int i = 0; i < 3; i++) {
      issue(d -> d.setCreatedAt(processedAt(second) + 1000).setType(2).setSeverity("MAJOR"));
    }
    for (int i = 0; i < 3; i++) {
      // a day apart, so the changes come in a known order
      change(issue(d -> d.setType(1).setSeverity("MINOR")), "2026-10-0" + (2 + i), "severity", "MINOR", "MAJOR", testing.insertUser("user" + i));
    }

    String issues = call().setParam("list", "new").setParam("p", "2").setParam("ps", "2").execute().getInput();
    String changes = call().setParam("list", "severity_changes").setParam("p", "2").setParam("ps", "2").execute().getInput();

    assertJson(issues).isSimilarTo("{\"paging\": {\"pageIndex\": 2, \"pageSize\": 2, \"total\": 3}}");
    assertThat(issues.split("\"issueKey\"")).hasSize(2);
    assertJson(changes).isSimilarTo("{\"paging\": {\"pageIndex\": 2, \"pageSize\": 2, \"total\": 3}, \"changes\": [{\"user\": {\"login\": \"user2\"}}]}");
  }

  @Test
  void fail_on_an_unknown_list() {
    TestRequest request = call().setParam("list", "everything");

    assertThatThrownBy(request::execute).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void remediated_issues_are_incomplete_past_the_retention_of_closed_issues() {
    settings.setProperty("sonar.dbcleaner.daysBeforeDeletingClosedIssues", 5);

    assertJson(call().execute().getInput()).isSimilarTo("{\"remediated\": {\"complete\": false, \"retentionDays\": 5}}");
  }

  @Test
  void fail_when_base_is_not_older() {
    TestRequest request = call().setParam("baseAnalysis", latest.getUuid()).setParam("analysis", second.getUuid());

    assertThatThrownBy(request::execute).isInstanceOf(BadRequestException.class);
  }

  @Test
  void fail_when_analysis_is_of_another_project() {
    ProjectData other = testing.insertProject(organization);
    SnapshotDto otherAnalysis = db.components().insertSnapshot(other.getMainBranchDto());
    TestRequest request = call().setParam("baseAnalysis", otherAnalysis.getUuid());

    assertThatThrownBy(request::execute).isInstanceOf(NotFoundException.class);
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
    return ws.newRequest()
      .setParam("organization", organization.getKey())
      .setParam("project", project.projectKey())
      .setParam("baseAnalysis", first.getUuid());
  }

  private SnapshotDto analysis(String on, boolean last) {
    return db.components().insertSnapshot(branch, s -> s.setCreatedAt(day(on)).setAnalysisDate(day(on) + HOUR).setLast(last));
  }

  private static long processedAt(SnapshotDto analysis) {
    return analysis.getAnalysisDate();
  }

  @SafeVarargs
  private IssueDto issue(Consumer<IssueDto>... populators) {
    return db.issues().insert(rule, branch, file, i -> {
      i.setCreatedAt(day("2026-09-01")).setStatus("OPEN").setResolution(null);
      for (Consumer<IssueDto> populator : populators) {
        populator.accept(i);
      }
    });
  }

  private void change(IssueDto issue, String on, String field, String from, String to, UserDto user) {
    db.issues().insertFieldDiffs(issue, new FieldDiffs()
      .setCreationDate(new Date(day(on) + 2 * HOUR))
      .setUserUuid(user.getUuid())
      .setDiff(field, from, to));
  }

  private void measure(SnapshotDto analysis, String metricKey, double value) {
    MetricDto metric = db.getDbClient().metricDao().selectByKey(db.getSession(), metricKey);
    if (metric == null) {
      metric = db.measures().insertMetric(m -> m.setKey(metricKey).setValueType("INT"));
    }
    db.measures().insertProjectMeasure(branch, analysis, metric, m -> m.setValue(value));
  }

  private static long day(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
