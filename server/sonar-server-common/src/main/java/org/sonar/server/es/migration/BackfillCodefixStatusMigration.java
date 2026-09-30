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
import org.elasticsearch.client.indices.GetIndexRequest;
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
 * Fills in {@code issues.codefixStatus} in Elasticsearch for issues whose rule has {@code ai_code_fix_enabled=true}, by
 * rewriting those issue documents from the database. A one-off, run by hand.
 *
 * <p>Elasticsearch's {@code update_by_query} rebuilds each matched document from its stored {@code _source}, and the
 * {@code issues} index does not store one (see {@link org.sonar.server.issue.index.IssueIndexDefinition}) — it fails on
 * every document with {@code "didn't store _source"}. So we read the affected issues from the database, which is the
 * source of truth for this index, and write their documents again.
 *
 * <p>Only issues of {@code ai_code_fix_enabled} rules that have not been removed are touched; every other issue already
 * holds the right value (none). For those in scope,
 * {@code IssueMapper#scrollIssuesForIndexationForMigration} uses {@code COALESCE(codefix_status, 'AVAILABLE')}:
 * everything ends up AVAILABLE, except issues that already have a real value stored, which is kept. That is
 * deliberately blunter than what a normal analysis writes. Analysis also checks {@code issue_ai_metadata} and skips the
 * variable-naming rules for instance variables, but issues created before that metadata existed have none, so the same
 * check would leave them NULL and defeat the point. The trade-off: an old instance-variable issue gets AVAILABLE here,
 * and its next analysis may set it back to NULL. Fine — these are historical issues.
 *
 * <p>It works branch by branch, several branches at a time, all writing through one shared
 * {@link org.sonar.server.es.BulkIndexer.Size#MIGRATION} indexer — see {@link IssueIndexer#reindexByRuleUuids} for why
 * that shape is much faster than one query over the whole table.
 *
 * <p><b>Run it when the system is quiet</b>, with analyses of the {@code ai_code_fix_enabled} projects stopped.
 * Searches are not degraded, but the run holds a database connection and an open query per worker for its whole
 * duration, and its writes are plain last-write-wins ones outside the usual retry path — so an analysis touching the
 * same issues could clash with it. With those analyses stopped, nobody else is writing and that cannot happen. If any
 * branch fails the whole run fails rather than claiming success on a half-done job, and running it again is the entire
 * recovery procedure: each document is simply written again with the value it should have, and a run that is cut short
 * leaves nothing behind to clean up.
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
    // If the index is gone, normal startup rebuilds it and writes codefixStatus anyway, so there is nothing to do.
    // Return 0 rather than letting the search below break the dry-run call with index_not_found_exception.
    if (!issuesIndexExists()) {
      return 0;
    }
    List<String> ruleUuids = dbClient.ruleDao().selectAiCodeFixBackfillRuleUuids(dbSession);
    if (ruleUuids.isEmpty()) {
      return 0;
    }
    // Counted in Elasticsearch rather than the database, so it may be slightly off.
    SearchRequest request = EsClient.prepareSearch(IssueIndexDefinition.TYPE_ISSUE.getMainType())
      .source(new SearchSourceBuilder().query(estimateQuery(ruleUuids)).size(0).trackTotalHits(true));
    return esClient.search(request).getHits().getTotalHits().value;
  }

  @Override
  public EsDataMigrationExecution execute(DbSession dbSession) {
    // Same as in estimate(), plus a worse failure mode: writing into a missing index makes Elasticsearch create it with
    // a guessed mapping instead of the one in IssueIndexDefinition. Step aside cleanly.
    if (!issuesIndexExists()) {
      return EsDataMigrationExecution.completed("nothing to do: issues index absent; the full reindex will repopulate codefixStatus");
    }
    // Look up the eligible rules once — usually a handful — then rewrite their issues branch by branch.
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
   * Whether the {@code issues} index exists right now. A missing one gets rebuilt and repopulated (codefixStatus
   * included) by normal startup, and this migration must not get in the way of that, so both {@link #estimate} and
   * {@link #execute} bow out when this is false.
   */
  private boolean issuesIndexExists() {
    return esClient.indexExists(new GetIndexRequest(IssueIndexDefinition.TYPE_ISSUE.getMainType().getIndex().getName()));
  }

  /**
   * Matches the issue documents of the given rules. Filtering on ruleUuid also skips the permission documents that share
   * this index, since those have no ruleUuid. Nothing filters on codefixStatus: execute() rewrites every in-scope issue,
   * so the estimate has to count the same set.
   */
  private static BoolQueryBuilder estimateQuery(List<String> ruleUuids) {
    return QueryBuilders.boolQuery()
      .filter(QueryBuilders.termsQuery(IssueIndexDefinition.FIELD_ISSUE_RULE_UUID, ruleUuids));
  }
}
