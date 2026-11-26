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
import org.sonar.server.platform.db.migration.sql.CreateIndexBuilder;
import org.sonar.server.platform.db.migration.sql.CreateTableBuilder;
import org.sonar.server.platform.db.migration.step.CreateTableChange;

import static org.sonar.server.platform.db.migration.def.BigIntegerColumnDef.newBigIntegerColumnDefBuilder;
import static org.sonar.server.platform.db.migration.def.TinyIntColumnDef.newTinyIntColumnDefBuilder;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.MAX_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.USER_UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.newVarcharColumnDefBuilder;

/**
 * One row per retained issue, keyed by the issue key alone: it is unique across branches, and a branch key changes when
 * the branch is renamed. It holds only what the CSV export needs once the issue, its file and its branch are
 * deleted; rules, users and the project are looked up by uuid because those rows outlive the issue. See CSN-204.
 */
public class CreateIssueTrackForRetainTable extends CreateTableChange {

  static final String TABLE_NAME = "cs_issue_track_for_retain";
  static final String BRANCH_UUID_COLUMN = "branch_uuid";
  static final String ISSUE_KEE_COLUMN = "issue_kee";
  static final String RETAINED_STATUS_COLUMN = "retained_status";
  static final String RULE_UUID_COLUMN = "rule_uuid";
  static final String COMPONENT_KEY_COLUMN = "component_key";
  static final String MESSAGE_COLUMN = "message";
  static final String SEVERITY_COLUMN = "severity";
  static final String ISSUE_TYPE_COLUMN = "issue_type";
  static final String TAGS_COLUMN = "tags";
  static final String ASSIGNEE_UUID_COLUMN = "assignee_uuid";
  static final String STATUS_MARKED_BY_UUID_COLUMN = "status_marked_by_uuid";
  static final String ISSUE_CREATION_DATE_COLUMN = "issue_creation_date";
  static final String STATUS_CHANGED_AT_COLUMN = "status_changed_at";
  /** Reads by branch: the CSV export, the comment copy at branch deletion and project deletion. */
  static final String BRANCH_INDEX_NAME = "ix_cs_issue_track_retain_br";

  private static final int ENTITY_UUID_SIZE = 50;
  private static final int ISSUE_KEE_SIZE = 50;
  private static final int STATUS_SIZE = 20;
  private static final int COMPONENT_KEY_SIZE = 1000;
  private static final int SEVERITY_SIZE = 10;

  public CreateIssueTrackForRetainTable(Database db) {
    super(db, TABLE_NAME);
  }

  @Override
  public void execute(Context context, String tableName) throws SQLException {
    context.execute(new CreateTableBuilder(getDialect(), tableName)
      .addPkColumn(newVarcharColumnDefBuilder().setColumnName(ISSUE_KEE_COLUMN).setIsNullable(false).setLimit(ISSUE_KEE_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(RETAINED_STATUS_COLUMN).setIsNullable(false).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_UUID_COLUMN).setIsNullable(false).setLimit(ENTITY_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(RULE_UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(COMPONENT_KEY_COLUMN).setIsNullable(true).setLimit(COMPONENT_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(MESSAGE_COLUMN).setIsNullable(true).setLimit(MAX_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(SEVERITY_COLUMN).setIsNullable(true).setLimit(SEVERITY_SIZE).build())
      .addColumn(newTinyIntColumnDefBuilder().setColumnName(ISSUE_TYPE_COLUMN).setIsNullable(true).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(TAGS_COLUMN).setIsNullable(true).setLimit(MAX_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ASSIGNEE_UUID_COLUMN).setIsNullable(true).setLimit(USER_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(STATUS_MARKED_BY_UUID_COLUMN).setIsNullable(true).setLimit(USER_UUID_SIZE).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(ISSUE_CREATION_DATE_COLUMN).setIsNullable(true).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(STATUS_CHANGED_AT_COLUMN).setIsNullable(false).build())
      .build());
    context.execute(new CreateIndexBuilder(getDialect())
      .setTable(tableName)
      .setName(BRANCH_INDEX_NAME)
      .addColumn(BRANCH_UUID_COLUMN, false)
      .build());
  }
}
