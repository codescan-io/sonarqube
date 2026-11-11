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

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskInputDao;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDao;
import org.sonar.db.issue.IssueDao;
import org.sonar.server.issue.IssueMarkingProliferationService;
import org.sonar.server.measure.live.LiveMeasureComputer;
import org.sonar.server.setting.ProjectConfigurationLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The wiring of the compute engine side of resolution proliferation: which tasks the processor claims, and the
 * guards it applies before it reads anything or writes anything. The matching itself is covered by
 * {@code IssueMarkingProliferationServiceTest}; these tests are about the hookup around it.
 */
public class ResolutionProliferationTaskProcessorTest {

  private static final String TASK_UUID = "task-uuid";
  private static final String BRANCH_UUID = "branch-main";
  private static final String PROJECT_UUID = "project-uuid";

  private final DbSession dbSession = mock(DbSession.class);
  private final DbClient dbClient = mock(DbClient.class);
  private final CeTaskInputDao ceTaskInputDao = mock(CeTaskInputDao.class);
  private final IssueDao issueDao = mock(IssueDao.class);
  private final BranchDao branchDao = mock(BranchDao.class);
  private final IssueMarkingProliferationService proliferationService = mock(IssueMarkingProliferationService.class);
  private final LiveMeasureComputer liveMeasureComputer = mock(LiveMeasureComputer.class);
  private final ProjectConfigurationLoader projectConfigurationLoader = mock(ProjectConfigurationLoader.class);
  private final Configuration configuration = mock(Configuration.class);

  private ResolutionProliferationTaskProcessor underTest;

  @Before
  public void setUp() {
    when(dbClient.openSession(false)).thenReturn(dbSession);
    when(dbClient.ceTaskInputDao()).thenReturn(ceTaskInputDao);
    when(dbClient.issueDao()).thenReturn(issueDao);
    when(dbClient.branchDao()).thenReturn(branchDao);
    when(projectConfigurationLoader.loadProjectConfiguration(any(), anyString())).thenReturn(configuration);
    enableProliferation(true);

    underTest = new ResolutionProliferationTaskProcessor(dbClient, proliferationService, System2.INSTANCE,
      liveMeasureComputer, projectConfigurationLoader);
    underTest.start();
  }

  @After
  public void tearDown() {
    underTest.stop();
  }

  @Test
  public void handlesOnlyTheProliferationTaskType() {
    assertThat(underTest.getHandledCeTaskTypes()).containsExactly(CeTaskTypes.RESOLUTION_PROLIFERATION);
  }

  @Test
  public void producesNoTaskResult() {
    assertThat(underTest.process(taskWith(DefaultTransitions.ACCEPT))).isNull();
  }

  /**
   * Gap A: the setting is read where the task was queued, but a task can sit in the queue behind a long analysis.
   * Turning the feature off has to stop the tasks already queued, not only the ones queued afterwards.
   */
  @Test
  public void doesNothingWhenProliferationIsDisabledForTheProject() {
    enableProliferation(false);

    underTest.process(taskWith(DefaultTransitions.ACCEPT));

    verifyNoInteractions(ceTaskInputDao, issueDao, branchDao, proliferationService, liveMeasureComputer);
  }

  /** The gate runs before the payload is read, so a disabled project costs one setting lookup and nothing more. */
  @Test
  public void checksTheSettingBeforeReadingThePayload() {
    enableProliferation(false);

    underTest.process(taskWith(DefaultTransitions.ACCEPT));

    verify(projectConfigurationLoader).loadProjectConfiguration(dbSession, PROJECT_UUID);
    verifyNoInteractions(ceTaskInputDao);
  }

  /** A setting left unset is off: proliferation is opt-in per project. */
  @Test
  public void treatsAnAbsentSettingAsDisabled() {
    when(configuration.getBoolean(IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED))
      .thenReturn(Optional.empty());

    underTest.process(taskWith(DefaultTransitions.ACCEPT));

    verifyNoInteractions(ceTaskInputDao, issueDao, proliferationService);
  }

  @Test
  public void proceedsPastTheGateWhenProliferationIsEnabled() {
    when(ceTaskInputDao.selectData(dbSession, TASK_UUID)).thenReturn(Optional.empty());

    underTest.process(taskWith(DefaultTransitions.ACCEPT));

    // The gate passed: the payload was read. It was empty, so the task stops there without touching the issues.
    verify(ceTaskInputDao).selectData(dbSession, TASK_UUID);
    verifyNoInteractions(issueDao, proliferationService, liveMeasureComputer);
  }

  /** A task with no stored keys is a no-op rather than a failure: there is nothing to copy. */
  @Test
  public void stopsWhenThePayloadHasNoIssueKeys() {
    when(ceTaskInputDao.selectData(dbSession, TASK_UUID)).thenReturn(Optional.empty());

    assertThat(underTest.process(taskWith(DefaultTransitions.ACCEPT))).isNull();

    verifyNoInteractions(issueDao, branchDao, proliferationService);
  }

  /**
   * A task carrying neither branch nor project cannot say what to proliferate or where from. {@link CeTask}
   * rejects one of the two being null on its own ("None or both component and entity must be non null"), so
   * both absent is the only shape this guard can actually see.
   */
  @Test
  public void stopsWhenTheTaskHasNeitherComponentNorEntity() {
    CeTask task = new CeTask.Builder()
      .setUuid(TASK_UUID)
      .setType(CeTaskTypes.RESOLUTION_PROLIFERATION)
      .setComponent(null)
      .setEntity(null)
      .setCharacteristics(Map.of(IssueMarkingProliferationService.CHARACTERISTIC_TRANSITION, DefaultTransitions.ACCEPT))
      .build();

    assertThat(underTest.process(task)).isNull();

    verifyNoInteractions(projectConfigurationLoader, ceTaskInputDao, issueDao, proliferationService);
  }

  /** The transition is read from the characteristics; without it the task cannot know which marking to copy. */
  @Test
  public void stopsWhenTheTaskCarriesNoTransition() {
    when(ceTaskInputDao.selectData(dbSession, TASK_UUID)).thenReturn(Optional.empty());

    assertThat(underTest.process(taskWith(null))).isNull();

    verifyNoInteractions(proliferationService, liveMeasureComputer);
  }

  private void enableProliferation(boolean enabled) {
    when(configuration.getBoolean(IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED))
      .thenReturn(Optional.of(enabled));
  }

  private static CeTask taskWith(@Nullable String transition) {
    Map<String, String> characteristics = transition == null ? Map.of()
      : Map.of(IssueMarkingProliferationService.CHARACTERISTIC_TRANSITION, transition);
    return new CeTask.Builder()
      .setUuid(TASK_UUID)
      .setType(CeTaskTypes.RESOLUTION_PROLIFERATION)
      .setComponent(new CeTask.Component(BRANCH_UUID, null, null))
      .setEntity(new CeTask.Component(PROJECT_UUID, null, null))
      .setCharacteristics(characteristics)
      .build();
  }
}
