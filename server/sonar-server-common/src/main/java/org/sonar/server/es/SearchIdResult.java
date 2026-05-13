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

import co.elastic.clients.elasticsearch.core.search.Hit;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import static java.util.Optional.ofNullable;

public class SearchIdResult<ID> {

  private final List<ID> uuids;
  private final Facets facets;
  private final long total;

  /**
   * Constructor for new Elasticsearch Java API Client (8.x).
   * Note: This constructor does not support facets from the response. If you need facets,
   * pass them separately using the LinkedHashMap constructor.
   */
  public <T> SearchIdResult(co.elastic.clients.elasticsearch.core.SearchResponse<T> response, Function<String, ID> converter, ZoneId timeZone) {
    this.facets = new Facets(new java.util.LinkedHashMap<>(), timeZone);
    this.total = getTotalHitsV2(response).value();
    this.uuids = convertToIdsV2(response.hits().hits(), converter);
  }

  private static <T> TotalHits getTotalHitsV2(co.elastic.clients.elasticsearch.core.SearchResponse<T> response) {
    return ofNullable(response.hits().total())
      .map(total -> {
        // Map the relation from new API to old API
        TotalHitsRelation relation = switch (total.relation()) {
          case Eq -> TotalHitsRelation.Eq;
          case Gte -> TotalHitsRelation.Gte;
        };
        return TotalHits.of(b -> b
          .value(total.value())
          .relation(relation)
        );
      })
      .orElseThrow(() -> new IllegalStateException("Could not get total hits of search results"));
  }

  public List<ID> getUuids() {
    return uuids;
  }

  public long getTotal() {
    return total;
  }

  public Facets getFacets() {
    return this.facets;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

  private static <I, T> List<I> convertToIdsV2(List<Hit<T>> hits, Function<String, I> converter) {
    List<I> docs = new ArrayList<>();
    for (Hit<T> hit : hits) {
      docs.add(converter.apply(hit.id()));
    }
    return docs;
  }
}
