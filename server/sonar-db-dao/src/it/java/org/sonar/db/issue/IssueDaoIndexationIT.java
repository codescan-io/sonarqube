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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.cursor.Cursor;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.component.ComponentTesting.newFileDto;

/**
 * Covers the two issue-indexation scrolls and the branch lookup that drives the codefixStatus backfill's per-branch
 * fan-out (BackfillCodefixStatusMigration). The two scrolls share their column list and joins via SQL fragments but
 * differ in the {@code codefixStatus} expression, which is the point of these tests: the migration scroll marks every
 * in-scope issue AVAILABLE, while the analysis scroll keeps the variableType narrowing.
 */
public class IssueDaoIndexationIT {

  @Rule
  public DbTester db = DbTester.create();

  private final IssueDao underTest = db.getDbClient().issueDao();

  @Test
  public void selectBranchUuidsForRuleUuids_returns_distinct_branches_having_issues_on_those_rules() {
    ComponentDto projectA = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto fileA = db.components().insertComponent(newFileDto(projectA));
    ComponentDto projectB = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto fileB = db.components().insertComponent(newFileDto(projectB));
    ComponentDto projectC = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto fileC = db.components().insertComponent(newFileDto(projectC));

    RuleDto inScope = insertAiFixEnabledRule();
    RuleDto outOfScope = db.rules().insert(r -> r.setAiCodeFixEnabled(false));

    // two issues on the same branch must not yield two entries
    db.issues().insertIssue(inScope, projectA, fileA);
    db.issues().insertIssue(inScope, projectA, fileA);
    db.issues().insertIssue(inScope, projectB, fileB);
    // branch C only has out-of-scope issues, so it has no work to do
    db.issues().insertIssue(outOfScope, projectC, fileC);

    List<String> branchUuids = underTest.selectBranchUuidsForRuleUuids(db.getSession(), singletonList(inScope.getUuid()));

    assertThat(branchUuids).containsExactlyInAnyOrder(projectA.branchUuid(), projectB.branchUuid());
  }

  @Test
  public void scrollIssuesForIndexationByBranchAndRuleUuids_is_scoped_to_one_branch_and_the_given_rules() throws Exception {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    ComponentDto otherProject = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto otherFile = db.components().insertComponent(newFileDto(otherProject));

    RuleDto inScope = insertAiFixEnabledRule();
    RuleDto outOfScope = db.rules().insert(r -> r.setAiCodeFixEnabled(false));

    IssueDto wanted = db.issues().insertIssue(inScope, project, file);
    IssueDto otherRule = db.issues().insertIssue(outOfScope, project, file);
    IssueDto otherBranch = db.issues().insertIssue(inScope, otherProject, otherFile);

    Map<String, String> statuses = scrollForMigration(project.branchUuid(), inScope);

    assertThat(statuses).containsOnlyKeys(wanted.getKey());
    assertThat(statuses).doesNotContainKeys(otherRule.getKey(), otherBranch.getKey());
  }

  @Test
  public void scrollIssuesForIndexationByBranchAndRuleUuids_marks_every_in_scope_issue_available() throws Exception {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    RuleDto plainRule = insertAiFixEnabledRule();
    // A variable-naming rule with INSTANCE metadata: the analysis scroll narrows this one away, the backfill must not.
    RuleDto namingRule = insertAiFixEnabledRule(RuleKey.of("pmd", "ShortVariable"));

    IssueDto plain = db.issues().insertIssue(plainRule, project, file);
    IssueDto naming = db.issues().insertIssue(namingRule, project, file,
      t -> t.setIssueAiMetadata("{\"variableType\":\"INSTANCE\"}"));
    IssueDto preset = insertIssueWithCodefixStatus(plainRule, project, file, "FIX_GENERATED");

    Map<String, String> statuses = scrollForMigration(project.branchUuid(), plainRule, namingRule);

    assertThat(statuses.get(plain.getKey())).isEqualTo("AVAILABLE");
    // blanket backfill: no variableType narrowing
    assertThat(statuses.get(naming.getKey())).isEqualTo("AVAILABLE");
    // COALESCE keeps a value already persisted in the DB
    assertThat(statuses.get(preset.getKey())).isEqualTo("FIX_GENERATED");
  }

  /**
   * Regression guard for splitting the shared statement in two: the analysis path must still narrow variable-naming
   * rules by variableType, and must NOT pick up the backfill's blanket AVAILABLE.
   */
  @Test
  public void scrollIssuesForIndexation_keeps_the_variable_type_narrowing_for_analysis() throws Exception {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    RuleDto plainRule = insertAiFixEnabledRule();
    RuleDto namingRule = insertAiFixEnabledRule(RuleKey.of("pmd", "ShortVariable"));
    RuleDto disabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(false));

    IssueDto plain = db.issues().insertIssue(plainRule, project, file);
    IssueDto namingInstance = db.issues().insertIssue(namingRule, project, file,
      t -> t.setIssueAiMetadata("{\"variableType\":\"INSTANCE\"}"));
    IssueDto namingLocal = db.issues().insertIssue(namingRule, project, file,
      t -> t.setIssueAiMetadata("{\"variableType\":\"LOCAL\"}"));
    IssueDto namingNoMetadata = db.issues().insertIssue(namingRule, project, file);
    IssueDto onDisabledRule = db.issues().insertIssue(disabledRule, project, file);

    Map<String, String> statuses = new HashMap<>();
    try (Cursor<IndexedIssueDto> cursor = underTest.scrollIssuesForIndexation(db.getSession(), project.branchUuid(), null)) {
      cursor.forEach(dto -> statuses.put(dto.getIssueKey(), dto.getCodefixStatus()));
    }

    assertThat(statuses.get(plain.getKey())).isEqualTo("AVAILABLE");
    assertThat(statuses.get(namingLocal.getKey())).isEqualTo("AVAILABLE");
    // narrowed away by the analysis expression
    assertThat(statuses.get(namingInstance.getKey())).isNull();
    assertThat(statuses.get(namingNoMetadata.getKey())).isNull();
    assertThat(statuses.get(onDisabledRule.getKey())).isNull();
  }

  /**
   * {@code rules.ai_code_fix_enabled} is NOT part of the rule INSERT statement — it is written only by the dedicated
   * {@code updateAiCodeFixEnabled} statement. Setting it on the DTO handed to {@code db.rules().insert()} therefore
   * persists nothing, so the flag has to be pushed with a follow-up update for the SQL under test to see it.
   */
  private RuleDto insertAiFixEnabledRule(RuleKey... ruleKey) {
    RuleDto rule = ruleKey.length == 0
      ? db.rules().insert(r -> r.setAiCodeFixEnabled(true))
      : db.rules().insert(r -> r.setAiCodeFixEnabled(true).setRuleKey(ruleKey[0]));
    db.getDbClient().ruleDao().updateAiCodeFixEnabled(db.getSession(), rule);
    db.commit();
    return rule;
  }

  /**
   * {@code issues.codefix_status} is NOT part of the issue INSERT statement — only of {@code update}. In production the
   * value therefore arrives via SetCodefixStatusAction -> IssueUpdater; reproduce that here with an explicit update, or
   * the column stays NULL however the DTO was populated.
   */
  private IssueDto insertIssueWithCodefixStatus(RuleDto rule, ComponentDto branch, ComponentDto file, String codefixStatus) {
    IssueDto issue = db.issues().insertIssue(rule, branch, file);
    issue.setCodefixStatus(codefixStatus);
    db.getDbClient().issueDao().update(db.getSession(), issue);
    db.commit();
    return issue;
  }

  private Map<String, String> scrollForMigration(String branchUuid, RuleDto... rules) throws Exception {
    List<String> ruleUuids = java.util.Arrays.stream(rules).map(RuleDto::getUuid).toList();
    Map<String, String> statuses = new HashMap<>();
    try (Cursor<IndexedIssueDto> cursor = underTest.scrollIssuesForIndexationByBranchAndRuleUuids(db.getSession(), branchUuid, ruleUuids)) {
      cursor.forEach(dto -> statuses.put(dto.getIssueKey(), dto.getCodefixStatus()));
    }
    return statuses;
  }
}
