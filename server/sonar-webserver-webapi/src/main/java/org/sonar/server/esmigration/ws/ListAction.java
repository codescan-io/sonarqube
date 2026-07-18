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
package org.sonar.server.esmigration.ws;

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.es.migration.EsDataMigrationEngine;
import org.sonar.server.es.migration.EsDataMigrationState;
import org.sonar.server.user.UserSession;

public class ListAction implements EsMigrationsWsAction {

  private final EsDataMigrationEngine engine;
  private final UserSession userSession;

  public ListAction(EsDataMigrationEngine engine, UserSession userSession) {
    this.engine = engine;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    controller.createAction("list")
      .setDescription("List all ES data migrations and their current status. Requires 'Administer System' permission.")
      .setSince("10.8")
      .setInternal(true)
      .setHandler(this);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkIsSystemAdministrator();
    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject().name("migrations").beginArray();
      for (EsDataMigrationState state : engine.list()) {
        EsMigrationsWs.writeState(json, state);
      }
      json.endArray().endObject();
    }
  }
}
