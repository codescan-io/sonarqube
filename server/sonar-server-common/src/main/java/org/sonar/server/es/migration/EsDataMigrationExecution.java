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
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Outcome of running an {@link EsDataMigration}. A migration completes in one of two ways:
 * <ul>
 *   <li>{@link #async(String)} — it submitted an asynchronous Elasticsearch task (e.g. {@code update_by_query})
 *       and returns its id; {@link EsDataMigrationEngine} then tracks the migration to completion by polling
 *       that task. NB: this mode only works on indices that store {@code _source}; none of SonarQube's indices
 *       currently do, so today's migrations use the synchronous mode.</li>
 *   <li>{@link #completed(String)} — it did its work synchronously (e.g. a DB-driven reindex) and returns a
 *       short human-readable summary; the engine records the migration as COMPLETED immediately.</li>
 * </ul>
 */
public final class EsDataMigrationExecution {

  private final String asyncTaskId;
  private final String detail;

  private EsDataMigrationExecution(@Nullable String asyncTaskId, @Nullable String detail) {
    this.asyncTaskId = asyncTaskId;
    this.detail = detail;
  }

  /** The migration submitted an asynchronous ES task; the engine polls {@code taskId} to completion. */
  public static EsDataMigrationExecution async(String taskId) {
    return new EsDataMigrationExecution(requireNonNull(taskId, "taskId"), null);
  }

  /** The migration finished synchronously; {@code detail} summarizes what it did (e.g. "reindexed 12 issue(s)"). */
  public static EsDataMigrationExecution completed(String detail) {
    return new EsDataMigrationExecution(null, detail);
  }

  /** Present only for the asynchronous case: the id of the ES task to poll. */
  public Optional<String> asyncTaskId() {
    return Optional.ofNullable(asyncTaskId);
  }

  /** Human-readable summary of a synchronous completion; {@code null} for the asynchronous case. */
  @CheckForNull
  public String detail() {
    return detail;
  }
}
