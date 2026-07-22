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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
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
 * the correct (absent/NULL) value in the index and are left untouched. The reindex runs in bounded batches and does
 * NOT flip {@code need_issue_sync}, so issue search stays available throughout. It is idempotent: each doc is simply
 * rewritten with its current canonical value, so a re-run (e.g. after a partial failure) is safe.
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

  private static final Logger LOGGER = LoggerFactory.getLogger(BackfillCodefixStatusMigration.class);
  /** Issue keys fetched and reindexed per page: bounds the keyset page size and the in-memory key list; one reindex pass per page. */
  private static final int REINDEX_PAGE_SIZE = 10_000;

  private final DbClient dbClient;
  private final IssueIndexer issueIndexer;

  public BackfillCodefixStatusMigration(DbClient dbClient, IssueIndexer issueIndexer) {
    this.dbClient = dbClient;
    this.issueIndexer = issueIndexer;
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
    return dbClient.issueDao().countIssuesForCodefixBackfill(dbSession);
  }

  @Override
  public EsDataMigrationExecution execute(DbSession dbSession) {
    long reindexed = 0;
    String afterKey = null;
    while (true) {
      List<String> keys = dbClient.issueDao()
        .selectIssueKeysForCodefixBackfill(dbSession, afterKey, Pagination.forPage(1).andSize(REINDEX_PAGE_SIZE));
      if (keys.isEmpty()) {
        break;
      }
      issueIndexer.indexByKeys(keys);
      reindexed += keys.size();
      afterKey = keys.get(keys.size() - 1);
      // Release the DB read snapshot between pages: reindexing a page is slow (many ES round-trips), so holding a
      // single transaction open across the whole backfill would pin the DB's cleanup horizon (e.g. block VACUUM)
      // for its entire duration. Safe to commit — the migration only reads from the DB.
      dbSession.commit();
      LOGGER.info("ES data migration {}: reindexed {} issue(s) so far", VERSION, reindexed);
      if (keys.size() < REINDEX_PAGE_SIZE) {
        break;
      }
    }
    if (reindexed == 0) {
      return EsDataMigrationExecution.completed("nothing to do: no issues on ai_code_fix_enabled rules");
    }
    return EsDataMigrationExecution.completed("reindexed " + reindexed + " issue(s) from DB");
  }
}
