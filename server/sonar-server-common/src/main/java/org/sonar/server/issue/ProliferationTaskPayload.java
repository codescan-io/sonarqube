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
package org.sonar.server.issue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;


public record ProliferationTaskPayload(List<String> issueKeys, @CheckForNull String comment,
  @CheckForNull String exceptionReason) {

  /** Cannot collide with an issue key: {@code issues.kee} is twenty generated base64 characters. */
  private static final String MARKER = "---CSN-PROLIFERATION-V1---";
  private static final String COMMENT_PREFIX = "comment ";
  private static final String REASON_PREFIX = "reason ";

  /** Length of a value that is absent, as opposed to present and empty. */
  private static final int ABSENT = -1;

  private static final byte[] MARKER_BYTES = MARKER.getBytes(StandardCharsets.UTF_8);
  private static final byte NEWLINE = '\n';

  public static InputStream serialize(List<String> issueKeys, @Nullable String comment,
    @Nullable String exceptionReason) {

    String keys = String.join("\n", issueKeys);
    if (comment == null && exceptionReason == null) {
      // Byte for byte what the submitter wrote before this format existed. Most markings carry no text, and a
      // payload that never grew a header is one a reader of either version handles without a special case.
      return new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8));
    }

    byte[] commentBytes = toBytes(comment);
    byte[] reasonBytes = toBytes(exceptionReason);
    StringBuilder header = new StringBuilder();
    if (!keys.isEmpty()) {
      header.append(keys).append('\n');
    }
    header.append(MARKER).append('\n')
      .append(COMMENT_PREFIX).append(comment == null ? ABSENT : commentBytes.length).append('\n')
      .append(REASON_PREFIX).append(exceptionReason == null ? ABSENT : reasonBytes.length).append('\n');

    byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
    byte[] payload = new byte[headerBytes.length + commentBytes.length + reasonBytes.length];
    System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
    System.arraycopy(commentBytes, 0, payload, headerBytes.length, commentBytes.length);
    System.arraycopy(reasonBytes, 0, payload, headerBytes.length + commentBytes.length, reasonBytes.length);
    return new ByteArrayInputStream(payload);
  }

  /**
   * Reads a payload of either format. A payload with no marker is one written before this format existed: every
   * line of it is a key. A payload whose header does not parse is read the same way rather than rejected - the
   * keys are the part that matters, and losing a comment is better than losing the marking it belongs to.
   */
  public static ProliferationTaskPayload parse(byte[] raw) {
    int markerAt = indexOfMarkerLine(raw);
    if (markerAt < 0) {
      return new ProliferationTaskPayload(keysOf(raw, 0, raw.length), null, null);
    }
    try {
      Cursor cursor = new Cursor(raw, markerAt + MARKER_BYTES.length + 1);
      int commentLength = cursor.readCount(COMMENT_PREFIX);
      int reasonLength = cursor.readCount(REASON_PREFIX);
      return new ProliferationTaskPayload(keysOf(raw, 0, markerAt),
        cursor.readText(commentLength), cursor.readText(reasonLength));
    } catch (RuntimeException e) {
      return new ProliferationTaskPayload(keysOf(raw, 0, raw.length), null, null);
    }
  }

  private static byte[] toBytes(@Nullable String value) {
    return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * The marker on a line of its own. Only the first occurrence counts: a later one is inside the text, which is
   * read by length and never scanned.
   */
  private static int indexOfMarkerLine(byte[] raw) {
    for (int at = 0; at + MARKER_BYTES.length <= raw.length; at++) {
      if ((at == 0 || raw[at - 1] == NEWLINE) && regionMatches(raw, at)
        && (at + MARKER_BYTES.length == raw.length || raw[at + MARKER_BYTES.length] == NEWLINE)) {
        return at;
      }
    }
    return -1;
  }

  private static boolean regionMatches(byte[] raw, int at) {
    for (int i = 0; i < MARKER_BYTES.length; i++) {
      if (raw[at + i] != MARKER_BYTES[i]) {
        return false;
      }
    }
    return true;
  }

  private static List<String> keysOf(byte[] raw, int from, int to) {
    List<String> keys = new ArrayList<>();
    int lineStart = from;
    for (int at = from; at < to; at++) {
      if (raw[at] == NEWLINE) {
        addKey(keys, raw, lineStart, at);
        lineStart = at + 1;
      }
    }
    addKey(keys, raw, lineStart, to);
    return keys;
  }

  private static void addKey(List<String> keys, byte[] raw, int from, int to) {
    if (to <= from) {
      return;
    }
    String key = new String(raw, from, to - from, StandardCharsets.UTF_8).trim();
    if (!key.isEmpty()) {
      keys.add(key);
    }
  }

  /** A position in the payload, so the header is read line by line and the text by byte count. */
  private static final class Cursor {
    private final byte[] raw;
    private int at;

    private Cursor(byte[] raw, int at) {
      this.raw = raw;
      this.at = at;
    }

    private int readCount(String prefix) {
      String line = readLine();
      if (!line.startsWith(prefix)) {
        throw new IllegalStateException("Expected a line starting with '" + prefix + "' but got '" + line + "'");
      }
      return Integer.parseInt(line.substring(prefix.length()).trim());
    }

    private String readLine() {
      int end = at;
      while (end < raw.length && raw[end] != NEWLINE) {
        end++;
      }
      String line = new String(raw, at, end - at, StandardCharsets.UTF_8);
      at = end + 1;
      return line;
    }

    @CheckForNull
    private String readText(int length) {
      if (length == ABSENT) {
        return null;
      }
      if (length < 0 || at + length > raw.length) {
        throw new IllegalStateException("Payload is shorter than its header says");
      }
      String text = new String(raw, at, length, StandardCharsets.UTF_8);
      at += length;
      return text;
    }
  }
}
