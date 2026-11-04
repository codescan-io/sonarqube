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
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.issue.IssueStatus;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;
import static org.sonar.db.DatabaseUtils.executeLargeUpdates;

/**
 * Access to the retained-issue tables: 'cs_retain_details' (branch), 'cs_issue_track_for_retain' (issue) and
 * 'cs_retain_issue_comments' (comments). Rows outlive the issue and its branch, and are removed only with the project.
 * See CSN-204.
 */
public class RetainedIssueDao implements Dao {

  /**
   * Writes the branch row, or refreshes its name and target if it exists, so a renamed branch keeps its latest name.
   * Select-then-write rather than an update count, because a batch session does not report update counts.
   */
  public void upsertBranch(DbSession dbSession, RetainDetailsDto dto) {
    RetainedIssueMapper mapper = mapper(dbSession);
    RetainDetailsDto existing = mapper.selectBranch(dto.getBranchUuid());
    if (existing == null) {
      mapper.insertBranch(dto);
    } else if (!Objects.equals(existing.getBranchKey(), dto.getBranchKey())
      || !Objects.equals(existing.getTargetBranchKey(), dto.getTargetBranchKey())) {
      mapper.updateBranch(dto);
    }
  }

  /** Every branch and pull request of the project that holds retained issues, deleted ones included. */
  public List<RetainedBranchDto> selectBranches(DbSession dbSession, String projectUuid) {
    return mapper(dbSession).selectBranchesByProjectUuid(projectUuid);
  }

  /**
   * Of {@code issueKeys}, those already retained in this status. One indexed query per partition, so callers check a
   * whole batch before inserting: a unique-key violation inside a batch session aborts the batch.
   */
  public Set<String> selectRetainedIssueKeys(DbSession dbSession, IssueStatus retainedStatus, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return emptySet();
    }
    return Set.copyOf(executeLargeInputs(issueKeys,
      partition -> mapper(dbSession).selectRetainedIssueKeys(retainedStatus.name(), partition)));
  }

  public void insertIssue(DbSession dbSession, IssueTrackForRetainDto dto) {
    mapper(dbSession).insertIssue(dto);
  }

  /** Drops the rows of issues that left {@code retainedStatus}: an issue that reopens was not fixed. */
  public void deleteRetainedIssues(DbSession dbSession, IssueStatus retainedStatus, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    executeLargeUpdates(issueKeys, partition -> mapper(dbSession).deleteRetainedIssues(retainedStatus.name(), partition));
  }

  /**
   * Streams the project's retained issues in fix order. A null or empty {@code branchUuids} streams every branch and
   * pull request the project ever had.
   */
  public void scrollIssues(DbSession dbSession, String projectUuid, IssueStatus retainedStatus,
    @Nullable Collection<String> branchUuids, ResultHandler<IssueTrackForRetainDto> handler) {
    mapper(dbSession).scrollIssuesByProjectUuid(projectUuid, retainedStatus.name(), branchUuids, handler);
  }

  public long countIssues(DbSession dbSession, String projectUuid) {
    return mapper(dbSession).countIssuesByProjectUuid(projectUuid);
  }

  /**
   * Copies the comments of those {@code issueKeys} that are retained. Call it before the issues are deleted; a
   * comment already copied is skipped.
   */
  public void copyCommentsOfIssues(DbSession dbSession, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    executeLargeUpdates(issueKeys, partition -> mapper(dbSession).copyCommentsOfIssueKeys(partition));
  }

  /** Copies the comments of every retained issue of the branch. Call it before the branch is deleted. */
  public void copyCommentsOfBranch(DbSession dbSession, String branchUuid) {
    mapper(dbSession).copyCommentsOfBranch(branchUuid);
  }

  public List<RetainIssueCommentDto> selectComments(DbSession dbSession, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return emptyList();
    }
    return executeLargeInputs(issueKeys, partition -> mapper(dbSession).selectCommentsByIssueKeys(partition));
  }

  /**
   * Drops everything retained for the project. Called when the project is deleted, never when a branch is: surviving
   * branch deletion is the point of these tables.
   */
  public void deleteByProjectUuid(DbSession dbSession, String projectUuid) {
    RetainedIssueMapper mapper = mapper(dbSession);
    mapper.deleteCommentsByProjectUuid(projectUuid);
    mapper.deleteIssuesByProjectUuid(projectUuid);
    mapper.deleteBranchesByProjectUuid(projectUuid);
  }

  private static RetainedIssueMapper mapper(DbSession dbSession) {
    return dbSession.getMapper(RetainedIssueMapper.class);
  }
}
