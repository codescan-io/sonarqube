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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.issue.impact.SoftwareQuality;
import org.sonar.api.rules.RuleType;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;

/**
 * The multi-component issue-group statements the live measure refresh uses must return, for each component, exactly
 * the rows the per-component statements return: the measures are computed from them.
 */
public class IssueDaoGroupsByComponentsIT {

  private static final long LEAK_START = 1_500_000_000_000L;

  @Rule
  public DbTester db = DbTester.create();

  private final IssueDao underTest = db.getDbClient().issueDao();

  private ComponentDto project;
  private ComponentDto fileA;
  private ComponentDto fileB;
  private ComponentDto fileWithoutIssues;

  private void givenIssues() {
    project = db.components().insertPrivateProject().getMainBranchComponent();
    fileA = db.components().insertComponent(newFileDto(project));
    fileB = db.components().insertComponent(newFileDto(project));
    fileWithoutIssues = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    for (ComponentDto file : List.of(fileA, fileB)) {
      insert(rule, file, STATUS_OPEN, null, RuleType.CODE_SMELL, "MAJOR", LEAK_START + 1, SoftwareQuality.MAINTAINABILITY,
        Severity.HIGH);
      insert(rule, file, STATUS_OPEN, null, RuleType.BUG, "CRITICAL", LEAK_START - 1, SoftwareQuality.RELIABILITY,
        Severity.MEDIUM);
      insert(rule, file, STATUS_RESOLVED, RESOLUTION_FALSE_POSITIVE, RuleType.CODE_SMELL, "MAJOR", LEAK_START + 1,
        SoftwareQuality.MAINTAINABILITY, Severity.HIGH);
      insert(rule, file, STATUS_RESOLVED, RESOLUTION_WONT_FIX, RuleType.VULNERABILITY, "BLOCKER", LEAK_START + 1,
        SoftwareQuality.SECURITY, Severity.HIGH);
      insert(rule, file, STATUS_CLOSED, "FIXED", RuleType.CODE_SMELL, "MINOR", LEAK_START + 1,
        SoftwareQuality.MAINTAINABILITY, Severity.LOW);
    }
    // fileB gets one more so the two files' groups differ
    insert(rule, fileB, STATUS_OPEN, null, RuleType.CODE_SMELL, "MAJOR", LEAK_START + 1, SoftwareQuality.MAINTAINABILITY,
      Severity.HIGH);
  }

  private void insert(RuleDto rule, ComponentDto file, String status, String resolution, RuleType type,
    String severity, long creationDate, SoftwareQuality quality, Severity impactSeverity) {
    db.issues().insertIssue(rule, project, file, i -> i
      .setStatus(status)
      .setResolution(resolution)
      .setType(type)
      .setSeverity(severity)
      .setEffort(10L)
      .setIssueCreationTime(creationDate)
      .replaceAllImpacts(List.of(new ImpactDto(quality, impactSeverity))));
  }

  private List<String> uuids() {
    return List.of(fileA.uuid(), fileB.uuid(), fileWithoutIssues.uuid(), project.uuid());
  }

  @Test
  public void issueGroupsMatchThePerComponentStatement() {
    givenIssues();
    for (long leak : new long[] {LEAK_START, -1L}) {
      assertSameAsPerComponent(underTest.selectIssueGroupsByComponentUuids(db.getSession(), uuids(), leak),
        (c, l) -> underTest.selectIssueGroupsByComponent(db.getSession(), c, l), leak);
    }
  }

  @Test
  public void impactGroupsMatchThePerComponentStatement() {
    givenIssues();
    for (long leak : new long[] {LEAK_START, -1L}) {
      assertSameAsPerComponent(underTest.selectIssueImpactGroupsByComponentUuids(db.getSession(), uuids(), leak),
        (c, l) -> underTest.selectIssueImpactGroupsByComponent(db.getSession(), c, l), leak);
    }
  }

  @Test
  public void impactSeverityGroupsMatchThePerComponentStatement() {
    givenIssues();
    for (long leak : new long[] {LEAK_START, -1L}) {
      assertSameAsPerComponent(
        underTest.selectIssueImpactSeverityGroupsByComponentUuids(db.getSession(), uuids(), leak),
        (c, l) -> underTest.selectIssueImpactSeverityGroupsByComponent(db.getSession(), c, l), leak);
    }
  }

  @Test
  public void coversEveryComponentWhenTheListSpansSeveralPartitions() {
    givenIssues();
    RuleDto rule = db.rules().insertIssueRule();
    List<String> uuids = new java.util.ArrayList<>();
    for (int i = 0; i < 120; i++) {
      ComponentDto file = db.components().insertComponent(newFileDto(project));
      insert(rule, file, STATUS_OPEN, null, RuleType.BUG, "MAJOR", LEAK_START + 1, SoftwareQuality.RELIABILITY,
        Severity.HIGH);
      uuids.add(file.uuid());
    }

    assertThat(underTest.selectIssueGroupsByComponentUuids(db.getSession(), uuids, LEAK_START)).hasSize(120);
    assertThat(underTest.selectIssueImpactGroupsByComponentUuids(db.getSession(), uuids, LEAK_START)).hasSize(120);
    assertThat(underTest.selectIssueImpactSeverityGroupsByComponentUuids(db.getSession(), uuids, LEAK_START))
      .hasSize(120);
  }

  private <T> void assertSameAsPerComponent(Map<String, List<T>> batched,
    BiFunction<ComponentDto, Long, Collection<T>> perComponent, long leak) {

    assertThat(batched.keySet()).containsExactlyInAnyOrder(fileA.uuid(), fileB.uuid());
    for (ComponentDto component : List.of(fileA, fileB, fileWithoutIssues, project)) {
      Collection<T> expected = perComponent.apply(component, leak);
      List<T> actual = batched.getOrDefault(component.uuid(), List.of());
      assertThat(actual)
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("componentUuid")
        .containsExactlyInAnyOrderElementsOf(expected);
    }
    assertThat(batched.get(fileA.uuid())).isNotEmpty();
  }
}
