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
package org.sonar.ce.queue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.api.utils.System2;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskCharacteristicDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ProjectData;
import org.sonar.server.platform.NodeInformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLUTION;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLVED_ISSUE;

public class MeasureRecomputeSubmitterIT {

  private final System2 system2 = new TestSystem2().setNow(1_700_000_000_000L);

  @Rule
  public DbTester db = DbTester.create(system2);

  private final CeQueue ceQueue = new CeQueueImpl(system2, db.getDbClient(), new SequenceUuidFactory(),
    mock(NodeInformation.class));
  private final MeasureRecomputeSubmitter underTest = new MeasureRecomputeSubmitter(db.getDbClient(), ceQueue);

  @Test
  public void queues_a_task_on_the_branch_that_carries_the_resolved_issue() {
    ProjectData project = db.components().insertPrivateProject();
    String branchUuid = project.getMainBranchComponent().uuid();

    String taskUuid = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", "submitter");

    CeQueueDto task = measureTasks().stream().filter(t -> t.getUuid().equals(taskUuid)).findFirst().orElseThrow();
    assertThat(task.getTaskType()).isEqualTo(CeTaskTypes.MEASURE_RECOMPUTE);
    assertThat(task.getStatus()).isEqualTo(CeQueueDto.Status.PENDING);
    assertThat(task.getComponentUuid()).isEqualTo(branchUuid);
    assertThat(task.getEntityUuid()).isEqualTo(project.getProjectDto().getUuid());
    assertThat(task.getSubmitterUuid()).isEqualTo("submitter");
    assertThat(characteristicsOf(taskUuid))
      .containsOnly(Map.entry(CHARACTERISTIC_RESOLVED_ISSUE, "issue-1"), Map.entry(CHARACTERISTIC_RESOLUTION, "FALSE-POSITIVE"));
  }

  @Test
  public void a_request_while_the_branch_has_a_task_pending_returns_that_task() {
    String branchUuid = db.components().insertPrivateProject().getMainBranchComponent().uuid();

    String first = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", null);
    String second = underTest.submit(branchUuid, "issue-2", "WONTFIX", null);
    String refreshOnly = underTest.submit(branchUuid, null, null, null);

    assertThat(second).isEqualTo(first);
    assertThat(refreshOnly).as("a task that broadcasts also does what a refresh-only request asks").isEqualTo(first);
    assertThat(measureTasks()).extracting(CeQueueDto::getUuid).containsExactly(first);
  }

  @Test
  public void a_request_while_the_task_runs_queues_another_once() {
    String branchUuid = db.components().insertPrivateProject().getMainBranchComponent().uuid();
    String running = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", null);
    db.getDbClient().ceQueueDao().tryToPeek(db.getSession(), running, "worker");
    db.commit();

    String next = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", null);
    String again = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", null);

    assertThat(next).as("the running task may already have read the issues this request is about").isNotEqualTo(running);
    assertThat(again).isEqualTo(next);
    assertThat(measureTasks()).extracting(CeQueueDto::getUuid).containsExactlyInAnyOrder(running, next);
  }

  @Test
  public void a_pending_refresh_only_task_does_not_stand_in_for_a_request_that_broadcasts() {
    String branchUuid = db.components().insertPrivateProject().getMainBranchComponent().uuid();

    String refreshOnly = underTest.submit(branchUuid, null, null, null);
    String broadcast = underTest.submit(branchUuid, "issue-1", "FALSE-POSITIVE", null);

    assertThat(broadcast).isNotEqualTo(refreshOnly);
    assertThat(characteristicsOf(refreshOnly)).isEmpty();
    assertThat(characteristicsOf(broadcast)).containsKey(CHARACTERISTIC_RESOLVED_ISSUE);
  }

  @Test
  public void each_branch_of_a_project_has_its_own_task() {
    ProjectData project = db.components().insertPrivateProject();
    BranchDto other = db.components().insertProjectBranch(project.getProjectDto());

    String onMain = underTest.submit(project.getMainBranchComponent().uuid(), "issue-1", "FALSE-POSITIVE", null);
    String onOther = underTest.submit(other.getUuid(), "issue-2", "FALSE-POSITIVE", null);

    assertThat(onOther).isNotEqualTo(onMain);
    assertThat(measureTasks()).extracting(CeQueueDto::getComponentUuid)
      .containsExactlyInAnyOrder(project.getMainBranchComponent().uuid(), other.getUuid());
  }

  @Test
  public void refuses_an_unknown_branch() {
    assertThatThrownBy(() -> underTest.submit("missing", null, null, null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Branch 'missing' not found");
    assertThat(measureTasks()).isEmpty();
  }

  private List<CeQueueDto> measureTasks() {
    return db.getDbClient().ceQueueDao().selectAllInAscOrder(db.getSession()).stream()
      .filter(task -> CeTaskTypes.MEASURE_RECOMPUTE.equals(task.getTaskType()))
      .toList();
  }

  private Map<String, String> characteristicsOf(String taskUuid) {
    return db.getDbClient().ceTaskCharacteristicsDao().selectByTaskUuids(db.getSession(), List.of(taskUuid)).stream()
      .collect(Collectors.toMap(CeTaskCharacteristicDto::getKey, CeTaskCharacteristicDto::getValue));
  }
}
