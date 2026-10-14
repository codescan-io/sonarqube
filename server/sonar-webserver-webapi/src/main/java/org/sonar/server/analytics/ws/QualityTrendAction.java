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
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.QualityTrendAggregator.Point;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** How the quality of the organization's main branches evolved over a period. */
public class QualityTrendAction implements AnalyticsWsAction {

  static final String PARAM_INTERVAL = "interval";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public QualityTrendAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("quality_trend")
      .setDescription("State of the organization's main branches at the end of every day or week of a period: how many projects " +
        "pass or fail their quality gate, and how many open issues, vulnerabilities and blocker issues they have. " +
        "A project counts from its first analysis in the year before the period.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("quality_trend-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_INTERVAL)
      .setDescription("Size of the buckets")
      .setPossibleValues("day", "week")
      .setDefaultValue("week");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      Interval interval = Interval.valueOf(request.mandatoryParam(PARAM_INTERVAL).toUpperCase(Locale.ENGLISH));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, interval, system2.now());

      QualityTrendAggregator aggregator = new QualityTrendAggregator(period);
      dbClient.projectMeasureDao().scrollMainBranchHistoryForOrganization(dbSession, organization.getUuid(),
        period.startMs() - QualityTrendAggregator.LOOKBACK_MS, period.endMs(), QualityTrendAggregator.METRIC_KEYS, aggregator);

      List<LocalDate> starts = period.bucketStarts();
      List<Point> points = aggregator.points();
      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        json.name("period").beginObject()
          .prop("from", period.from().toString())
          .prop("to", period.to().toString())
          .prop("timeZone", period.zone().getId())
          .prop("interval", period.interval().name().toLowerCase(Locale.ENGLISH))
          .endObject();
        json.name("timeline").beginArray();
        for (int i = 0; i < starts.size(); i++) {
          Point point = points.get(i);
          json.beginObject()
            .prop("date", starts.get(i).toString())
            .prop("projects", point.projects())
            .prop("passed", point.passed())
            .prop("failed", point.failed())
            .prop("openIssues", point.openIssues())
            .prop("vulnerabilities", point.vulnerabilities())
            .prop("blockerIssues", point.blockerIssues())
            .endObject();
        }
        json.endArray().endObject();
      }
    }
  }
}
