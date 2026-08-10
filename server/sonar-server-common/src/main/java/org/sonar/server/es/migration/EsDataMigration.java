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

import org.sonar.db.DbSession;

/**
 * A one-shot, versioned Elasticsearch data migration — a backfill of a newly added field, done either by a
 * DB-driven reindex (the only option for SonarQube's {@code _source}-less indices) or, on a {@code _source}-storing
 * index, by an asynchronous {@code update_by_query}. Implementations are registered in DI and executed on demand
 * through {@link EsDataMigrationEngine} (never automatically). State is tracked in the ES {@code metadatas} index.
 */
public interface EsDataMigration {

  /**
   * Unique and ordered version identifier. Convention: {@code YYYYMMDDNN}, e.g. {@code 2026_07_18_01L}
   * written as {@code 2026071801L}.
   */
  long version();

  /** Human-readable description of what the migration does. */
  String description();

  /**
   * Number of documents the migration would touch. Side-effect free; used for the dry-run estimate.
   */
  long estimate(DbSession dbSession);

  /**
   * Runs the migration and returns its {@link EsDataMigrationExecution outcome}: either an asynchronous ES task
   * for the engine to poll, or a synchronous completion (the common case) with a short summary. Throwing marks
   * the migration FAILED; implementations should let exceptions propagate rather than swallow a partial run.
   */
  EsDataMigrationExecution execute(DbSession dbSession);
}
