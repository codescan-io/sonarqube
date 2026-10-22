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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
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
import org.sonar.server.analytics.ws.SecurityRemediationAggregator.Fixes;
import org.sonar.server.analytics.ws.SecurityRemediationAggregator.Severity;
import org.sonar.server.exceptions.BadRequestException;

import static org.sonar.core.config.PurgeConstants.DAYS_BEFORE_DELETING_CLOSED_ISSUES;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;
import static org.sonar.server.analytics.ws.IssueResolutionsAction.DEFAULT_RETENTION_DAYS;

/** How fast the organization fixes security issues on its main branches, against a remediation SLA per severity. */
public class SecurityRemediationAction implements AnalyticsWsAction {

  static final String PARAM_INTERVAL = "interval";
  static final String PARAM_SLA_DAYS = "slaDays";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;
  private final Configuration configuration;

  public SecurityRemediationAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2, Configuration configuration) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
    this.configuration = configuration;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("security_remediation")
      .setDescription("How fast issues with a security impact get fixed on the organization's main branches: time to fix (from " +
        "creation to the analysis that found them fixed) and share fixed within the SLA of their severity, over a period and the " +
        "period before it; and the issues still open, with how many are past their SLA.<br/>" +
        "The platform deletes closed issues after '" + DAYS_BEFORE_DELETING_CLOSED_ISSUES + "' days: fixes before 'completeFrom' " +
        "are incomplete.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("security_remediation-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_INTERVAL)
      .setDescription("Size of the buckets")
      .setPossibleValues("day", "week")
      .setDefaultValue("week");
    action.createParam(PARAM_SLA_DAYS)
      .setDescription("Days to fix an issue of each security impact severity, overriding the defaults: " +
        SecurityRemediationAggregator.DEFAULT_SLA_DAYS)
      .setExampleValue("BLOCKER:7,HIGH:30");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      Interval interval = Interval.valueOf(request.mandatoryParam(PARAM_INTERVAL).toUpperCase(Locale.ENGLISH));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, interval, now);

      SecurityRemediationAggregator aggregator = new SecurityRemediationAggregator(period, parseSlaDays(request.param(PARAM_SLA_DAYS)), now);
      dbClient.issueDao().scrollFixedSecurityIssuesForOrganization(dbSession, organization.getUuid(), aggregator.scrollFromMs(),
        aggregator.scrollToMs(), context -> aggregator.addFixed(context.getResultObject()));
      dbClient.issueDao().scrollOpenSecurityIssuesForOrganization(dbSession, organization.getUuid(),
        context -> aggregator.addOpen(context.getResultObject()));

      int retentionDays = configuration.getInt(DAYS_BEFORE_DELETING_CLOSED_ISSUES).orElse(DEFAULT_RETENTION_DAYS);
      LocalDate completeFrom = Instant.ofEpochMilli(now).atZone(period.zone()).toLocalDate().minusDays(retentionDays);

      try (JsonWriter json = response.newJsonWriter()) {
        write(json, aggregator, retentionDays, completeFrom);
      }
    }
  }

  static Map<String, Integer> parseSlaDays(@Nullable String param) {
    Map<String, Integer> slaDays = new LinkedHashMap<>(SecurityRemediationAggregator.DEFAULT_SLA_DAYS);
    if (param == null || param.isBlank()) {
      return slaDays;
    }
    for (String entry : param.split(",")) {
      String[] parts = entry.trim().split(":");
      Integer days = parts.length == 2 ? parsePositive(parts[1].trim()) : null;
      if (days == null || parts[0].isBlank()) {
        throw BadRequestException.create(String.format("'%s' must be a list of SEVERITY:DAYS, got '%s'", PARAM_SLA_DAYS, entry));
      }
      slaDays.put(parts[0].trim().toUpperCase(Locale.ENGLISH), days);
    }
    return slaDays;
  }

  @Nullable
  private static Integer parsePositive(String value) {
    try {
      int days = Integer.parseInt(value);
      return days > 0 ? days : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static void write(JsonWriter json, SecurityRemediationAggregator aggregator, int retentionDays, LocalDate completeFrom) {
    AnalyticsPeriod period = aggregator.current();
    AnalyticsPeriod previous = aggregator.previous();
    json.beginObject();
    json.name("period").beginObject()
      .prop("from", period.from().toString())
      .prop("to", period.to().toString())
      .prop("timeZone", period.zone().getId())
      .prop("interval", period.interval().name().toLowerCase(Locale.ENGLISH))
      .endObject();
    json.prop("retentionDays", retentionDays)
      .prop("completeFrom", completeFrom.toString());

    Fixes totals = aggregator.totals();
    json.name("totals").beginObject();
    writeFixes(json, totals);
    json.prop("averageTimeToFixMs", totals.timesToFix().average())
      .prop("open", totals.open())
      .prop("openPastSla", totals.openPastSla())
      .endObject();

    json.name("previous").beginObject()
      .prop("from", previous.from().toString())
      .prop("to", previous.to().toString())
      .prop("complete", !previous.from().isBefore(completeFrom));
    writeFixes(json, aggregator.previousTotals());
    json.endObject();

    json.name("severities").beginArray();
    for (Map.Entry<String, Severity> entry : aggregator.severities().entrySet()) {
      Severity severity = entry.getValue();
      json.beginObject()
        .prop("severity", entry.getKey())
        .prop("slaDays", severity.slaDays());
      writeFixes(json, severity.fixes());
      json.prop("open", severity.open())
        .prop("openPastSla", severity.openPastSla())
        .prop("oldestOpenAgeMs", severity.open() == 0 ? null : severity.oldestOpenAgeMs())
        .endObject();
    }
    json.endArray();

    json.name("timeline").beginArray();
    List<LocalDate> starts = period.bucketStarts();
    for (int i = 0; i < starts.size(); i++) {
      json.beginObject().prop("date", starts.get(i).toString());
      writeFixes(json, aggregator.buckets().get(i));
      json.endObject();
    }
    json.endArray();
    json.endObject();
  }

  private static void writeFixes(JsonWriter json, Fixes fixes) {
    json.prop("fixed", fixes.fixed())
      .prop("fixedWithinSla", fixes.fixedWithinSla())
      .prop("medianTimeToFixMs", fixes.timesToFix().percentile(0.5));
  }
}
