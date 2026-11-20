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
package org.sonar.server.issue;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.utils.System2;
import org.sonar.ce.queue.CeQueue;
import org.sonar.ce.queue.CeQueueImpl;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.DbInputStream;
import org.sonar.server.platform.NodeInformation;
import org.sonar.server.setting.ProjectConfigurationLoader;
import org.sonar.server.setting.ProjectConfigurationLoaderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The wiring, against a real database: what the web server actually leaves behind for the compute engine when a
 * user marks an issue.
 * <p>
 * Every other test of this class mocks {@link CeQueue} and {@link DbClient}, so none of them can see whether the
 * {@code ce_task_input} blob and the {@code ce_queue} row are really written, whether they agree with each other,
 * or what the caller's session looks like afterwards. That last point is the one that matters most: the submitter
 * borrows the request's session, and {@code BulkChangeAction} keeps using it after the call returns.
 */
public class IssueProliferationSubmitterIT {

  private static final String USER_UUID = "user-uuid";

  private final System2 system2 = System2.INSTANCE;

  @Rule
  public DbTester db = DbTester.create(system2);

  private final DbClient dbClient = db.getDbClient();
  private final CeQueue ceQueue = new CeQueueImpl(system2, dbClient, UuidFactoryFast.getInstance(),
    mock(NodeInformation.class));
  private final ProjectConfigurationLoader configurationLoader = new ProjectConfigurationLoaderImpl(
    new MapSettings(), dbClient);

  private final IssueProliferationSubmitter underTest = new IssueProliferationSubmitter(dbClient, ceQueue,
    configurationLoader);

  @Test
  public void writesAQueueRowAndItsPayloadForAMarking() {
    ProjectData project = db.components().insertPrivateProject();
    BranchDto branch = project.getMainBranchDto();
    enableProliferation(project);

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, branch.getUuid(), List.of("issue-1", "issue-2"), userContext(),
        DefaultTransitions.FALSE_POSITIVE, "looks fine to me", null);
    }

    List<CeQueueDto> queued = dbClient.ceQueueDao().selectAllInAscOrder(db.getSession());
    assertThat(queued).hasSize(1);
    CeQueueDto task = queued.get(0);
    assertThat(task.getTaskType()).isEqualTo(CeTaskTypes.RESOLUTION_PROLIFERATION);
    assertThat(task.getComponentUuid()).isEqualTo(branch.getUuid());
    assertThat(task.getEntityUuid()).isEqualTo(project.projectUuid());
    assertThat(task.getSubmitterUuid()).isEqualTo(USER_UUID);

    ProliferationTaskPayload payload = payloadOf(task.getUuid());
    assertThat(payload.issueKeys()).containsExactly("issue-1", "issue-2");
    assertThat(payload.comment()).isEqualTo("looks fine to me");
    assertThat(payload.exceptionReason()).isNull();
  }

  /** The payload is written under the task's own uuid, or a worker would pick the task up and find nothing. */
  @Test
  public void storesThePayloadUnderTheQueuedTaskUuid() {
    ProjectData project = db.components().insertPrivateProject();
    enableProliferation(project);

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, project.getMainBranchDto().getUuid(), List.of("issue-1"), userContext(),
        DefaultTransitions.EXCEPTION, null, "approved by security");
    }

    String taskUuid = dbClient.ceQueueDao().selectAllInAscOrder(db.getSession()).get(0).getUuid();
    assertThat(dbClient.ceTaskInputDao().selectData(db.getSession(), taskUuid)).isPresent();
    assertThat(payloadOf(taskUuid).exceptionReason()).isEqualTo("approved by security");
  }

  @Test
  public void writesNothingWhenProliferationIsDisabledForTheProject() {
    ProjectData project = db.components().insertPrivateProject();

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, project.getMainBranchDto().getUuid(), List.of("issue-1"), userContext(),
        DefaultTransitions.ACCEPT, null, null);
    }

    assertThat(dbClient.ceQueueDao().selectAllInAscOrder(db.getSession())).isEmpty();
  }

  @Test
  public void writesNothingForATransitionThatIsNotAMarking() {
    ProjectData project = db.components().insertPrivateProject();
    enableProliferation(project);

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, project.getMainBranchDto().getUuid(), List.of("issue-1"), userContext(),
        DefaultTransitions.REOPEN, null, null);
    }

    assertThat(dbClient.ceQueueDao().selectAllInAscOrder(db.getSession())).isEmpty();
  }

  @Test
  public void writesNothingWhenTheSourceBranchIsUnknown() {
    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, "no-such-branch", List.of("issue-1"), userContext(),
        DefaultTransitions.ACCEPT, null, null);
    }

    assertThat(dbClient.ceQueueDao().selectAllInAscOrder(db.getSession())).isEmpty();
  }

  /**
   * The contract the rest of the request depends on: after submit returns, whether it queued anything or not,
   * the caller's session is still usable. BulkChangeAction refreshes live measures on it immediately afterwards.
   */
  @Test
  public void leavesTheCallersSessionUsable() {
    ProjectData project = db.components().insertPrivateProject();
    enableProliferation(project);

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, project.getMainBranchDto().getUuid(), List.of("issue-1"), userContext(),
        DefaultTransitions.ACCEPT, null, null);

      // Any statement will do; the point is that the session has not been closed or left aborted.
      assertThat(dbClient.branchDao().selectByUuid(dbSession, project.getMainBranchDto().getUuid())).isPresent();
    }
  }

  /** One task per {@link IssueProliferationSubmitter#ISSUES_PER_TASK} issues, each with its own payload. */
  @Test
  public void splitsALargeMarkingIntoSeveralTasks() {
    ProjectData project = db.components().insertPrivateProject();
    enableProliferation(project);
    List<String> keys = new java.util.ArrayList<>();
    for (int i = 0; i < IssueProliferationSubmitter.ISSUES_PER_TASK + 1; i++) {
      keys.add("issue-" + i);
    }

    try (DbSession dbSession = dbClient.openSession(false)) {
      underTest.submit(dbSession, project.getMainBranchDto().getUuid(), keys, userContext(),
        DefaultTransitions.ACCEPT, null, null);
    }

    List<CeQueueDto> queued = dbClient.ceQueueDao().selectAllInAscOrder(db.getSession());
    assertThat(queued).hasSize(2);
    assertThat(payloadOf(queued.get(0).getUuid()).issueKeys())
      .hasSize(IssueProliferationSubmitter.ISSUES_PER_TASK);
    assertThat(payloadOf(queued.get(1).getUuid()).issueKeys()).hasSize(1);
  }

  private ProliferationTaskPayload payloadOf(String taskUuid) {
    Optional<DbInputStream> data = dbClient.ceTaskInputDao().selectData(db.getSession(), taskUuid);
    assertThat(data).isPresent();
    try (DbInputStream stream = data.get()) {
      return ProliferationTaskPayload.parse(stream.readAllBytes());
    } catch (Exception e) {
      throw new IllegalStateException("could not read the payload of task " + taskUuid, e);
    }
  }

  private void enableProliferation(ProjectData project) {
    db.properties().insertProperty(
      IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED, "true", project.projectUuid());
  }

  private static IssueChangeContext userContext() {
    return IssueChangeContext.issueChangeContextByUserBuilder(new Date(), USER_UUID).build();
  }
}
