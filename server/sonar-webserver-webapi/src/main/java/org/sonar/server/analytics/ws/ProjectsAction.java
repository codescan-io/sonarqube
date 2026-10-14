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
import org.sonar.db.ce.AnalyticsProjectActivityDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/** The projects behind the "projects analyzed" figure of {@code api/analytics/activity}, or the ones it leaves out. */
public class ProjectsAction implements AnalyticsWsAction {

  static final String PARAM_ANALYZED = "analyzed";

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public ProjectsAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("projects")
      .setDescription("Projects of the organization with their analyses in a period, most analyzed first. " +
        "With analyzed=false, the projects with no analysis in the period instead, with the date of their latest analysis " +
        "when the platform still has it.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("projects-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    AnalyticsPeriodParams.define(action);
    action.createParam(PARAM_ANALYZED)
      .setDescription("Projects analyzed in the period, or the ones that were not")
      .setBooleanPossibleValues()
      .setDefaultValue(true);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      AnalyticsPeriod period = AnalyticsPeriodParams.parse(request, Interval.DAY, system2.now());
      Pagination pagination = AnalyticsJson.pagination(request);
      String orgUuid = organization.getUuid();
      long from = period.startMs();
      long to = period.endMs();

      List<AnalyticsProjectActivityDto> projects;
      int total;
      if (request.mandatoryParamAsBoolean(PARAM_ANALYZED)) {
        projects = dbClient.ceActivityDao().selectAnalyzedProjects(dbSession, orgUuid, from, to, pagination);
        total = dbClient.ceActivityDao().countAnalyzedProjects(dbSession, orgUuid, from, to);
      } else {
        projects = dbClient.ceActivityDao().selectNotAnalyzedProjects(dbSession, orgUuid, from, to, pagination);
        total = dbClient.ceActivityDao().countNotAnalyzedProjects(dbSession, orgUuid, from, to);
      }

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        AnalyticsJson.writePaging(json, pagination, total);
        json.name("projects").beginArray();
        for (AnalyticsProjectActivityDto project : projects) {
          Long last = project.getLastSubmittedAt();
          json.beginObject()
            .prop("key", project.getProjectKey())
            .prop("name", project.getProjectName())
            .prop("analyses", project.getAnalyses())
            .prop("success", project.getSuccess())
            .prop("failed", project.getFailed())
            .prop("canceled", project.getCanceled())
            .prop("avgExecutionTimeMs", project.getAvgExecutionTimeMs())
            .prop("lastAnalysisAt", last == null ? null : formatDateTime(last))
            .endObject();
        }
        json.endArray().endObject();
      }
    }
  }
}
