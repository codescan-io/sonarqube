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
  /** Log a progress line every this many documents during {@link #reindexByRuleUuids}. */
  private static final long REINDEX_LOG_INTERVAL = 100_000L;
  /**
   * How many branches {@link #reindexByRuleUuids} works on at the same time. Each one holds a database connection and an
   * open query for as long as its branch takes, so this is kept comfortably below the connection pool size
   * ({@code sonar.jdbc.maxActive}, 60 by default) instead of growing with the number of branches.
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
   * Rewrites, straight from the database, every issue whose rule is one of {@code ruleUuids}. Each document is rebuilt in
   * full, so {@code codefixStatus} gets its correct value along with every other field.
   *
   * <p><b>Why a rewrite and not an update.</b> Elasticsearch's {@code update_by_query} rebuilds each matched document
   * from its stored {@code _source}, and the {@code issues} index does not store one (see {@code IssueIndexDefinition}).
   * The database is the source of truth for this index, so we read from there and write the documents again.
   *
   * <p><b>One branch at a time, several branches at once.</b> We first ask the database which branches actually have
   * matching issues ({@code IssueDao#selectBranchUuidsForRuleUuids}), then give each branch its own database connection
   * and its own query, running {@link #REINDEX_MAX_THREADS} of them together. SonarQube's own startup reindex works the
   * same way (one task per branch), and it is what makes this fast rather than merely correct:
   * <ul>
   *   <li>One query over the whole table had to sort every matching row before it could return the first one, because
   *       Postgres cannot sort and stream at the same time. (The sort is not optional: MyBatis needs all the rows of one
   *       issue next to each other to assemble its impacts and dependencies.) One branch's worth sorts quickly.</li>
   *   <li>Elasticsearch places issue documents by project, so a batch from one branch lands on a single shard instead of
   *       being split across all of them.</li>
   * </ul>
   *
   * <p><b>Why {@link Size#MIGRATION}.</b> Every thread writes through one shared {@link BulkIndexer}, so the index is
   * flushed and refreshed once at the end instead of once per branch — thousands of refreshes would leave it full of
   * tiny segments. We avoid {@link Size#LARGE} because that mode is built for filling an index from scratch: when it
   * finishes it force-merges and re-replicates the WHOLE index, which costs the same whether we rewrote 1% of it or all
   * of it. (LARGE's other two tricks do nothing here anyway — this index never auto-refreshes, and replicas are already
   * 0 unless you run a cluster.) MIGRATION leaves index settings alone like {@link Size#REGULAR}, but sends bigger
   * batches and allows a few to be in flight at once. REGULAR allows none, so whichever thread happens to fill a batch
   * has to send it itself while all the other threads wait behind it.
   *
   * <p><b>Run it when the system is quiet</b>, with analyses of the affected projects stopped. Searches keep working
   * normally, but each worker holds a database connection and an open query for as long as its branch takes. More
   * importantly these are plain last-write-wins writes, outside the usual retry-on-failure path, so an analysis writing
   * the same issues at the same time could clash with us. With those analyses stopped there is nobody else writing and
   * that cannot happen. Note this also does not set {@code need_issue_sync}, unlike {@link #indexProject}.
   *
   * <p><b>If it fails, run it again.</b> Rewriting a document with the value it should already have is harmless, so
   * re-runs are safe, and a run that dies partway leaves nothing to clean up because no index settings were changed.
   * Two things can go wrong:
   * <ul>
   *   <li>Elasticsearch rejects some documents (a transient hiccup, say). {@code stop()} still finishes flushing and
   *       refreshing, and only THEN does {@link IndexingListener#FAIL_ON_ERROR} throw
   *       {@code "Unrecoverable indexing failures: N errors ..."}. The reason for each rejected document is in the
   *       server/Elasticsearch log, not in the migration's failure message.</li>
   *   <li>One branch's query fails. That fails the whole run on purpose — a half-done backfill must never be reported as
   *       COMPLETED.</li>
   * </ul>
   * Either way, re-run until it reports COMPLETED, which only happens once every document is indexed.
   *
   * @return how many issue documents were rewritten
   */
  public long reindexByRuleUuids(Collection<String> ruleUuids) {
    if (ruleUuids.isEmpty()) {
      // With no rules the SQL would read `rule_uuid in ()`, which is a syntax error. Callers are meant to skip the
      // reindex in that case; give them the same answer instead of blowing up.
      return 0;
    }
    List<String> branchUuids;
    try (DbSession dbSession = dbClient.openSession(false)) {
      branchUuids = dbClient.issueDao().selectBranchUuidsForRuleUuids(dbSession, ruleUuids);
    }
    if (branchUuids.isEmpty()) {
      // Nothing to do. Returning here skips starting a thread pool and, more usefully, skips the index refresh that
      // BulkIndexer.stop() would otherwise do for no reason.
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
      // Wait for every branch before flushing, and report the first failure. We let the other branches finish rather
      // than cancelling them: their documents are already queued in the shared indexer, and stop() below has to send
      // them anyway.
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
      // Hold on to the real cause, so that a later failure while flushing cannot hide it. This is what shows up as the
      // migration's FAILED reason, and it is what anyone diagnosing the run will read first.
      drainFailure = e;
      throw e;
    } finally {
      executor.shutdownNow();
      // stop() sends whatever is still queued and refreshes the index. It has to run even when a branch failed, so the
      // already-queued documents are not thrown away. Keep its own failure separate: if a branch already failed, that
      // stays the main error and this one is attached to it. Otherwise a failure here is the failure.
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
   * Reads one branch's matching issues and feeds them to the shared indexer. {@link BulkIndexer#add} and
   * {@link IndexingResult} are both safe to call from several threads at once (the underlying {@code BulkProcessor}
   * queues the calls, and the counters are atomic), so the workers need no coordination beyond the progress log below.
   */
  private void drainBranch(String branchUuid, Collection<String> ruleUuids, BulkIndexer bulkIndexer,
    AtomicLong reindexed, long startedAt) {
    try (IssueIterator issues = issueIteratorFactory.createForBranchAndRuleUuids(branchUuid, ruleUuids)) {
      while (issues.hasNext()) {
        bulkIndexer.add(newIndexRequest(issues.next()));
        // incrementAndGet gives every caller a different number, so exactly one thread sees each multiple of the
        // interval. That is enough to keep this to one log line per interval, with no locking.
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
