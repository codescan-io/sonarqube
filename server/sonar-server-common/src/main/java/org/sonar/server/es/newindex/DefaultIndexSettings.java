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
package org.sonar.server.es.newindex;

import java.util.Arrays;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.TimeUnit;
import co.elastic.clients.elasticsearch.indices.IndexSettings;

public class DefaultIndexSettings {

  /** Minimum length of ngrams. */
  public static final int MINIMUM_NGRAM_LENGTH = 2;
  /** Maximum length of ngrams. */
  public static final int MAXIMUM_NGRAM_LENGTH = 15;

  /** Pattern, that splits the user search input **/
  public static final String SEARCH_TERM_TOKENIZER_PATTERN = "[\\s]+";

  public static final String ANALYSIS = "index.analysis";
  public static final String DELIMITER = ".";

  public static final String TOKENIZER = "tokenizer";
  public static final String FILTER = "filter";
  public static final String CHAR_FILTER = "char_filter";
  public static final String ANALYZER = "analyzer";
  public static final String SEARCH_ANALYZER = "search_analyzer";

  public static final String TYPE = "type";
  public static final String INDEX = "index";
  public static final String INDEX_SEARCHABLE = "true";
  public static final String INDEX_NOT_SEARCHABLE = "false";
  public static final String FIELD_TYPE_TEXT = "text";
  public static final String FIELD_TYPE_KEYWORD = "keyword";
  public static final String NORMS = "norms";
  public static final String STORE = "store";
  public static final String FIELD_FIELDDATA = "fielddata";
  public static final String FIELDDATA_ENABLED = "true";
  public static final String FIELD_TERM_VECTOR = "term_vector";
  public static final String STANDARD = "standard";
  public static final String PATTERN = "pattern";
  public static final String CUSTOM = "custom";
  public static final String KEYWORD = "keyword";
  public static final String CLASSIC = "classic";
  public static final Refresh REFRESH_IMMEDIATE = Refresh.WaitFor;
  public static final Refresh REFRESH_NONE = Refresh.False;

  public static final String TRUNCATE = "truncate";

  public static final String SUB_FIELD_DELIMITER = ".";
  public static final String TRIM = "trim";
  public static final String LOWERCASE = "lowercase";
  public static final String WHITESPACE = "whitespace";
  public static final String STOP = "stop";
  public static final String ASCIIFOLDING = "asciifolding";
  public static final String PORTER_STEM = "porter_stem";
  public static final String MIN_GRAM = "min_gram";
  public static final String MAX_GRAM = "max_gram";
  public static final String LENGTH = "length";
  public static final String HTML_STRIP = "html_strip";
  public static final String DEFAULT_NUMBER_OF_SHARDS = "1";
  public static final int DEFAULT_REFRESH_INTERVAL_SECONDS = 30;

  private DefaultIndexSettings() {
    // only static stuff
  }

  public static IndexSettings.Builder defaults() {
    IndexSettings.Builder builder = new IndexSettings.Builder()
      .numberOfShards(DEFAULT_NUMBER_OF_SHARDS)
      .refreshInterval(t -> t.time(DEFAULT_REFRESH_INTERVAL_SECONDS, TimeUnit.Seconds))
    ;

    Arrays.stream(DefaultIndexSettingsElement.values())
      .map(DefaultIndexSettingsElement::settings)
      .map(IndexSettings::otherSettings)
      .flatMap(map -> map.entrySet().stream())
      .forEach(entry -> builder.otherSettings(entry.getKey(), entry.getValue()));

    return builder;
  }
}
