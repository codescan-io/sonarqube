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

import java.util.Arrays;
import java.util.Date;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.es.migration.EsDataMigrationState;

public class EsMigrationsWs implements WebService {

  public static final String CONTROLLER = "api/es_migrations";

  private final EsMigrationsWsAction[] actions;

  public EsMigrationsWs(EsMigrationsWsAction... actions) {
    this.actions = actions;
  }

  @Override
  public void define(Context context) {
    NewController controller = context.createController(CONTROLLER)
      .setSince("10.8")
      .setDescription("Manage one-shot Elasticsearch data migrations (backfills). System administrators only.");
    Arrays.stream(actions).forEach(action -> action.define(controller));
    controller.done();
  }

  /** Serializes a single migration state as a JSON object; shared by list/run/status actions. */
  static void writeState(JsonWriter json, EsDataMigrationState state) {
    json.beginObject()
      .prop("version", state.getVersion())
      .prop("description", state.getDescription())
      .prop("status", state.getStatus() == null ? null : state.getStatus().name())
      .prop("taskId", state.getTaskId())
      .prop("detail", state.getDetail())
      .propDateTime("updatedAt", new Date(state.getUpdatedAt()))
      .endObject();
  }
}
