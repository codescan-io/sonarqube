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

import java.util.Map;
import java.util.TreeMap;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.measure.index.ProjectMeasuresIndex;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * Quality gate status of every project of the organization in one call, so the page does not need one measures request per
 * batch of projects.
 */
public class ProjectQualityGatesAction implements AnalyticsWsAction {

  private final DbClient dbClient;
  private final ProjectMeasuresIndex projectMeasuresIndex;
  private final AnalyticsWsSupport support;

  public ProjectQualityGatesAction(DbClient dbClient, ProjectMeasuresIndex projectMeasuresIndex, AnalyticsWsSupport support) {
    this.dbClient = dbClient;
    this.projectMeasuresIndex = projectMeasuresIndex;
    this.support = support;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("project_quality_gates")
      .setDescription("Quality gate status of the main branch of every project of the organization the user can browse. Projects " +
        "with no status yet are left out.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("project_quality_gates-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    OrganizationDto organization;
    try (DbSession dbSession = dbClient.openSession(false)) {
      organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
    }
    Map<String, String> statuses = new TreeMap<>();
    projectMeasuresIndex.searchQualityGateStatuses(organization.getUuid(), statuses::put);

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject().name("projects").beginArray();
      statuses.forEach((key, status) -> json.beginObject().prop("key", key).prop("status", status).endObject());
      json.endArray().endObject();
    }
  }
}
