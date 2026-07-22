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

import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.cursor.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
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
 */
@ServerSide
public class BackfillCodefixStatusMigration implements EsDataMigration {

  static final long VERSION = 2026_07_18_01L;

  private static final Logger LOGGER = LoggerFactory.getLogger(BackfillCodefixStatusMigration.class);
  /** Issue keys reindexed per batch: at most this many keys are held in memory, and each batch is one reindex pass. */
  private static final int REINDEX_BATCH_SIZE = 10_000;

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
    List<String> batch = new ArrayList<>(REINDEX_BATCH_SIZE);
    try (Cursor<String> issueKeys = dbClient.issueDao().scrollIssueKeysForCodefixBackfill(dbSession)) {
      for (String issueKey : issueKeys) {
        batch.add(issueKey);
        if (batch.size() >= REINDEX_BATCH_SIZE) {
          reindexed += flush(batch);
          LOGGER.info("ES data migration {}: reindexed {} issue(s) so far", VERSION, reindexed);
        }
      }
      reindexed += flush(batch);
    }
    if (reindexed == 0) {
      return EsDataMigrationExecution.completed("nothing to do: no issues on ai_code_fix_enabled rules");
    }
    return EsDataMigrationExecution.completed("reindexed " + reindexed + " issue(s) from DB");
  }

  /** Reindexes the accumulated batch (from the DB, full-doc), clears it, and returns how many were reindexed. */
  private long flush(List<String> batch) {
    if (batch.isEmpty()) {
      return 0;
    }
    int size = batch.size();
    issueIndexer.indexByKeys(batch);
    batch.clear();
    return size;
  }
}
