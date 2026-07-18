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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.server.es.EsClient;
import org.sonar.server.issue.index.IssueIndexDefinition;

/**
 * Backfills {@code issues.codefixStatus = AVAILABLE} for issues whose rule has {@code ai_code_fix_enabled=true}
 * (excluding the 3 variable-naming rules) and that do not already carry the field. Runs as an ES-side
 * {@code update_by_query} so it does not touch the DB scroll/reindex path. Idempotent: the {@code must_not exists}
 * clause means a re-run only touches docs still missing the field.
 *
 * <p>The backfill snapshots {@code ai_code_fix_enabled} at run time; rules toggled later do not retroactively
 * update old docs (same limitation as the existing per-issue indexing).
 */
@ServerSide
public class BackfillCodefixStatusMigration implements EsDataMigration {

  static final long VERSION = 2026_07_18_01L;

  private final DbClient dbClient;
  private final EsClient esClient;

  public BackfillCodefixStatusMigration(DbClient dbClient, EsClient esClient) {
    this.dbClient = dbClient;
    this.esClient = esClient;
  }

  @Override
  public long version() {
    return VERSION;
  }

  @Override
  public String description() {
    return "Backfill issues.codefixStatus=AVAILABLE for issues of ai_code_fix_enabled rules that miss the field";
  }

  @Override
  public long estimate(DbSession dbSession) {
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return 0;
    }
    SearchRequest request = EsClient.prepareSearch(IssueIndexDefinition.TYPE_ISSUE.getMainType())
      .source(new SearchSourceBuilder().query(buildQuery(ruleUuids)).size(0).trackTotalHits(true));
    return esClient.search(request).getHits().getTotalHits().value;
  }

  @Override
  public Optional<String> execute(DbSession dbSession) {
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return Optional.empty();
    }
    UpdateByQueryRequest request = new UpdateByQueryRequest(IssueIndexDefinition.DESCRIPTOR.getName());
    request.setQuery(buildQuery(ruleUuids));
    request.setScript(new Script(ScriptType.INLINE, "painless",
      "ctx._source." + IssueIndexDefinition.FIELD_ISSUE_CODEFIX_STATUS + " = 'AVAILABLE'", Map.of()));
    // concurrent issue updates win and carry fresh values, so proceed past version conflicts
    request.setConflicts("proceed");
    request.setSlices(AbstractBulkByScrollRequest.AUTO_SLICES);
    return Optional.of(esClient.submitUpdateByQueryTask(request).getTask());
  }

  /**
   * Idempotent selection: only docs of the given rules that are still missing the field. The terms clause on
   * ruleUuid also excludes authorization parent docs (they have no ruleUuid). Mirrors the COALESCE/CASE in
   * IssueMapper#scrollIssuesForIndexation for pre-existing issues (whose issue_ai_metadata is always null).
   */
  private static BoolQueryBuilder buildQuery(List<String> ruleUuids) {
    return QueryBuilders.boolQuery()
      .filter(QueryBuilders.termsQuery(IssueIndexDefinition.FIELD_ISSUE_RULE_UUID, ruleUuids))
      .mustNot(QueryBuilders.existsQuery(IssueIndexDefinition.FIELD_ISSUE_CODEFIX_STATUS));
  }
}
