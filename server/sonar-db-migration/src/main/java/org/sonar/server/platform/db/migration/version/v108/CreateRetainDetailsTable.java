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

import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.newVarcharColumnDefBuilder;

/**
 * One row per branch or pull request that has retained issues. It keeps the branch's name and type, which are gone
 * from 'project_branches' once the branch is deleted. Keyed by the branch uuid: a branch re-created with the same
 * name gets a new uuid and therefore its own row. See CSN-204.
 */
public class CreateRetainDetailsTable extends CreateTableChange {

  static final String TABLE_NAME = "cs_retain_details";
  static final String BRANCH_UUID_COLUMN = "branch_uuid";
  static final String PROJECT_UUID_COLUMN = "project_uuid";
  static final String BRANCH_KEY_COLUMN = "branch_key";
  static final String BRANCH_TYPE_COLUMN = "branch_type";
  static final String TARGET_BRANCH_KEY_COLUMN = "target_branch_key";

  private static final int ENTITY_UUID_SIZE = 50;
  private static final int BRANCH_KEY_SIZE = 255;
  private static final int BRANCH_TYPE_SIZE = 12;

  public CreateRetainDetailsTable(Database db) {
    super(db, TABLE_NAME);
  }

  @Override
  public void execute(Context context, String tableName) throws SQLException {
    context.execute(new CreateTableBuilder(getDialect(), tableName)
      .addPkColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_UUID_COLUMN).setIsNullable(false).setLimit(ENTITY_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(PROJECT_UUID_COLUMN).setIsNullable(false).setLimit(ENTITY_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_KEY_COLUMN).setIsNullable(false).setLimit(BRANCH_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_TYPE_COLUMN).setIsNullable(false).setLimit(BRANCH_TYPE_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(TARGET_BRANCH_KEY_COLUMN).setIsNullable(true).setLimit(BRANCH_KEY_SIZE).build())
      .build());
  }
}
