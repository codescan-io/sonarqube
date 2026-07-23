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
import java.util.function.Supplier;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
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
 * straight from it instead. Reindexing recomputes {@code codefixStatus} from the canonical
 * {@code IssueMapper#scrollIssuesForIndexation} expression (which already narrows variable-naming rules by
 * {@code variableType}), so the value written matches exactly what a normal analysis reindex would write — this
 * does NOT over-mark.
 *
 * <p>Scope is limited to issues of non-REMOVED {@code ai_code_fix_enabled} rules; issues of other rules already hold
 * the correct (absent/NULL) value in the index and are left untouched. The reindex streams the in-scope keys in bounded
 * keyset pages under a single bulk (see {@link IssueIndexer#reindexByKeyPages}) and does NOT flip
 * {@code need_issue_sync}, so issue search stays available throughout. It is idempotent: each doc is simply rewritten
 * with its current canonical value, so a re-run (e.g. after a partial failure) is safe.
 *
 * <p><b>Run this while analyses of {@code ai_code_fix_enabled} projects are quiesced</b> (e.g. a maintenance
 * window). It writes issue docs from the DB directly — outside the resilient {@code es_queue} path and with plain
 * last-write-wins index requests — so it takes no part in the analysis/indexing serialization and is NOT protected
 * against concurrent writers. If an analysis re-indexes one of these issues at the same time, the two writers race
 * and the migration can overwrite the fresher analysis doc with the snapshot it read a moment earlier; that doc
 * then stays stale until the issue is next re-indexed. The migration is admin-triggered on demand (never
 * automatically) precisely so an operator can pick a quiet moment, and because it is idempotent, simply re-running
 * it once analyses have settled repairs any doc lost to such a race.
 */
@ServerSide
public class BackfillCodefixStatusMigration implements EsDataMigration {

  static final long VERSION = 2026_07_18_01L;

  /** Issue keys fetched and reindexed per page: bounds the keyset page size and the in-memory key list; one reindex pass per page. */
  private static final int REINDEX_PAGE_SIZE = 10_000;

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
    // Resolve the (typically handful of) eligible rule uuids once, up front. The keyset page query then filters on
    // issues.rule_uuid alone — no per-page join to rules — so each page is an incremental walk of the issues key index.
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return EsDataMigrationExecution.completed("nothing to do: no ai_code_fix_enabled rules");
    }
    KeyPageCursor cursor = new KeyPageCursor(dbSession, ruleUuids);
    // A single bulk spans every page (one refresh at the end, not per page); the cursor keyset-paginates and commits
    // the read snapshot between pages so no long transaction is held.
    issueIndexer.reindexByKeyPages(cursor);
    if (cursor.total == 0) {
      return EsDataMigrationExecution.completed("nothing to do: no issues on ai_code_fix_enabled rules");
    }
    return EsDataMigrationExecution.completed("reindexed " + cursor.total + " issue(s) from DB");
  }

  /**
   * Keyset-paginates the in-scope issue keys for {@link IssueIndexer#reindexByKeyPages}. Each {@link #get()} fetches the
   * next page ordered by key and commits the DB read snapshot, so the indexer never holds a long-lived transaction while
   * it does its (slow, many-round-trip) ES writes. Returns an empty list once the keys are exhausted.
   */
  private final class KeyPageCursor implements Supplier<List<String>> {
    private final DbSession dbSession;
    private final List<String> ruleUuids;
    private String afterKey = null;
    private long total = 0;

    private KeyPageCursor(DbSession dbSession, List<String> ruleUuids) {
      this.dbSession = dbSession;
      this.ruleUuids = ruleUuids;
    }

    @Override
    public List<String> get() {
      List<String> keys = dbClient.issueDao()
        .selectIssueKeysForCodefixBackfill(dbSession, ruleUuids, afterKey, Pagination.forPage(1).andSize(REINDEX_PAGE_SIZE));
      if (keys.isEmpty()) {
        return keys;
      }
      afterKey = keys.get(keys.size() - 1);
      total += keys.size();
      // Safe to commit — the migration only reads from the DB. Per-page fetch/reindex timing is logged by
      // IssueIndexer.reindexByKeyPages (which owns both phases), so we don't double-log progress here.
      dbSession.commit();
      return keys;
    }
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
