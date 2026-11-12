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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.ce.queue.CeQueue;
import org.sonar.ce.queue.CeTaskSubmit;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskInputDao;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDao;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.server.setting.ProjectConfigurationLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The web server side of the hookup: what {@code bulk_change} and {@code do_transition} hand to the compute
 * engine, and the guards that decide whether a task is queued at all.
 */
public class IssueProliferationSubmitterTest {

  private static final String BRANCH_UUID = "branch-main";
  private static final String PROJECT_UUID = "project-uuid";
  private static final String USER_UUID = "user-uuid";
  private static final String TASK_UUID = "generated-task-uuid";

  private final DbSession dbSession = mock(DbSession.class);
  private final DbClient dbClient = mock(DbClient.class);
  private final BranchDao branchDao = mock(BranchDao.class);
  private final CeTaskInputDao ceTaskInputDao = mock(CeTaskInputDao.class);
  private final CeQueue ceQueue = mock(CeQueue.class);
  private final IssueMarkingProliferationService proliferationService = mock(IssueMarkingProliferationService.class);
  private final ProjectConfigurationLoader projectConfigurationLoader = mock(ProjectConfigurationLoader.class);
  private final Configuration configuration = mock(Configuration.class);

  private IssueProliferationSubmitter underTest;

  @Before
  public void setUp() {
    when(dbClient.branchDao()).thenReturn(branchDao);
    when(dbClient.ceTaskInputDao()).thenReturn(ceTaskInputDao);
    when(branchDao.selectByUuid(dbSession, BRANCH_UUID)).thenReturn(Optional.of(branch()));
    when(projectConfigurationLoader.loadProjectConfiguration(any(), anyString())).thenReturn(configuration);
    when(ceQueue.prepareSubmit()).thenAnswer(invocation -> new CeTaskSubmit.Builder(TASK_UUID));
    when(proliferationService.isProliferable(anyString())).thenReturn(false);
    when(proliferationService.isProliferable(DefaultTransitions.ACCEPT)).thenReturn(true);
    when(proliferationService.isProliferable(DefaultTransitions.FALSE_POSITIVE)).thenReturn(true);
    enableProliferation(true);

    underTest = new IssueProliferationSubmitter(dbClient, ceQueue, proliferationService, projectConfigurationLoader);
  }

  @Test
  public void queuesOneTaskOfTheProliferationType() {
    submit(DefaultTransitions.ACCEPT, List.of("issue-1", "issue-2"));

    CeTaskSubmit submitted = captureSubmission();
    assertThat(submitted.getType()).isEqualTo(CeTaskTypes.RESOLUTION_PROLIFERATION);
    assertThat(submitted.getComponent()).isPresent();
    assertThat(submitted.getComponent().get().getUuid()).isEqualTo(BRANCH_UUID);
    assertThat(submitted.getComponent().get().getEntityUuid()).isEqualTo(PROJECT_UUID);
  }

  /**
   * Background Tasks resolves the submitter uuid to a name; leaving it null is what rendered proliferation tasks
   * as "Anonymous" while every other task type showed who submitted it.
   */
  @Test
  public void creditsTheUserBehindTheMarking() {
    submit(DefaultTransitions.ACCEPT, List.of("issue-1"));

    assertThat(captureSubmission().getSubmitterUuid()).isEqualTo(USER_UUID);
  }

  @Test
  public void leavesTheSubmitterNullWhenNoUserIsBehindTheChange() {
    underTest.submit(dbSession, BRANCH_UUID, List.of("issue-1"),
      IssueChangeContext.issueChangeContextByScanBuilder(new Date()).build(),
      DefaultTransitions.ACCEPT, null, null);

    assertThat(captureSubmission().getSubmitterUuid()).isNull();
  }

  @Test
  public void carriesTheTransitionAndAuthorInTheCharacteristics() {
    submit(DefaultTransitions.FALSE_POSITIVE, List.of("issue-1"));

    assertThat(captureSubmission().getCharacteristics())
      .containsEntry(IssueMarkingProliferationService.CHARACTERISTIC_TRANSITION, DefaultTransitions.FALSE_POSITIVE)
      .containsEntry(IssueMarkingProliferationService.CHARACTERISTIC_AUTHOR, USER_UUID)
      .containsEntry(IssueMarkingProliferationService.CHARACTERISTIC_SOURCE_BRANCH_LABEL, "main");
  }

  /** The keys must be readable before the task row exists, or a worker could find nothing to do. */
  @Test
  public void storesTheIssueKeysBeforeSubmitting() {
    submit(DefaultTransitions.ACCEPT, List.of("issue-1", "issue-2"));

    verify(ceTaskInputDao).insert(any(DbSession.class), anyString(), any());
    verify(ceQueue).massSubmit(any());
  }

  @Test
  public void queuesNothingWhenThereAreNoIssues() {
    submit(DefaultTransitions.ACCEPT, List.of());

    verifyNoInteractions(branchDao, ceTaskInputDao);
    verify(ceQueue, never()).massSubmit(any());
  }

  /** reopen, confirm and resolve-as-fixed are not markings this feature copies. */
  @Test
  public void queuesNothingForATransitionThatIsNotAMarking() {
    submit(DefaultTransitions.REOPEN, List.of("issue-1"));

    verifyNoInteractions(branchDao, ceTaskInputDao);
    verify(ceQueue, never()).massSubmit(any());
  }

  @Test
  public void queuesNothingWhenProliferationIsDisabledForTheProject() {
    enableProliferation(false);

    submit(DefaultTransitions.ACCEPT, List.of("issue-1"));

    verifyNoInteractions(ceTaskInputDao);
    verify(ceQueue, never()).massSubmit(any());
  }

  @Test
  public void queuesNothingWhenTheSourceBranchIsUnknown() {
    when(branchDao.selectByUuid(dbSession, BRANCH_UUID)).thenReturn(Optional.empty());

    submit(DefaultTransitions.ACCEPT, List.of("issue-1"));

    verifyNoInteractions(ceTaskInputDao);
    verify(ceQueue, never()).massSubmit(any());
  }

  /** A failure to queue must not fail the bulk change the user just made: the issues are already saved. */
  @Test
  public void swallowsAFailureToQueue() {
    when(ceQueue.prepareSubmit()).thenThrow(new IllegalStateException("queue is down"));

    submit(DefaultTransitions.ACCEPT, List.of("issue-1"));

    verify(ceQueue, never()).massSubmit(any());
  }

  private void submit(String transition, List<String> issueKeys) {
    underTest.submit(dbSession, BRANCH_UUID, issueKeys, userContext(), transition, null, null);
  }

  private CeTaskSubmit captureSubmission() {
    ArgumentCaptor<List<CeTaskSubmit>> captor = ArgumentCaptor.forClass(List.class);
    verify(ceQueue).massSubmit(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    return captor.getValue().get(0);
  }

  private void enableProliferation(boolean enabled) {
    when(configuration.getBoolean(IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED))
      .thenReturn(Optional.of(enabled));
  }

  private static IssueChangeContext userContext() {
    return IssueChangeContext.issueChangeContextByUserBuilder(new Date(), USER_UUID).build();
  }

  private static BranchDto branch() {
    return new BranchDto()
      .setUuid(BRANCH_UUID)
      .setProjectUuid(PROJECT_UUID)
      .setKey("main")
      .setBranchType(BranchType.BRANCH)
      .setIsMain(true);
  }
}
