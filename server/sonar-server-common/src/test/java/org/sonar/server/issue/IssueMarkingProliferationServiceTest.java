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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueMarkingProliferationService.ProliferatedMarking;
import org.sonar.server.issue.IssueMarkingProliferationService.Signature;
import org.sonar.server.issue.workflow.IssueWorkflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
  private final IssueWorkflow issueWorkflow = mock(IssueWorkflow.class);
  private final ProliferationIssueStorage issueStorage = mock(ProliferationIssueStorage.class);
  private final IssueFieldsSetter issueFieldsSetter = mock(IssueFieldsSetter.class);

  private IssueMarkingProliferationService underTest;

  @Before
  public void setUp() {
    when(dbClient.issueDao()).thenReturn(issueDao);
    underTest = new IssueMarkingProliferationService(dbClient, issueWorkflow, issueStorage, issueFieldsSetter);
  }

  private static IssueDto issue(String key, String branchUuid) {
    IssueDto dto = new IssueDto();
    dto.setKee(key);
    dto.setProjectUuid(branchUuid);
    dto.setComponentUuid("component-" + key);
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

  /** What the batched lookup returns for the one rule-and-file these tests use. */
  private void givenCandidates(IssueDto... candidates) {
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(eq(dbSession), any(), eq(RULE_UUID), eq(FILE_PATH), any()))
      .thenReturn(List.of(candidates));
  }

  /** The two phases as the task processor runs them: index every branch once, then write one branch from it. */
  private Set<String> proliferate(List<IssueDto> sources, String transition, @Nullable String comment,
    IssueChangeContext context) {
    Map<String, Map<Signature, List<IssueDto>>> index =
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID));
    return underTest.proliferateToBranch(dbSession, sources, index.getOrDefault(TARGET_BRANCH_UUID, Map.of()),
      TARGET_BRANCH_UUID, new ProliferatedMarking(context, transition, comment, null, SOURCE_BRANCH_LABEL));
  }

  private Set<String> proliferate(List<IssueDto> sources, String transition, @Nullable String comment) {
    return proliferate(sources, transition, comment, context());
  }

  private Collection<DefaultIssue> captureSaved() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<DefaultIssue>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(issueStorage, org.mockito.Mockito.atLeastOnce()).save(eq(dbSession), captor.capture(), any(), anyLong(),
      any(), any());
    return captor.getAllValues().stream().flatMap(Collection::stream).toList();
  }

  @Test
  public void doesNothingForATransitionThatIsNotAMarking() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto target = issue("BBB-777", TARGET_BRANCH_UUID);
    Map<Signature, List<IssueDto>> targets = Map.of(Signature.of(source), List.of(target));

    underTest.proliferateToBranch(dbSession, List.of(source), targets, TARGET_BRANCH_UUID,
      new ProliferatedMarking(context(), "reopen", "c", null, SOURCE_BRANCH_LABEL));

    verifyNoInteractions(issueWorkflow, issueStorage, issueFieldsSetter);
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
    verify(issueDao, times(0)).selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), any(), any());
  }

  @Test
  public void asksOncePerRuleAndFileRatherThanOncePerIssue() {
    IssueDto first = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto second = issue("AAA-222", SOURCE_BRANCH_UUID);
    second.setLine(43);
    IssueDto third = issue("AAA-333", SOURCE_BRANCH_UUID);
    third.setFilePath("src/Other.cls");

    underTest.indexTargetsBySignature(dbSession, List.of(first, second, third), List.of(TARGET_BRANCH_UUID));

    // Two statements for three issues: the first two share a rule and a file, the third has its own file.
    verify(issueDao, times(2)).selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), any(), any());
  }

  @Test
  public void narrowsTheBroadLookupDownToTheExactLineAndMessage() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto sameLine = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto otherLine = issue("BBB-888", TARGET_BRANCH_UUID);
    otherLine.setLine(99);
    IssueDto otherMessage = issue("BBB-999", TARGET_BRANCH_UUID);
    otherMessage.setMessage("Something else entirely");
    givenCandidates(sameLine, otherLine, otherMessage);
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(List.of(source), DefaultTransitions.ACCEPT, null);

    // The statement returns every issue of the rule in the file; line and message are matched in memory.
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

    // A null line matches only a null line - the "line is null" arm the SQL used to take.
    assertThat(captureSaved()).extracting(DefaultIssue::key).containsExactly("BBB-777");
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
  public void keepsEachBranchesCandidatesApart() {
    IssueDto source = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto onTarget = issue("BBB-777", TARGET_BRANCH_UUID);
    IssueDto onOther = issue("CCC-777", "branch-develop");
    givenCandidates(onTarget, onOther);

    Map<String, Map<Signature, List<IssueDto>>> index = underTest.indexTargetsBySignature(dbSession,
      List.of(source), List.of(TARGET_BRANCH_UUID, "branch-develop"));

    // One statement covered both branches; the rows are split by issues.project_uuid, which is the branch.
    verify(issueDao, times(1)).selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), any(), any());
    assertThat(index.get(TARGET_BRANCH_UUID).get(Signature.of(source))).extracting(IssueDto::getKey)
      .containsExactly("BBB-777");
    assertThat(index.get("branch-develop").get(Signature.of(source))).extracting(IssueDto::getKey)
      .containsExactly("CCC-777");
  }

  @Test
  public void aFailingLookupDoesNotAbortTheRestOrThrow() {
    IssueDto bad = issue("AAA-111", SOURCE_BRANCH_UUID);
    IssueDto good = issue("AAA-222", SOURCE_BRANCH_UUID);
    good.setFilePath("src/Other.cls");
    IssueDto target = issue("BBB-777", TARGET_BRANCH_UUID);
    target.setFilePath("src/Other.cls");
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), eq(FILE_PATH), any()))
      .thenThrow(new IllegalStateException("boom"));
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), eq("src/Other.cls"), any()))
      .thenReturn(List.of(target));
    when(issueWorkflow.doManualTransition(any(DefaultIssue.class), anyString(), any())).thenReturn(true);

    proliferate(List.of(bad, good), DefaultTransitions.ACCEPT, "c");

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
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(eq(dbSession), any(), any(), eq(FILE_PATH), any()))
      .thenReturn(List.of(issue("BBB-777", TARGET_BRANCH_UUID)));
    IssueDto onSecond = issue("BBB-888", TARGET_BRANCH_UUID);
    onSecond.setFilePath("src/Second.cls");
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(eq(dbSession), any(), any(), eq("src/Second.cls"), any()))
      .thenReturn(List.of(onSecond));
    IssueDto onThird = issue("BBB-999", TARGET_BRANCH_UUID);
    onThird.setFilePath("src/Third.cls");
    when(issueDao.selectUnresolvedByRuleAndFileInBranches(eq(dbSession), any(), any(), eq("src/Third.cls"), any()))
      .thenReturn(List.of(onThird));

    List<Map<String, Map<Signature, List<IssueDto>>>> slices = List.of(
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 0, 3),
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 1, 3),
      underTest.indexTargetsBySignature(dbSession, sources, List.of(TARGET_BRANCH_UUID), 2, 3));

    // Three groups over three slices: each slice reads one, and no group is read twice.
    verify(issueDao, times(3)).selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), any(), any());
    Map<String, Map<Signature, List<IssueDto>>> merged = IssueMarkingProliferationService.mergeIndexes(slices);
    assertThat(merged.get(TARGET_BRANCH_UUID).values().stream().flatMap(List::stream))
      .extracting(IssueDto::getKey).containsExactlyInAnyOrder("BBB-777", "BBB-888", "BBB-999");
  }

  @Test
  public void aSliceReadsNothingWhenItHasNoGroups() {
    IssueDto only = issue("AAA-111", SOURCE_BRANCH_UUID);

    Map<String, Map<Signature, List<IssueDto>>> empty =
      underTest.indexTargetsBySignature(dbSession, List.of(only), List.of(TARGET_BRANCH_UUID), 1, 2);

    // One group, two slices: slice 1 has nothing to do and must not issue a statement.
    verify(issueDao, times(0)).selectUnresolvedByRuleAndFileInBranches(any(), any(), any(), any(), any());
    assertThat(empty.get(TARGET_BRANCH_UUID)).isEmpty();
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
