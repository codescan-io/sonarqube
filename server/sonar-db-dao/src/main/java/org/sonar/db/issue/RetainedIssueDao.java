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
    return Optional.ofNullable(mapper(dbSession).selectBranchContextByBranchUuid(branchUuid));
  }

  /**
   * Writes the branch row, or refreshes its key, name and target if they changed, so a renamed branch keeps its latest name
   * once deleted. Call it once per branch before its issues are retained.
   */
  public void writeBranch(DbSession dbSession, RetainBranchContextDto branch) {
    RetainedIssueMapper mapper = mapper(dbSession);
    if (branch.getRetainedBranchUuid() == null) {
      mapper.insertBranch(branch);
    } else if (!Objects.equals(branch.getRetainedBranchKey(), branch.getBranchKey())
      || !Objects.equals(branch.getRetainedBranchName(), branch.getBranchName())
      || !Objects.equals(branch.getRetainedTargetBranchKey(), branch.getTargetBranchKey())
      || !Objects.equals(branch.getRetainedTargetBranchUuid(), branch.getTargetBranchUuid())) {
      mapper.updateBranch(branch);
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
   * Retains the issues the purge of disabled components closed at {@code closedAt} on the branch's
   * {@code closedComponentUuids}, when its organization has the toggle on. Statements select them in the database, so
   * they are never loaded; a purge that closed nothing reads nothing.
   */
  public void insertIssuesClosedByPurge(DbSession dbSession, String branchUuid, Collection<String> closedComponentUuids,
    long closedAt) {
    if (closedComponentUuids.isEmpty()) {
      return;
    }
    Optional<RetainBranchContextDto> branch = selectBranchContext(dbSession, branchUuid)
      .filter(RetainBranchContextDto::isRetainEnabled);
    if (branch.isEmpty()) {
      return;
    }
    writeBranch(dbSession, branch.get());
    RetainedIssueMapper mapper = mapper(dbSession);
    int hotspot = RuleType.SECURITY_HOTSPOT.getDbConstant();
    executeLargeUpdates(closedComponentUuids, partition -> mapper.insertIssuesOfDisabledComponents(IssueStatus.FIXED.name(),
      null, closedAt, partition, hotspot));
  }

  /** Whether the branch has retained issues, so its deletion has something to keep. */
  public boolean hasRetainedIssues(DbSession dbSession, String branchUuid) {
    return mapper(dbSession).countRetainedIssuesOfBranch(branchUuid) > 0;
  }

  /** Drops the branch's retained row; for a branch deleted without retained issues. */
  public void deleteBranch(DbSession dbSession, String branchUuid) {
    mapper(dbSession).deleteBranch(branchUuid);
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

  /**
   * Refreshes the status, resolution and date of retained issues that stay Fixed under another status, such as one a user
   * resolved and the analysis then closed. The issue rows must already be written in {@code dbSession}; keys without a
   * row are ignored, so no issue is retained here.
   */
  public void updateIssueStatuses(DbSession dbSession, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    executeLargeUpdates(issueKeys, partition -> mapper(dbSession).updateIssueStatuses(partition));
  }

  /** Copies the comments of every retained issue of the branch. Call it before the branch is deleted. */
  public void copyCommentsOfBranch(DbSession dbSession, String branchUuid) {
    mapper(dbSession).copyCommentsOfBranch(branchUuid);
  }

  /**
   * Copies the date of the latest assignment of every assigned retained issue of the branch, which goes with its
   * 'issue_changes' rows. Call it before the branch is deleted.
   */
  public void copyAssignedDatesOfBranch(DbSession dbSession, String branchUuid) {
    mapper(dbSession).copyAssignedDatesOfBranch(branchUuid);
  }

  /**
   * Records that the branch is being deleted: sets 'deleted_at' and refreshes its name and, for a pull request, its
   * target from 'project_branches', so the row keeps the last values even when no issue was retained after a rename or
   * a retarget. Call it before the branch row is deleted. A branch without a row is ignored.
   */
  public void markBranchDeleted(DbSession dbSession, String branchUuid, long now) {
    mapper(dbSession).markBranchDeleted(branchUuid, now);
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
