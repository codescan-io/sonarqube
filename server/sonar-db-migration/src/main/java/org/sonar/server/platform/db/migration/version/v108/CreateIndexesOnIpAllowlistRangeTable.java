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
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.CIDR_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.ORGANIZATION_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.TABLE_NAME;

/**
 * Same names as codescanng's Flyway migration V83__create_ip_allowlist.sql, so its CREATE ... IF NOT EXISTS
 * statements skip what this step created, and this step skips what V83 created. V83 makes the pair unique with a
 * constraint; Postgres backs that constraint with an index of the same name, which is what is looked for here.
 */
public class CreateIndexesOnIpAllowlistRangeTable extends DdlChange {

  static final String UNIQUE_INDEX_NAME = "uq_cs_ip_allowlist_range";
  static final String ORGANIZATION_INDEX_NAME = "ix_cs_ip_allowlist_range_org";

  public CreateIndexesOnIpAllowlistRangeTable(Database db) {
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
          .addColumn(ORGANIZATION_UUID_COLUMN, false)
          .addColumn(CIDR_COLUMN, false)
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
