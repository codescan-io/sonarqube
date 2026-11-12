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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.ibatis.annotations.Param;

public interface RetainedIssueMapper {

  @CheckForNull
  RetainBranchContextDto selectBranchContext(@Param("branchUuid") String branchUuid);

  void insertBranch(@Param("dto") RetainDetailsDto dto);

  void updateBranch(@Param("dto") RetainDetailsDto dto);

  /**
   * @param issueKeys keys of issues of {@code branchUuid}, at most one partition of
   *                  {@link org.sonar.db.DatabaseUtils#PARTITION_SIZE_FOR_ORACLE}
   */
  void insertIssuesFromIssues(@Param("branchUuid") String branchUuid, @Param("retainedStatus") String retainedStatus,
    @Nullable @Param("statusMarkedByUuid") String statusMarkedByUuid, @Param("now") long now,
    @Param("issueKeys") Collection<String> issueKeys, @Param("excludedIssueType") int excludedIssueType);

  void deleteRetainedIssues(@Param("retainedStatus") String retainedStatus, @Param("issueKeys") Collection<String> issueKeys);

  void copyCommentsOfBranch(@Param("branchUuid") String branchUuid);

  void deleteCommentsByProjectUuid(@Param("projectUuid") String projectUuid);

  void deleteIssuesByProjectUuid(@Param("projectUuid") String projectUuid);

  void deleteBranchesByProjectUuid(@Param("projectUuid") String projectUuid);
}
