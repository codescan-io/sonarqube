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
package org.sonar.ce.task.projectanalysis.taskprocessor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.server.measure.live.LiveMeasureComputer;
import org.sonar.server.qualitygate.changeevent.QGChangeEvent;
import org.sonar.server.qualitygate.changeevent.QGChangeEventListeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLUTION;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLVED_ISSUE;
import static org.sonar.db.component.ComponentTesting.newFileDto;

public class MeasureRecomputeTaskProcessorIT {

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public LogTester logTester = new LogTester();

  private final LiveMeasureComputer liveMeasureComputer = mock(LiveMeasureComputer.class);
  private final QGChangeEventListeners qualityGateListeners = mock(QGChangeEventListeners.class);
  private final MeasureRecomputeTaskProcessor underTest = new MeasureRecomputeTaskProcessor(db.getDbClient(),
    liveMeasureComputer, qualityGateListeners, System2.INSTANCE);

  @Test
  public void handles_the_measure_recompute_task_type() {
    assertThat(underTest.getHandledCeTaskTypes()).containsOnly(CeTaskTypes.MEASURE_RECOMPUTE);
  }

  @Test
  public void recomputes_every_component_of_the_branch_and_broadcasts_the_carried_issue() {
    ProjectData project = db.components().insertPrivateProject();
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setKey("feature"));
    ComponentDto branchComponent = db.components().getComponentDto(branch);
    ComponentDto file1 = db.components().insertComponent(newFileDto(branchComponent));
    ComponentDto file2 = db.components().insertComponent(newFileDto(branchComponent));
    db.components().insertComponent(newFileDto(project.getMainBranchComponent()));
    List<QGChangeEvent> events = List.of(mock(QGChangeEvent.class));
    when(liveMeasureComputer.refresh(any(), anyCollection())).thenReturn(events);
    logTester.setLevel(Level.INFO);

    underTest.process(task(project, branch.getUuid(),
      Map.of(CHARACTERISTIC_RESOLVED_ISSUE, "issue-1", CHARACTERISTIC_RESOLUTION, "FALSE-POSITIVE")));

    ArgumentCaptor<Collection<ComponentDto>> components = ArgumentCaptor.captor();
    verify(liveMeasureComputer).refresh(any(), components.capture());
    assertThat(components.getValue()).extracting(ComponentDto::uuid)
      .as("every component of the branch, and none of another branch")
      .containsExactlyInAnyOrder(branchComponent.uuid(), file1.uuid(), file2.uuid());
    ArgumentCaptor<List<DefaultIssue>> issues = ArgumentCaptor.captor();
    verify(qualityGateListeners).broadcastOnIssueChange(issues.capture(), eq(events), eq(false));
    assertThat(issues.getValue()).singleElement().satisfies(issue -> {
      assertThat(issue.key()).isEqualTo("issue-1");
      assertThat(issue.projectUuid()).isEqualTo(branch.getUuid());
      assertThat(issue.status()).isEqualTo("RESOLVED");
      assertThat(issue.resolution()).isEqualTo("FALSE-POSITIVE");
    });
    assertThat(logTester.logs(Level.INFO))
      .anyMatch(log -> log.startsWith("Recomputed measures of project " + project.getProjectDto().getKey()
        + " branch feature: 3 component(s)"));
  }

  @Test
  public void a_task_naming_no_issue_recomputes_and_hands_the_listeners_an_empty_list() {
    ProjectData project = db.components().insertPrivateProject();
    String branchUuid = project.getMainBranchComponent().uuid();

    underTest.process(task(project, branchUuid, Map.of()));

    verify(liveMeasureComputer).refresh(any(), anyCollection());
    verify(qualityGateListeners).broadcastOnIssueChange(eq(List.of()), anyList(), anyBoolean());
  }

  @Test
  public void a_failed_refresh_fails_the_task_and_logs_the_project_and_branch() {
    ProjectData project = db.components().insertPrivateProject();
    BranchDto branch = db.components().insertProjectBranch(project.getProjectDto(), b -> b.setKey("feature"));
    when(liveMeasureComputer.refresh(any(), anyCollection())).thenThrow(new IllegalStateException("boom"));

    CeTask task = task(project, branch.getUuid(), Map.of());
    assertThatThrownBy(() -> underTest.process(task))
      .as("an exception out of the processor is what makes the worker record the task as failed")
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("boom");

    verifyNoInteractions(qualityGateListeners);
    assertThat(logTester.logs(Level.ERROR)).containsExactly("Could not recompute measures of project "
      + project.getProjectDto().getKey() + " branch feature (" + branch.getUuid() + "): boom");
  }

  @Test
  public void a_task_without_a_branch_fails() {
    CeTask task = new CeTask.Builder().setOrganizationUuid("org").setUuid("task-1")
      .setType(CeTaskTypes.MEASURE_RECOMPUTE).build();

    assertThatThrownBy(() -> underTest.process(task))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Measure recomputation task task-1 has no branch");
    verifyNoInteractions(liveMeasureComputer, qualityGateListeners);
  }

  private static CeTask task(ProjectData project, String branchUuid, Map<String, String> characteristics) {
    return new CeTask.Builder()
      .setOrganizationUuid("org")
      .setUuid("task-1")
      .setType(CeTaskTypes.MEASURE_RECOMPUTE)
      .setComponent(new CeTask.Component(branchUuid, project.getProjectDto().getKey(), project.getProjectDto().getName()))
      .setEntity(new CeTask.Component(project.getProjectDto().getUuid(), project.getProjectDto().getKey(),
        project.getProjectDto().getName()))
      .setCharacteristics(characteristics)
      .build();
  }
}
