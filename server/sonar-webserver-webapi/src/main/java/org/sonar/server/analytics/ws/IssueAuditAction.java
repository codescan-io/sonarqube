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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.issue.IssueAuditChangeDto;
import org.sonar.db.issue.IssueAuditQuery;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.AuditActionClassifier.Action;
import org.sonar.server.analytics.ws.AuditActionClassifier.Classified;
import org.sonar.server.exceptions.NotFoundException;

import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * Audit trail of the issues and security hotspots of an organization's main branches: who accepted, marked as false positive,
 * reopened or reviewed what, when, and what they wrote; and a summary of each user's activity.
 */
public class IssueAuditAction implements AnalyticsWsAction {

  static final String PARAM_CATEGORIES = "categories";
  static final String PARAM_USER = "user";
  static final String PARAM_PROJECT = "project";
  static final String PARAM_INCLUDE_ANALYSIS = "includeAnalysisChanges";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public IssueAuditAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("issue_audit")
      .setDescription("Changes made over a period to the issues and security hotspots of the organization's main branches, the most " +
        "recent first: status changes (accepted, false positive, reopened, reviewed...), severity changes, comments and exception " +
        "reasons, each with the user who made it. 'users' sums up the activity of every user over the whole selection.<br/>" +
        "The changes of closed issues are deleted with them, after the closed issues retention period.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("issue_audit-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_CATEGORIES)
      .setDescription("Kinds of changes to list")
      .setPossibleValues(IssueAuditQuery.CATEGORY_STATUS, IssueAuditQuery.CATEGORY_SEVERITY, IssueAuditQuery.CATEGORY_COMMENT,
        IssueAuditQuery.CATEGORY_EXCEPTION_REASON)
      .setDefaultValue(String.join(",", IssueAuditQuery.CATEGORY_STATUS, IssueAuditQuery.CATEGORY_SEVERITY,
        IssueAuditQuery.CATEGORY_COMMENT, IssueAuditQuery.CATEGORY_EXCEPTION_REASON));
    action.createParam(PARAM_USER)
      .setDescription("Login of a user, to list only the changes they made")
      .setExampleValue("alice");
    action.createParam(PARAM_PROJECT)
      .setDescription("Key of a project, to list only the changes of its issues")
      .setExampleValue("my-project");
    action.createParam(PARAM_INCLUDE_ANALYSIS)
      .setDescription("Whether to also list the changes made by the analysis, such as closing an issue gone from the code")
      .setBooleanPossibleValues()
      .setDefaultValue(false);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, Interval.DAY, system2.now());
      String userUuid = userUuid(dbSession, request.param(PARAM_USER));
      String projectKey = request.param(PARAM_PROJECT);
      String projectUuid = projectKey == null ? null
        : AnalyticsIssues.checkProjectOfOrganization(dbClient, dbSession, organization, projectKey).getUuid();
      IssueAuditQuery query = new IssueAuditQuery(organization.getUuid(), period.startMs(), period.endMs(),
        request.mandatoryParamAsStrings(PARAM_CATEGORIES), userUuid, projectUuid, request.mandatoryParamAsBoolean(PARAM_INCLUDE_ANALYSIS));
      Pagination pagination = AnalyticsJson.pagination(request);

      List<IssueAuditChangeDto> changes = dbClient.issueChangeDao().selectAuditChangesForOrganization(dbSession, query, pagination);
      int total = dbClient.issueChangeDao().countAuditChangesForOrganization(dbSession, query);
      Map<String, UserActivity> activities = new HashMap<>();
      dbClient.issueChangeDao().scrollAuditChangesForOrganization(dbSession, query, context -> {
        IssueAuditChangeDto change = context.getResultObject();
        Classified classified = AuditActionClassifier.classify(change.getChangeType(), change.getChangeData());
        activities.computeIfAbsent(change.getUserUuid() == null ? "" : change.getUserUuid(), k -> new UserActivity()).add(classified.action());
      });

      Set<String> userUuids = new HashSet<>(activities.keySet());
      userUuids.remove("");
      Map<String, UserDto> users = AnalyticsIssues.usersByUuid(dbClient, dbSession, userUuids);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        json.name("period").beginObject()
          .prop("from", period.from().toString())
          .prop("to", period.to().toString())
          .prop("timeZone", period.zone().getId())
          .endObject();
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("changes").beginArray();
        for (IssueAuditChangeDto change : changes) {
          writeChange(json, change, users);
        }
        json.endArray();
        writeUsers(json, activities, users);
        json.endObject();
      }
    }
  }

  @Nullable
  private String userUuid(DbSession dbSession, @Nullable String login) {
    if (login == null) {
      return null;
    }
    UserDto user = dbClient.userDao().selectByLogin(dbSession, login);
    if (user == null) {
      throw new NotFoundException(String.format("User '%s' not found", login));
    }
    return user.getUuid();
  }

  private static void writeChange(JsonWriter json, IssueAuditChangeDto change, Map<String, UserDto> users) {
    Classified classified = AuditActionClassifier.classify(change.getChangeType(), change.getChangeData());
    json.beginObject()
      .prop("id", change.getUuid())
      .prop("date", formatDateTime(change.getChangedAt()));
    AnalyticsIssues.writeUser(json, "user", change.getUserUuid(), users);
    json.prop("action", classified.action().name())
      .prop("from", classified.from())
      .prop("to", classified.to())
      .prop("text", classified.text())
      .prop("issueKey", change.getIssueKey())
      .prop("type", AnalyticsIssues.typeName(change.getIssueType()))
      .prop("severity", change.getSeverity())
      .prop("rule", change.getRuleRepository() + ":" + change.getRuleKey())
      .prop("ruleName", change.getRuleName())
      .prop("projectKey", change.getProjectKey())
      .prop("projectName", change.getProjectName())
      .prop("filePath", change.getFilePath())
      .endObject();
  }

  /** Users with the most changes first; changes made by the analysis come under a user without login. */
  private static void writeUsers(JsonWriter json, Map<String, UserActivity> activities, Map<String, UserDto> users) {
    List<Map.Entry<String, UserActivity>> sorted = new ArrayList<>(activities.entrySet());
    sorted.sort((a, b) -> Integer.compare(b.getValue().total, a.getValue().total));
    json.name("users").beginArray();
    for (Map.Entry<String, UserActivity> entry : sorted) {
      json.beginObject();
      if (!entry.getKey().isEmpty()) {
        UserDto user = users.get(entry.getKey());
        json.prop("login", user == null ? entry.getKey() : user.getLogin())
          .prop("name", user == null ? null : user.getName());
      }
      json.prop("total", entry.getValue().total);
      json.name("actions").beginObject();
      entry.getValue().byAction.forEach((action, count) -> json.prop(action.name().toLowerCase(Locale.ENGLISH), count.intValue()));
      json.endObject().endObject();
    }
    json.endArray();
  }

  private static class UserActivity {
    private final Map<Action, Integer> byAction = new EnumMap<>(Action.class);
    private int total;

    void add(Action action) {
      byAction.merge(action, 1, Integer::sum);
      total++;
    }
  }
}
