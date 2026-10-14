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

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/** Finished tasks of an organization's projects submitted in [{@code fromSubmittedAt}, {@code toSubmittedAt}). */
public class AnalyticsTaskQuery {

  public enum Sort {
    /** Most recent first */
    SUBMITTED_AT,
    /** Slowest first, tasks without an execution time last */
    EXECUTION_TIME,
    /** Longest wait in the queue first, tasks that never started last */
    QUEUE_TIME
  }

  private final String organizationUuid;
  private final long fromSubmittedAt;
  private final long toSubmittedAt;
  private String taskType;
  private List<String> statuses = List.of();
  private Sort sort = Sort.SUBMITTED_AT;

  public AnalyticsTaskQuery(String organizationUuid, long fromSubmittedAt, long toSubmittedAt) {
    this.organizationUuid = requireNonNull(organizationUuid);
    this.fromSubmittedAt = fromSubmittedAt;
    this.toSubmittedAt = toSubmittedAt;
  }

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  public long getFromSubmittedAt() {
    return fromSubmittedAt;
  }

  public long getToSubmittedAt() {
    return toSubmittedAt;
  }

  @CheckForNull
  public String getTaskType() {
    return taskType;
  }

  public AnalyticsTaskQuery setTaskType(@Nullable String taskType) {
    this.taskType = taskType;
    return this;
  }

  public List<String> getStatuses() {
    return statuses;
  }

  public AnalyticsTaskQuery setStatuses(List<String> statuses) {
    this.statuses = List.copyOf(statuses);
    return this;
  }

  public Sort getSort() {
    return sort;
  }

  public AnalyticsTaskQuery setSort(Sort sort) {
    this.sort = requireNonNull(sort);
    return this;
  }
}
