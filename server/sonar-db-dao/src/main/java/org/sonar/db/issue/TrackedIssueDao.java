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
package org.sonar.db.issue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.issue.IssueStatus;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;

/**
 * Access to 'cs_tracked_issue', the issue history that is kept for the lifetime of the project.
 *
 * <p>Reads are built for streaming: {@link #scroll} hands rows to a {@link ResultHandler} straight off a forward-only
 * cursor, so an export's heap cost is the fetch size and not the size of the result.
 */
public class TrackedIssueDao implements Dao {

  /**
   * Inserts the row. Callers reach this through {@link #selectCapturedIssueKeys} so that a retried task does not
   * attempt a duplicate: the unique index on the table is the backstop, not the first line of defence, because a
   * constraint violation inside a batch session aborts the whole batch.
   */
  public void insert(DbSession dbSession, TrackedIssueDto dto) {
    mapper(dbSession).insert(dto);
  }

  /**
   * Of {@code issueKeys}, those already held for this branch in this status. One indexed query per batch, not per
   * issue.
   */
  public Set<String> selectCapturedIssueKeys(DbSession dbSession, String projectUuid, String branchKey,
    IssueStatus issueStatus, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return emptySet();
    }
    return Set.copyOf(executeLargeInputs(issueKeys,
      partition -> mapper(dbSession).selectCapturedIssueKeys(projectUuid, branchKey, issueStatus.name(), partition)));
  }

  /**
   * Drops the rows for issues that have stopped holding {@code issueStatus} — an issue reopened because the problem
   * was never gone was not fixed, and must not be counted as one. The columns match the unique index, so the delete
   * is a direct lookup rather than a scan of the project's history. See CSN-253.
   */
  public void deleteCaptured(DbSession dbSession, String projectUuid, String branchKey, IssueStatus issueStatus,
    Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    executeLargeInputs(issueKeys, partition -> {
      mapper(dbSession).deleteCapturedIssues(projectUuid, branchKey, issueStatus.name(), partition);
      return emptyList();
    });
  }

  /**
   * Streams the project's history in capture order. A null or empty {@code branchKeys} streams every branch and pull
   * request the project ever had.
   */
  public void scroll(DbSession dbSession, String projectUuid, IssueStatus issueStatus,
    @Nullable Collection<String> branchKeys, ResultHandler<TrackedIssueDto> handler) {
    mapper(dbSession).scrollByProjectUuid(projectUuid, issueStatus.name(), branchKeys, handler);
  }

  /** Every branch and pull request the project holds history for, deleted ones included. */
  public List<TrackedIssueBranchDto> selectBranches(DbSession dbSession, String projectUuid) {
    return mapper(dbSession).selectBranchesByProjectUuid(projectUuid);
  }

  public long countByProjectUuid(DbSession dbSession, String projectUuid) {
    return mapper(dbSession).countByProjectUuid(projectUuid);
  }

  /**
   * Drops the project's whole history. Called when the project is deleted, and never when a branch or pull request is
   * deleted — surviving branch deletion is the point of the table. See CSN-256.
   */
  public void deleteByProjectUuid(DbSession dbSession, String projectUuid) {
    mapper(dbSession).deleteByProjectUuid(projectUuid);
  }

  private static TrackedIssueMapper mapper(DbSession dbSession) {
    return dbSession.getMapper(TrackedIssueMapper.class);
  }
}
