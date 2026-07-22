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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Runs and tracks {@link EsDataMigration}s. Migrations never run automatically: they are triggered through
 * the {@code api/es_migrations} WebService. State is stored (Gson) in the ES {@code metadatas} index, keyed
 * by version. A {@code RUNNING} migration is refreshed by polling its ES task on every {@link #status}/{@link #list}.
 */
@ServerSide
public class EsDataMigrationEngine {

  private static final Logger LOGGER = LoggerFactory.getLogger(EsDataMigrationEngine.class);
  private static final Gson GSON = new Gson();
  // The metadata "value" field is a keyword: keep the persisted state well under Lucene's ~32KB doc-values
  // per-term limit so persist() can never throw on an oversized detail and leave the state stuck at RUNNING.
  private static final int MAX_DETAIL_LENGTH = 8000;

  private final DbClient dbClient;
  private final EsClient esClient;
  private final MetadataIndex metadataIndex;
  private final System2 system2;
  private final Executor executor;
  private final Map<Long, EsDataMigration> migrationsByVersion;
  
  @Autowired(required = false)
  public EsDataMigrationEngine(DbClient dbClient, EsClient esClient, MetadataIndex metadataIndex,
    System2 system2, EsDataMigration... migrations) {
    this(dbClient, esClient, metadataIndex, system2, newBackgroundExecutor(), migrations);
  }

  @VisibleForTesting
  @Autowired(required = false)
  EsDataMigrationEngine(DbClient dbClient, EsClient esClient, MetadataIndex metadataIndex,
    System2 system2, Executor executor, EsDataMigration... migrations) {
    this.dbClient = dbClient;
    this.esClient = esClient;
    this.metadataIndex = metadataIndex;
    this.system2 = system2;
    this.executor = executor;
    this.migrationsByVersion = Arrays.stream(migrations)
      .collect(Collectors.toMap(EsDataMigration::version, m -> m, (a, b) -> {
        throw new IllegalStateException("Duplicate ES data migration version: " + a.version());
      }, TreeMap::new));
  }

  /** Single daemon thread: migrations run one at a time, off the request thread, and never block JVM shutdown. */
  private static Executor newBackgroundExecutor() {
    return Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "es-data-migration");
      t.setDaemon(true);
      return t;
    });
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
   * Submits the migration. {@code force} allows re-running a migration in any state: COMPLETED, FAILED, or a
   * RUNNING one that got wedged (e.g. because its ES task was lost to a node restart). A forced re-run submits
   * a fresh task and overwrites the tracked task id. Synchronized so two concurrent submissions on the same
   * node cannot both launch a task; the underlying query is idempotent, so a cross-node race — or a forced
   * re-run over a task that is genuinely still running — is at worst a duplicate no-op task.
   */
  public synchronized EsDataMigrationState run(long version, boolean force) {
    EsDataMigration migration = getMigration(version);
    EsDataMigrationState current = status(version);
    if (!force) {
      // IllegalArgumentException (not IllegalStateException) so the WebService maps these client-recoverable
      // conditions to HTTP 400 rather than 500 (see WebServiceEngine).
      if (current.getStatus() == Status.RUNNING) {
        throw new IllegalArgumentException("ES data migration " + version + " is already running. Use force to re-run.");
      }
      if (current.getStatus() == Status.COMPLETED) {
        throw new IllegalArgumentException("ES data migration " + version + " already completed. Use force to re-run.");
      }
    }
    // Persist RUNNING up front, then run the (potentially long) migration on a background thread.
    long startedAt = system2.now();
    EsDataMigrationState running = newState(version, Status.RUNNING, null, "indexing started");
    persist(running);
    LOGGER.info("Starting ES data migration {} ({}){}", version, migration.description(), force ? " [forced re-run]" : "");
    executor.execute(() -> runToCompletion(version, migration, startedAt));
    return withDescription(running, migration);
  }

  /**
   * Runs the migration off the request thread and persists its terminal state. If execute() instead submits an
   * asynchronous ES task, the state stays RUNNING with that task id so status() polls it to completion. A crash
   * here leaves the state at RUNNING; it can be re-run with force (the migration is idempotent).
   */
  private void runToCompletion(long version, EsDataMigration migration, long startedAt) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      EsDataMigrationExecution execution = migration.execute(dbSession);
      if (execution.asyncTaskId().isPresent()) {
        persist(newState(version, Status.RUNNING, execution.asyncTaskId().get(), null));
        LOGGER.info("ES data migration {} submitted as ES task {}", version, execution.asyncTaskId().get());
      } else {
        long durationMs = system2.now() - startedAt;
        persist(newState(version, Status.COMPLETED, null, execution.detail()).setDurationMs(durationMs));
        LOGGER.info("ES data migration {} completed in {} ms: {}", version, durationMs, execution.detail());
      }
    } catch (Exception e) {
      long durationMs = system2.now() - startedAt;
      LOGGER.error("ES data migration {} failed", version, e);
      persist(newState(version, Status.FAILED, null, e.getMessage()).setDurationMs(durationMs));
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
      Optional<String> taskJson = esClient.getTaskIfExists(state.getTaskId());
      if (taskJson.isEmpty()) {
        // ES no longer knows the task (e.g. the node running the async update_by_query restarted before its
        // result was stored, or the result was evicted). It can neither be polled nor resumed, so fail the
        // migration rather than leaving it wedged at RUNNING forever; an admin can re-run it with force=true.
        String detail = "task " + state.getTaskId()
          + " no longer exists on the cluster (node restart or evicted result); re-run with force=true";
        EsDataMigrationState failed = newState(state.getVersion(), Status.FAILED, state.getTaskId(), detail)
          .setDurationMs(system2.now() - state.getUpdatedAt());
        persist(failed);
        LOGGER.error("ES data migration {} failed: {}", state.getVersion(), detail);
        return failed;
      }
      JsonObject task = JsonParser.parseString(taskJson.get()).getAsJsonObject();
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
        JsonArray failures = response.has("failures") ? response.getAsJsonArray("failures") : new JsonArray();
        boolean hasFailures = !failures.isEmpty();
        status = hasFailures ? Status.FAILED : Status.COMPLETED;
        // "took" is the ES-measured update_by_query execution time, in millis. Only summarize the failures
        // (count + first, truncated): the raw array can hold ~1000 entries and would blow the metadata field's
        // size limit, so persist() would throw and the terminal state would never be recorded (stuck RUNNING).
        detail = "took=" + response.get("took") + "ms, updated=" + response.get("updated")
          + ", versionConflicts=" + response.get("version_conflicts")
          + (hasFailures ? (", failures=" + failures.size() + " (first: " + truncate(failures.get(0).toString(), 500) + ")") : "");
      }
      long wallClockMs = system2.now() - state.getUpdatedAt();
      EsDataMigrationState done = newState(state.getVersion(), status, state.getTaskId(), detail).setDurationMs(wallClockMs);
      persist(done);
      if (status == Status.FAILED) {
        LOGGER.error("ES data migration {} failed (task {}) after {} ms: {}", done.getVersion(), done.getTaskId(), wallClockMs, detail);
      } else {
        LOGGER.info("ES data migration {} completed (task {}) in {} ms wall-clock: {}", done.getVersion(), done.getTaskId(), wallClockMs, detail);
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
      .setDetail(truncate(detail, MAX_DETAIL_LENGTH))
      .setUpdatedAt(system2.now());
  }

  @Nullable
  private static String truncate(@Nullable String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "... [truncated]";
  }

  private static EsDataMigrationState withDescription(EsDataMigrationState state, EsDataMigration migration) {
    state.setDescription(migration.description());
    return state;
  }
}
