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
package org.sonar.server.issue.ws;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.api.utils.System2;
import org.sonar.ce.queue.CeQueueImpl;
import org.sonar.ce.queue.MeasureRecomputeSubmitter;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskCharacteristicDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.platform.NodeInformation;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.rules.RuleType.CODE_SMELL;
import static org.sonar.db.component.ComponentTesting.newFileDto;

public class BulkTransitionActionIT {

  private static final long NOW = 1_700_000_000_000L;

  private final System2 system2 = new TestSystem2().setNow(NOW);

  @Rule
  public DbTester db = DbTester.create(system2);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = db.getDbClient();
  /**
   * The indexer owns the commit, as upstream's commitAndIndexIssues does: the es_queue rows have to go in with
   * the issues. A mock that only records the call would therefore swallow the commit and roll the chunk back, so
   * it commits the session it is handed.
   */
  private final IssueIndexer issueIndexer = mock(IssueIndexer.class, invocation -> {
    if ("commitAndIndexIssueKeys".equals(invocation.getMethod().getName())) {
      ((DbSession) invocation.getArgument(0)).commit();
    }
    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
  });
  private final MeasureRecomputeSubmitter submitter = new MeasureRecomputeSubmitter(dbClient,
    new CeQueueImpl(system2, dbClient, new SequenceUuidFactory(), mock(NodeInformation.class)));

  private final WsActionTester tester = new WsActionTester(new BulkTransitionAction(dbClient, userSession,
    issueIndexer, new SequenceUuidFactory(), system2, submitter));

  @Test
  public void requires_system_administrator() {
    userSession.logIn();

    assertThatThrownBy(() -> tester.newRequest()
      .setParam("issues", "any")
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", "any")
      .setParam("refreshMeasures", "false")
      .execute())
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void rejects_more_than_the_maximum_number_of_issues() {
    userSession.logIn().setSystemAdministrator();
    String tooMany = String.join(",",
      IntStream.rangeClosed(0, BulkTransitionAction.MAX_ISSUES).mapToObj(i -> "issue-" + i).toList());

    assertThatThrownBy(() -> tester.newRequest()
      .setParam("issues", tooMany)
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", "any")
      .setParam("refreshMeasures", "false")
      .execute())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Number of issues is limited to " + BulkTransitionAction.MAX_ISSUES);
  }

  @Test
  public void rejects_a_transition_that_does_not_resolve() {
    userSession.logIn().setSystemAdministrator();

    assertThatThrownBy(() -> tester.newRequest()
      .setParam("issues", "any")
      .setParam("transition", "reopen")
      .setParam("branchUuid", "any")
      .setParam("refreshMeasures", "false")
      .execute())
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void resolves_the_issues_and_reports_what_it_moved() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto open = insertOpenIssue(rule, project, file);
    IssueDto alreadyResolved = db.issues().insertIssue(rule, project, file,
      i -> i.setStatus("RESOLVED").setResolution("FIXED").setType(CODE_SMELL));

    String response = callOn(project, List.of(open.getKey(), alreadyResolved.getKey()));

    assertThat(response).contains("\"requested\":2", "\"transitioned\":1");
    assertThat(response).contains(alreadyResolved.getKey());
    assertThat(statusOf(open.getKey())).isEqualTo("RESOLVED");
    assertThat(resolutionOf(open.getKey())).isEqualTo("FALSE-POSITIVE");
  }

  /**
   * The keys are supplied by the caller, so nothing but the predicate stops a key from another branch being
   * resolved here - and it would then be filed in the changelog under this branch and never reindexed.
   */
  @Test
  public void never_touches_an_issue_from_another_branch() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto mainBranch = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto mainFile = db.components().insertComponent(newFileDto(mainBranch));
    ComponentDto otherBranch = db.components().insertProjectBranch(mainBranch);
    ComponentDto otherFile = db.components().insertComponent(newFileDto(otherBranch));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto mine = insertOpenIssue(rule, mainBranch, mainFile);
    IssueDto foreign = insertOpenIssue(rule, otherBranch, otherFile);

    String response = callOn(mainBranch, List.of(mine.getKey(), foreign.getKey()));

    assertThat(response).contains("\"requested\":2", "\"transitioned\":1");
    assertThat(statusOf(mine.getKey())).isEqualTo("RESOLVED");
    assertThat(statusOf(foreign.getKey())).isEqualTo(STATUS_OPEN);
    assertThat(resolutionOf(foreign.getKey())).isNull();
    assertThat(changelogRowsOf(foreign.getKey())).isZero();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> reindexed = ArgumentCaptor.forClass(Collection.class);
    verify(issueIndexer).commitAndIndexIssueKeys(any(), reindexed.capture(), eq(mainBranch.uuid()));
    assertThat(reindexed.getValue()).containsExactly(mine.getKey());
  }

  @Test
  public void writes_the_changelog_under_the_branch_it_was_given() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = insertOpenIssue(rule, project, file);

    callOn(project, List.of(issue.getKey()));

    assertThat(db.select(db.getSession(),
      "select project_uuid as \"p\" from issue_changes where issue_key = '" + issue.getKey() + "'"))
      .isNotEmpty()
      .allSatisfy(row -> assertThat(row).containsEntry("p", project.uuid()));
  }

  @Test
  public void an_empty_key_list_is_only_allowed_as_a_refresh() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();

    assertThatThrownBy(() -> tester.newRequest()
      .setParam("branchUuid", project.uuid())
      .setParam("refreshMeasures", "false")
      .execute())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("issues is required unless refreshMeasures is true");
  }

  @Test
  public void an_empty_key_list_with_refresh_moves_nothing_and_queues_the_recomputation() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = insertOpenIssue(rule, project, file);

    String response = tester.newRequest()
      .setParam("branchUuid", project.uuid())
      .setParam("refreshMeasures", "true")
      .execute().getInput();

    CeQueueDto task = onlyRecomputationOf(project);
    assertThat(response).contains("\"requested\":0", "\"transitioned\":0", "\"ceTaskId\":\"" + task.getUuid() + "\"");
    assertThat(statusOf(issue.getKey())).isEqualTo(STATUS_OPEN);
    assertThat(characteristicsOf(task))
      .as("no key was named, so there is no issue to carry to the quality-gate listeners")
      .isEmpty();
    verifyNoInteractions(issueIndexer);
  }

  /**
   * The quality-gate listeners are dropped before any of them is called when the task carries no issue, so a
   * transfer that flips the gate would send no webhook and leave no quality-gate event on the project's activity.
   */
  @Test
  public void the_recomputation_carries_an_issue_so_the_quality_gate_listeners_are_reached() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto open = insertOpenIssue(rule, project, file);

    String response = tester.newRequest()
      .setParam("issues", open.getKey())
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", project.uuid())
      .setParam("refreshMeasures", "true")
      .execute().getInput();

    assertThat(response).contains("\"transitioned\":1", "\"ceTaskId\":");
    assertThat(characteristicsOf(onlyRecomputationOf(project))).containsOnly(
      Map.entry(MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLVED_ISSUE, open.getKey()),
      Map.entry(MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLUTION, "FALSE-POSITIVE"));
  }

  /** Nothing moved, but an earlier chunk of the same run did, so the gate may still have changed. */
  @Test
  public void a_recomputation_that_moved_nothing_still_carries_an_issue() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto alreadyResolved = db.issues().insertIssue(rule, project, file,
      i -> i.setStatus("RESOLVED").setResolution("FALSE-POSITIVE").setType(CODE_SMELL));

    tester.newRequest()
      .setParam("issues", alreadyResolved.getKey())
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", project.uuid())
      .setParam("refreshMeasures", "true")
      .execute();

    assertThat(characteristicsOf(onlyRecomputationOf(project)))
      .containsEntry(MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLVED_ISSUE, alreadyResolved.getKey());
  }

  @Test
  public void broadcast_only_transitions_nothing_and_answers_with_the_queued_task() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto open = insertOpenIssue(rule, project, file);

    String response = broadcastOnly(project, open);

    CeQueueDto task = onlyRecomputationOf(project);
    assertThat(response).contains("\"requested\":1", "\"transitioned\":0", "\"notTransitioned\":[]",
      "\"ceTaskId\":\"" + task.getUuid() + "\"");
    assertThat(task.getStatus()).isEqualTo(CeQueueDto.Status.PENDING);
    assertThat(statusOf(open.getKey())).isEqualTo(STATUS_OPEN);
    assertThat(changelogRowsOf(open.getKey())).isZero();
    verifyNoInteractions(issueIndexer);
  }

  @Test
  public void a_repeated_broadcast_while_the_task_is_pending_answers_with_the_same_task() {
    userSession.logIn().setSystemAdministrator();
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    IssueDto open = insertOpenIssue(db.rules().insertIssueRule(), project, file);

    String first = broadcastOnly(project, open);
    String second = broadcastOnly(project, open);
    String third = broadcastOnly(project, open);

    String taskId = onlyRecomputationOf(project).getUuid();
    assertThat(List.of(first, second, third)).allMatch(response -> response.contains("\"ceTaskId\":\"" + taskId + "\""));
  }

  private String broadcastOnly(ComponentDto branch, IssueDto issue) {
    return tester.newRequest()
      .setParam("issues", issue.getKey())
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", branch.uuid())
      .setParam("refreshMeasures", "true")
      .setParam("broadcastOnly", "true")
      .execute().getInput();
  }

  private CeQueueDto onlyRecomputationOf(ComponentDto branch) {
    List<CeQueueDto> tasks = dbClient.ceQueueDao().selectAllInAscOrder(db.getSession()).stream()
      .filter(task -> CeTaskTypes.MEASURE_RECOMPUTE.equals(task.getTaskType()))
      .toList();
    assertThat(tasks).extracting(CeQueueDto::getComponentUuid).containsExactly(branch.uuid());
    return tasks.get(0);
  }

  private Map<String, String> characteristicsOf(CeQueueDto task) {
    return dbClient.ceTaskCharacteristicsDao().selectByTaskUuids(db.getSession(), List.of(task.getUuid())).stream()
      .collect(Collectors.toMap(CeTaskCharacteristicDto::getKey, CeTaskCharacteristicDto::getValue));
  }

  private IssueDto insertOpenIssue(RuleDto rule, ComponentDto branch, ComponentDto file) {
    return db.issues().insertIssue(rule, branch, file,
      i -> i.setStatus(STATUS_OPEN).setResolution(null).setType(CODE_SMELL));
  }

  private String callOn(ComponentDto branch, List<String> keys) {
    return tester.newRequest()
      .setParam("issues", String.join(",", keys))
      .setParam("transition", "falsepositive")
      .setParam("branchUuid", branch.uuid())
      .setParam("comment", "transferred")
      .setParam("refreshMeasures", "false")
      .execute().getInput();
  }

  private IssueDto reload(String issueKey) {
    return dbClient.issueDao().selectByKey(db.getSession(), issueKey).orElseThrow();
  }

  private String statusOf(String issueKey) {
    return reload(issueKey).getStatus();
  }

  private String resolutionOf(String issueKey) {
    return reload(issueKey).getResolution();
  }

  private int changelogRowsOf(String issueKey) {
    return db.countSql(db.getSession(),
      "select count(1) from issue_changes where issue_key = '" + issueKey + "'");
  }
}
