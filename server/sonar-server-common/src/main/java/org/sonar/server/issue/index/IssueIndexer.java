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
package org.sonar.server.issue.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.db.component.ComponentQualifiers;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.es.EsQueueDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.es.AnalysisIndexer;
import org.sonar.server.es.BulkIndexer;
import org.sonar.server.es.BulkIndexer.Size;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EventIndexer;
import org.sonar.server.es.IndexType;
import org.sonar.server.es.Indexers;
import org.sonar.server.es.IndexingListener;
import org.sonar.server.es.IndexingResult;
import org.sonar.server.es.OneToManyResilientIndexingListener;
import org.sonar.server.es.OneToOneResilientIndexingListener;
import org.sonar.server.permission.index.AuthorizationDoc;
import org.sonar.server.permission.index.AuthorizationScope;
import org.sonar.server.permission.index.NeedAuthorizationIndexer;

import static java.util.Collections.emptyList;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.sonar.server.issue.index.IssueIndexDefinition.FIELD_ISSUE_BRANCH_UUID;
import static org.sonar.server.issue.index.IssueIndexDefinition.FIELD_ISSUE_PROJECT_UUID;
import static org.sonar.server.issue.index.IssueIndexDefinition.TYPE_ISSUE;

/**
 * Indexes issues. All issues belong directly to a project branch, so they only change when a project branch changes.
 */
public class IssueIndexer implements EventIndexer, AnalysisIndexer, NeedAuthorizationIndexer {

  /**
   * Indicates that es_queue.doc_id references an issue. Only this issue must be indexed.
   */
  private static final String ID_TYPE_ISSUE_KEY = "issueKey";
  /**
   * Indicates that es_queue.doc_id references a branch. All the issues of the branch must be indexed.
   * Note that the constant is misleading, but we can't update it since there might some items in the DB during the upgrade.
   */
  private static final String ID_TYPE_BRANCH_UUID = "projectUuid";
  /**
   * Indicates that es_queue.doc_id references a project and that all issues in it should be delete.
   */
  private static final String ID_TYPE_DELETE_PROJECT_UUID = "deleteProjectUuid";

  private static final Logger LOGGER = LoggerFactory.getLogger(IssueIndexer.class);
  private static final AuthorizationScope AUTHORIZATION_SCOPE = new AuthorizationScope(TYPE_ISSUE, entity -> ComponentQualifiers.PROJECT.equals(entity.getQualifier()));
  private static final Set<IndexType> INDEX_TYPES = Set.of(TYPE_ISSUE);
  /** Emit a progress log every this-many docs queued during a rule-scoped reindex (see {@link #reindexByRuleUuids}). */
  private static final long REINDEX_LOG_INTERVAL = 100_000L;
  /**
   * Upper bound on branches drained concurrently by {@link #reindexByRuleUuids}. Each thread holds one DB connection and
   * one open scroll cursor for as long as its branch takes, so this is bounded well under the JDBC pool
   * ({@code sonar.jdbc.maxActive}, 60 by default) rather than scaled to the branch count.
   */
  private static final int REINDEX_MAX_THREADS = 8;

  private final EsClient esClient;
  private final DbClient dbClient;
  private final IssueIteratorFactory issueIteratorFactory;
  private final AsyncIssueIndexing asyncIssueIndexing;

  public IssueIndexer(EsClient esClient, DbClient dbClient, IssueIteratorFactory issueIteratorFactory, AsyncIssueIndexing asyncIssueIndexing) {
    this.esClient = esClient;
    this.dbClient = dbClient;
    this.issueIteratorFactory = issueIteratorFactory;
    this.asyncIssueIndexing = asyncIssueIndexing;
  }

  @Override
  public AuthorizationScope getAuthorizationScope() {
    return AUTHORIZATION_SCOPE;
  }

  @Override
  public Set<IndexType> getIndexTypes() {
    return INDEX_TYPES;
  }

  @Override
  public Type getType() {
    return Type.ASYNCHRONOUS;
  }

  @Override
  public void triggerAsyncIndexOnStartup(Set<IndexType> uninitializedIndexTypes) {
    asyncIssueIndexing.triggerOnIndexCreation();
  }

  public void indexAllIssues() {
    try (IssueIterator issues = issueIteratorFactory.createForAll()) {
      doIndex(issues);
    }
  }

  /**
   * Reindexes from the DB every issue whose rule is one of {@code ruleUuids}: a full-document index that recomputes every
   * field (including {@code codefixStatus}) from {@code IssueMapper#scrollIssuesForIndexationForMigration}. This is the
   * {@code _source}-free way to backfill a field, since the {@code issues} index stores no {@code _source} (production
   * builds {@code IssueIndexDefinition} with source disabled) and so cannot be updated with {@code update_by_query}.
   *
   * <p><b>Partitioned by branch, drained in parallel.</b> The in-scope branches are resolved first
   * ({@code IssueDao#selectBranchUuidsForRuleUuids}) and each is streamed by its own scroll cursor on its own DB session,
   * {@link #REINDEX_MAX_THREADS} at a time. This mirrors how SonarQube's own startup reindex gets its throughput — it
   * submits one CE task per branch — and it is what makes this fast rather than merely correct:
   * <ul>
   *   <li>the whole-table {@code rule_uuid IN (...)} scroll had to sort its entire joined result set before returning
   *       row one (Postgres cannot stream a sorted result, and {@code ORDER BY kee} is required: MyBatis
   *       {@code resultOrdered="true"} plus the impacts/dependency {@code <collection>}s need one issue's rows adjacent).
   *       Per branch, that sort is small enough to stay in memory;</li>
   *   <li>issue docs route on the project, so a branch-scoped batch lands on a single shard instead of fanning every
   *       bulk across all of them.</li>
   * </ul>
   *
   * <p>All producers share ONE {@link BulkIndexer} under {@link Size#MIGRATION}, so the run does a single flush+refresh
   * at the end rather than one per branch — thousands of refreshes would leave the index a mess of tiny segments.
   * MIGRATION rather than {@link Size#LARGE}: LARGE is the from-scratch bulk-load path, and on {@link BulkIndexer#stop()}
   * it force-merges and re-replicates the WHOLE index, a cost proportional to the entire index rather than to the subset
   * rewritten. (Its other two effects are no-ops here anyway: this index is permanently {@code refresh_interval=-1}, and
   * replicas are already 0 outside cluster mode.) MIGRATION keeps settings untouched like REGULAR but flushes bigger
   * bulks and keeps several in flight, which REGULAR's {@code concurrentRequests == 0} could not do without stalling
   * every producer behind whichever one trips the flush threshold.
   *
   * <p>Still meant for a quiet period: these are plain last-write-wins index requests. Unlike {@link #indexProject} this
   * does NOT set {@code need_issue_sync}; and unlike the resilient paths it fails fast (no {@code es_queue} recovery), so
   * an analysis writing the same issues concurrently can race it. With analyses of the affected projects quiesced there
   * are no competing writers and the race cannot happen — the behaviour a one-shot admin data migration wants.
   *
   * <p>Any indexing error is recoverable by a plain force re-run (the reindex is idempotent). A <em>bulk-item</em> failure
   * (e.g. a transient ES hiccup on some docs) is the common shape: {@code stop()} still runs to completion — it flushes
   * and refreshes — and only THEN {@link IndexingListener#FAIL_ON_ERROR} throws
   * {@code "Unrecoverable indexing failures: N errors ..."}. Just re-run to convergence (COMPLETED is reached only when
   * every doc indexed, so there is no silent partial backfill). The per-doc causes are in the server/Elasticsearch log,
   * not in the FAILED detail. A branch whose scroll fails takes the whole run down for the same reason — a partial
   * backfill must not be reported as COMPLETED. A run that DIES before {@code stop()} finishes leaves nothing to clean
   * up: no index settings were changed, so a re-run is the whole recovery procedure.
   *
   * @return the number of issue documents reindexed
   */
  public long reindexByRuleUuids(Collection<String> ruleUuids) {
    if (ruleUuids.isEmpty()) {
      // An empty rule set would render as `rule_uuid in ()` and fail as a syntax error. Callers are expected to skip the
      // reindex entirely in that case; return the same answer rather than blowing up.
      return 0;
    }
    List<String> branchUuids;
    try (DbSession dbSession = dbClient.openSession(false)) {
      branchUuids = dbClient.issueDao().selectBranchUuidsForRuleUuids(dbSession, ruleUuids);
    }
    if (branchUuids.isEmpty()) {
      return 0;
    }
    int threads = Math.max(1, Math.min(REINDEX_MAX_THREADS, branchUuids.size()));
    LOGGER.info("reindexByRuleUuids: {} branch(es) to reindex, {} thread(s)", branchUuids.size(), threads);

    BulkIndexer bulkIndexer = new BulkIndexer(esClient, TYPE_ISSUE, Size.MIGRATION, IndexingListener.FAIL_ON_ERROR);
    bulkIndexer.start();
    AtomicLong reindexed = new AtomicLong(0);
    long startedAt = System.currentTimeMillis();
    ExecutorService executor = Executors.newFixedThreadPool(threads, r -> {
      Thread t = new Thread(r, "issue-reindex-by-rule");
      t.setDaemon(true);
      return t;
    });
    RuntimeException drainFailure = null;
    try {
      List<Future<?>> futures = new ArrayList<>(branchUuids.size());
      for (String branchUuid : branchUuids) {
        futures.add(executor.submit(() -> drainBranch(branchUuid, ruleUuids, bulkIndexer, reindexed, startedAt)));
      }
      // Wait for every branch before flushing, and surface the first failure. Other branches are left to finish rather
      // than cancelled: their docs are already queued in the shared indexer and stop() below has to flush them anyway.
      for (Future<?> future : futures) {
        try {
          future.get();
        } catch (ExecutionException e) {
          if (drainFailure == null) {
            drainFailure = new IllegalStateException("Fail to reindex issues of ai_code_fix_enabled rules", e.getCause());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while reindexing issues of ai_code_fix_enabled rules", e);
        }
      }
      if (drainFailure != null) {
        throw drainFailure;
      }
    } catch (RuntimeException e) {
      // Remember the real cause (fetch/build/bulk error) so a failure while flushing below cannot mask it — that root
      // cause is what ends up in the migration's FAILED status and drives diagnosis.
      drainFailure = e;
      throw e;
    } finally {
      executor.shutdownNow();
      // stop() flushes queued bulk requests and refreshes the index. It must run even if a drain failed, to flush what
      // was queued. Isolate its own failure: if the drain already failed keep that as the primary error and only attach
      // the stop() failure; else a flush failure on an otherwise-successful drain is itself the failure and propagates.
      try {
        long flushStart = System.currentTimeMillis();
        bulkIndexer.stop();
        LOGGER.info("reindexByRuleUuids: final flush + refresh took {} ms ({} docs total in {} ms)",
          System.currentTimeMillis() - flushStart, reindexed.get(), System.currentTimeMillis() - startedAt);
      } catch (RuntimeException stopFailure) {
        if (drainFailure != null) {
          drainFailure.addSuppressed(stopFailure);
        } else {
          throw stopFailure;
        }
      }
    }
    return reindexed.get();
  }

  /**
   * Streams one branch's in-scope issues into the shared indexer. {@link BulkIndexer#add} and {@link IndexingResult} are
   * both safe to call from several threads (the underlying {@code BulkProcessor} serialises, and the counters are
   * atomic), so producers need no coordination beyond the progress log below.
   */
  private void drainBranch(String branchUuid, Collection<String> ruleUuids, BulkIndexer bulkIndexer,
    AtomicLong reindexed, long startedAt) {
    try (IssueIterator issues = issueIteratorFactory.createForBranchAndRuleUuids(branchUuid, ruleUuids)) {
      while (issues.hasNext()) {
        bulkIndexer.add(newIndexRequest(issues.next()));
        // incrementAndGet hands out distinct values, so exactly one thread observes each interval boundary — no
        // coordination needed to keep this to one line per interval.
        long total = reindexed.incrementAndGet();
        if (total % REINDEX_LOG_INTERVAL == 0) {
          LOGGER.info("reindexByRuleUuids: {} docs queued after {} ms", total, System.currentTimeMillis() - startedAt);
        }
      }
    }
  }

  @Override
  public void indexOnAnalysis(String branchUuid) {
    try (IssueIterator issues = issueIteratorFactory.createForBranch(branchUuid)) {
      doIndex(issues);
    }
  }

  @Override
  public void indexOnAnalysis(String branchUuid, Collection<String> diffToIndex) {
    if (diffToIndex.isEmpty()) {
      return;
    }
    try (IssueIterator issues = issueIteratorFactory.createForIssueKeys(diffToIndex)) {
      doIndex(issues);
    }
  }

  @Override
  public boolean supportDiffIndexing() {
    return true;
  }

  public void indexProject(String projectUuid) {
    asyncIssueIndexing.triggerForProject(projectUuid);
  }

  @Override
  public Collection<EsQueueDto> prepareForRecoveryOnEntityEvent(DbSession dbSession, Collection<String> entityUuids, Indexers.EntityEvent cause) {
    return switch (cause) {
      case CREATION, PROJECT_KEY_UPDATE, PROJECT_TAGS_UPDATE, PERMISSION_CHANGE ->
        // Nothing to do, issues do not exist at project creation
        // Measures, permissions, project key and tags are not used in type issues/issue
        emptyList();

      case DELETION -> {
        List<EsQueueDto> items = createProjectDeleteRecoveryItems(entityUuids);
        yield dbClient.esQueueDao().insert(dbSession, items);
      }
    };
  }

  @Override
  public Collection<EsQueueDto> prepareForRecoveryOnBranchEvent(DbSession dbSession, Collection<String> branchUuids, Indexers.BranchEvent cause) {
    return switch (cause) {
      case MEASURE_CHANGE ->
        // Measures, permissions, project key and tags are not used in type issues/issue
        emptyList();

      case DELETION, SWITCH_OF_MAIN_BRANCH -> {
        // switch of main branch requires to reindex the project issues
        List<EsQueueDto> items = createBranchRecoveryItems(branchUuids);
        yield dbClient.esQueueDao().insert(dbSession, items);
      }
    };
  }

  private static List<EsQueueDto> createProjectDeleteRecoveryItems(Collection<String> entityUuids) {
    return entityUuids.stream()
      .map(entityUuid -> createQueueDto(entityUuid, ID_TYPE_DELETE_PROJECT_UUID, entityUuid))
      .toList();
  }

  private static List<EsQueueDto> createBranchRecoveryItems(Collection<String> branchUuids) {
    return branchUuids.stream()
      .map(branchUuid -> createQueueDto(branchUuid, ID_TYPE_BRANCH_UUID, branchUuid))
      .toList();
  }

  /**
   * Commits the DB transaction and adds the issues to Elasticsearch index.
   * <p>
   * If indexing fails, then the recovery daemon will retry later and this
   * method successfully returns. Meanwhile these issues will be "eventually
   * consistent" when requesting the index.
   */
  public void commitAndIndexIssues(DbSession dbSession, Collection<IssueDto> issues) {
    ListMultimap<String, EsQueueDto> itemsByIssueKey = ArrayListMultimap.create();
    issues.stream()
      .map(issue -> createQueueDto(issue.getKey(), ID_TYPE_ISSUE_KEY, issue.getProjectUuid()))
      // a mutable ListMultimap is needed for doIndexIssueItems, so MoreCollectors.index() is
      // not used
      .forEach(i -> itemsByIssueKey.put(i.getDocId(), i));
    dbClient.esQueueDao().insert(dbSession, itemsByIssueKey.values());

    dbSession.commit();

    doIndexIssueItems(dbSession, itemsByIssueKey);
  }

  @Override
  public IndexingResult index(DbSession dbSession, Collection<EsQueueDto> items) {
    ListMultimap<String, EsQueueDto> itemsByIssueKey = ArrayListMultimap.create();
    ListMultimap<String, EsQueueDto> itemsByBranchUuid = ArrayListMultimap.create();
    ListMultimap<String, EsQueueDto> itemsByDeleteProjectUuid = ArrayListMultimap.create();

    items.forEach(i -> {
      if (ID_TYPE_ISSUE_KEY.equals(i.getDocIdType())) {
        itemsByIssueKey.put(i.getDocId(), i);
      } else if (ID_TYPE_BRANCH_UUID.equals(i.getDocIdType())) {
        itemsByBranchUuid.put(i.getDocId(), i);
      } else if (ID_TYPE_DELETE_PROJECT_UUID.equals(i.getDocIdType())) {
        itemsByDeleteProjectUuid.put(i.getDocId(), i);
      } else {
        LOGGER.error("Unsupported es_queue.doc_id_type for issues. Manual fix is required: " + i);
      }
    });

    IndexingResult result = new IndexingResult();
    result.add(doIndexIssueItems(dbSession, itemsByIssueKey));
    result.add(doIndexBranchItems(dbSession, itemsByBranchUuid));
    result.add(doDeleteProjectIndexItems(dbSession, itemsByDeleteProjectUuid));
    return result;
  }

  private IndexingResult doIndexIssueItems(DbSession dbSession, ListMultimap<String, EsQueueDto> itemsByIssueKey) {
    if (itemsByIssueKey.isEmpty()) {
      return new IndexingResult();
    }
    IndexingListener listener = new OneToOneResilientIndexingListener(dbClient, dbSession, itemsByIssueKey.values());
    BulkIndexer bulkIndexer = createBulkIndexer(listener);
    bulkIndexer.start();

    try (IssueIterator issues = issueIteratorFactory.createForIssueKeys(itemsByIssueKey.keySet())) {
      while (issues.hasNext()) {
        IssueDoc issue = issues.next();
        bulkIndexer.add(newIndexRequest(issue));
        itemsByIssueKey.removeAll(issue.getId());
      }
    }

    // the remaining uuids reference issues that don't exist in db. They must
    // be deleted from index.
    itemsByIssueKey.values().forEach(
      item -> bulkIndexer.addDeletion(TYPE_ISSUE.getMainType(), item.getDocId(), item.getDocRouting()));

    return bulkIndexer.stop();
  }

  private IndexingResult doDeleteProjectIndexItems(DbSession dbSession, ListMultimap<String, EsQueueDto> itemsByDeleteProjectUuid) {
    IndexingListener listener = new OneToManyResilientIndexingListener(dbClient, dbSession, itemsByDeleteProjectUuid.values());
    BulkIndexer bulkIndexer = createBulkIndexer(listener);
    bulkIndexer.start();
    for (String projectUuid : itemsByDeleteProjectUuid.keySet()) {
      addProjectDeletionToBulkIndexer(bulkIndexer, projectUuid);
    }
    return bulkIndexer.stop();
  }

  private IndexingResult doIndexBranchItems(DbSession dbSession, ListMultimap<String, EsQueueDto> itemsByBranchUuid) {
    if (itemsByBranchUuid.isEmpty()) {
      return new IndexingResult();
    }

    // one branch, referenced by es_queue.doc_id = many issues
    IndexingListener listener = new OneToManyResilientIndexingListener(dbClient, dbSession, itemsByBranchUuid.values());
    BulkIndexer bulkIndexer = createBulkIndexer(listener);
    bulkIndexer.start();

    for (String branchUuid : itemsByBranchUuid.keySet()) {
      try (IssueIterator issues = issueIteratorFactory.createForBranch(branchUuid)) {
        if (issues.hasNext()) {
          do {
            IssueDoc doc = issues.next();
            bulkIndexer.add(newIndexRequest(doc));
          } while (issues.hasNext());
        } else {
          // branch does not exist or has no issues. In both cases,
          // all the documents related to this branch are deleted.
          Optional<BranchDto> branch = dbClient.branchDao().selectByUuid(dbSession, branchUuid);
          branch.ifPresent(b -> addBranchDeletionToBulkIndexer(bulkIndexer, b.getProjectUuid(), b.getUuid()));
        }
      }
    }

    return bulkIndexer.stop();
  }

  // Used by Compute Engine, no need to recovery on errors
  public void deleteByKeys(String projectUuid, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }

    BulkIndexer bulkIndexer = createBulkIndexer(IndexingListener.FAIL_ON_ERROR);
    bulkIndexer.start();
    issueKeys.forEach(issueKey -> bulkIndexer.addDeletion(TYPE_ISSUE.getMainType(), issueKey, AuthorizationDoc.idOf(projectUuid)));
    bulkIndexer.stop();
  }

  @VisibleForTesting
  protected void index(Iterator<IssueDoc> issues) {
    doIndex(issues);
  }

  private void doIndex(Iterator<IssueDoc> issues) {
    BulkIndexer bulk = createBulkIndexer(IndexingListener.FAIL_ON_ERROR);
    bulk.start();
    while (issues.hasNext()) {
      IssueDoc issue = issues.next();
      bulk.add(newIndexRequest(issue));
    }
    bulk.stop();
  }

  private static IndexRequest newIndexRequest(IssueDoc issue) {
    return new IndexRequest(TYPE_ISSUE.getMainType().getIndex().getName())
      .id(issue.getId())
      .routing(issue.getRouting().orElseThrow(() -> new IllegalStateException("IssueDoc should define a routing")))
      .source(issue.getFields());
  }

  private static void addProjectDeletionToBulkIndexer(BulkIndexer bulkIndexer, String projectUuid) {
    SearchRequest search = EsClient.prepareSearch(TYPE_ISSUE.getMainType())
      .routing(AuthorizationDoc.idOf(projectUuid))
      .source(new SearchSourceBuilder().query(boolQuery().must(termQuery(FIELD_ISSUE_PROJECT_UUID, projectUuid))));

    bulkIndexer.addDeletion(search);
  }

  private static void addBranchDeletionToBulkIndexer(BulkIndexer bulkIndexer, String projectUUid, String branchUuid) {
    SearchRequest search = EsClient.prepareSearch(TYPE_ISSUE.getMainType())
      // routing is based on the parent (See BaseDoc#getRouting).
      // The parent is set to the projectUUid when an issue is indexed (See IssueDoc#setProjectUuid). We need to set it here
      // so that the search finds the indexed docs to be deleted.
      .routing(AuthorizationDoc.idOf(projectUUid))
      .source(new SearchSourceBuilder().query(boolQuery().must(termQuery(FIELD_ISSUE_BRANCH_UUID, branchUuid))));

    bulkIndexer.addDeletion(search);
  }

  private static EsQueueDto createQueueDto(String docId, String docIdType, String projectUuid) {
    return EsQueueDto.create(TYPE_ISSUE.format(), docId, docIdType, AuthorizationDoc.idOf(projectUuid));
  }

  private BulkIndexer createBulkIndexer(IndexingListener listener) {
    return new BulkIndexer(esClient, TYPE_ISSUE, Size.REGULAR, listener);
  }
}
