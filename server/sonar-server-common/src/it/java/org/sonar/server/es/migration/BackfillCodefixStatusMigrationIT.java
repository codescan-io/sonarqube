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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.DbTester;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.index.IssueIndexDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.issue.IssueDocTesting.newDoc;
import static org.sonar.server.issue.index.IssueIndexDefinition.TYPE_ISSUE;

public class BackfillCodefixStatusMigrationIT {

  private static final String PROJECT_UUID = "project-uuid";

  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public DbTester db = DbTester.create();

  private final BackfillCodefixStatusMigration underTest = new BackfillCodefixStatusMigration(db.getDbClient(), es.client());

  @Test
  public void execute_sets_available_only_for_enabled_rule_issues_missing_the_field() throws Exception {
    RuleDto enabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    RuleDto disabledRule = db.rules().insert(r -> r.setAiCodeFixEnabled(false));
    // enabled but a variable-naming rule -> excluded by the DAO select (covers the SQL exclusion here too)
    RuleDto namingRule = db.rules().insert(r -> r.setAiCodeFixEnabled(true).setRuleKey(RuleKey.of("pmd", "ShortVariable")));

    es.putDocuments(TYPE_ISSUE,
      newDoc().setKey("issue1").setProjectUuid(PROJECT_UUID).setRuleUuid(enabledRule.getUuid()),
      newDoc().setKey("issue2").setProjectUuid(PROJECT_UUID).setRuleUuid(disabledRule.getUuid()),
      newDoc().setKey("issue3").setProjectUuid(PROJECT_UUID).setRuleUuid(enabledRule.getUuid()).setCodefixStatus("FIX_GENERATED"),
      newDoc().setKey("issue4").setProjectUuid(PROJECT_UUID).setRuleUuid(namingRule.getUuid()));

    assertThat(underTest.estimate(db.getSession())).isEqualTo(1);

    Optional<String> taskId = underTest.execute(db.getSession());
    assertThat(taskId).isPresent();
    waitForTaskCompletion(taskId.get());
    es.client().refresh(IssueIndexDefinition.DESCRIPTOR);

    Map<String, String> statuses = codefixStatusByKey();
    assertThat(statuses.get("issue1")).isEqualTo("AVAILABLE");
    assertThat(statuses.get("issue2")).isNull();
    assertThat(statuses.get("issue3")).isEqualTo("FIX_GENERATED");
    assertThat(statuses.get("issue4")).isNull();
  }

  @Test
  public void execute_returns_empty_when_no_enabled_rules() {
    assertThat(underTest.execute(db.getSession())).isEmpty();
  }

  private void waitForTaskCompletion(String taskId) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      JsonObject task = JsonParser.parseString(es.client().getTaskAsJson(taskId)).getAsJsonObject();
      if (task.get("completed").getAsBoolean()) {
        return;
      }
      Thread.sleep(50);
    }
    throw new IllegalStateException("update_by_query task " + taskId + " did not complete in time");
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
