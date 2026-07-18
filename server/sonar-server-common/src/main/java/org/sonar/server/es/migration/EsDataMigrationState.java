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

import javax.annotation.Nullable;

/**
 * Persistent state of an {@link EsDataMigration}, Gson-serialized into the ES {@code metadatas} index.
 * {@code description} is {@code transient} (not persisted); it is refilled from the migration registry on read.
 */
public class EsDataMigrationState {

  public enum Status {
    PENDING, RUNNING, COMPLETED, FAILED
  }

  private long version;
  private transient String description;
  private Status status;
  @Nullable
  private String taskId;
  @Nullable
  private String detail;
  private long updatedAt;

  public long getVersion() {
    return version;
  }

  public EsDataMigrationState setVersion(long version) {
    this.version = version;
    return this;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  public EsDataMigrationState setDescription(@Nullable String description) {
    this.description = description;
    return this;
  }

  public Status getStatus() {
    return status;
  }

  public EsDataMigrationState setStatus(Status status) {
    this.status = status;
    return this;
  }

  @Nullable
  public String getTaskId() {
    return taskId;
  }

  public EsDataMigrationState setTaskId(@Nullable String taskId) {
    this.taskId = taskId;
    return this;
  }

  @Nullable
  public String getDetail() {
    return detail;
  }

  public EsDataMigrationState setDetail(@Nullable String detail) {
    this.detail = detail;
    return this;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public EsDataMigrationState setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
