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
import javax.annotation.Nullable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

public interface TrackedIssueMapper {

  void insert(@Param("dto") TrackedIssueDto dto);

  List<String> selectCapturedIssueKeys(@Param("projectUuid") String projectUuid, @Param("branchKey") String branchKey,
    @Param("issueStatus") String issueStatus, @Param("issueKeys") Collection<String> issueKeys);

  void scrollByProjectUuid(@Param("projectUuid") String projectUuid, @Param("issueStatus") String issueStatus,
    @Nullable @Param("branchKeys") Collection<String> branchKeys, ResultHandler<TrackedIssueDto> handler);

  List<TrackedIssueBranchDto> selectBranchesByProjectUuid(@Param("projectUuid") String projectUuid);

  void deleteCapturedIssues(@Param("projectUuid") String projectUuid, @Param("branchKey") String branchKey,
    @Param("issueStatus") String issueStatus, @Param("issueKeys") Collection<String> issueKeys);

  long countByProjectUuid(@Param("projectUuid") String projectUuid);

  void deleteByProjectUuid(@Param("projectUuid") String projectUuid);
}
