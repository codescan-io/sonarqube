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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.AnalyticsProjectTaskStatusDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.organization.OrganizationDto;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * The background task status of every project of the organization in one call, as {@code api/ce/component} gives it for one
 * project: a running task, else a pending one, else the latest finished one.
 */
public class ProjectTaskStatusesAction implements AnalyticsWsAction {

  private static final Comparator<AnalyticsProjectTaskStatusDto> LATEST = Comparator
    .comparingLong(AnalyticsProjectTaskStatusDto::getCreatedAt)
    .thenComparing(AnalyticsProjectTaskStatusDto::getUuid);

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;

  public ProjectTaskStatusesAction(DbClient dbClient, AnalyticsWsSupport support) {
    this.dbClient = dbClient;
    this.support = support;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("project_task_statuses")
      .setDescription("Background task status of every project of the organization: the status of its running task, else of its " +
        "oldest pending one, else of its latest finished one. Projects that never had a task are left out.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("project_task_statuses-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));

      Map<String, AnalyticsProjectTaskStatusDto> latest = new HashMap<>();
      dbClient.ceActivityDao().selectCurrentStatusesForOrganization(dbSession, organization.getUuid())
        .forEach(task -> latest.merge(task.getProjectKey(), task, (a, b) -> LATEST.compare(a, b) >= 0 ? a : b));

      Map<String, String> statuses = new TreeMap<>();
      latest.forEach((projectKey, task) -> statuses.put(projectKey, task.getStatus()));
      for (AnalyticsProjectTaskStatusDto task : dbClient.ceQueueDao().selectStatusesForOrganization(dbSession, organization.getUuid())) {
        String inProgress = CeQueueDto.Status.IN_PROGRESS.name();
        if (task.getStatus().equals(inProgress) || !inProgress.equals(statuses.get(task.getProjectKey()))) {
          statuses.put(task.getProjectKey(), task.getStatus());
        }
      }

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject().name("projects").beginArray();
        statuses.forEach((projectKey, status) -> json.beginObject()
          .prop("key", projectKey)
          .prop("status", status)
          .endObject());
        json.endArray().endObject();
      }
    }
  }
}
