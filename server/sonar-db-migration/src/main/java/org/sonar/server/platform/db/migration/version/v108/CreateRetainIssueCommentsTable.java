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
import static org.sonar.server.platform.db.migration.def.ClobColumnDef.newClobColumnDefBuilder;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.USER_UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.newVarcharColumnDefBuilder;

/**
 * Comments of retained issues, copied from 'issue_changes' just before the issue is deleted. The primary key is the
 * source row's uuid, so a comment can never be copied twice. Change history is not kept. See CSN-204.
 */
public class CreateRetainIssueCommentsTable extends CreateTableChange {

  static final String TABLE_NAME = "cs_retain_issue_comments";
  static final String UUID_COLUMN = "uuid";
  static final String ISSUE_KEE_COLUMN = "issue_kee";
  static final String CHANGE_DATA_COLUMN = "change_data";
  static final String USER_UUID_COLUMN = "user_uuid";
  static final String ISSUE_CHANGE_CREATION_DATE_COLUMN = "issue_change_creation_date";

  private static final int ISSUE_KEE_SIZE = 50;

  public CreateRetainIssueCommentsTable(Database db) {
    super(db, TABLE_NAME);
  }

  @Override
  public void execute(Context context, String tableName) throws SQLException {
    context.execute(new CreateTableBuilder(getDialect(), tableName)
      .addPkColumn(newVarcharColumnDefBuilder().setColumnName(UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ISSUE_KEE_COLUMN).setIsNullable(false).setLimit(ISSUE_KEE_SIZE).build())
      .addColumn(newClobColumnDefBuilder().setColumnName(CHANGE_DATA_COLUMN).setIsNullable(true).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(USER_UUID_COLUMN).setIsNullable(true).setLimit(USER_UUID_SIZE).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(ISSUE_CHANGE_CREATION_DATE_COLUMN).setIsNullable(true).build())
      .build());
  }
}
