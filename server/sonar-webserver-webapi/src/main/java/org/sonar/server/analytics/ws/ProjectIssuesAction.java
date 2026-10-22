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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.issue.index.IssueIndex;

import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * Open issues of every project of the organization by type and severity, in one call, so the page does not need one issue search
 * per batch of projects, type and severity.
 */
public class ProjectIssuesAction implements AnalyticsWsAction {

  private final DbClient dbClient;
  private final IssueIndex issueIndex;
  private final AnalyticsWsSupport support;

  public ProjectIssuesAction(DbClient dbClient, IssueIndex issueIndex, AnalyticsWsSupport support) {
    this.dbClient = dbClient;
    this.issueIndex = issueIndex;
    this.support = support;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("project_issues")
      .setDescription("Unresolved issues on the main branch of every project of the organization the user can browse, counted by " +
        "type and severity, with the estimated time to fix them. Security hotspots are left out, and so are projects with no " +
        "unresolved issue.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("project_issues-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));

      Map<String, ProjectIssues> byProjectUuid = new HashMap<>();
      issueIndex.countUnresolvedByProjectTypeAndSeverity(organization.getUuid(), count -> {
        ProjectIssues project = byProjectUuid.computeIfAbsent(count.projectUuid(), k -> new ProjectIssues());
        project.counts.computeIfAbsent(count.type(), k -> new TreeMap<>()).put(count.severity(), count.count());
        project.effortMinutes += count.effortMinutes();
      });

      Map<String, ProjectIssues> byProjectKey = new TreeMap<>();
      for (ProjectDto project : dbClient.projectDao().selectByUuids(dbSession, byProjectUuid.keySet())) {
        byProjectKey.put(project.getKey(), byProjectUuid.get(project.getUuid()));
      }

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject().name("projects").beginArray();
        byProjectKey.forEach((projectKey, project) -> {
          json.beginObject()
            .prop("key", projectKey)
            .prop("effortMinutes", project.effortMinutes)
            .name("issues").beginObject();
          project.counts.forEach((type, bySeverity) -> {
            json.name(type).beginObject();
            bySeverity.forEach((severity, count) -> json.prop(severity, count.longValue()));
            json.endObject();
          });
          json.endObject().endObject();
        });
        json.endArray().endObject();
      }
    }
  }

  private static class ProjectIssues {
    private final Map<String, Map<String, Long>> counts = new TreeMap<>();
    private long effortMinutes;
  }
}
