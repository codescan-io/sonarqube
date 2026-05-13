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

import co.elastic.clients.elasticsearch._types.mapping.SourceField;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.*;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static java.lang.Boolean.TRUE;
import static org.sonar.core.util.CollectionUtils.isNotEmpty;

final class EsRequestDetails {
  private static final String ON_INDICES_MESSAGE = " on indices '%s'";

  private EsRequestDetails() {
    // this is utility class only
  }

  static String computeDetailsAsString(SearchRequest searchRequest) {
    StringBuilder message = new StringBuilder();
    message.append(String.format("ES search request '%s'", searchRequest));
    if (isNotEmpty(searchRequest.index())) {
      message.append(String.format(ON_INDICES_MESSAGE, searchRequest.index()));
    }
    return message.toString();
  }

  public static String computeDetailsAsString(ScrollRequest searchScrollRequest) {
    return String.format("ES search scroll request for scroll id '%s'", searchScrollRequest.scrollId());
  }

  static String computeDetailsAsString(DeleteRequest deleteRequest) {
    return new StringBuilder()
      .append("ES delete request of doc ")
      .append(deleteRequest.id())
      .append(" in index ")
      .append(deleteRequest.index())
      .toString();
  }

  static String computeDetailsAsString(RefreshRequest refreshRequest) {
    StringBuilder message = new StringBuilder();
    message.append("ES refresh request");
    if (!refreshRequest.index().isEmpty()) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(refreshRequest.index(), ",")));
    }
    return message.toString();
  }

  static String computeDetailsAsString(ClearCacheRequest request) {
    StringBuilder message = new StringBuilder();
    message.append("ES clear cache request");
    if (isNotEmpty(request.index())) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(request.index(), ",")));
    }
    List<String> fields = request.fields();
    if (isNotEmpty(fields)) {
      message.append(String.format(" on fields '%s'", StringUtils.join(fields, ",")));
    }
    if (TRUE.equals(request.query())) {
      message.append(" with filter cache");
    }
    if (TRUE.equals(request.fielddata())) {
      message.append(" with field data cache");
    }
    if (TRUE.equals(request.request())) {
      message.append(" with request cache");
    }
    return message.toString();
  }

  static <T> String computeDetailsAsString(IndexRequest<T> indexRequest) {
    return new StringBuilder().append("ES index request")
      .append(String.format(" for key '%s'", indexRequest.id()))
      .append(String.format(" on index '%s'", indexRequest.index()))
      .toString();
  }

  static String computeDetailsAsString(GetRequest request) {
    return new StringBuilder().append("ES get request")
      .append(String.format(" for key '%s'", request.id()))
      .append(String.format(" on index '%s'", request.index()))
      .toString();
  }

  static String computeDetailsAsString(GetIndexRequest getIndexRequest) {
    StringBuilder message = new StringBuilder();
    message.append("ES indices exists request");
    if (isNotEmpty(getIndexRequest.index())) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(getIndexRequest.index(), ",")));
    }
    return message.toString();
  }

  static String computeDetailsAsString(CreateIndexRequest createIndexRequest) {
    return String.format("ES create index '%s'", createIndexRequest.index());
  }

  static String computeDetailsAsString(PutMappingRequest request) {
    StringBuilder message = new StringBuilder();
    message.append("ES put mapping request");
    if (isNotEmpty(request.index())) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(request.index(), ",")));
    }
//    BytesReference source = request.source();
    SourceField source = request.source();
    if (source != null) {
      message.append(String.format(" with source '%s'", source));
    }

    return message.toString();
  }

  static String computeDetailsAsString(HealthRequest clusterHealthRequest) {
    StringBuilder message = new StringBuilder();
    message.append("ES cluster health request");
    List<String> indices = clusterHealthRequest.index();
    if (isNotEmpty(indices)) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(indices, ",")));
    }
    return message.toString();
  }

  static String computeDetailsAsString(String... indices) {
    StringBuilder message = new StringBuilder();
    message.append("ES indices stats request");
    if (indices.length > 0) {
      message.append(String.format(ON_INDICES_MESSAGE, StringUtils.join(indices, ",")));
    }
    return message.toString();
  }

}
