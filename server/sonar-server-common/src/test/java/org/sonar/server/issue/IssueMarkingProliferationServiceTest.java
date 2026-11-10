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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.rules.RuleType;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDao;
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.issue.ProliferationCandidateDto;
import org.sonar.server.issue.IssueMarkingProliferationService.BranchOutcome;
import org.sonar.server.issue.IssueMarkingProliferationService.ProliferatedMarking;
import org.sonar.server.issue.IssueMarkingProliferationService.Signature;
import org.sonar.server.issue.IssueMarkingProliferationService.TargetIndex;
import org.sonar.server.issue.workflow.IssueWorkflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IssueMarkingProliferationServiceTest {

  private static final String SOURCE_BRANCH_UUID = "branch-main";
  private static final String TARGET_BRANCH_UUID = "branch-release";
  private static final String SOURCE_BRANCH_LABEL = "main";
  private static final String RULE_UUID = "rule-uuid";
  private static final String FILE_PATH = "src/AccountService.cls";
  private static final String MESSAGE = "Avoid instantiating new objects inside loops";

  private final DbSession dbSession = mock(DbSession.class);
  private final DbClient dbClient = mock(DbClient.class);
  private final IssueDao issueDao = mock(IssueDao.class);
  private final ComponentDao componentDao = mock(ComponentDao.class);
  private final IssueWorkflow issueWorkflow = mock(IssueWorkflow.class);
  private final ProliferationIssueStorage issueStorage = mock(ProliferationIssueStorage.class);
  private final IssueFieldsSetter issueFieldsSetter = mock(IssueFieldsSetter.class);

  /** Stand-in for the issues table: the candidates of each file, and every issue by key. */
  private final Map<String, List<IssueDto>> candidatesByFile = new HashMap<>();
  private final Map<String, IssueDto> issuesByKey = new HashMap<>();

  private IssueMarkingProliferationService underTest;

  @Before
  public void setUp() {
    when(dbClient.issueDao()).thenReturn(issueDao);
    when(dbClient.componentDao()).thenReturn(componentDao);
    // The lookup resolves a file to its components on the target branches before asking for issues. These tests
    // are about the matching, not the resolution, so every file resolves to one stand-in component.
    when(componentDao.selectUuidsByKeyAndPathInBranches(any(), anyString(), anyString(), any()))
      .thenAnswer(invocation -> List.of(componentsOf(invocation.getArgument(2))));
    // The candidate statement, filtering as the SQL does: rule, status and line; the message is left to the caller.
    org.mockito.Mockito.doAnswer(invocation -> {
      Collection<String> components = invocation.getArgument(1);
      String ruleUuid = invocation.getArgument(2);
      Collection<String> statuses = invocation.getArgument(3);
      Collection<Integer> lines = invocation.getArgument(4);
      boolean includeNullLine = invocation.getArgument(5);
      Consumer<ProliferationCandidateDto> consumer = invocation.getArgument(6);
      candidatesByFile.forEach((filePath, candidates) -> {
        if (!components.contains(componentsOf(filePath))) {
          return;
        }
        candidates.stream()
          .filter(c -> ruleUuid.equals(c.getRuleUuid()) && statuses.contains(c.getStatus()))
          .filter(c -> c.getLine() == null ? includeNullLine : lines.contains(c.getLine()))
          .forEach(c -> consumer.accept(new ProliferationCandidateDto().setIssueKey(c.getKey())
            .setBranchUuid(c.getProjectUuid()).setLine(c.getLine()).setMessage(c.getMessage())));
      });
      return null;
    }).when(issueDao).scrollProliferationCandidates(any(), anyCollection(), anyString(), anyCollection(),
      anyCollection(), anyBoolean(), any());
    when(issueDao.selectByKeys(any(), anyCollection())).thenAnswer(invocation -> {
      Collection<String> keys = invocation.getArgument(1);
      return keys.stream().map(issuesByKey::get).filter(java.util.Objects::nonNull).toList();
    });
    // The storage reports every issue it was handed as written, as it does when no row changed underneath it.
    when(issueStorage.save(any(), any(), any(), anyLong(), any(), any())).thenAnswer(invocation -> {
      Collection<DefaultIssue> issues = invocation.getArgument(1);
      return issues.stream().map(DefaultIssue::key).toList();
    });
    underTest = new IssueMarkingProliferationService(dbClient, issueWorkflow, issueStorage, issueFieldsSetter);
  }

  /** The stand-in component uuid a file path resolves to, so a mock can be keyed on the path it came from. */
  private static String componentsOf(String filePath) {
    return "component-of:" + filePath;
  }

  private static IssueDto issue(String key, String branchUuid) {
    IssueDto dto = new IssueDto();
    dto.setKee(key);
    dto.setProjectUuid(branchUuid);
    dto.setComponentUuid("component-" + key);
    dto.setComponentKey("project:" + FILE_PATH);
    dto.setRuleUuid(RULE_UUID);
    dto.setFilePath(FILE_PATH);
    dto.setMessage(MESSAGE);
    dto.setLine(42);
    dto.setType(RuleType.CODE_SMELL);
    dto.setRuleKey("apex", "AvoidInstantiatingObjectsInLoops");
    dto.setLanguage("apex");
    dto.setStatus("OPEN");
    return dto;
  }

  private static IssueChangeContext context() {
    return IssueChangeContext.issueChangeContextByUserBuilder(new Date(1_700_000_000_000L), "user-uuid").build();
  }

  /** Issues on the target branches, filed under their file path as the candidate statement sees them. */
  private void givenCandidates(IssueDto... candidates) {
    for (IssueDto candidate : candidates) {
      candidatesByFile.computeIfAbsent(candidate.getFilePath(), k -> new ArrayList<>()).add(candidate);
      issuesByKey.put(candidate.getKey(), candidate);
    }
  }

  /** The two phases as the task processor runs them: index every branch once, then write one branch from it. */
  private BranchOutcome proliferateOutcome(List<IssueDto> sources, String transition, @Nullable String comment,
    IssueChangeContext context) {
    TargetIndex index = underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID));
    return underTest.proliferateToBranch(dbSession, sources, index.of(TARGET_BRANCH_UUID), TARGET_BRANCH_UUID,
      new ProliferatedMarking(context, transition, comment, null, SOURCE_BRANCH_LABEL));
  }

  private Set<String> proliferate(List<IssueDto> sources, String transition, @Nullable String comment,
    IssueChangeContext context) {
    return proliferateOutcome(sources, transition, comment, context).writtenComponents();
  }

  private Set<String> proliferate(List<IssueDto> sources, String transition, @Nullable String comment) {
    return proliferate(sources, transition, comment, context());
  }

  private Collection<DefaultIssue> captureSaved() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<DefaultIssue>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(issueStorage, atLeast(0)).save(eq(dbSession), captor.capture(), any(), anyLong(), any(), any());
    return captor.getAllValues().stream().flatMap(Collection::stream).toList();
  }

  private void verifyCandidateStatements(int times) {
    verify(issueDao, times(times)).scrollProliferationCandidates(any(), anyCollection(), anyString(),
      anyCollection(), anyCollection(), anyBoolean(), any());
  }

  @Test
  public void doesNothingForATransitionThatIsNotAMarking() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    Map<Signature, List<String>> targets = Map.of(Signature.of(source), List.of("BBB-777"));

    BranchOutcome outcome = underTest.proliferateToBranch(dbSession, List.of(source), targets, TARGET_BRANCH_UUID,
      new ProliferatedMarking(context(), "reopen", "c", null, SOURCE_BRANCH_LABEL));

    verifyNoInteractions(issueWorkflow, issueStorage, issueFieldsSetter);
    assertThat(outcome.writtenComponents()).isEmpty();
  }

  @Test
  public void appliesTheMarkingToAMatchingIssueOnTheTargetBranch() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    source.setIssueResolutionExpiresAt(123L);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), eq(DefaultTransitions.FALSE_POSITIVE), any()))
      .thenReturn(true);

    proliferate(List.of(source), DefaultTransitions.FALSE_POSITIVE, "looks fine");

    Collection<DefaultIssue> saved = captureSaved();
    assertThat(saved).extracting(DefaultIssue::key).containsExactly("BBB-777");
    // The expiry is copied from the source so every branch expires together.
    assertThat(saved.iterator().next().issueResolutionExpiresAt()).isEqualTo(123L);
  }

  @Test
  public void reportsTheTouchedComponents() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    assertThat(proliferate(List.of(source), DefaultTransitions.ACCEPT, null))
      .containsExactly("component-BBB-777");
  }

  @Test
  public void leavesOutTheComponentsOfIssuesThatWereNotWritten() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID), issue("BBB-888", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);
    doReturn(List.of("BBB-888")).when(issueStorage).save(any(), any(), any(), anyLong(), any(), any());

    assertThat(proliferate(List.of(source), DefaultTransitions.ACCEPT, null))
      .containsExactly("component-BBB-888");
  }

  @Test
  public void copiesTheUserCommentAndTheProvenanceNoteStampedWithTheSourceMarkingTime() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    IssueChangeContext context = context();
    proliferate(List.of(source), DefaultTransitions.ACCEPT, "looks fine", context);

    Date markedAt = context.date();
    verify(issueFieldsSetter).addComment(any(DefaultIssue.class), eq("looks fine"), eq(context), eq(markedAt));
    verify(issueFieldsSetter).addComment(any(DefaultIssue.class), eq("Resolution proliferated from main branch"),
      eq(context), eq(markedAt));
  }

  @Test
  public void writesNothingWhenTheWorkflowDeclinesTheTransition() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(false);

    proliferate(List.of(source), DefaultTransitions.ACCEPT, "c");

    // No comment: a declined transition must not leave a trail, or repeated runs would stack comments.
    verifyNoInteractions(issueFieldsSetter);
    assertThat(captureSaved()).isEmpty();
  }

  @Test
  public void skipsASourceWithNoFileOrNoMessage() {
    IssueDto noFile = issue("AAA-111", SOURCE_BRANCH_UUID);
    noFile.setFilePath(null);
    IssueDto noMessage = issue("AAA-222", SOURCE_BRANCH_UUID);
    noMessage.setMessage(null);

    proliferate(List.of(noFile, noMessage), DefaultTransitions.ACCEPT, "c");

    // Without both, the signature is not well defined and every issue of that rule on the file would match - so
    // neither is even looked up.
    verifyNoInteractions(issueWorkflow);
    verifyCandidateStatements(0);
  }

  @Test
  public void asksOncePerRuleAndFileRatherThanOncePerIssue() {
    IssueDto first = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto second = issue("AAA-222", SOURCE_BRANCH_UUID);
    second.setLine(43);
    IssueDto third = issue("AAA-333", SOURCE_BRANCH_UUID);
    third.setFilePath("src/Other.cls");
    third.setComponentKey("project:src/Other.cls");

    underTest.indexTargetsBySignature(dbSession, List.of(first, second, third), List.of(TARGET_BRANCH_UUID));

    // Two statements for three issues: the first two share a rule and a file, the third has its own file.
    verifyCandidateStatements(2);
  }

  @Test
  public void resolvesTheFileByItsKeyAndPath() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);

    underTest.indexTargetsBySignature(dbSession, List.of(source), List.of(TARGET_BRANCH_UUID));

    verify(componentDao).selectUuidsByKeyAndPathInBranches(dbSession, "project:" + FILE_PATH, FILE_PATH,
      List.of(TARGET_BRANCH_UUID));
  }

  @Test
  public void asksOnlyForTheLinesBeingMarked() {
    IssueDto first = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto second = issue("AAA-222", SOURCE_BRANCH_UUID);
    second.setLine(43);

    underTest.indexTargetsBySignature(dbSession, List.of(first, second), List.of(TARGET_BRANCH_UUID));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Integer>> lines = ArgumentCaptor.forClass(Collection.class);
    verify(issueDao).scrollProliferationCandidates(eq(dbSession), eq(List.of(componentsOf(FILE_PATH))),
      eq(RULE_UUID), anyCollection(), lines.capture(), eq(false), any());
    assertThat(lines.getValue()).containsExactlyInAnyOrder(42, 43);
  }

  @Test
  public void narrowsTheCandidatesDownToTheExactLineAndMessage() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto sameLine = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto otherLine = issue("BBB-888", TARGET_BRANCH_UUID);
    otherLine.setLine(99);
    IssueDto otherMessage = issue("BBB-999", TARGET_BRANCH_UUID);
    otherMessage.setMessage("Something else entirely");
    givenCandidates(sameLine, otherLine, otherMessage);
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(List.of(source), DefaultTransitions.ACCEPT, null);

    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-777");
  }

  @Test
  public void matchesAnIssueThatHasNoLine() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    source.setLine(null);
    IssueDto noLine = issue("BBB-777", TARGET_BRANCH_UUID);
    noLine.setLine(null);
    IssueDto withLine = issue("BBB-888", TARGET_BRANCH_UUID);
    givenCandidates(noLine, withLine);
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(List.of(source), DefaultTransitions.ACCEPT, null);

    // A null line matches only a null line.
    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-777");
    verify(issueDao).scrollProliferationCandidates(any(), anyCollection(), anyString(), anyCollection(),
      eq(Set.of()), eq(true), any());
  }

  @Test
  public void marksATargetOnceEvenWhenTwoSourcesShareItsSignature() {
    IssueDto first = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto duplicate = issue("AAA-222", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(List.of(first, duplicate), DefaultTransitions.ACCEPT, "c");

    // Both sources carry the same signature; without the guard the target would be marked - and commented - twice.
    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-777");
  }

  @Test
  public void skipsAMatchWhoseStatusChangedSinceItWasRead() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto target = issue("BBB-777", TARGET_BRANCH_UUID);
    givenCandidates(target);
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);
    TargetIndex index = underTest.indexTargetsBySignature(dbSession, List.of(source), List.of(TARGET_BRANCH_UUID));
    // Resolved by someone else between the read and the write.
    target.setStatus("RESOLVED");

    underTest.proliferateToBranch(dbSession, List.of(source), index.of(TARGET_BRANCH_UUID), TARGET_BRANCH_UUID,
      new ProliferatedMarking(context(), DefaultTransitions.ACCEPT, null, null, SOURCE_BRANCH_LABEL));

    assertThat(captureSaved()).isEmpty();
    verifyNoInteractions(issueWorkflow);
  }

  @Test
  public void loadsAndWritesTheMatchesInBatches() {
    List<IssueDto> sources = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      IssueDto source = issue("AAA-" + i, SOURCE_BRANCH_UUID);
      source.setLine(i + 1);
      sources.add(source);
      IssueDto target = issue("BBB-" + i, TARGET_BRANCH_UUID);
      target.setLine(i + 1);
      givenCandidates(target);
    }
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(sources, DefaultTransitions.ACCEPT, null);

    // 501 matches, 500 per batch: two loads and two writes, never one load of every match.
    verify(issueDao, times(2)).selectByKeys(any(), anyCollection());
    verify(issueStorage, times(2)).save(any(), any(), any(), anyLong(), any(), any());
    assertThat(captureSaved()).hasSize(501);
  }

  @Test
  public void countsAMatchThatFailsToPrepareAndWritesTheOthers() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto bad = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto good = issue("BBB-888", TARGET_BRANCH_UUID);
    givenCandidates(bad, good);
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenAnswer(invocation -> {
      DefaultIssue issue = invocation.getArgument(0);
      if (issue.key().equals("BBB-777")) {
        throw new IllegalStateException("simulated");
      }
      return true;
    });

    BranchOutcome outcome = proliferateOutcome(List.of(source), DefaultTransitions.ACCEPT, null, context());

    assertThat(outcome.failures()).isEqualTo(1);
    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-888");
  }

  @Test
  public void countsABranchThatCouldNotBeIndexed() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    givenCandidates(issue("BBB-777", TARGET_BRANCH_UUID));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("es down")).when(issueStorage).indexBranch(any(), any());

    BranchOutcome outcome = proliferateOutcome(List.of(source), DefaultTransitions.ACCEPT, null, context());

    assertThat(outcome.failures()).isEqualTo(1);
    assertThat(outcome.writtenComponents()).containsExactly("component-BBB-777");
  }

  @Test
  public void keepsEachBranchesCandidatesApart() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto onTarget = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto onOther = issue("CCC-777", "branch-develop");
    givenCandidates(onTarget, onOther);

    TargetIndex index = underTest.indexTargetsBySignature(dbSession, List.of(source),
      List.of(TARGET_BRANCH_UUID, "branch-develop"));

    // One statement covered both branches; the rows are split by issues.project_uuid, which is the branch.
    verifyCandidateStatements(1);
    assertThat(index.of(TARGET_BRANCH_UUID).get(Signature.of(source))).containsExactly("BBB-777");
    assertThat(index.of("branch-develop").get(Signature.of(source))).containsExactly("CCC-777");
  }

  @Test
  public void countsAFailingLookupAndReadsTheRest() {
    IssueDto bad = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto good = issue("AAA-222", SOURCE_BRANCH_UUID);
    good.setFilePath("src/Other.cls");
    good.setComponentKey("project:src/Other.cls");
    IssueDto target = issue("BBB-777", TARGET_BRANCH_UUID);
    target.setFilePath("src/Other.cls");
    givenCandidates(target);
    when(componentDao.selectUuidsByKeyAndPathInBranches(any(), anyString(), eq(FILE_PATH), any()))
      .thenThrow(new IllegalStateException("boom"));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    TargetIndex index = underTest.indexTargetsBySignature(dbSession, List.of(bad, good), List.of(TARGET_BRANCH_UUID));
    underTest.proliferateToBranch(dbSession, List.of(bad, good), index.of(TARGET_BRANCH_UUID), TARGET_BRANCH_UUID,
      new ProliferatedMarking(context(), DefaultTransitions.ACCEPT, null, null, SOURCE_BRANCH_LABEL));

    assertThat(index.failedGroups()).isEqualTo(1);
    // The failed statement may have aborted the transaction, so the session is rolled back before the next group.
    verify(dbSession).rollback();
    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-777");
  }

  @Test
  public void slicesCoverEveryGroupExactlyOnceAndMergeBack() {
    IssueDto first = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto second = issue("AAA-222", SOURCE_BRANCH_UUID);
    second.setFilePath("src/Second.cls");
    IssueDto third = issue("AAA-333", SOURCE_BRANCH_UUID);
    third.setFilePath("src/Third.cls");
    List<IssueDto> sources = List.of(first, second, third);
    IssueDto onFirst = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto onSecond = issue("BBB-888", TARGET_BRANCH_UUID);
    onSecond.setFilePath("src/Second.cls");
    IssueDto onThird = issue("BBB-999", TARGET_BRANCH_UUID);
    onThird.setFilePath("src/Third.cls");
    givenCandidates(onFirst, onSecond, onThird);

    List<TargetIndex> slices = List.of(
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 0, 3),
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 1, 3),
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 2, 3));

    // Three groups over three slices: each slice reads one, and no group is read twice.
    verifyCandidateStatements(3);
    TargetIndex merged = IssueMarkingProliferationService.mergeIndexes(slices);
    assertThat(merged.of(TARGET_BRANCH_UUID).values().stream().flatMap(List::stream))
      .containsExactlyInAnyOrder("BBB-777", "BBB-888", "BBB-999");
  }

  @Test
  public void aSliceReadsNothingWhenItHasNoGroups() {
    IssueDto only = issue("AAA-111", SOURCE_BRANCH_UUID);

    TargetIndex empty = underTest.indexTargetsBySignature(dbSession, List.of(only), List.of(TARGET_BRANCH_UUID), 1,
      2);

    // One group, two slices: slice 1 has nothing to do and must not issue a statement.
    verifyCandidateStatements(0);
    assertThat(empty.hasMatches(TARGET_BRANCH_UUID)).isFalse();
  }

  @Test
  public void namesTheProvenanceNoteAfterTheSourceBranch() {
    assertThat(IssueMarkingProliferationService.proliferationNote("main"))
      .isEqualTo("Resolution proliferated from main branch");
  }

  @Test
  public void namesAPullRequestSourceWithItsPrefix() {
    BranchDto pr = new BranchDto();
    pr.setKey("1234");
    pr.setBranchType(BranchType.PULL_REQUEST);
    assertThat(IssueMarkingProliferationService.branchLabel(pr)).isEqualTo("#1234");
  }

  @Test
  public void onlyOpenAndReopenedIssuesAreCandidates() {
    assertThat(underTest.targetStatuses()).containsExactlyInAnyOrder("OPEN", "REOPENED");
  }
}
