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
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.ISSUE_KEE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.TABLE_NAME;

/**
 * Reads the comments of an issue for the CSV export and removes them when the project is deleted. See CSN-204.
 */
public class CreateIndexOnRetainIssueCommentsTable extends DdlChange {

  static final String ISSUE_INDEX_NAME = "ix_cs_retain_comments_issue";

  public CreateIndexOnRetainIssueCommentsTable(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    try (Connection connection = getDatabase().getDataSource().getConnection()) {
      if (!indexExistsIgnoreCase(TABLE_NAME, ISSUE_INDEX_NAME, connection)) {
        context.execute(new CreateIndexBuilder(getDialect())
          .setTable(TABLE_NAME)
          .setName(ISSUE_INDEX_NAME)
          .addColumn(ISSUE_KEE_COLUMN, false)
          .build());
      }
    }
  }
}
