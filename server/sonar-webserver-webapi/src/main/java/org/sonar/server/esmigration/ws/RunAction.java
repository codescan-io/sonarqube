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

public class RunAction implements EsMigrationsWsAction {

  private static final String PARAM_VERSION = "version";
  private static final String PARAM_DRY_RUN = "dryRun";
  private static final String PARAM_FORCE = "force";

  private final EsDataMigrationEngine engine;
  private final UserSession userSession;

  public RunAction(EsDataMigrationEngine engine, UserSession userSession) {
    this.engine = engine;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("run")
      .setDescription("Run (or dry-run) an ES data migration by version. Requires 'Administer System' permission. "
        + "Run migrations during a quiet period: a migration may reindex documents from the DB outside the "
        + "resilient indexing path, so an analysis writing the same documents concurrently can race it and be "
        + "overwritten. Migrations are idempotent — if in doubt, re-run once analyses have settled.")
      .setSince("10.8")
      .setPost(true)
      .setInternal(true)
      .setHandler(this);
    action.createParam(PARAM_VERSION)
      .setDescription("Version of the migration to run")
      .setRequired(true)
      .setExampleValue("2026071801");
    action.createParam(PARAM_DRY_RUN)
      .setDescription("If true, only estimate the number of documents that would be affected, without running the migration")
      .setBooleanPossibleValues()
      .setDefaultValue("false");
    action.createParam(PARAM_FORCE)
      .setDescription("If true, allow re-running a migration that already completed or failed")
      .setBooleanPossibleValues()
      .setDefaultValue("false");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkIsSystemAdministrator();
    long version = EsMigrationsWs.parseVersion(request.mandatoryParam(PARAM_VERSION));
    if (request.mandatoryParamAsBoolean(PARAM_DRY_RUN)) {
      long estimate = engine.estimate(version);
      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject().prop("version", version).prop("estimatedDocs", estimate).endObject();
      }
      return;
    }
    EsDataMigrationState state = engine.run(version, request.mandatoryParamAsBoolean(PARAM_FORCE));
    try (JsonWriter json = response.newJsonWriter()) {
      EsMigrationsWs.writeState(json, state);
    }
  }
}
