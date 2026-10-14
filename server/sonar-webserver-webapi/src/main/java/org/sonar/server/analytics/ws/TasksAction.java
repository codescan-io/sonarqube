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
import org.sonar.db.ce.AnalyticsTaskQuery;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** The finished tasks behind the figures of {@code api/analytics/activity}. */
public class TasksAction implements AnalyticsWsAction {

  static final String PARAM_STATUSES = "statuses";
  static final String PARAM_TYPE = "type";
  static final String PARAM_SORT = "s";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public TasksAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("tasks")
      .setDescription("Finished background tasks of the organization's projects submitted in a period.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("tasks-example.json"))
      .setHandler(this);

    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_STATUSES)
      .setDescription("Comma-separated list of statuses. All statuses when omitted.")
      .setPossibleValues(CeActivityDto.Status.values());
    action.createParam(PARAM_TYPE)
      .setDescription("Task type")
      .setDefaultValue(CeTaskTypes.REPORT)
      .setExampleValue(CeTaskTypes.REPORT);
    action.createParam(PARAM_SORT)
      .setDescription("Order of the tasks: most recent first, slowest first, or longest wait in the queue first")
      .setPossibleValues("submittedAt", "executionTime", "queueTime")
      .setDefaultValue("submittedAt");
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, Interval.DAY, now);
      List<String> statuses = request.paramAsStrings(PARAM_STATUSES);
      AnalyticsTaskQuery query = new AnalyticsTaskQuery(organization.getUuid(), period.startMs(), period.endMs())
        .setTaskType(request.mandatoryParam(PARAM_TYPE))
        .setStatuses(statuses == null ? List.of() : statuses)
        .setSort(toSort(request.mandatoryParam(PARAM_SORT)));
      Pagination pagination = AnalyticsJson.pagination(request);

      List<AnalyticsTaskDto> tasks = dbClient.ceActivityDao().selectForOrganizationAnalytics(dbSession, query, pagination);
      int total = dbClient.ceActivityDao().countForOrganizationAnalytics(dbSession, query);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("tasks").beginArray();
        tasks.forEach(task -> AnalyticsJson.writeTask(json, task, now));
        json.endArray().endObject();
      }
    }
  }

  private static AnalyticsTaskQuery.Sort toSort(String sort) {
    return switch (sort) {
      case "executionTime" -> AnalyticsTaskQuery.Sort.EXECUTION_TIME;
      case "queueTime" -> AnalyticsTaskQuery.Sort.QUEUE_TIME;
      default -> AnalyticsTaskQuery.Sort.SUBMITTED_AT;
    };
  }
}
