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

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.es.IndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EsDataMigrationEngineIT {

  @Rule
  public EsTester es = EsTester.createCustom(new MetadataBridge());
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final MetadataIndexImpl metadataIndex = new MetadataIndexImpl(es.client());

  private EsDataMigrationEngine newEngine(EsDataMigration... migrations) {
    // direct (synchronous) executor so the background run completes inline -> deterministic assertions
    return new EsDataMigrationEngine(db.getDbClient(), es.client(), metadataIndex, System2.INSTANCE, Runnable::run, migrations);
  }

  @Test
  public void list_reports_pending_for_unrun_migration() {
    EsDataMigrationEngine engine = newEngine(fakeMigration(1L, EsDataMigrationExecution.completed("nothing to do")));
    assertThat(engine.list())
      .extracting(EsDataMigrationState::getVersion, EsDataMigrationState::getStatus)
      .containsExactly(tuple(1L, EsDataMigrationState.Status.PENDING));
    // never-run migration has no duration yet
    assertThat(engine.list().get(0).getDurationMs()).isNull();
  }

  @Test
  public void run_returns_running_immediately_then_completes_in_background() {
    EsDataMigrationEngine engine = newEngine(fakeMigration(1L, EsDataMigrationExecution.completed("nothing to do")));
    // run() returns immediately with RUNNING ("indexing started"); the work runs on the background executor,
    // which is synchronous in the test, so it has already finished by the time we poll status()
    EsDataMigrationState state = engine.run(1L, false);
    assertThat(state.getStatus()).isEqualTo(EsDataMigrationState.Status.RUNNING);
    assertThat(state.getDetail()).isEqualTo("indexing started");

    EsDataMigrationState done = engine.status(1L);
    assertThat(done.getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
    // a completed run records how long it took
    assertThat(done.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
  }

  @Test
  public void run_refuses_completed_migration_without_force() {
    EsDataMigrationEngine engine = newEngine(fakeMigration(1L, EsDataMigrationExecution.completed("nothing to do")));
    engine.run(1L, false); // completes synchronously via the direct executor
    // completed-without-force is a client-recoverable condition -> IllegalArgumentException (HTTP 400), not 500
    assertThatThrownBy(() -> engine.run(1L, false)).isInstanceOf(IllegalArgumentException.class);
    // force allows re-run: run() returns RUNNING, the background work then completes
    assertThat(engine.run(1L, true).getStatus()).isEqualTo(EsDataMigrationState.Status.RUNNING);
    assertThat(engine.status(1L).getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
  }

  @Test
  public void run_unknown_version_fails() {
    assertThatThrownBy(() -> newEngine().run(999L, false)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void status_transitions_running_async_migration_to_completed() {
    EsClient esClientMock = mock(EsClient.class);
    EsDataMigrationEngine engine = new EsDataMigrationEngine(db.getDbClient(), esClientMock, metadataIndex, System2.INSTANCE,
      Runnable::run, fakeMigration(1L, EsDataMigrationExecution.async("task-1")));

    EsDataMigrationState running = engine.run(1L, false);
    assertThat(running.getStatus()).isEqualTo(EsDataMigrationState.Status.RUNNING);
    // run() returns before the background task records the ES task id; duration is only set at a terminal state
    assertThat(running.getDurationMs()).isNull();

    when(esClientMock.getTaskIfExists("task-1")).thenReturn(Optional.of(
      "{\"completed\": true, \"response\": {\"took\": 42, \"updated\": 5, \"version_conflicts\": 0, \"failures\": []}}"));

    EsDataMigrationState done = engine.status(1L);
    assertThat(done.getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
    assertThat(done.getDetail()).contains("took=42").contains("updated=5");
    assertThat(done.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    // the terminal state is persisted, so a later status() call is stable
    assertThat(engine.status(1L).getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
  }

  @Test
  public void status_keeps_async_migration_running_and_surfaces_progress_until_task_completes() {
    EsClient esClientMock = mock(EsClient.class);
    EsDataMigrationEngine engine = new EsDataMigrationEngine(db.getDbClient(), esClientMock, metadataIndex, System2.INSTANCE,
      Runnable::run, fakeMigration(1L, EsDataMigrationExecution.async("task-1")));
    engine.run(1L, false);

    when(esClientMock.getTaskIfExists("task-1")).thenReturn(Optional.of(
      "{\"completed\": false, \"task\": {\"status\": {\"updated\": 3, \"total\": 10}}}"));

    EsDataMigrationState state = engine.status(1L);
    assertThat(state.getStatus()).isEqualTo(EsDataMigrationState.Status.RUNNING);
    assertThat(state.getDetail()).contains("in progress").contains("updated=3").contains("total=10");
    // not terminal yet -> no duration recorded
    assertThat(state.getDurationMs()).isNull();
  }

  @Test
  public void status_fails_async_migration_when_task_no_longer_exists() {
    EsClient esClientMock = mock(EsClient.class);
    EsDataMigrationEngine engine = new EsDataMigrationEngine(db.getDbClient(), esClientMock, metadataIndex, System2.INSTANCE,
      Runnable::run, fakeMigration(1L, EsDataMigrationExecution.async("task-1")));
    engine.run(1L, false);

    when(esClientMock.getTaskIfExists("task-1")).thenReturn(Optional.empty());

    EsDataMigrationState state = engine.status(1L);
    assertThat(state.getStatus()).isEqualTo(EsDataMigrationState.Status.FAILED);
    assertThat(state.getDetail()).contains("no longer exists");
    assertThat(state.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
  }

  private static EsDataMigration fakeMigration(long version, EsDataMigrationExecution executeResult) {
    return new EsDataMigration() {
      @Override
      public long version() {
        return version;
      }

      @Override
      public String description() {
        return "fake";
      }

      @Override
      public long estimate(DbSession dbSession) {
        return 0;
      }

      @Override
      public EsDataMigrationExecution execute(DbSession dbSession) {
        return executeResult;
      }
    };
  }

  private static class MetadataBridge implements IndexDefinition {
    @Override
    public void define(IndexDefinitionContext context) {
      new MetadataIndexDefinition(new MapSettings().asConfig()).define(context);
    }
  }
}
