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

import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.server.es.EsTester;
import org.sonar.server.es.IndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexDefinition;
import org.sonar.server.es.metadata.MetadataIndexImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

public class EsDataMigrationEngineIT {

  @Rule
  public EsTester es = EsTester.createCustom(new MetadataBridge());
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final MetadataIndexImpl metadataIndex = new MetadataIndexImpl(es.client());

  private EsDataMigrationEngine newEngine(EsDataMigration... migrations) {
    return new EsDataMigrationEngine(db.getDbClient(), es.client(), metadataIndex, System2.INSTANCE, migrations);
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
  public void run_with_no_work_marks_completed_immediately() {
    EsDataMigrationEngine engine = newEngine(fakeMigration(1L, EsDataMigrationExecution.completed("nothing to do")));
    EsDataMigrationState state = engine.run(1L, false);
    assertThat(state.getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
    // a completed run records how long it took
    assertThat(state.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    assertThat(engine.status(1L).getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
  }

  @Test
  public void run_refuses_completed_migration_without_force() {
    EsDataMigrationEngine engine = newEngine(fakeMigration(1L, EsDataMigrationExecution.completed("nothing to do")));
    engine.run(1L, false);
    // completed-without-force is a client-recoverable condition -> IllegalArgumentException (HTTP 400), not 500
    assertThatThrownBy(() -> engine.run(1L, false)).isInstanceOf(IllegalArgumentException.class);
    // force allows re-run
    assertThat(engine.run(1L, true).getStatus()).isEqualTo(EsDataMigrationState.Status.COMPLETED);
  }

  @Test
  public void run_unknown_version_fails() {
    assertThatThrownBy(() -> newEngine().run(999L, false)).isInstanceOf(IllegalArgumentException.class);
  }

  // NOTE: the RUNNING -> COMPLETED transition against a real ES task is covered by
  // BackfillCodefixStatusMigrationIT (Task 6) + the end-to-end pass — not here.

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
