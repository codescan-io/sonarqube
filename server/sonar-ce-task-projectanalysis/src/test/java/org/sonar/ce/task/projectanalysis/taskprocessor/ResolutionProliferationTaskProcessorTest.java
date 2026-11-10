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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.db.DbClient;
import org.sonar.db.DbInputStream;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskInputDao;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDao;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDao;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueMarkingProliferationService;
import org.sonar.server.issue.IssueMarkingProliferationService.Signature;
import org.sonar.server.issue.ProliferationTaskPayload;
import org.sonar.server.measure.live.LiveMeasureComputer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.server.issue.IssueMarkingProliferationService.CHARACTERISTIC_TRANSITION;

public class ResolutionProliferationTaskProcessorTest {

  private static final String PROJECT_UUID = "project";
  private static final String SOURCE_BRANCH = "main";
  private static final String SOURCE_KEY = "source-issue";

  private final DbClient dbClient = mock(DbClient.class);
  private final DbSession dbSession = mock(DbSession.class);
  private final IssueDao issueDao = mock(IssueDao.class);
  private final BranchDao branchDao = mock(BranchDao.class);
  private final ComponentDao componentDao = mock(ComponentDao.class);
  private final CeTaskInputDao ceTaskInputDao = mock(CeTaskInputDao.class);
  private final IssueMarkingProliferationService service = mock(IssueMarkingProliferationService.class);
  private final LiveMeasureComputer liveMeasureComputer = mock(LiveMeasureComputer.class);

  private final ResolutionProliferationTaskProcessor underTest = new ResolutionProliferationTaskProcessor(dbClient,
    service, System2.INSTANCE, liveMeasureComputer);

  private final IssueDto source = new IssueDto().setKee(SOURCE_KEY).setProjectUuid(SOURCE_BRANCH);
  private final Signature signature = new Signature("rule", "src/A.cls", 1, "message");

  @Before
  public void setUp() {
    when(dbClient.openSession(false)).thenReturn(dbSession);
    when(dbClient.issueDao()).thenReturn(issueDao);
    when(dbClient.branchDao()).thenReturn(branchDao);
    when(dbClient.componentDao()).thenReturn(componentDao);
    when(dbClient.ceTaskInputDao()).thenReturn(ceTaskInputDao);
    when(ceTaskInputDao.selectData(any(), eq("task"))).thenAnswer(invocation -> Optional.of(new DbInputStream(
      mock(PreparedStatement.class), mock(ResultSet.class),
      ProliferationTaskPayload.serialize(List.of(SOURCE_KEY), null, null))));
    when(issueDao.selectByKeys(any(), eq(List.of(SOURCE_KEY)))).thenReturn(List.of(source));
    when(componentDao.selectByUuids(any(), anyCollection())).thenAnswer(invocation -> {
      Collection<String> uuids = invocation.getArgument(1);
      return uuids.stream().map(uuid -> new ComponentDto().setUuid(uuid)).toList();
    });
    underTest.start();
  }

  @After
  public void tearDown() {
    underTest.stop();
  }

  private void givenBranches(String... targetBranchUuids) {
    List<BranchDto> branches = new ArrayList<>();
    branches.add(new BranchDto().setUuid(SOURCE_BRANCH));
    for (String uuid : targetBranchUuids) {
      branches.add(new BranchDto().setUuid(uuid));
    }
    when(branchDao.selectByProjectUuid(any(), eq(PROJECT_UUID))).thenReturn(branches);
  }

  /** The candidates the read phase finds, per branch; a branch absent from the map has none. */
  private void givenCandidates(Map<String, Map<Signature, List<IssueDto>>> byBranch) {
    when(service.indexTargetsBySignature(any(), anyCollection(), anyCollection())).thenReturn(byBranch);
  }

  private Map<Signature, List<IssueDto>> oneMatch(String branchUuid) {
    return Map.of(signature, List.of(new IssueDto().setKee("target-on-" + branchUuid).setProjectUuid(branchUuid)));
  }

  private void givenWrittenComponents(String branchUuid, Set<String> componentUuids) {
    when(service.proliferateToBranch(any(), anyCollection(), any(), eq(branchUuid), any())).thenReturn(componentUuids);
  }

  private static CeTask task() {
    return new CeTask.Builder()
      .setUuid("task")
      .setType(CeTaskTypes.RESOLUTION_PROLIFERATION)
      .setComponent(new CeTask.Component(SOURCE_BRANCH, null, null))
      .setEntity(new CeTask.Component(PROJECT_UUID, null, null))
      .setCharacteristics(Map.of(CHARACTERISTIC_TRANSITION, DefaultTransitions.FALSE_POSITIVE))
      .build();
  }

  private static Collection<ComponentDto> componentsNamed(String... uuids) {
    return argThat(components -> components != null
      && components.stream().map(ComponentDto::uuid).toList().containsAll(List.of(uuids))
      && components.size() == uuids.length);
  }

  @Test
  public void refreshesOnlyTheComponentsWrittenOnEachBranch() {
    givenBranches("b1", "b2", "b3");
    givenCandidates(Map.of("b1", oneMatch("b1"), "b2", oneMatch("b2"), "b3", Map.of()));
    givenWrittenComponents("b1", Set.of("file-1", "file-2"));
    givenWrittenComponents("b2", Set.of());

    underTest.process(task());

    verify(liveMeasureComputer).refresh(any(), componentsNamed("file-1", "file-2"));
    verify(liveMeasureComputer, times(1)).refresh(any(), anyCollection());
    // A branch with no candidate is neither written nor refreshed.
    verify(service, never()).proliferateToBranch(any(), anyCollection(), any(), eq("b3"), any());
  }

  @Test
  public void refreshesBranchesConcurrently() throws InterruptedException {
    givenBranches("b1", "b2");
    givenCandidates(Map.of("b1", oneMatch("b1"), "b2", oneMatch("b2")));
    givenWrittenComponents("b1", Set.of("file-b1"));
    givenWrittenComponents("b2", Set.of("file-b2"));
    // Each refresh waits until the other one has started; run one after the other, neither would see the other.
    CountDownLatch bothRefreshing = new CountDownLatch(2);
    Map<String, Boolean> sawTheOther = new ConcurrentHashMap<>();
    when(liveMeasureComputer.refresh(any(), anyCollection())).thenAnswer(invocation -> {
      Collection<ComponentDto> components = invocation.getArgument(1);
      bothRefreshing.countDown();
      sawTheOther.put(components.iterator().next().uuid(), bothRefreshing.await(10, TimeUnit.SECONDS));
      return List.of();
    });

    underTest.process(task());

    assertThat(sawTheOther).containsOnly(Map.entry("file-b1", true), Map.entry("file-b2", true));
  }

  @Test
  public void aFailingBranchDoesNotStopTheOthersFromBeingWrittenAndRefreshed() {
    givenBranches("b1", "b2");
    givenCandidates(Map.of("b1", oneMatch("b1"), "b2", oneMatch("b2")));
    when(service.proliferateToBranch(any(), anyCollection(), any(), eq("b1"), any()))
      .thenThrow(new IllegalStateException("simulated"));
    givenWrittenComponents("b2", Set.of("file-b2"));

    underTest.process(task());

    verify(liveMeasureComputer).refresh(any(), componentsNamed("file-b2"));
    verify(liveMeasureComputer, times(1)).refresh(any(), anyCollection());
  }
}
