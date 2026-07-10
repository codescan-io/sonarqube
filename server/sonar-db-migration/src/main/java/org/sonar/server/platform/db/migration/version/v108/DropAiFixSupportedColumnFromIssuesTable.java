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
package org.sonar.server.platform.db.migration.version.v108;

import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.step.DropColumnChange;

/**
 * Drops the legacy boolean {@code ai_fix_supported} column from the {@code issues} table. It is
 * replaced by the JSON {@code issue_ai_metadata} column (see
 * {@link AddIssueAiMetadataColumnToIssuesTable}). The drop is guarded by an existence check, so it
 * is a no-op if the column was never created.
 */
public class DropAiFixSupportedColumnFromIssuesTable extends DropColumnChange {

  private static final String ISSUES_TABLE_NAME = "issues";
  private static final String AI_FIX_SUPPORTED_COLUMN_NAME = "ai_fix_supported";

  public DropAiFixSupportedColumnFromIssuesTable(Database db) {
    super(db, ISSUES_TABLE_NAME, AI_FIX_SUPPORTED_COLUMN_NAME);
  }
}
