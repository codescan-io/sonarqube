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
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeQueueStatusCount;
import org.sonar.db.organization.OrganizationDto;

import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

public class QueueAction implements AnalyticsWsAction {

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;

  public QueueAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("queue")
      .setDescription("Background tasks of the organization's projects that are waiting or running right now.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("queue-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      List<CeQueueStatusCount> counts = dbClient.ceQueueDao().countByStatusForOrganization(dbSession, organization.getUuid());

      int pending = 0;
      int inProgress = 0;
      Long oldestPendingAt = null;
      for (CeQueueStatusCount count : counts) {
        if (count.getStatus() == CeQueueDto.Status.PENDING) {
          pending = count.getTotal();
          oldestPendingAt = count.getOldestCreatedAt();
        } else if (count.getStatus() == CeQueueDto.Status.IN_PROGRESS) {
          inProgress = count.getTotal();
        }
      }

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject()
          .prop("pending", pending)
          .prop("inProgress", inProgress);
        if (oldestPendingAt != null) {
          json.prop("oldestPendingAt", formatDateTime(oldestPendingAt))
            .prop("oldestPendingAgeMs", Math.max(0, system2.now() - oldestPendingAt));
        }
        json.endObject();
      }
    }
  }
}
