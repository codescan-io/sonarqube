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

import java.util.List;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.ce.AnalyticsTaskDto;
import org.sonar.db.ce.CeQueueStatusCount;
import org.sonar.db.organization.OrganizationDto;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** The tasks behind {@code api/analytics/queue}: running ones first, then pending ones, oldest first. */
public class QueuedTasksAction implements AnalyticsWsAction {

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public QueuedTasksAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("queued_tasks")
      .setDescription("Background tasks of the organization's projects that are running or waiting right now. " +
        "For each, 'queueTimeMs' is how long it waited, or has been waiting so far.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("queued_tasks-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      Pagination pagination = AnalyticsJson.pagination(request);
      List<AnalyticsTaskDto> tasks = dbClient.ceQueueDao().selectForOrganizationAnalytics(dbSession, organization.getUuid(), pagination);
      int total = dbClient.ceQueueDao().countByStatusForOrganization(dbSession, organization.getUuid()).stream()
        .mapToInt(CeQueueStatusCount::getTotal)
        .sum();

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("tasks").beginArray();
        tasks.forEach(task -> AnalyticsJson.writeTask(json, task, now));
        json.endArray().endObject();
      }
    }
  }
}
