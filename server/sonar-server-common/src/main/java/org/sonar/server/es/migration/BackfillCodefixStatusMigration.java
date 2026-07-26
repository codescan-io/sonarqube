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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.server.es.EsClient;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;

/**
 * Backfills {@code issues.codefixStatus} for issues whose rule has {@code ai_code_fix_enabled=true} by reindexing
 * those issues from the DB.
 *
 * <p>The obvious implementation would be an ES-side {@code update_by_query}, but the {@code issues} index is created
 * with {@code _source} disabled (see {@link org.sonar.server.issue.index.IssueIndexDefinition}), and
 * {@code update_by_query} rebuilds every matched doc from its {@code _source} — so it fails on every document with
 * {@code "didn't store _source"}. The DB is the source of truth for the index, so we reindex the affected issues
 * straight from it instead. This is a one-off BLANKET backfill: the by-rule scroll writes
 * {@code codefixStatus = COALESCE(codefix_status, 'AVAILABLE')}, marking every in-scope issue AVAILABLE and defaulting
 * only the NULLs (any already-persisted value is preserved). It deliberately SKIPS the {@code variableType} narrowing
 * that normal analysis indexing applies to variable-naming rules: old issues predate {@code issue_ai_metadata}, so
 * that narrowing would leave those rules' issues NULL, whereas the backfill intent is to mark them all. As a result the
 * backfilled value for an old INSTANCE-variable issue can differ from what its next analysis would write (which would
 * set it back to NULL) — accepted, as these are historical issues.
 *
 * <p>Scope is limited to issues of non-REMOVED {@code ai_code_fix_enabled} rules; issues of other rules already hold
 * the correct (absent/NULL) value in the index and are left untouched. The reindex streams the whole in-scope set in a
 * single server-side scroll under one {@link org.sonar.server.es.BulkIndexer.Size#LARGE} bulk
 * (see {@link IssueIndexer#reindexByRuleUuids}) — the fast bulk-load path. It is idempotent: each doc is simply
 * rewritten with its current canonical value, so a re-run (e.g. after a partial failure) is safe.
 *
 * <p><b>Run this only inside a maintenance/downtime window, with issue search and analyses of the
 * {@code ai_code_fix_enabled} projects stopped.</b> The LARGE bulk disables the index's replicas and periodic refresh
 * for the whole run (search is degraded until it completes) and the single scroll holds one DB read cursor open
 * throughout. Because no analyses run concurrently, there are no competing writers, so the doc-vs-analysis race the
 * resilient indexing paths guard against cannot happen and the plain last-write-wins index requests are safe. If the
 * run is interrupted (e.g. the server is restarted mid-migration) the LARGE settings can be left stranded on the index
 * — see {@link IssueIndexer#reindexByRuleUuids}; because the migration is idempotent, simply re-running it to
 * completion restores the settings and finishes the backfill.
 */
@ServerSide
public class BackfillCodefixStatusMigration implements EsDataMigration {

  static final long VERSION = 2026_07_18_01L;

  private final DbClient dbClient;
  private final IssueIndexer issueIndexer;
  private final EsClient esClient;

  public BackfillCodefixStatusMigration(DbClient dbClient, IssueIndexer issueIndexer, EsClient esClient) {
    this.dbClient = dbClient;
    this.issueIndexer = issueIndexer;
    this.esClient = esClient;
  }

  @Override
  public long version() {
    return VERSION;
  }

  @Override
  public String description() {
    return "Backfill issues.codefixStatus for issues of ai_code_fix_enabled rules by reindexing them from the DB";
  }

  @Override
  public long estimate(DbSession dbSession) {
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return 0;
    }
    // Fast dry-run count: ask Elasticsearch how many issue docs belong to these rules. It can differ from DB count
    SearchRequest request = EsClient.prepareSearch(IssueIndexDefinition.TYPE_ISSUE.getMainType())
      .source(new SearchSourceBuilder().query(estimateQuery(ruleUuids)).size(0).trackTotalHits(true));
    return esClient.search(request).getHits().getTotalHits().value;
  }

  @Override
  public EsDataMigrationExecution execute(DbSession dbSession) {
    // Resolve the (typically handful of) eligible rule uuids once, then stream-reindex every issue of those rules in a
    // single scroll under a LARGE bulk (IssueIndexer.reindexByRuleUuids). Meant to run inside a downtime window.
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return EsDataMigrationExecution.completed("nothing to do: no ai_code_fix_enabled rules");
    }
    long reindexed = issueIndexer.reindexByRuleUuids(ruleUuids);
    if (reindexed == 0) {
      return EsDataMigrationExecution.completed("nothing to do: no issues on ai_code_fix_enabled rules");
    }
      return EsDataMigrationExecution.completed("reindexed " + reindexed + " issue(s) from DB");
  }

  /**
   * Matches issue docs of the given (enabled, non-removed) rules. The terms clause on ruleUuid also excludes
   * authorization parent docs, which carry no ruleUuid. No codefixStatus narrowing: execute() reindexes every
   * in-scope issue, so the estimate counts the same coarse set.
   */
  private static BoolQueryBuilder estimateQuery(List<String> ruleUuids) {
    return QueryBuilders.boolQuery()
      .filter(QueryBuilders.termsQuery(IssueIndexDefinition.FIELD_ISSUE_RULE_UUID, ruleUuids));
  }
}
