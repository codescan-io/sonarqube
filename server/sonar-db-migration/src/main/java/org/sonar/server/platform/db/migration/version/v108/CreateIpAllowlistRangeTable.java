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

import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.server.platform.db.migration.sql.CreateTableBuilder;
import org.sonar.server.platform.db.migration.step.CreateTableChange;

import static org.sonar.server.platform.db.migration.def.BigIntegerColumnDef.newBigIntegerColumnDefBuilder;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.newVarcharColumnDefBuilder;

/**
 * Same table as codescanng's Flyway migration V83__create_ip_allowlist.sql, which writes it. Skipped when that
 * migration already created it.
 */
public class CreateIpAllowlistRangeTable extends CreateTableChange {

  static final String TABLE_NAME = "cs_ip_allowlist_range";
  static final String UUID_COLUMN = "uuid";
  static final String ORGANIZATION_UUID_COLUMN = "organization_uuid";
  static final String CIDR_COLUMN = "cidr";
  static final String DESCRIPTION_COLUMN = "description";
  static final String CREATED_BY_COLUMN = "created_by";
  static final String CREATED_AT_COLUMN = "created_at";
  private static final int CIDR_SIZE = 43;
  private static final int DESCRIPTION_SIZE = 255;
  private static final int CREATED_BY_SIZE = 255;

  public CreateIpAllowlistRangeTable(Database db) {
    super(db, TABLE_NAME);
  }

  @Override
  public void execute(Context context, String tableName) throws SQLException {
    context.execute(new CreateTableBuilder(getDialect(), tableName)
      .addPkColumn(newVarcharColumnDefBuilder().setColumnName(UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ORGANIZATION_UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(CIDR_COLUMN).setIsNullable(false).setLimit(CIDR_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(DESCRIPTION_COLUMN).setIsNullable(false).setLimit(DESCRIPTION_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(CREATED_BY_COLUMN).setIsNullable(true).setLimit(CREATED_BY_SIZE).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(CREATED_AT_COLUMN).setIsNullable(false).build())
      .build());
  }
}
