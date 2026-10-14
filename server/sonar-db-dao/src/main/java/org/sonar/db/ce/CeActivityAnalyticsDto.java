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
import javax.annotation.Nullable;

/**
 * The few columns of a CE activity row that organization analytics aggregates. Rows are streamed,
 * so this stays small on purpose.
 */
public class CeActivityAnalyticsDto {
  // set by reflection by MyBatis
  private String entityUuid;
  private String taskType;
  private String status;
  private long submittedAt;
  private Long startedAt;
  private Long executionTimeMs;
  private String branchType;
  private Boolean isMain;

  public String getEntityUuid() {
    return entityUuid;
  }

  public String getTaskType() {
    return taskType;
  }

  public CeActivityDto.Status getStatus() {
    return CeActivityDto.Status.valueOf(status);
  }

  public long getSubmittedAt() {
    return submittedAt;
  }

  @CheckForNull
  public Long getStartedAt() {
    return startedAt;
  }

  @CheckForNull
  public Long getExecutionTimeMs() {
    return executionTimeMs;
  }

  /**
   * {@code BRANCH} or {@code PULL_REQUEST}, or null when the task is not attached to a branch
   * (for example a project-level task).
   */
  @CheckForNull
  public String getBranchType() {
    return branchType;
  }

  @CheckForNull
  public Boolean getIsMain() {
    return isMain;
  }

  public CeActivityAnalyticsDto setEntityUuid(String entityUuid) {
    this.entityUuid = entityUuid;
    return this;
  }

  public CeActivityAnalyticsDto setTaskType(String taskType) {
    this.taskType = taskType;
    return this;
  }

  public CeActivityAnalyticsDto setStatus(CeActivityDto.Status status) {
    this.status = status.name();
    return this;
  }

  public CeActivityAnalyticsDto setSubmittedAt(long submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  public CeActivityAnalyticsDto setStartedAt(@Nullable Long startedAt) {
    this.startedAt = startedAt;
    return this;
  }

  public CeActivityAnalyticsDto setExecutionTimeMs(@Nullable Long executionTimeMs) {
    this.executionTimeMs = executionTimeMs;
    return this;
  }

  public CeActivityAnalyticsDto setBranchType(@Nullable String branchType) {
    this.branchType = branchType;
    return this;
  }

  public CeActivityAnalyticsDto setIsMain(@Nullable Boolean isMain) {
    this.isMain = isMain;
    return this;
  }
}
