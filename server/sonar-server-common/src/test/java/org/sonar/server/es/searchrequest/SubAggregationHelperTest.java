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

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.util.NamedValue;
import org.junit.Test;

import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.server.es.searchrequest.TopAggregationHelperTest.DEFAULT_BUCKET_SIZE;

public class SubAggregationHelperTest {
  private static final NamedValue<SortOrder> ES_BUILTIN_TIE_BREAKER = NamedValue.of("_key", SortOrder.Asc);
  private static final NamedValue<SortOrder> SQ_DEFAULT_BUCKET_ORDER = NamedValue.of("_count", SortOrder.Desc);

  private final Aggregation customSubAgg = AggregationBuilders.sum(s -> s);
  private final SubAggregationHelper underTest = new SubAggregationHelper();
  private final NamedValue<SortOrder> customOrder = NamedValue.of("_count", SortOrder.Asc);
  private final SubAggregationHelper underTestWithCustomSubAgg = new SubAggregationHelper(customSubAgg);
  private final SubAggregationHelper underTestWithCustomsSubAggAndOrder = new SubAggregationHelper(customSubAgg, customOrder);

  @Test
  public void buildTermsAggregation_adds_term_subaggregation_with_minDoc_1_and_default_sort() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);

    Stream.of(
      underTest,
      underTestWithCustomSubAgg)
      .forEach(t -> {
        Aggregation agg = t.buildTermsAggregation(aggName, topAggregation, null);

        TermsAggregation terms = agg.terms();
        assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());
        assertThat(terms.size()).isEqualTo(DEFAULT_BUCKET_SIZE);
        assertThat(terms.minDocCount()).isOne();
        assertThat(terms.order()).isEqualTo(List.of(SQ_DEFAULT_BUCKET_ORDER, ES_BUILTIN_TIE_BREAKER));

        if (t == underTestWithCustomSubAgg) {
          Aggregation subAgg = agg.aggregations().get(aggName);
          assertThat(subAgg).isEqualTo(customSubAgg);
        }
      });
  }

  @Test
  public void buildTermsAggregation_adds_custom_order_from_constructor() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);

    Aggregation agg = underTestWithCustomsSubAggAndOrder.buildTermsAggregation(aggName, topAggregation, null);

    TermsAggregation terms = agg.terms();
    assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());
    assertThat(terms.order()).isEqualTo(List.of(customOrder, ES_BUILTIN_TIE_BREAKER));

    Aggregation subAgg = agg.aggregations().get(aggName);
    assertThat(subAgg).isEqualTo(customSubAgg);
  }

  @Test
  public void buildTermsAggregation_adds_custom_sub_agg_from_constructor() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);

    Stream.of(
      underTestWithCustomSubAgg,
      underTestWithCustomsSubAggAndOrder)
      .forEach(t -> {
        Aggregation agg = t.buildTermsAggregation(aggName, topAggregation, null);

        TermsAggregation terms = agg.terms();
        assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());

        assertThat(agg.aggregations()).hasSize(1);
        Aggregation subAgg = agg.aggregations().get(aggName);
        assertThat(subAgg).isSameAs(customSubAgg);
      });
  }

  @Test
  public void buildTermsAggregation_adds_custom_size_if_TermTopAggregation_specifies_one() {
    String aggName = secure().nextAlphabetic(10);
    int customSize = 1 + new Random().nextInt(400);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);

    Stream.of(
      underTest,
      underTestWithCustomSubAgg,
      underTestWithCustomsSubAggAndOrder)
      .forEach(t -> {
        Aggregation agg = t.buildTermsAggregation(aggName, topAggregation, customSize);

        TermsAggregation terms = agg.terms();
        assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());
        assertThat(terms.size()).isEqualTo(customSize);

        if (t != underTest) {
          Aggregation subAgg = agg.aggregations().get(aggName);
          assertThat(subAgg).isEqualTo(customSubAgg);
        }
      });
  }

  @Test
  public void buildSelectedItemsAggregation_returns_empty_if_no_selected_item() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);

    Stream.of(
      underTest,
      underTestWithCustomSubAgg,
      underTestWithCustomsSubAggAndOrder)
      .forEach(t -> assertThat(t.buildSelectedItemsAggregation(aggName, topAggregation, new Object[0])).isEmpty());
  }

  @Test
  public void buildSelectedItemsAggregation_does_not_add_custom_order_from_constructor() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);
    String[] selected = randomNonEmptySelected();

    Aggregation agg = underTestWithCustomsSubAggAndOrder.buildSelectedItemsAggregation(aggName, topAggregation, selected)
      .get();

    TermsAggregation terms = agg.terms();
    assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());
    assertThat(terms.order()).isEqualTo(List.of(SQ_DEFAULT_BUCKET_ORDER, ES_BUILTIN_TIE_BREAKER));

    Aggregation subAgg = agg.aggregations().get(aggName + "_selected");
    assertThat(subAgg).isEqualTo(customSubAgg);
  }

  @Test
  public void buildSelectedItemsAggregation_adds_custom_sub_agg_from_constructor() {
    String aggName = secure().nextAlphabetic(10);
    SimpleFieldTopAggregationDefinition topAggregation = new SimpleFieldTopAggregationDefinition("bar", false);
    String[] selected = randomNonEmptySelected();

    Stream.of(
      underTestWithCustomSubAgg,
      underTestWithCustomsSubAggAndOrder)
      .forEach(t -> {
        Aggregation agg = t.buildSelectedItemsAggregation(aggName, topAggregation, selected).get();

        TermsAggregation terms = agg.terms();
        assertThat(terms.field()).isEqualTo(topAggregation.getFilterScope().getFieldName());

        assertThat(agg.aggregations()).hasSize(1);
        Aggregation subAgg = agg.aggregations().get(aggName + "_selected");
        assertThat(subAgg).isSameAs(customSubAgg);
      });
  }

  private static String[] randomNonEmptySelected() {
    return IntStream.range(0, 1 + new Random().nextInt(22))
      .mapToObj(i -> "selected_" + i)
      .toArray(String[]::new);
  }

}
