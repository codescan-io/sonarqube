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
import org.sonar.db.issue.ExpiringExceptionDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.exceptions.BadRequestException;

import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** Accepted and false-positive issues whose exception expires soon, so they can be reviewed before they reopen. */
public class ExceptionsAction implements AnalyticsWsAction {

  static final String PARAM_WITHIN_DAYS = "withinDays";
  private static final long DAY_MS = 24L * 60 * 60 * 1000;

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public ExceptionsAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("expiring_exceptions")
      .setDescription("Accepted and false-positive issues of the organization's main branches whose exception expires within " +
        "the given number of days, soonest first. Exceptions without an expiry date are not listed.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("expiring_exceptions-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    action.createParam(PARAM_WITHIN_DAYS)
      .setDescription("Number of days ahead")
      .setDefaultValue(30)
      .setMaximumValue(365);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      long now = system2.now();
      int withinDays = request.mandatoryParamAsInt(PARAM_WITHIN_DAYS);
      if (withinDays < 1) {
        throw BadRequestException.create("'" + PARAM_WITHIN_DAYS + "' must be at least 1");
      }
      long expiresBefore = now + withinDays * DAY_MS;
      Pagination pagination = AnalyticsJson.pagination(request);
      List<ExpiringExceptionDto> exceptions = dbClient.issueDao()
        .selectExpiringExceptionsForOrganization(dbSession, organization.getUuid(), expiresBefore, pagination);
      int total = dbClient.issueDao().countExpiringExceptionsForOrganization(dbSession, organization.getUuid(), expiresBefore);

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("exceptions").beginArray();
        for (ExpiringExceptionDto exception : exceptions) {
          json.beginObject()
            .prop("issueKey", exception.getIssueKey())
            .prop("issueStatus", "FALSE-POSITIVE".equals(exception.getResolution()) ? "FALSE_POSITIVE" : "ACCEPTED")
            .prop("expiresAt", formatDateTime(exception.getExpiresAt()))
            .prop("expired", exception.getExpiresAt() <= now)
            .prop("rule", exception.getRuleRepository() + ":" + exception.getRuleKey())
            .prop("ruleName", exception.getRuleName())
            .prop("projectKey", exception.getProjectKey())
            .prop("projectName", exception.getProjectName())
            .prop("filePath", exception.getFilePath())
            .endObject();
        }
        json.endArray().endObject();
      }
    }
  }
}
