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
package org.sonar.db.ce;

import javax.annotation.CheckForNull;

/**
 * One background task of an organization, finished ({@code ce_activity}) or queued ({@code ce_queue}),
 * with the project and branch it ran on.
 */
public class AnalyticsTaskDto {
  // set by reflection by MyBatis
  private String uuid;
  private String taskType;
  private String status;
  private String projectKey;
  private String projectName;
  private String branchKey;
  private String branchType;
  private Boolean isMain;
  private long submittedAt;
  private Long startedAt;
  private Long executedAt;
  private Long executionTimeMs;
  private String errorMessage;

  public String getUuid() {
    return uuid;
  }

  public String getTaskType() {
    return taskType;
  }

  /** A {@link CeActivityDto.Status} for finished tasks, a {@link CeQueueDto.Status} for queued ones. */
  public String getStatus() {
    return status;
  }

  public String getProjectKey() {
    return projectKey;
  }

  @CheckForNull
  public String getProjectName() {
    return projectName;
  }

  /** Branch name, or pull request id. Null when the branch was deleted since. */
  @CheckForNull
  public String getBranchKey() {
    return branchKey;
  }

  @CheckForNull
  public String getBranchType() {
    return branchType;
  }

  @CheckForNull
  public Boolean getIsMain() {
    return isMain;
  }

  public long getSubmittedAt() {
    return submittedAt;
  }

  @CheckForNull
  public Long getStartedAt() {
    return startedAt;
  }

  @CheckForNull
  public Long getExecutedAt() {
    return executedAt;
  }

  @CheckForNull
  public Long getExecutionTimeMs() {
    return executionTimeMs;
  }

  @CheckForNull
  public String getErrorMessage() {
    return errorMessage;
  }
}
