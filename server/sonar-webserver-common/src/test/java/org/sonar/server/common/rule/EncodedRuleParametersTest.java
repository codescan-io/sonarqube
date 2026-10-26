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
package org.sonar.server.common.rule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonar.server.exceptions.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncodedRuleParametersTest {

  private static final String FIELD = "encodedParams";

  @ParameterizedTest
  @ValueSource(strings = {
    "//Literal[(@StringLiteral = 'true' or (not(@StringLiteral)))][not(../following-sibling::PrimarySuffix)]",
    "^(?:Test\\w+|\\w+Test)$",
    "matches(@Image, '(?i)^[\"\\x27]?(authorization|x-api-key)[\"\\x27]?$')",
    "key1=\"v;1\";key2=\"v=2\"",
    "Évitez ç ü — 日本語 😀",
    "a",
    "ab",
    "abc"
  })
  void decode_whenValueIsUnpaddedBase64Url_returnsOriginalText(String original) {
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(original.getBytes(StandardCharsets.UTF_8));

    assertThat(encoded).matches("[A-Za-z0-9_-]*");
    assertThat(EncodedRuleParameters.decode(encoded, FIELD)).isEqualTo(original);
  }

  @Test
  void decode_whenValueIsPadded_returnsOriginalText() {
    String encoded = Base64.getUrlEncoder().encodeToString("ab".getBytes(StandardCharsets.UTF_8));

    assertThat(encoded).endsWith("=");
    assertThat(EncodedRuleParameters.decode(encoded, FIELD)).isEqualTo("ab");
  }

  @Test
  void decode_whenValueIsEmpty_returnsEmptyText() {
    assertThat(EncodedRuleParameters.decode("", FIELD)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    // standard alphabet: "?>?" is "Pz4/" and "??>" is "Pz8+"; base64url uses '_' and '-' instead
    "Pz4/",
    "Pz8+",
    "not base64!",
    "YWJj ZGVm",
    "YWJj\nZGVm",
    // a single trailing character cannot complete a byte
    "YWJjZ",
    "YQ===",
    // the plain form is not accepted as encoded
    "xpath=\"//A\""
  })
  void decode_whenValueIsNotBase64Url_shouldFail(String encoded) {
    assertThatThrownBy(() -> EncodedRuleParameters.decode(encoded, FIELD))
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Parameter 'encodedParams' is not valid base64url");
  }

  @Test
  void decode_whenBytesAreNotUtf8_shouldFail() {
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[] {(byte) 0xC3, (byte) 0x28});

    assertThatThrownBy(() -> EncodedRuleParameters.decode(encoded, FIELD))
      .isInstanceOf(BadRequestException.class)
      .hasMessage("Parameter 'encodedParams' is not valid base64url");
  }

  @Test
  void decode_whenItFails_namesTheGivenField() {
    assertThatThrownBy(() -> EncodedRuleParameters.decode("Pz4/", "encodedParameters"))
      .hasMessage("Parameter 'encodedParameters' is not valid base64url");
  }
}
