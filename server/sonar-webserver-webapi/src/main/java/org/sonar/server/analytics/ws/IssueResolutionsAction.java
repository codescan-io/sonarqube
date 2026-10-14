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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.sonar.api.config.Configuration;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.ResolutionAggregator.Counts;

import static org.sonar.core.config.PurgeConstants.DAYS_BEFORE_DELETING_CLOSED_ISSUES;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** How many issues were introduced and resolved on the organization's main branches, and how they got resolved. */
public class IssueResolutionsAction implements AnalyticsWsAction {

  static final String PARAM_INTERVAL = "interval";
  static final int DEFAULT_RETENTION_DAYS = 30;

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;
  private final Configuration configuration;

  public IssueResolutionsAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2, Configuration configuration) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
    this.configuration = configuration;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("issue_resolutions")
      .setDescription("Issues introduced on the organization's main branches over a period, and issues resolved: fixed (gone from " +
        "the code), removed (rule removed or file deleted), accepted, marked as false positive, or reopened.<br/>" +
        "The platform deletes closed issues, with their history, after '" + DAYS_BEFORE_DELETING_CLOSED_ISSUES + "' days: fixed " +
        "and removed counts before 'completeFrom' are incomplete.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("issue_resolutions-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_INTERVAL)
      .setDescription("Size of the buckets")
      .setPossibleValues("day", "week")
      .setDefaultValue("day");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      Interval interval = Interval.valueOf(request.mandatoryParam(PARAM_INTERVAL).toUpperCase(Locale.ENGLISH));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, interval, now);

      ResolutionAggregator aggregator = new ResolutionAggregator(period);
      dbClient.issueDao().scrollCreationDatesForOrganization(dbSession, organization.getUuid(), period.startMs(), period.endMs(),
        context -> aggregator.addCreation(context.getResultObject()));
      dbClient.issueChangeDao().scrollResolutionChangesForOrganization(dbSession, organization.getUuid(), period.startMs(),
        period.endMs(), context -> aggregator.addChange(context.getResultObject()));

      int retentionDays = configuration.getInt(DAYS_BEFORE_DELETING_CLOSED_ISSUES).orElse(DEFAULT_RETENTION_DAYS);
      LocalDate completeFrom = Instant.ofEpochMilli(now).atZone(period.zone()).toLocalDate().minusDays(retentionDays);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        json.name("period").beginObject()
          .prop("from", period.from().toString())
          .prop("to", period.to().toString())
          .prop("timeZone", period.zone().getId())
          .prop("interval", period.interval().name().toLowerCase(Locale.ENGLISH))
          .endObject();
        json.prop("retentionDays", retentionDays)
          .prop("completeFrom", completeFrom.toString());

        Counts totals = aggregator.totals();
        json.name("totals").beginObject();
        writeCounts(json, totals);
        json.prop("resolved", totals.resolved())
          .prop("fixedByAnalysis", aggregator.fixedByAnalysis())
          .prop("netChange", totals.introduced() + totals.reopened() - totals.resolved())
          .endObject();

        json.name("timeline").beginArray();
        List<LocalDate> starts = period.bucketStarts();
        for (int i = 0; i < starts.size(); i++) {
          json.beginObject().prop("date", starts.get(i).toString());
          writeCounts(json, aggregator.buckets().get(i));
          json.endObject();
        }
        json.endArray().endObject();
      }
    }
  }

  private static void writeCounts(JsonWriter json, Counts counts) {
    json.prop("introduced", counts.introduced())
      .prop("fixed", counts.fixed())
      .prop("removed", counts.removed())
      .prop("accepted", counts.accepted())
      .prop("falsePositive", counts.falsePositive())
      .prop("reopened", counts.reopened());
  }
}
