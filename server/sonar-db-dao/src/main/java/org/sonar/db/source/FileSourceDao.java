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

import com.google.common.base.Splitter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.db.Dao;
import org.sonar.db.DatabaseUtils;
import org.sonar.db.DbSession;

import static org.sonar.db.DatabaseUtils.toUniqueAndSortedPartitions;

public class FileSourceDao implements Dao {

  private static final Splitter END_OF_LINE_SPLITTER = Splitter.on('\n');

  @CheckForNull
  public FileSourceDto selectByFileUuid(DbSession session, String fileUuid) {
    return mapper(session).selectByFileUuid(fileUuid);
  }

  @CheckForNull
  public LineHashVersion selectLineHashesVersion(DbSession dbSession, String fileUuid) {
    Integer version = mapper(dbSession).selectLineHashesVersion(fileUuid);
    return version == null ? null : LineHashVersion.valueOf(version);
  }

  /**
   * The returning object doesn't contain all fields filled. For example, binary data is not loaded.
   */
  public void scrollFileHashesByProjectUuid(DbSession dbSession, String projectUuid, ResultHandler<FileHashesDto> rowHandler) {
    mapper(dbSession).scrollHashesForProject(projectUuid, rowHandler);
  }

  // The line_hashes column stores one MD5 hash per line, newline-separated.  For very large
  // files (e.g. 250k-line Salesforce Profiles) the value can exceed 8 MB.  Loading it in a
  // single JDBC fetch causes OutOfMemoryError inside the PostgreSQL driver's receive buffer
  // before Java code ever sees the data (ZD-264349).
  //
  // Fix: fetch the column in 1 MB chunks using SQL SUBSTRING so the JDBC driver never needs
  // to buffer more than ~1 MB at a time.  Each chunk is parsed immediately, keeping peak heap
  // usage proportional to the chunk size rather than the total column size.  All hashes are
  // still returned, so issue history is fully preserved.
  //
  // Performance: for normal files whose line_hashes fit in one chunk the loop runs exactly
  // once — identical query count to the original implementation.
  private static final int LINE_HASHES_CHUNK_SIZE = 1_000_000; // characters per SQL SUBSTRING fetch

  @CheckForNull
  public List<String> selectLineHashes(DbSession dbSession, String fileUuid) {
    Connection connection = dbSession.getConnection();
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      pstmt = connection.prepareStatement(
        "SELECT substring(line_hashes FROM ? FOR ?) FROM file_sources WHERE file_uuid=?");

      List<String> hashes = new ArrayList<>();
      String leftover = "";

      for (int offset = 1; ; offset += LINE_HASHES_CHUNK_SIZE) {
        pstmt.setInt(1, offset);
        pstmt.setInt(2, LINE_HASHES_CHUNK_SIZE);
        pstmt.setString(3, fileUuid);
        rs = pstmt.executeQuery();
        boolean rowExists = rs.next();
        String chunk = rowExists ? rs.getString(1) : null;
        DatabaseUtils.closeQuietly(rs);
        rs = null;

        if (!rowExists) {
          // File row absent — only expected on the very first iteration.
          return offset == 1 ? null : hashes;
        }
        if (chunk == null) {
          // line_hashes IS NULL in the database.
          return Collections.emptyList();
        }
        if (chunk.isEmpty()) {
          // substring() past end of string — no more data.
          break;
        }

        // Split on newlines: every element except the last is a complete hash.
        // The last element is either a partial hash (no newline yet) or "" (chunk
        // ended exactly on a newline).  Either way it becomes the leftover for the
        // next iteration, and is added after the loop once all chunks are read.
        List<String> lines = END_OF_LINE_SPLITTER.splitToList(leftover + chunk);
        for (int i = 0; i < lines.size() - 1; i++) {
          hashes.add(lines.get(i));
        }
        leftover = lines.get(lines.size() - 1);

        if (chunk.length() < LINE_HASHES_CHUNK_SIZE) {
          // Received less than requested — this was the last chunk.
          break;
        }
      }

      if (!leftover.isEmpty()) {
        hashes.add(leftover);
      }
      return hashes;
    } catch (SQLException e) {
      throw new IllegalStateException("Fail to read FILE_SOURCES.LINE_HASHES of file " + fileUuid, e);
    } finally {
      DatabaseUtils.closeQuietly(rs);
      DatabaseUtils.closeQuietly(pstmt);
      DatabaseUtils.closeQuietly(connection);
    }
  }

  /**
   * Scroll line hashes of all <strong>enabled</strong> components (should be files, but not enforced) with specified
   * uuids in no specific order with 'SOURCE' source and a non null path.
   */
  public void scrollLineHashes(DbSession dbSession, Collection<String> fileUUids, ResultHandler<LineHashesWithUuidDto> rowHandler) {
    for (List<String> fileUuidsPartition : toUniqueAndSortedPartitions(fileUUids)) {
      mapper(dbSession).scrollLineHashes(fileUuidsPartition, rowHandler);
    }
  }

  public void insert(DbSession session, FileSourceDto dto) {
    mapper(session).insert(dto);
  }

  public void update(DbSession session, FileSourceDto dto) {
    mapper(session).update(dto);
  }

  private static FileSourceMapper mapper(DbSession session) {
    return session.getMapper(FileSourceMapper.class);
  }
}
