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

import co.elastic.clients.elasticsearch._types.TimeUnit;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.indices.*;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EsRequestDetailsTest {

  @Test
  public void should_format_SearchRequest() {
    SearchRequest searchRequest = SearchRequest.of(b -> b.index("index"));
    assertThat(EsRequestDetails.computeDetailsAsString(searchRequest))
      .isEqualTo("ES search request 'SearchRequest{searchType=QUERY_THEN_FETCH, indices=[index],"
          + " indicesOptions=IndicesOptions[ignore_unavailable=false, allow_no_indices=true,"
          + " expand_wildcards_open=true, expand_wildcards_closed=false, expand_wildcards_hidden=false,"
          + " allow_aliases_to_multiple_indices=true, forbid_closed_indices=true, ignore_aliases=false,"
          + " ignore_throttled=true], types=[], routing='null', preference='null', requestCache=null,"
          + " scroll=null, maxConcurrentShardRequests=0, batchedReduceSize=512, preFilterShardSize=null,"
          + " allowPartialSearchResults=null, localClusterAlias=null, getOrCreateAbsoluteStartMillis=-1,"
          + " ccsMinimizeRoundtrips=true, enableFieldsEmulation=false, source={}}' on indices '[index]'");
  }

  @Test
  public void should_format_search_SearchScrollRequest() {
    ScrollRequest scrollRequest = ScrollRequest.of(b -> b
      .scrollId("scroll-id")
      .scroll(sb -> sb
        .time(0, TimeUnit.Microseconds)
      )
    );
    assertThat(EsRequestDetails.computeDetailsAsString(scrollRequest))
      .isEqualTo("ES search scroll request for scroll id 'Scroll{keepAlive=0s}'");
  }

  @Test
  public void should_format_DeleteRequest() {
    DeleteRequest deleteRequest = DeleteRequest.of(b -> b
      .index("some-index")
      .id("some-id")
    );
    assertThat(EsRequestDetails.computeDetailsAsString(deleteRequest))
      .isEqualTo("ES delete request of doc some-id in index some-index");
  }

  @Test
  public void should_format_RefreshRequest() {
    RefreshRequest deleteRequest = RefreshRequest.of(b -> b
      .index("index-1", "index-2")
    );
    assertThat(EsRequestDetails.computeDetailsAsString(deleteRequest))
      .isEqualTo("ES refresh request on indices 'index-1,index-2'");
  }

  @Test
  public void should_format_ClearIndicesCacheRequest() {
    ClearCacheRequest clearIndicesCacheRequest = ClearCacheRequest.of(b -> b
      .index("index-1")
      .fields("field-1")
      .query(true)
      .fielddata(true)
      .request(true)
    );
    assertThat(EsRequestDetails.computeDetailsAsString(clearIndicesCacheRequest))
      .isEqualTo("ES clear cache request on indices 'index-1' on fields 'field-1' with filter cache with field data cache with request cache");
  }

  @Test
  public void should_format_IndexRequest() {
    IndexRequest indexRequest = IndexRequest.of(b -> b
      .index("index-1")
      .id("id-1")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(indexRequest))
      .isEqualTo("ES index request for key 'id-1' on index 'index-1'");
  }

  @Test
  public void should_format_GetRequest() {
    GetRequest request = GetRequest.of(b -> b
      .index("index-1")
      .id("id-1")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(request))
      .isEqualTo("ES get request for key 'id-1' on index 'index-1'");
  }

  @Test
  public void should_format_GetIndexRequest() {
    GetIndexRequest request = GetIndexRequest.of(b -> b
      .index("index-1", "index-2")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(request))
      .isEqualTo("ES indices exists request on indices 'index-1,index-2'");
  }

  @Test
  public void should_format_CreateIndexRequest() {
    CreateIndexRequest request = CreateIndexRequest.of(b -> b
      .index("index-1")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(request))
      .isEqualTo("ES create index 'index-1'");
  }

  @Test
  public void should_format_PutMappingRequest() {
    PutMappingRequest request = PutMappingRequest.of(b -> b
      .index("index-1")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(request))
      .isEqualTo("ES put mapping request on indices 'index-1'");
  }

  @Test
  public void should_format_ClusterHealthRequest() {
    HealthRequest request = HealthRequest.of(b -> b
      .index("index-1")
    );

    assertThat(EsRequestDetails.computeDetailsAsString(request))
      .isEqualTo("ES cluster health request on indices 'index-1'");
  }

  @Test
  public void should_format_IndicesStats() {
    assertThat(EsRequestDetails.computeDetailsAsString("index-1", "index-2"))
      .isEqualTo("ES indices stats request on indices 'index-1,index-2'");
  }
}
