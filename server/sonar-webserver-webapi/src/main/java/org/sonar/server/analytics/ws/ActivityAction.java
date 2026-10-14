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

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.analytics.ws.ActivityAggregator.Stats;
import org.sonar.server.analytics.ws.ActivityAggregator.StatusCounts;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

public class ActivityAction implements AnalyticsWsAction {

  static final String PARAM_INTERVAL = "interval";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public ActivityAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("activity")
      .setDescription("Background task activity of the organization's projects over a period: how many analyses ran, " +
        "how they ended and how long they took, compared with the previous period of the same length.<br/>" +
        "Execution times are Compute Engine processing times of successful analyses; they do not include the time " +
        "spent by the scanner. Activity older than " + AnalyticsPeriod.MAX_DAYS + " days is purged by the platform.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("activity-example.json"))
      .setHandler(this);

    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_INTERVAL)
      .setDescription("Size of the timeline buckets")
      .setPossibleValues("day", "week")
      .setDefaultValue("day");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      Interval interval = Interval.valueOf(request.mandatoryParam(PARAM_INTERVAL).toUpperCase(Locale.ENGLISH));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, interval, system2.now());

      ActivityAggregator aggregator = new ActivityAggregator(period);
      dbClient.ceActivityDao().scrollForOrganizationAnalytics(dbSession, organization.getUuid(),
        aggregator.scrollFromMs(), aggregator.scrollToMs(), aggregator);
      int totalProjects = dbClient.projectDao().countProjectsByOrganization(dbSession, organization.getUuid());

      try (JsonWriter json = response.newJsonWriter()) {
        write(json, aggregator, totalProjects);
      }
    }
  }

  private static void write(JsonWriter json, ActivityAggregator aggregator, int totalProjects) {
    AnalyticsPeriod period = aggregator.current();
    json.beginObject();

    json.name("period").beginObject()
      .prop("from", period.from().toString())
      .prop("to", period.to().toString())
      .prop("timeZone", period.zone().getId())
      .prop("interval", period.interval().name().toLowerCase(Locale.ENGLISH))
      .endObject();
    json.prop("totalProjects", totalProjects);

    json.name("analyses");
    writeStats(json, aggregator.currentStats());

    AnalyticsPeriod previous = aggregator.previous();
    json.name("previous").beginObject()
      .prop("from", previous.from().toString())
      .prop("to", previous.to().toString())
      .name("analyses");
    writeStats(json, aggregator.previousStats());
    json.endObject();

    json.name("taskTypes").beginArray();
    for (Map.Entry<String, StatusCounts> entry : aggregator.taskTypes().entrySet()) {
      json.beginObject().prop("type", entry.getKey());
      writeCounts(json, entry.getValue());
      json.endObject();
    }
    json.endArray();

    json.name("targets").beginArray();
    for (Map.Entry<ActivityAggregator.Target, Stats> entry : aggregator.targets().entrySet()) {
      json.beginObject().prop("target", entry.getKey().name());
      writeCounts(json, entry.getValue());
      json.prop("projects", entry.getValue().projects())
        .endObject();
    }
    json.endArray();

    json.name("timeline").beginArray();
    List<LocalDate> starts = period.bucketStarts();
    List<Stats> buckets = aggregator.buckets();
    for (int i = 0; i < starts.size(); i++) {
      Stats bucket = buckets.get(i);
      json.beginObject().prop("date", starts.get(i).toString());
      writeCounts(json, bucket);
      json.prop("medianExecutionTimeMs", bucket.executionTimes().percentile(0.5))
        .prop("p90ExecutionTimeMs", bucket.executionTimes().percentile(0.9))
        .endObject();
    }
    json.endArray();

    json.endObject();
  }

  private static void writeStats(JsonWriter json, Stats stats) {
    json.beginObject();
    writeCounts(json, stats);
    json.prop("projects", stats.projects())
      .prop("avgExecutionTimeMs", stats.executionTimes().average())
      .prop("medianExecutionTimeMs", stats.executionTimes().percentile(0.5))
      .prop("p90ExecutionTimeMs", stats.executionTimes().percentile(0.9))
      .prop("avgQueueTimeMs", stats.queueTimes().average())
      .prop("medianQueueTimeMs", stats.queueTimes().percentile(0.5))
      .endObject();
  }

  private static void writeCounts(JsonWriter json, StatusCounts counts) {
    json.prop("total", counts.total())
      .prop("success", counts.success())
      .prop("failed", counts.failed())
      .prop("canceled", counts.canceled());
  }
}
