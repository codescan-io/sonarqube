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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.AnalyticsIssueDto;
import org.sonar.db.issue.IssueGroupCountDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.CustomRuleDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_REMOVED;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * The custom rules of an organization (rules created from a template, such as XPath or AI generated rules) and the findings they
 * raise on its main branches: how many, how severe, how they trend, and how often they are dismissed, a sign the rule needs tuning.
 */
public class CustomRulesAction implements AnalyticsWsAction {

  static final String PARAM_INTERVAL = "interval";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public CustomRulesAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("custom_rules")
      .setDescription("Custom rules of the organization, and shared custom rules that raised issues on its main branches, the rules " +
        "with the most open issues first: open issues by severity, accepted, false-positive and fixed issues, and how many issues " +
        "each rule raised and closed over every day or week of a period. 'dismissalRate' is the share of the issues of a rule that " +
        "were accepted or marked as false positive.<br/>" +
        "Closed issues are deleted after the closed issues retention period, and so do not count after it.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("custom_rules-example.json"))
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
      int buckets = period.bucketStarts().size();

      Map<String, RuleStats> stats = new HashMap<>();
      for (CustomRuleDto rule : dbClient.ruleDao().selectCustomRulesForOrganization(dbSession, organization.getUuid())) {
        stats.put(rule.getUuid(), new RuleStats(rule, buckets));
      }
      for (IssueGroupCountDto count : dbClient.issueDao().countCustomRuleIssuesForOrganization(dbSession, organization.getUuid())) {
        RuleStats rule = stats.get(count.getGroupKey());
        if (rule != null) {
          rule.add(count);
        }
      }
      dbClient.issueDao().scrollCustomRuleIssuesForOrganization(dbSession, organization.getUuid(), period.startMs(), period.endMs(),
        context -> {
          AnalyticsIssueDto issue = context.getResultObject();
          RuleStats rule = stats.get(issue.getRuleUuid());
          if (rule != null) {
            rule.addDates(period, issue);
          }
        });

      List<RuleStats> sorted = new ArrayList<>(stats.values());
      sorted.sort(Comparator.comparingLong((RuleStats r) -> r.open).reversed()
        .thenComparing(Comparator.comparingLong((RuleStats r) -> r.total).reversed()));
      List<LocalDate> starts = period.bucketStarts();

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        json.name("period").beginObject()
          .prop("from", period.from().toString())
          .prop("to", period.to().toString())
          .prop("timeZone", period.zone().getId())
          .prop(PARAM_INTERVAL, period.interval().name().toLowerCase(Locale.ENGLISH))
          .endObject();
        json.name("rules").beginArray();
        for (RuleStats rule : sorted) {
          rule.write(json, starts);
        }
        json.endArray().endObject();
      }
    }
  }

  private static class RuleStats {
    private final CustomRuleDto rule;
    private final Map<String, Long> openBySeverity = new TreeMap<>();
    private final int[] introduced;
    private final int[] closed;
    private long total;
    private long open;
    private long accepted;
    private long falsePositive;
    private long fixed;

    RuleStats(CustomRuleDto rule, int buckets) {
      this.rule = rule;
      this.introduced = new int[buckets];
      this.closed = new int[buckets];
    }

    void add(IssueGroupCountDto count) {
      long n = count.getCount();
      total += n;
      String resolution = count.getResolution();
      if (resolution == null) {
        open += n;
        if (count.getSeverity() != null) {
          openBySeverity.merge(count.getSeverity(), n, Long::sum);
        }
      } else if (RESOLUTION_WONT_FIX.equals(resolution)) {
        accepted += n;
      } else if (RESOLUTION_FALSE_POSITIVE.equals(resolution)) {
        falsePositive += n;
      } else if (RESOLUTION_FIXED.equals(resolution) || RESOLUTION_REMOVED.equals(resolution)) {
        fixed += n;
      }
    }

    void addDates(AnalyticsPeriod period, AnalyticsIssueDto issue) {
      increment(period, introduced, issue.getCreationDate());
      String resolution = issue.getResolution();
      if (RESOLUTION_FIXED.equals(resolution) || RESOLUTION_REMOVED.equals(resolution)) {
        increment(period, closed, issue.getCloseDate());
      }
    }

    private static void increment(AnalyticsPeriod period, int[] buckets, Long date) {
      if (date != null && date >= period.startMs() && date < period.endMs()) {
        int index = period.bucketIndex(date);
        if (index >= 0 && index < buckets.length) {
          buckets[index]++;
        }
      }
    }

    void write(JsonWriter json, List<LocalDate> starts) {
      Integer priority = rule.getPriority();
      json.beginObject()
        .prop("key", rule.getRepository() + ":" + rule.getRuleKey())
        .prop("name", rule.getName())
        .prop("language", rule.getLanguage())
        .prop("template", rule.getTemplateRepository() + ":" + rule.getTemplateKey())
        .prop("severity", priority == null || priority < 0 || priority >= Severity.ALL.size() ? null : Severity.ALL.get(priority))
        .prop("type", AnalyticsIssues.typeName(rule.getRuleType()))
        .prop("status", rule.getStatus());
      if (rule.getCreatedAt() != null) {
        json.prop("createdAt", formatDateTime(rule.getCreatedAt()));
      }
      json.name("issues").beginObject()
        .prop("total", total)
        .prop("open", open)
        .prop("accepted", accepted)
        .prop("falsePositive", falsePositive)
        .prop("fixed", fixed);
      json.name("openBySeverity").beginObject();
      openBySeverity.forEach((severity, count) -> json.prop(severity, count.longValue()));
      json.endObject().endObject();
      if (total > 0) {
        json.prop("dismissalRate", (double) (accepted + falsePositive) / total);
      }
      int introducedTotal = 0;
      int closedTotal = 0;
      json.name("timeline").beginArray();
      for (int i = 0; i < starts.size(); i++) {
        json.beginObject()
          .prop("date", starts.get(i).toString())
          .prop("introduced", introduced[i])
          .prop("closed", closed[i])
          .endObject();
        introducedTotal += introduced[i];
        closedTotal += closed[i];
      }
      json.endArray()
        .prop("introduced", introducedTotal)
        .prop("closed", closedTotal)
        .endObject();
    }
  }
}
