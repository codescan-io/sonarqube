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
package org.sonar.server.es.migration;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.server.issue.IssueDocTesting.newDoc;
import static org.sonar.server.issue.index.IssueIndexDefinition.TYPE_ISSUE;

public class BackfillCodefixStatusMigrationIT {

  private static final String PROJECT_UUID = "project-uuid";

  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public DbTester db = DbTester.create();

  private final IssueIndexer issueIndexer = new IssueIndexer(es.client(), db.getDbClient(),
    new IssueIteratorFactory(db.getDbClient()), null);
  private final BackfillCodefixStatusMigration underTest = new BackfillCodefixStatusMigration(db.getDbClient(), issueIndexer, es.client());

  @Test
  public void execute_reindexes_only_ai_fix_rule_issues_marking_them_all_available() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));

    RuleDto enabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    RuleDto disabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(false));
    // enabled variable-naming rule with no AI metadata: normal analysis indexing would leave this NULL (variableType
    // narrowing), but the one-off BLANKET backfill marks every in-scope issue AVAILABLE, so this must become AVAILABLE too.
    RuleDto namingRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true).setRuleKey(RuleKey.of("pmd", "ShortVariable")));
    // REMOVED rule: enabled flag may linger but its issues are out of scope (status != 'REMOVED').
    RuleDto removedRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true).setStatus(RuleStatus.REMOVED));

    IssueDto onEnabled = db.issues().insert(enabledRule, project, file);
    IssueDto onDisabled = db.issues().insert(disabledRule, project, file);
    IssueDto onEnabledPreset = db.issues().insert(enabledRule, project, file, t -> t.setCodefixStatus("FIX_GENERATED"));
    IssueDto onNaming = db.issues().insert(namingRule, project, file);
    IssueDto onRemoved = db.issues().insert(removedRule, project, file);

    EsDataMigrationExecution execution = underTest.execute(db.getSession());
    assertThat(execution.asyncTaskId()).isEmpty();
    assertThat(execution.detail()).contains("reindexed 3");
    es.client().refresh(IssueIndexDefinition.DESCRIPTOR);

    Map<String, String> statuses = codefixStatusByKey();
    // blanket backfill: every in-scope issue is marked AVAILABLE, any already-persisted DB value is preserved
    assertThat(statuses.get(onEnabled.getKey())).isEqualTo("AVAILABLE");
    assertThat(statuses.get(onEnabledPreset.getKey())).isEqualTo("FIX_GENERATED"); // pre-set DB value preserved via COALESCE
    assertThat(statuses.get(onNaming.getKey())).isEqualTo("AVAILABLE"); // naming rule w/o metadata still marked (blanket backfill, no variableType narrowing)
    // out of scope -> never indexed:
    assertThat(statuses).doesNotContainKey(onDisabled.getKey());
    assertThat(statuses).doesNotContainKey(onRemoved.getKey());
  }

  @Test
  public void execute_reports_nothing_to_do_when_no_ai_fix_rule_issues() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto disabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(false));
    db.issues().insert(disabledRule, project, file);

    assertThat(underTest.estimate(db.getSession())).isZero();

    EsDataMigrationExecution execution = underTest.execute(db.getSession());
    assertThat(execution.asyncTaskId()).isEmpty();
    assertThat(execution.detail()).contains("nothing to do");
    es.client().refresh(IssueIndexDefinition.DESCRIPTOR);
    assertThat(codefixStatusByKey()).isEmpty();
  }

  @Test
  public void estimate_counts_index_docs_of_enabled_rules_from_elasticsearch() {
    RuleDto enabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    RuleDto disabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(false));
    RuleDto namingRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true).setRuleKey(RuleKey.of("pmd", "ShortVariable")));
    RuleDto removedRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true).setStatus(RuleStatus.REMOVED));

    // docs already live in the index (issues are indexed at analysis time; the backfill only refreshes a field)
    es.putDocuments(TYPE_ISSUE,
      newDoc().setKey("i1").setProjectUuid(PROJECT_UUID).setRuleUuid(enabledRule.getUuid()),
      newDoc().setKey("i2").setProjectUuid(PROJECT_UUID).setRuleUuid(enabledRule.getUuid()),
      newDoc().setKey("i3").setProjectUuid(PROJECT_UUID).setRuleUuid(namingRule.getUuid()),
      newDoc().setKey("i4").setProjectUuid(PROJECT_UUID).setRuleUuid(disabledRule.getUuid()),
      newDoc().setKey("i5").setProjectUuid(PROJECT_UUID).setRuleUuid(removedRule.getUuid()));

    // enabled(2) + naming(1); disabled and removed rules are out of scope
    assertThat(underTest.estimate(db.getSession())).isEqualTo(3);
  }

  private Map<String, String> codefixStatusByKey() {
    SearchRequest request = EsClient.prepareSearch(TYPE_ISSUE.getMainType())
      .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(100));
    Map<String, String> result = new HashMap<>();
    for (SearchHit hit : es.client().search(request).getHits().getHits()) {
      Object status = hit.getSourceAsMap().get(IssueIndexDefinition.FIELD_ISSUE_CODEFIX_STATUS);
      result.put(hit.getId(), status == null ? null : status.toString());
    }
    return result;
  }
}
