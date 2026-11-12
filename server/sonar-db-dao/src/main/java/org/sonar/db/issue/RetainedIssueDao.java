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
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;

import static org.sonar.db.DatabaseUtils.executeLargeUpdates;

/**
 * Access to the retained-issue tables: 'cs_retain_details' (branch), 'cs_issue_track_for_retain' (issue) and
 * 'cs_retain_issue_comments' (comments). Rows outlive the issue and its branch, and are removed only with the project.
 * See CSN-204.
 */
public class RetainedIssueDao implements Dao {

  /** The branch, its organization's toggle and its retained branch row, or empty when the branch does not exist. */
  public Optional<RetainBranchContextDto> selectBranchContext(DbSession dbSession, String branchUuid) {
    return Optional.ofNullable(mapper(dbSession).selectBranchContext(branchUuid));
  }

  /**
   * Writes the branch row, or refreshes its name and target if they changed, so a renamed branch keeps its latest name
   * once deleted. Call it once per branch before its issues are retained.
   */
  public void writeBranch(DbSession dbSession, RetainBranchContextDto branch) {
    RetainedIssueMapper mapper = mapper(dbSession);
    if (branch.getRetainedBranchUuid() == null) {
      mapper.insertBranch(branch.toRetainDetails());
    } else if (!Objects.equals(branch.getRetainedBranchKey(), branch.getBranchKey())
      || !Objects.equals(branch.getRetainedTargetBranchKey(), branch.getTargetBranchKey())) {
      mapper.updateBranch(branch.toRetainDetails());
    }
  }

  /**
   * Retains issues of one branch that entered {@code retainedStatus}, copying their columns from 'issues' in one
   * statement per partition of keys. The issue rows must already be written in {@code dbSession}. Issues already
   * retained in that status keep their row, so the first time they entered it is kept; security hotspots and, for
   * FIXED, issues whose row is not Fixed are skipped. The caller checks the toggle and writes the branch row.
   *
   * @param statusMarkedByUuid the user who made the change, null when the analysis or the purge made it
   * @param now                when the change was made; used for issues without a close date, such as those a user
   *                           resolved
   */
  public void insertIssues(DbSession dbSession, String branchUuid, IssueStatus retainedStatus,
    @Nullable String statusMarkedByUuid, long now, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    RetainedIssueMapper mapper = mapper(dbSession);
    int hotspot = RuleType.SECURITY_HOTSPOT.getDbConstant();
    executeLargeUpdates(issueKeys, partition -> mapper.insertIssuesFromIssues(branchUuid, retainedStatus.name(),
      statusMarkedByUuid, now, partition, hotspot));
  }

  /**
   * Retains one set of issues of a branch when its organization has the toggle on: reads the branch, writes its row and
   * inserts. For callers that retain once, such as the purge; callers that retain repeatedly read the branch once.
   */
  public void insertIssuesOfBranch(DbSession dbSession, String branchUuid, IssueStatus retainedStatus,
    @Nullable String statusMarkedByUuid, long now, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    Optional<RetainBranchContextDto> branch = selectBranchContext(dbSession, branchUuid);
    if (branch.isEmpty() || !branch.get().isRetainEnabled()) {
      return;
    }
    writeBranch(dbSession, branch.get());
    insertIssues(dbSession, branchUuid, retainedStatus, statusMarkedByUuid, now, issueKeys);
  }

  /**
   * Drops the rows of issues that left {@code retainedStatus}: an issue that reopens was not fixed. For FIXED, a row
   * whose issue is still Fixed is kept.
   */
  public void deleteRetainedIssues(DbSession dbSession, IssueStatus retainedStatus, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    executeLargeUpdates(issueKeys, partition -> mapper(dbSession).deleteRetainedIssues(retainedStatus.name(), partition));
  }

  /** Copies the comments of every retained issue of the branch. Call it before the branch is deleted. */
  public void copyCommentsOfBranch(DbSession dbSession, String branchUuid) {
    mapper(dbSession).copyCommentsOfBranch(branchUuid);
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
