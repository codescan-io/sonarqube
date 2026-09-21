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
package org.sonar.db.source;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;

class FileSourceDaoIT {

  @RegisterExtension
  private final DbTester dbTester = DbTester.create(System2.INSTANCE);

  private final DbSession dbSession = dbTester.getSession();
  private final FileSourceDao underTest = dbTester.getDbClient().fileSourceDao();

  @Test
  void selectLineHashes_returns_hashes_for_normal_file() {
    List<String> expected = List.of("abc123hash0000000", "def456hash1111111", "ghi789hash2222222");
    underTest.insert(dbSession, new FileSourceDto()
      .setUuid(Uuids.createFast())
      .setProjectUuid("PRJ_UUID")
      .setFileUuid("FILE_UUID")
      .setLineHashes(expected)
      .setCreatedAt(1500000000000L)
      .setUpdatedAt(1500000000001L));
    dbSession.commit();

    assertThat(underTest.selectLineHashes(dbSession, "FILE_UUID")).isEqualTo(expected);
  }

  @Test
  void selectLineHashes_returns_null_when_file_not_found() {
    assertThat(underTest.selectLineHashes(dbSession, "UNKNOWN_UUID")).isNull();
  }

  @Test
  void selectLineHashes_does_not_fail_when_line_hashes_is_null() {
    underTest.insert(dbSession, new FileSourceDto()
      .setUuid(Uuids.createFast())
      .setProjectUuid("PRJ_UUID")
      .setFileUuid("FILE_UUID")
      .setCreatedAt(1500000000000L)
      .setUpdatedAt(1500000000001L));
    dbSession.commit();

    assertThat(underTest.selectLineHashes(dbSession, "FILE_UUID")).isEmpty();
  }

  @Test
  void selectLineHashes_returns_all_hashes_for_large_file_spanning_multiple_chunks() {
    // 31,000 hashes x 33 chars each (32 + newline separator) = ~1.02 MB, which forces the
    // chunked read path (chunk size = 1 MB) and exercises the leftover-stitching logic.
    // Note: with 32-char hashes the chunk boundary always falls mid-hash (not on a newline).
    int lineCount = 31_000;
    List<String> expected = IntStream.range(0, lineCount)
      .mapToObj(i -> String.format("%032d", i))
      .toList();
    underTest.insert(dbSession, new FileSourceDto()
      .setUuid(Uuids.createFast())
      .setProjectUuid("PRJ_UUID")
      .setFileUuid("LARGE_FILE_UUID")
      .setLineHashes(expected)
      .setCreatedAt(1500000000000L)
      .setUpdatedAt(1500000000001L));
    dbSession.commit();

    assertThat(underTest.selectLineHashes(dbSession, "LARGE_FILE_UUID")).isEqualTo(expected);
  }

  @Test
  void selectLineHashes_returns_all_hashes_when_chunk_boundary_falls_exactly_on_newline() {
    // With hash length 24 the separator pattern is 25 chars (24 + newline).
    // 25 x 40,000 = 1,000,000 exactly, so the newline after entry 39,999 lands at
    // position 1,000,000 — the last character of chunk 1.  This is the edge case where
    // the previous (buggy) implementation would have appended a spurious empty string.
    int lineCount = 40_001; // one entry beyond the boundary so the newline is not at EOF
    List<String> expected = IntStream.range(0, lineCount)
      .mapToObj(i -> String.format("%024d", i))
      .toList();
    underTest.insert(dbSession, new FileSourceDto()
      .setUuid(Uuids.createFast())
      .setProjectUuid("PRJ_UUID")
      .setFileUuid("BOUNDARY_FILE_UUID")
      .setLineHashes(expected)
      .setCreatedAt(1500000000000L)
      .setUpdatedAt(1500000000001L));
    dbSession.commit();

    assertThat(underTest.selectLineHashes(dbSession, "BOUNDARY_FILE_UUID")).isEqualTo(expected);
  }
}
