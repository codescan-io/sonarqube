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

import java.sql.Connection;
import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.sql.CreateIndexBuilder;
import org.sonar.server.platform.db.migration.step.DdlChange;

import static org.sonar.db.DatabaseUtils.indexExistsIgnoreCase;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.BRANCH_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.CAPTURED_AT_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_CLOSE_DATE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_STATUS_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ORGANIZATION_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.PROJECT_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.TABLE_NAME;

/**
 * The unique index is the deduplication mechanism: capture inserts only when no row matches, so a retried compute
 * engine task or a repeated transition cannot add a second row for the same issue in the same status. See CSN-253,
 * CSN-254.
 */
public class CreateIndexesOnTrackedIssueTable extends DdlChange {

  static final String UNIQUE_INDEX_NAME = "uq_cs_tracked_issue";
  static final String PROJECT_INDEX_NAME = "ix_cs_tracked_issue_project";
  static final String STATUS_INDEX_NAME = "ix_cs_tracked_issue_status";
  static final String ORGANIZATION_INDEX_NAME = "ix_cs_tracked_issue_org";

  public CreateIndexesOnTrackedIssueTable(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    try (Connection connection = getDatabase().getDataSource().getConnection()) {
      if (!indexExistsIgnoreCase(TABLE_NAME, UNIQUE_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(UNIQUE_INDEX_NAME)
          .setUnique(true)
          .addColumn(PROJECT_UUID_COLUMN, false)
          .addColumn(BRANCH_KEY_COLUMN, false)
          .addColumn(ISSUE_KEY_COLUMN, false)
          .addColumn(ISSUE_STATUS_COLUMN, false)
          .build());
      }
      if (!indexExistsIgnoreCase(TABLE_NAME, PROJECT_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(PROJECT_INDEX_NAME)
          .addColumn(PROJECT_UUID_COLUMN, false)
          .addColumn(CAPTURED_AT_COLUMN, false)
          .build());
      }
      if (!indexExistsIgnoreCase(TABLE_NAME, STATUS_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(STATUS_INDEX_NAME)
          .addColumn(PROJECT_UUID_COLUMN, false)
          .addColumn(ISSUE_STATUS_COLUMN, false)
          .addColumn(ISSUE_CLOSE_DATE_COLUMN, false)
          .build());
      }
      if (!indexExistsIgnoreCase(TABLE_NAME, ORGANIZATION_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(ORGANIZATION_INDEX_NAME)
          .addColumn(ORGANIZATION_UUID_COLUMN, false)
          .build());
      }
    }
  }
}
