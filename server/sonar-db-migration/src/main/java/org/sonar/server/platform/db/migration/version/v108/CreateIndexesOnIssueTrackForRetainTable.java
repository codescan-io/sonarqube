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
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.BRANCH_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.FIXED_AT_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.ISSUE_KEE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.RETAINED_STATUS_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.TABLE_NAME;

/**
 * The unique index keeps one row per issue and status. It is keyed by the issue key alone, not by branch, because
 * issue keys are unique across branches and a branch key changes when the branch is renamed. The second index serves
 * the CSV export and project deletion. See CSN-204.
 */
public class CreateIndexesOnIssueTrackForRetainTable extends DdlChange {

  static final String UNIQUE_INDEX_NAME = "uq_cs_issue_track_retain_key";
  static final String BRANCH_INDEX_NAME = "ix_cs_issue_track_retain_br";

  public CreateIndexesOnIssueTrackForRetainTable(Database db) {
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
          .addColumn(ISSUE_KEE_COLUMN, false)
          .addColumn(RETAINED_STATUS_COLUMN, false)
          .build());
      }
      if (!indexExistsIgnoreCase(TABLE_NAME, BRANCH_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(BRANCH_INDEX_NAME)
          .addColumn(BRANCH_UUID_COLUMN, false)
          .addColumn(FIXED_AT_COLUMN, false)
          .build());
      }
    }
  }
}
