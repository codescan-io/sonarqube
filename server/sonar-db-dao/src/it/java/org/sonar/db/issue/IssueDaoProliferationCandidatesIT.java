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
package org.sonar.db.issue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.STATUS_CONFIRMED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_REOPENED;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;

/**
 * CodeScan: the two lookups of the proliferation read phase - the file across branches by key and path, and the
 * candidates of one rule on those files filtered by line in SQL.
 */
public class IssueDaoProliferationCandidatesIT {

  private static final List<String> CANDIDATE_STATUSES = List.of(STATUS_OPEN, STATUS_REOPENED);

  @Rule
  public DbTester db = DbTester.create();

  private final IssueDao underTest = db.getDbClient().issueDao();

  private ComponentDto branchA;
  private ComponentDto branchB;
  private ComponentDto fileOnA;
  private ComponentDto fileOnB;
  private RuleDto rule;

  /** Two branch roots holding the same file, under the same key and path, as analysed branches of a project do. */
  private void givenTheFileOnTwoBranches() {
    branchA = db.components().insertPrivateProject().getMainBranchComponent();
    branchB = db.components().insertPrivateProject().getMainBranchComponent();
    fileOnA = db.components().insertComponent(newFileDto(branchA).setKey("project:src/A.cls").setPath("src/A.cls"));
    fileOnB = db.components().insertComponent(newFileDto(branchB).setKey("project:src/A.cls").setPath("src/A.cls"));
    rule = db.rules().insertIssueRule();
  }

  private IssueDto insertIssue(ComponentDto branch, ComponentDto file, RuleDto issueRule, Integer line,
    String status) {
    return db.issues().insertIssue(issueRule, branch, file, i -> i
      .setLine(line)
      .setMessage("message " + line)
      .setStatus(status)
      .setResolution(STATUS_RESOLVED.equals(status) ? RESOLUTION_FALSE_POSITIVE : null));
  }

  private List<ProliferationCandidateDto> scroll(Set<Integer> lines, boolean includeNullLine) {
    List<ProliferationCandidateDto> rows = new ArrayList<>();
    underTest.scrollProliferationCandidates(db.getSession(), List.of(fileOnA.uuid(), fileOnB.uuid()), rule.getUuid(),
      CANDIDATE_STATUSES, lines, includeNullLine, rows::add);
    return rows;
  }

  @Test
  public void resolvesTheFileOnEveryRequestedBranchThroughItsKey() {
    givenTheFileOnTwoBranches();
    ComponentDto branchC = db.components().insertPrivateProject().getMainBranchComponent();
    db.components().insertComponent(newFileDto(branchC).setKey("project:src/A.cls").setPath("src/A.cls"));
    ComponentDto branchD = db.components().insertPrivateProject().getMainBranchComponent();
    db.components().insertComponent(newFileDto(branchD).setKey("project:src/A.cls").setPath("src/A.cls")
      .setEnabled(false));

    List<String> uuids = db.getDbClient().componentDao().selectUuidsByKeyAndPathInBranches(db.getSession(),
      "project:src/A.cls", "src/A.cls", List.of(branchA.uuid(), branchB.uuid(), branchD.uuid()));

    // branchC is not asked for; the file on branchD is disabled.
    assertThat(uuids).containsExactlyInAnyOrder(fileOnA.uuid(), fileOnB.uuid());
  }

  @Test
  public void theKeyAloneIsNotEnoughThePathMustMatchToo() {
    givenTheFileOnTwoBranches();

    assertThat(db.getDbClient().componentDao().selectUuidsByKeyAndPathInBranches(db.getSession(),
      "project:src/A.cls", "src/Other.cls", List.of(branchA.uuid(), branchB.uuid()))).isEmpty();
  }

  @Test
  public void returnsOnlyTheRequestedLinesInTheCandidateStatusesOfTheRule() {
    givenTheFileOnTwoBranches();
    IssueDto onA10 = insertIssue(branchA, fileOnA, rule, 10, STATUS_OPEN);
    IssueDto onB10 = insertIssue(branchB, fileOnB, rule, 10, STATUS_REOPENED);
    IssueDto onA20 = insertIssue(branchA, fileOnA, rule, 20, STATUS_OPEN);
    insertIssue(branchA, fileOnA, rule, 30, STATUS_OPEN);
    insertIssue(branchA, fileOnA, rule, null, STATUS_OPEN);
    insertIssue(branchB, fileOnB, rule, 10, STATUS_CONFIRMED);
    insertIssue(branchB, fileOnB, rule, 10, STATUS_RESOLVED);
    insertIssue(branchA, fileOnA, db.rules().insertIssueRule(), 10, STATUS_OPEN);

    assertThat(scroll(Set.of(10, 20), false))
      .extracting(ProliferationCandidateDto::getIssueKey, ProliferationCandidateDto::getBranchUuid,
        ProliferationCandidateDto::getLine, ProliferationCandidateDto::getMessage)
      .containsExactlyInAnyOrder(
        tuple(onA10.getKey(), branchA.uuid(), 10, "message 10"),
        tuple(onB10.getKey(), branchB.uuid(), 10, "message 10"),
        tuple(onA20.getKey(), branchA.uuid(), 20, "message 20"));
  }

  @Test
  public void includesTheIssuesWithoutALineOnlyWhenAsked() {
    givenTheFileOnTwoBranches();
    IssueDto onLine = insertIssue(branchA, fileOnA, rule, 10, STATUS_OPEN);
    IssueDto noLine = insertIssue(branchA, fileOnA, rule, null, STATUS_OPEN);

    assertThat(scroll(Set.of(10), true)).extracting(ProliferationCandidateDto::getIssueKey)
      .containsExactlyInAnyOrder(onLine.getKey(), noLine.getKey());
    assertThat(scroll(Set.of(), true)).extracting(ProliferationCandidateDto::getIssueKey)
      .containsExactly(noLine.getKey());
    assertThat(scroll(Set.of(), false)).isEmpty();
  }

  @Test
  public void partitionsTheLinesAndAsksForTheNullLineOnce() {
    givenTheFileOnTwoBranches();
    IssueDto first = insertIssue(branchA, fileOnA, rule, 1, STATUS_OPEN);
    IssueDto last = insertIssue(branchA, fileOnA, rule, 2500, STATUS_OPEN);
    IssueDto noLine = insertIssue(branchA, fileOnA, rule, null, STATUS_OPEN);
    Set<Integer> manyLines = Set.copyOf(IntStream.rangeClosed(1, 2500).boxed().toList());

    assertThat(scroll(manyLines, true)).extracting(ProliferationCandidateDto::getIssueKey)
      .containsExactlyInAnyOrder(first.getKey(), last.getKey(), noLine.getKey());
  }
}
