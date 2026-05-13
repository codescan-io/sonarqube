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
package org.sonar.server.es;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.TimeUnit;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndicesSettingsResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.BackoffPolicy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.log.Profiler;
import org.sonar.core.util.ProgressLogger;

import static java.lang.String.format;

/**
 * Helper to bulk requests in an efficient way :
 * <ul>
 * <li>bulk request is sent on the wire when its size is higher than 5Mb</li>
 * <li>on large table indexing, replicas and automatic refresh can be temporarily disabled</li>
 * </ul>
 */
public class BulkIndexer<Document> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BulkIndexer.class);
  private static final int FLUSH_ACTIONS = -1;
  private static final long FLUSH_BYTE_SIZE = 1_000_000;
  private static final String REFRESH_INTERVAL_SETTING = "index.refresh_interval";
  private static final int DEFAULT_NUMBER_OF_SHARDS = 5;

  private final EsClient esClient;

  private final IndexType indexType;
  private final BulkIngester<Document> bulkIngester;
  private final IndexingResult result = new IndexingResult();
  private final IndexingListener indexingListener;
  private final SizeHandler<Document> sizeHandler;
  private final Class<Document> tClass;

  public BulkIndexer(EsClient client, IndexType indexType, Size size, Class<Document> tClass) {
    this(client, indexType, size, IndexingListener.FAIL_ON_ERROR, tClass);
  }

  public BulkIndexer(EsClient client, IndexType indexType, Size size, IndexingListener indexingListener, Class<Document> tClass) {
    this.esClient = client;
    this.indexType = indexType;
    this.sizeHandler = size.createHandler(Runtime2.INSTANCE);
    this.indexingListener = indexingListener;
    BulkProcessorListener<Document> bulkProcessorListener = new BulkProcessorListener<>();
    this.bulkIngester = BulkIngester.of(b -> b
      .client(esClient.client())
      .maxOperations(FLUSH_ACTIONS)
      .maxSize(FLUSH_BYTE_SIZE)
      .backoffPolicy(BackoffPolicy.exponentialBackoff())
      .listener(bulkProcessorListener)
    );
    this.tClass = tClass;
  }

  public IndexType getIndexType() {
    return indexType;
  }

  public void start() {
    result.clear();
    sizeHandler.beforeStart(this);
  }

  /**
   * @return the number of documents successfully indexed
   */
  public IndexingResult stop() {
    bulkIngester.close();

    esClient.refreshV2(indexType.getMainType().getIndex());

    sizeHandler.afterStop(this);
    indexingListener.onFinish(result);
    return result;
  }

  public void add(IndexRequest<Document> request) {
    result.incrementRequests();
    bulkIngester.add(bo -> bo
      .index(idx -> idx
        .index(request.index())
        .id(request.id())
        .document(request.document())
      )
    );
  }

  public void add(IndexOperation<Document> request) {
    result.incrementRequests();
    bulkIngester.add(bo -> bo
      .index(request)
    );
  }

  public void add(DeleteOperation request) {
    result.incrementRequests();
    bulkIngester.add(bo -> bo
      .delete(request)
    );
  }

  public void addDeletion(SearchRequest searchRequest) {
    // this search is synchronous. An optimization would be to be non-blocking,
    // but it requires to tracking pending requests in close().
    // Same semaphore can't be reused because of potential deadlock (requires to acquire
    // two locks)
    var fixedSearchRequest = searchRequest
      .rebuild()
      .scroll(tb -> tb
        .time(5, TimeUnit.Minutes)
      )
      .sort(so -> so
        .field(f -> f
          .field("_doc")
          .order(SortOrder.Asc)
        )
      )
      .size(100)
      // load only doc ids, not _source fields
      .source(s -> s.fetch(false));

    SearchResponse<Document> searchResponse = esClient.searchV2(b -> fixedSearchRequest, tClass);
    List<Hit<Document>> hits = searchResponse.hits().hits();
    String scrollId = searchResponse.scrollId();
    while (true) {
      for (Hit<Document> hit: hits) {
        String routing = hit.routing();
        DeleteOperation deleteOperation = DeleteOperation.of(b ->  {
          b.index(hit.index())
            .id(hit.id());
          if (routing != null) {
            b.routing(routing);
          }
          return b;
        });
        add(deleteOperation);
      }
      if (scrollId == null) {
        break;
      }
      String searchScrollId = scrollId;
      ScrollResponse<Document> scrollResponse = esClient.scrollV2(b -> b
        .scrollId(searchScrollId)
        .scroll(tb -> tb
          .time(5, TimeUnit.Minutes)
        ),
        tClass
      );
      if (hits.isEmpty()) {
        esClient.clearScrollV2(b -> b
          .scrollId(searchScrollId)
        );
        break;
      }
      hits = scrollResponse.hits().hits();
      scrollId = scrollResponse.scrollId();
    }
  }

  public void addDeletion(IndexType indexType, String id) {
    add(DeleteOperation.of(b -> b
      .index(indexType.getMainType().getIndex().getName())
      .id(id)
    ));
  }

  public void addDeletion(IndexType indexType, String id, @Nullable String routing) {
    add(DeleteOperation.of(b -> b
      .index(indexType.getMainType().getIndex().getName())
      .id(id)
      .routing(routing)
    ));
  }

  /**
   * Delete all the documents matching the given search request. This method is blocking.
   * Index is refreshed, so docs are not searchable as soon as method is executed.
   * <p>
   * Note that the parameter indexType could be removed if progress logs are not needed.
   */
  public static <D> IndexingResult delete(EsClient client, IndexType indexType, SearchRequest searchRequest, Class<D> tClass) {
    BulkIndexer<D> bulk = new BulkIndexer<>(client, indexType, Size.REGULAR, tClass);
    bulk.start();
    bulk.addDeletion(searchRequest);
    return bulk.stop();
  }

  private final class BulkProcessorListener<Context> implements BulkListener<Context> {
    // a map containing per each request the associated profiler
    private final Map<BulkRequest, Profiler> profilerByRequest = new ConcurrentHashMap<>();

    @Override
    public void beforeBulk(long executionId, BulkRequest request, List<Context> data) {
      final Profiler profiler = Profiler.createIfTrace(EsClient.LOGGER);
      profiler.start();
      profilerByRequest.put(request, profiler);
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Context> data, BulkResponse response) {
      stopProfiler(request);
      List<DocId> successDocIds = new ArrayList<>();
      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          LOGGER.error("index [{}], type [{}], id [{}], message [{}]", item.index(), item.operationType(), item.id(), item.error());
        } else {
          result.incrementSuccess();
          successDocIds.add(new DocId(item.index(), item.operationType().name(), item.id()));
        }
      }
      indexingListener.onSuccess(successDocIds);
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Context> data, Throwable e) {
      LOGGER.error("Fail to execute bulk index request: {}", request, e);
      stopProfiler(request);
    }

    private void stopProfiler(BulkRequest request) {
      final Profiler profiler = profilerByRequest.get(request);
      if (Objects.nonNull(profiler) && profiler.isTraceEnabled()) {
        profiler.stopTrace(toString(request));
      }
      profilerByRequest.remove(request);
    }

    private String toString(BulkRequest bulkRequest) {
      StringBuilder message = new StringBuilder();
      message.append("Bulk[");
      Multiset<BulkRequestKey> groupedRequests = LinkedHashMultiset.create();
      for (int i = 0; i < bulkRequest.operations().size(); i++) {
        BulkOperation item = bulkRequest.operations().get(i);
        String requestType;
        if (item.isIndex()) {
          requestType = "index";
        } else if (item.isUpdate()) {
          requestType = "update";
        } else if (item.isDelete()) {
          requestType = "delete";
        } else {
          // Cannot happen, not allowed by BulkRequest's contract
          throw new IllegalStateException("Unsupported bulk request type: " + item.getClass());
        }
        groupedRequests.add(new BulkRequestKey(requestType, item.index().index()));
      }

      Set<Multiset.Entry<BulkRequestKey>> entrySet = groupedRequests.entrySet();
      int size = entrySet.size();
      int current = 0;
      for (Multiset.Entry<BulkRequestKey> requestEntry : entrySet) {
        message.append(requestEntry.getCount()).append(" ").append(requestEntry.getElement().toString());
        current++;
        if (current < size) {
          message.append(", ");
        }
      }

      message.append("]");
      return message.toString();
    }
  }

  private static class BulkRequestKey {
    private final String requestType;
    private final String index;

    private BulkRequestKey(String requestType, String index) {
      this.requestType = requestType;
      this.index = index;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BulkRequestKey that = (BulkRequestKey) o;
      return Objects.equals(index, that.index) && Objects.equals(requestType, that.requestType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(requestType, index);
    }

    @Override
    public String toString() {
      return String.format("%s requests on %s", requestType, index);
    }
  }

  public enum Size {
    /**
     * Use this size for a limited number of documents.
     */
    REGULAR {
      @Override
      <D> SizeHandler<D> createHandler(Runtime2 runtime2) {
        return new SizeHandler<D>();
      }
    },

    /**
     * Large indexing is a heavy operation that populates an index generally from scratch. Replicas and
     * automatic refresh are disabled during bulk indexing and lucene segments are optimized at the end.
     * Use this size for initial indexing and if you expect unusual huge numbers of documents.
     */
    LARGE {
      @Override
      <D> SizeHandler<D> createHandler(Runtime2 runtime2) {
        return new LargeSizeHandler<D>(runtime2);
      }
    };

    abstract <D> SizeHandler<D> createHandler(Runtime2 runtime2);
  }

  @VisibleForTesting
  static class Runtime2 {
    private static final Runtime2 INSTANCE = new Runtime2();

    int getCores() {
      return Runtime.getRuntime().availableProcessors();
    }
  }

  static class SizeHandler<D> {
    int getConcurrentRequests() {
      // in the same thread by default
      return 0;
    }

    void beforeStart(BulkIndexer<D> bulkIndexer) {
      // nothing to do, to be overridden if needed
    }

    void afterStop(BulkIndexer<D> bulkIndexer) {
      // nothing to do, to be overridden if needed
    }
  }

  static class LargeSizeHandler<D> extends SizeHandler<D> {
    private static final String SETTING_NUMBER_OF_REPLICAS = "index.number_of_replicas";

    private final Map<String, Object> initialSettings = new HashMap<>();
    private final Runtime2 runtime2;
    private ProgressLogger progress;

    LargeSizeHandler(Runtime2 runtime2) {
      this.runtime2 = runtime2;
    }

    @Override
    int getConcurrentRequests() {
      // see SONAR-8075
      int cores = runtime2.getCores();
      // FIXME do not use DEFAULT_NUMBER_OF_SHARDS
      return Math.max(1, cores / DEFAULT_NUMBER_OF_SHARDS) - 1;
    }

    @Override
    void beforeStart(BulkIndexer<D> bulkIndexer) {
      String index = bulkIndexer.indexType.getMainType().getIndex().getName();
      this.progress = new ProgressLogger(format("Progress[BulkIndexer[%s]]", index), bulkIndexer.result.total, LOGGER)
        .setPluralLabel("requests");
      this.progress.start();
      Map<String, Object> temporarySettings = new HashMap<>();

      GetIndicesSettingsResponse settingsResp = bulkIndexer.esClient.getSettingsV2(b -> b);

      // deactivate replicas
      int initialReplicas = Integer.parseInt(settingsResp.settings().get(index).settings().numberOfReplicas());
      if (initialReplicas > 0) {
        initialSettings.put(SETTING_NUMBER_OF_REPLICAS, initialReplicas);
        temporarySettings.put(SETTING_NUMBER_OF_REPLICAS, 0);
      }

      // deactivate periodical refresh
      Time refreshInterval = settingsResp.get(index).settings().refreshInterval();
      initialSettings.put(REFRESH_INTERVAL_SETTING, refreshInterval);
      temporarySettings.put(REFRESH_INTERVAL_SETTING, "-1");

      updateSettings(bulkIndexer, temporarySettings);
    }

    @Override
    void afterStop(BulkIndexer<D> bulkIndexer) {
      // optimize lucene segments and revert index settings
      // Optimization must be done before re-applying replicas:
      // http://www.elasticsearch.org/blog/performance-considerations-elasticsearch-indexing/
      bulkIndexer.esClient.forcemergeV2(b -> b
        .index(bulkIndexer.indexType.getMainType().getIndex().getName())
      );

      updateSettings(bulkIndexer, initialSettings);
      this.progress.stop();
    }

    private static <D> void updateSettings(BulkIndexer<D> bulkIndexer, Map<String, Object> settings) {
      Map<String, JsonData> jsonSettings = new HashMap<>();
      settings.forEach((key, value) -> jsonSettings.put(key, JsonData.of(value)));

      bulkIndexer.esClient.putSettingsV2(b -> b
        .index(bulkIndexer.indexType.getMainType().getIndex().getName())
        .settings(isb -> isb
          .otherSettings(jsonSettings)
        )
      );
    }
  }
}
