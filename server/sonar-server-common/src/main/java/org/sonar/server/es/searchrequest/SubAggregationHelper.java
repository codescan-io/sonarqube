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
package org.sonar.server.es.searchrequest;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.util.NamedValue;
import org.sonar.server.es.EsUtils;
import org.sonar.server.es.Facets;

import static java.lang.Math.max;
import static java.util.Optional.of;

public class SubAggregationHelper {
  private static final int TERM_AGGREGATION_MIN_DOC_COUNT = 1;
  private static final NamedValue<SortOrder> ORDER_BY_BUCKET_SIZE_DESC = NamedValue.of("_count", SortOrder.Desc);
  /** In some cases the user selects >15 items for one facet. In that case, we want to calculate the doc count for all of them (not just the first 15 items, which would be the
   * default for the TermsAggregation). */
  private static final int MAXIMUM_NUMBER_OF_SELECTED_ITEMS_WHOSE_DOC_COUNT_WILL_BE_CALCULATED = 50;
  private static final Collector<CharSequence, ?, String> PIPE_JOINER = Collectors.joining("|");

  @CheckForNull
  private final Aggregation subAggregation;
  private final NamedValue<SortOrder> order;

  public SubAggregationHelper() {
    this(null, null);
  }

  public SubAggregationHelper(@Nullable Aggregation subAggregation) {
    this(subAggregation, null);
  }

  public SubAggregationHelper(@Nullable Aggregation subAggregation, @Nullable NamedValue<SortOrder> order) {
    this.subAggregation = subAggregation;
    this.order = order == null ? ORDER_BY_BUCKET_SIZE_DESC : order;
  }

  public Aggregation buildTermsAggregation(String name,
    TopAggregationDefinition<?> topAggregation, @Nullable Integer numberOfTerms) {
    TermsAggregation.Builder termsAggregationBuilder = new TermsAggregation.Builder()
      .field(topAggregation.getFilterScope().getFieldName())
      .order(order)
      .minDocCount(TERM_AGGREGATION_MIN_DOC_COUNT);
    if (numberOfTerms != null) {
      termsAggregationBuilder.size(numberOfTerms);
    }
    Aggregation.Builder.ContainerBuilder aggregation = new Aggregation.Builder()
      .terms(termsAggregationBuilder.build());
    if (subAggregation != null) {
      aggregation.aggregations(name, subAggregation);
    }
    return aggregation.build();
  }

  public <T> Optional<Aggregation> buildSelectedItemsAggregation(String name, TopAggregationDefinition<?> topAggregation, T[] selected) {
    if (selected.length == 0) {
      return Optional.empty();
    }

    List<String> includes = Arrays.stream(selected)
      .filter(Objects::nonNull)
      .map(s -> EsUtils.escapeSpecialRegexChars(s.toString()))
      .toList();

    TermsAggregation termsAggregation = TermsAggregation.of(b -> b
      .field(topAggregation.getFilterScope().getFieldName())
      .size(max(MAXIMUM_NUMBER_OF_SELECTED_ITEMS_WHOSE_DOC_COUNT_WILL_BE_CALCULATED, includes.size()))
      .include(i -> i
        .terms(includes)
      )
    );
    Aggregation.Builder.ContainerBuilder selectedTerms = new Aggregation.Builder()
      .terms(termsAggregation);
    if (subAggregation != null) {
      selectedTerms.aggregations(name + Facets.SELECTED_SUB_AGG_NAME_SUFFIX, subAggregation);
    }
    return of(selectedTerms.build());
  }
}
