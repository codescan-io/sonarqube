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
package org.sonar.server.analytics.ws;

import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.Pagination;
import org.sonar.db.ce.AnalyticsTaskDto;
import org.sonar.server.analytics.ws.ActivityAggregator.Target;

import static org.sonar.api.utils.DateUtils.formatDateTime;

/** JSON pieces shared by the drill-down actions. */
class AnalyticsJson {

  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 100;

  private AnalyticsJson() {
    // static only
  }

  static void definePaging(WebService.NewAction action) {
    action.addPagingParams(DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  }

  static Pagination pagination(Request request) {
    return Pagination.forPage(request.mandatoryParamAsInt(WebService.Param.PAGE))
      .andSize(request.mandatoryParamAsInt(WebService.Param.PAGE_SIZE));
  }

  static void writePaging(JsonWriter json, Pagination pagination, int total) {
    json.name("paging").beginObject()
      .prop("pageIndex", pagination.getOffset() / pagination.getPageSize() + 1)
      .prop("pageSize", pagination.getPageSize())
      .prop("total", total)
      .endObject();
  }

  /**
   * @param now used as the end of the wait of a task still pending
   */
  static void writeTask(JsonWriter json, AnalyticsTaskDto task, long now) {
    Long startedAt = task.getStartedAt();
    long queueEnd = startedAt == null ? now : startedAt;
    json.beginObject()
      .prop("id", task.getUuid())
      .prop("type", task.getTaskType())
      .prop("status", task.getStatus())
      .prop("projectKey", task.getProjectKey())
      .prop("projectName", task.getProjectName())
      .prop("branch", task.getBranchKey())
      .prop("target", Target.of(task.getIsMain(), task.getBranchType()).name())
      .prop("submittedAt", formatDateTime(task.getSubmittedAt()))
      .prop("startedAt", formatNullable(startedAt))
      .prop("executedAt", formatNullable(task.getExecutedAt()))
      .prop("executionTimeMs", task.getExecutionTimeMs());
    // a finished task that never started (canceled while pending) has no queue time
    if (startedAt != null || task.getExecutedAt() == null) {
      json.prop("queueTimeMs", Math.max(0, queueEnd - task.getSubmittedAt()));
    }
    json.prop("errorMessage", task.getErrorMessage())
      .endObject();
  }

  @Nullable
  private static String formatNullable(@Nullable Long time) {
    return time == null ? null : formatDateTime(time);
  }
}
