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
package org.sonar.server.common.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.sonar.server.exceptions.BadRequestException;

import static java.lang.String.format;

/**
 * Free-text request values (rule parameters such as regexes and XPath expressions, descriptions, comments, URLs) can
 * match the SQL injection or path traversal signatures of a WAF body inspection, so the web UI sends them
 * base64url-encoded, which leaves only [A-Za-z0-9_-] in the request body.
 */
public final class EncodedParameters {

  private EncodedParameters() {
    // only static methods
  }

  /**
   * Decodes a base64url (RFC 4648 §5, padding optional) value back to its UTF-8 text.
   *
   * @throws BadRequestException naming {@code fieldName} when the value is not base64url or not UTF-8
   */
  public static String decode(String encoded, String fieldName) {
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(encoded);
      return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(decoded))
        .toString();
    } catch (IllegalArgumentException | CharacterCodingException e) {
      throw BadRequestException.create(format("Parameter '%s' is not valid base64url", fieldName));
    }
  }
}
