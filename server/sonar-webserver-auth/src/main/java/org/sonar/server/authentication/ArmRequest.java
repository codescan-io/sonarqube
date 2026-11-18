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
package org.sonar.server.authentication;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.http.HttpRequest;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Recognises requests from AutoRABIT ARM: they carry a key that only ARM holds. A request with a matching key skips
 * the organizations' IP allowlists altogether; authentication and permissions are not affected. Several keys may be
 * configured, comma-separated, so one can be replaced without downtime. Neither the configured keys nor the presented
 * header value is ever logged.
 */
@ServerSide
public class ArmRequest {

  public static final String ARM_KEY_HEADER = "X-CodeScan-ARM-Key";
  public static final String KEYS_PROPERTY = "sonar.codescan.armKeys";
  static final int MIN_KEY_LENGTH = 32;

  private static final Logger LOG = LoggerFactory.getLogger(ArmRequest.class);

  private final List<byte[]> keys;

  /**
   * @param processProperties the web server's startup properties. Not {@code Configuration}: it also returns values
   *        saved in the database, and a key saved there through the settings web service would let its holder past
   *        the address check of every organization.
   */
  public ArmRequest(Properties processProperties) {
    String configured = processProperties.getProperty(KEYS_PROPERTY, "");
    List<String> nonBlank = Arrays.stream(configured.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
    this.keys = nonBlank.stream().filter(k -> k.length() >= MIN_KEY_LENGTH).map(k -> k.getBytes(UTF_8)).toList();
    if (keys.size() < nonBlank.size()) {
      LOG.warn("Ignoring {} ARM key(s) shorter than {} characters", nonBlank.size() - keys.size(), MIN_KEY_LENGTH);
    }
  }

  /**
   * Compares the presented key with every configured key, without stopping at a match, and in time that depends on
   * the configured key's length only, so the timing reveals neither which key matched nor how much of one did.
   */
  public boolean isArm(HttpRequest request) {
    String presented = request.getHeader(ARM_KEY_HEADER);
    if (keys.isEmpty() || presented == null || presented.isEmpty()) {
      return false;
    }
    byte[] presentedBytes = presented.getBytes(UTF_8);
    boolean match = false;
    for (byte[] key : keys) {
      match |= MessageDigest.isEqual(key, presentedBytes);
    }
    return match;
  }
}
