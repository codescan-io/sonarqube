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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.issue.AnalyticsIssueDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueGroupCountDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.BadRequestException;

import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * Governance of the accepted and false-positive issues of an organization: who marked each one, why, and when the exception
 * expires; how many there are, how many expired, and how many findings still wait for a review.
 */
public class ExceptionGovernanceAction implements AnalyticsWsAction {

  static final String PARAM_STATUSES = "statuses";
  static final String PARAM_PROJECT = "project";
  static final String PARAM_SOON_DAYS = "soonDays";
  static final String ACCEPTED = "ACCEPTED";
  static final String FALSE_POSITIVE = "FALSE_POSITIVE";
  private static final long DAY_MS = 24L * 60 * 60 * 1000;

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public ExceptionGovernanceAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("exceptions")
      .setDescription("Accepted and false-positive issues of the organization's main branches, the most recently changed first: who " +
        "marked each one and when, the rationale given (the exception reason, or else the latest comment), and when the exception " +
        "expires. The summary counts them by expiry, and counts the security hotspots still to review.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("exceptions-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    action.createParam(PARAM_STATUSES)
      .setDescription("Statuses of the issues to list")
      .setPossibleValues(ACCEPTED, FALSE_POSITIVE)
      .setDefaultValue(ACCEPTED + "," + FALSE_POSITIVE);
    action.createParam(PARAM_PROJECT)
      .setDescription("Key of a project, to list only its issues. The summary always covers the organization.")
      .setExampleValue("my-project");
    action.createParam(PARAM_SOON_DAYS)
      .setDescription("Exceptions that expire within this number of days count as expiring soon")
      .setDefaultValue(30)
      .setMaximumValue(365);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      int soonDays = request.mandatoryParamAsInt(PARAM_SOON_DAYS);
      if (soonDays < 1) {
        throw BadRequestException.create("'" + PARAM_SOON_DAYS + "' must be at least 1");
      }
      List<String> resolutions = request.mandatoryParamAsStrings(PARAM_STATUSES).stream()
        .map(status -> ACCEPTED.equals(status) ? RESOLUTION_WONT_FIX : RESOLUTION_FALSE_POSITIVE)
        .distinct()
        .toList();
      String projectKey = request.param(PARAM_PROJECT);
      String projectUuid = projectKey == null ? null
        : AnalyticsIssues.checkProjectOfOrganization(dbClient, dbSession, organization, projectKey).getUuid();
      Pagination pagination = AnalyticsJson.pagination(request);

      List<AnalyticsIssueDto> issues = dbClient.issueDao()
        .selectExceptionsForOrganization(dbSession, organization.getUuid(), resolutions, projectUuid, pagination);
      int total = dbClient.issueDao().countExceptionsForOrganization(dbSession, organization.getUuid(), resolutions, projectUuid);
      Map<String, History> histories = histories(dbSession, issues);
      Set<String> userUuids = new HashSet<>();
      histories.values().forEach(history -> history.addUsers(userUuids));
      Map<String, UserDto> users = AnalyticsIssues.usersByUuid(dbClient, dbSession, userUuids);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        writeSummary(json, dbSession, organization, now, soonDays);
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("exceptions").beginArray();
        for (AnalyticsIssueDto issue : issues) {
          json.beginObject();
          AnalyticsIssues.writeIssueProps(json, issue);
          json.prop("issueStatus", AnalyticsIssues.exceptionStatus(issue.getResolution()));
          Long expiresAt = issue.getResolutionExpiresAt();
          AnalyticsIssues.writeDate(json, "expiresAt", expiresAt);
          json.prop("expired", expiresAt != null && expiresAt <= now);
          histories.getOrDefault(issue.getIssueKey(), new History()).write(json, users);
          json.endObject();
        }
        json.endArray().endObject();
      }
    }
  }

  private void writeSummary(JsonWriter json, DbSession dbSession, OrganizationDto organization, long now, int soonDays) {
    long accepted = 0;
    long falsePositive = 0;
    Map<String, Long> byExpiry = new HashMap<>();
    for (IssueGroupCountDto count : dbClient.issueDao()
      .countExceptionsByExpiryForOrganization(dbSession, organization.getUuid(), now, now + soonDays * DAY_MS)) {
      if (RESOLUTION_WONT_FIX.equals(count.getResolution())) {
        accepted += count.getCount();
      } else {
        falsePositive += count.getCount();
      }
      byExpiry.merge(count.getGroupKey(), count.getCount(), Long::sum);
    }
    json.name("summary").beginObject()
      .prop("accepted", accepted)
      .prop("falsePositive", falsePositive)
      .prop("withoutExpiry", byExpiry.getOrDefault("NONE", 0L))
      .prop("expired", byExpiry.getOrDefault("EXPIRED", 0L))
      .prop("expiringSoon", byExpiry.getOrDefault("SOON", 0L))
      .prop("soonDays", soonDays)
      .prop("hotspotsToReview", dbClient.issueDao().countHotspotsToReviewForOrganization(dbSession, organization.getUuid()))
      .endObject();
  }

  private Map<String, History> histories(DbSession dbSession, List<AnalyticsIssueDto> issues) {
    Map<String, History> histories = new HashMap<>();
    if (issues.isEmpty()) {
      return histories;
    }
    List<IssueChangeDto> changes = new ArrayList<>(dbClient.issueChangeDao()
      .selectByIssueKeys(dbSession, issues.stream().map(AnalyticsIssueDto::getIssueKey).toList()));
    changes.sort(Comparator.comparingLong(ExceptionGovernanceAction::changedAt));
    for (IssueChangeDto change : changes) {
      histories.computeIfAbsent(change.getIssueKey(), k -> new History()).add(change);
    }
    return histories;
  }

  private static long changedAt(IssueChangeDto change) {
    Long date = change.getIssueChangeCreationDate();
    if (date != null) {
      return date;
    }
    return change.getCreatedAt() == null ? 0 : change.getCreatedAt();
  }

  /** What the changes of one issue, oldest first, tell about its exception. */
  private static class History {
    private IssueChangeDto marked;
    private IssueChangeDto reason;
    private IssueChangeDto lastComment;
    private int comments;

    void add(IssueChangeDto change) {
      switch (change.getChangeType()) {
        case IssueChangeDto.TYPE_EXCEPTION_REASON -> reason = change;
        case IssueChangeDto.TYPE_COMMENT -> {
          lastComment = change;
          comments++;
        }
        case IssueChangeDto.TYPE_FIELD_CHANGE -> {
          FieldDiffs.Diff<?> diff = FieldDiffs.parse(change.getChangeData()).get("resolution");
          Object newValue = diff == null ? null : diff.newValue();
          if (newValue != null && (RESOLUTION_WONT_FIX.equals(newValue.toString()) || RESOLUTION_FALSE_POSITIVE.equals(newValue.toString()))) {
            marked = change;
          }
        }
        default -> {
          // not used
        }
      }
    }

    @CheckForNull
    private IssueChangeDto rationale() {
      return reason != null ? reason : lastComment;
    }

    void addUsers(Set<String> uuids) {
      for (IssueChangeDto change : new IssueChangeDto[] {marked, rationale()}) {
        if (change != null && change.getUserUuid() != null) {
          uuids.add(change.getUserUuid());
        }
      }
    }

    void write(JsonWriter json, Map<String, UserDto> users) {
      if (marked != null) {
        AnalyticsIssues.writeUser(json, "markedBy", marked.getUserUuid(), users);
        AnalyticsIssues.writeDate(json, "markedAt", changedAt(marked));
      }
      IssueChangeDto rationale = rationale();
      if (rationale != null) {
        json.name("rationale").beginObject()
          .prop("source", rationale == reason ? "EXCEPTION_REASON" : "COMMENT")
          .prop("text", rationale.getChangeData());
        AnalyticsIssues.writeUser(json, "user", rationale.getUserUuid(), users);
        AnalyticsIssues.writeDate(json, "date", changedAt(rationale));
        json.endObject();
      }
      json.prop("comments", comments);
    }
  }
}
