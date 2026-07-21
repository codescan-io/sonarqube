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
package org.sonar.server.es.migration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.metadata.MetadataIndex;
import org.sonar.server.es.migration.EsDataMigrationState.Status;

/**
 * Runs and tracks {@link EsDataMigration}s. Migrations never run automatically: they are triggered through
 * the {@code api/es_migrations} WebService. State is stored (Gson) in the ES {@code metadatas} index, keyed
 * by version. A {@code RUNNING} migration is refreshed by polling its ES task on every {@link #status}/{@link #list}.
 */
@ServerSide
public class EsDataMigrationEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(EsDataMigrationEngine.class);
  private static final Gson GSON = new Gson();

  private final DbClient dbClient;
  private final EsClient esClient;
  private final MetadataIndex metadataIndex;
  private final System2 system2;
  private final Map<Long, EsDataMigration> migrationsByVersion;

  public EsDataMigrationEngine(DbClient dbClient, EsClient esClient, MetadataIndex metadataIndex,
    System2 system2, EsDataMigration... migrations) {
    this.dbClient = dbClient;
    this.esClient = esClient;
    this.metadataIndex = metadataIndex;
    this.system2 = system2;
    this.migrationsByVersion = Arrays.stream(migrations)
      .collect(Collectors.toMap(EsDataMigration::version, m -> m, (a, b) -> {
        throw new IllegalStateException("Duplicate ES data migration version: " + a.version());
      }, TreeMap::new));
  }

  /** One entry per registered migration, ordered by version; RUNNING entries are refreshed from their ES task. */
  public List<EsDataMigrationState> list() {
    return migrationsByVersion.keySet().stream().map(this::status).collect(Collectors.toList());
  }

  /** Dry-run: number of documents the migration would touch. */
  public long estimate(long version) {
    EsDataMigration migration = getMigration(version);
    try (DbSession dbSession = dbClient.openSession(false)) {
      return migration.estimate(dbSession);
    }
  }

  /**
   * Submits the migration. {@code force} allows re-running a COMPLETED/FAILED migration. Synchronized so two
   * concurrent submissions on the same node cannot both launch a task; the underlying query is idempotent, so
   * a cross-node race is at worst a duplicate no-op task.
   */
  public synchronized EsDataMigrationState run(long version, boolean force) {
    EsDataMigration migration = getMigration(version);
    EsDataMigrationState current = status(version);
    if (current.getStatus() == Status.RUNNING) {
      throw new IllegalStateException("ES data migration " + version + " is already running (task " + current.getTaskId() + ")");
    }
    if (current.getStatus() == Status.COMPLETED && !force) {
      throw new IllegalStateException("ES data migration " + version + " already completed. Use force to re-run.");
    }
    try (DbSession dbSession = dbClient.openSession(false)) {
      LOGGER.info("Starting ES data migration {} ({}){}", version, migration.description(), force ? " [forced re-run]" : "");
      Optional<String> taskId = migration.execute(dbSession);
      EsDataMigrationState state;
      if (taskId.isPresent()) {
        state = newState(version, Status.RUNNING, taskId.get(), null);
        LOGGER.info("ES data migration {} submitted as ES task {}", version, taskId.get());
      } else {
        state = newState(version, Status.COMPLETED, null, "nothing to do");
        LOGGER.info("ES data migration {} completed: nothing to do", version);
      }
      persist(state);
      return withDescription(state, migration);
    } catch (Exception e) {
      LOGGER.error("ES data migration {} failed to start", version, e);
      EsDataMigrationState state = newState(version, Status.FAILED, null, e.getMessage());
      persist(state);
      return withDescription(state, migration);
    }
  }

  /** Current state; if RUNNING, polls the ES task and transitions to COMPLETED/FAILED (persisting) when done. */
  public EsDataMigrationState status(long version) {
    EsDataMigration migration = getMigration(version);
    EsDataMigrationState state = metadataIndex.getEsDataMigrationState(version)
      .map(json -> GSON.fromJson(json, EsDataMigrationState.class))
      .orElseGet(() -> newState(version, Status.PENDING, null, null));
    if (state.getStatus() == Status.RUNNING && state.getTaskId() != null) {
      state = refreshFromEsTask(state);
    }
    return withDescription(state, migration);
  }

  /** Polls {@code GET /_tasks/{id}}; transitions RUNNING -> COMPLETED/FAILED and persists, or enriches progress. */
  private EsDataMigrationState refreshFromEsTask(EsDataMigrationState state) {
    try {
      JsonObject task = JsonParser.parseString(esClient.getTaskAsJson(state.getTaskId())).getAsJsonObject();
      if (!task.get("completed").getAsBoolean()) {
        // still running: surface live progress without transitioning
        JsonObject progress = task.getAsJsonObject("task").getAsJsonObject("status");
        state.setDetail("in progress: updated=" + progress.get("updated") + " of total=" + progress.get("total"));
        return state;
      }
      Status status;
      String detail;
      if (task.has("error")) {
        // a task that finished by throwing carries a top-level "error" and no "response"
        status = Status.FAILED;
        detail = "task error: " + task.get("error");
      } else {
        JsonObject response = task.getAsJsonObject("response");
        boolean hasFailures = response.has("failures") && !response.getAsJsonArray("failures").isEmpty();
        status = hasFailures ? Status.FAILED : Status.COMPLETED;
        detail = "updated=" + response.get("updated") + ", versionConflicts=" + response.get("version_conflicts")
          + (hasFailures ? (", failures=" + response.getAsJsonArray("failures")) : "");
      }
      EsDataMigrationState done = newState(state.getVersion(), status, state.getTaskId(), detail);
      persist(done);
      if (status == Status.FAILED) {
        LOGGER.error("ES data migration {} failed (task {}): {}", done.getVersion(), done.getTaskId(), detail);
      } else {
        LOGGER.info("ES data migration {} completed (task {}): {}", done.getVersion(), done.getTaskId(), detail);
      }
      return done;
    } catch (Exception e) {
      // never let a transient ES/parse error escape to the WS action or wedge the state at RUNNING
      LOGGER.warn("Could not refresh ES data migration {} from task {}", state.getVersion(), state.getTaskId(), e);
      state.setDetail("could not read task status: " + e.getMessage());
      return state;
    }
  }

  private EsDataMigration getMigration(long version) {
    EsDataMigration migration = migrationsByVersion.get(version);
    if (migration == null) {
      throw new IllegalArgumentException("Unknown ES data migration version: " + version);
    }
    return migration;
  }

  private void persist(EsDataMigrationState state) {
    metadataIndex.setEsDataMigrationState(state.getVersion(), GSON.toJson(state));
  }

  private EsDataMigrationState newState(long version, Status status, @Nullable String taskId, @Nullable String detail) {
    return new EsDataMigrationState()
      .setVersion(version)
      .setStatus(status)
      .setTaskId(taskId)
      .setDetail(detail)
      .setUpdatedAt(system2.now());
  }

  private static EsDataMigrationState withDescription(EsDataMigrationState state, EsDataMigration migration) {
    state.setDescription(migration.description());
    return state;
  }
}
