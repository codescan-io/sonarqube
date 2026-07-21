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

public class StatusAction implements EsMigrationsWsAction {

  private static final String PARAM_VERSION = "version";

  private final EsDataMigrationEngine engine;
  private final UserSession userSession;

  public StatusAction(EsDataMigrationEngine engine, UserSession userSession) {
    this.engine = engine;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("status")
      .setDescription("Status of a single ES data migration by version. Requires 'Administer System' permission.")
      .setSince("10.8")
      .setInternal(true)
      .setHandler(this);
    action.createParam(PARAM_VERSION)
      .setDescription("Version of the migration")
      .setRequired(true)
      .setExampleValue("2026071801");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkIsSystemAdministrator();
    long version = EsMigrationsWs.parseVersion(request.mandatoryParam(PARAM_VERSION));
    EsDataMigrationState state = engine.status(version);
    try (JsonWriter json = response.newJsonWriter()) {
      EsMigrationsWs.writeState(json, state);
    }
  }
}
