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
import static org.sonar.server.platform.db.migration.def.IntegerColumnDef.newIntegerColumnDefBuilder;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.MAX_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.USER_UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.UUID_SIZE;
import static org.sonar.server.platform.db.migration.def.VarcharColumnDef.newVarcharColumnDefBuilder;

/**
 * History of issues that reached a terminal resolution, kept for the lifetime of the project rather than for the
 * closed-issue retention window. Rows outlive the 'issues' row they describe, and outlive the branch or pull request
 * that produced it, so every reference to a branch, a component or a rule is stored by value and not as a foreign key.
 * See CSN-204.
 */
public class CreateTrackedIssueTable extends CreateTableChange {

  static final String TABLE_NAME = "cs_tracked_issue";
  static final String UUID_COLUMN = "uuid";
  static final String ORGANIZATION_UUID_COLUMN = "organization_uuid";
  static final String PROJECT_UUID_COLUMN = "project_uuid";
  static final String BRANCH_UUID_COLUMN = "branch_uuid";
  static final String BRANCH_KEY_COLUMN = "branch_key";
  static final String BRANCH_TYPE_COLUMN = "branch_type";
  static final String BASE_BRANCH_KEY_COLUMN = "base_branch_key";
  static final String ISSUE_KEY_COLUMN = "issue_key";
  static final String ISSUE_STATUS_COLUMN = "issue_status";
  static final String ISSUE_CLOSE_DATE_COLUMN = "issue_close_date";
  static final String CAPTURED_AT_COLUMN = "captured_at";

  private static final String RULE_UUID_COLUMN = "rule_uuid";
  private static final String RULE_KEY_COLUMN = "rule_key";
  private static final String COMPONENT_KEY_COLUMN = "component_key";
  private static final String LINE_COLUMN = "line";
  private static final String MESSAGE_COLUMN = "message";
  private static final String SEVERITY_COLUMN = "severity";
  private static final String ISSUE_TYPE_COLUMN = "issue_type";
  private static final String CLEAN_CODE_ATTRIBUTE_COLUMN = "clean_code_attribute";
  private static final String IMPACTS_COLUMN = "impacts";
  private static final String TAGS_COLUMN = "tags";
  private static final String AUTHOR_LOGIN_COLUMN = "author_login";
  private static final String ASSIGNEE_UUID_COLUMN = "assignee_uuid";
  private static final String EFFORT_COLUMN = "effort";
  private static final String STATUS_COLUMN = "status";
  private static final String RESOLUTION_COLUMN = "resolution";
  private static final String ISSUE_CREATION_DATE_COLUMN = "issue_creation_date";
  private static final String CAPTURED_BY_COLUMN = "captured_by";
  private static final String CAPTURED_BY_UUID_COLUMN = "captured_by_uuid";

  private static final int ENTITY_UUID_SIZE = 50;
  private static final int ISSUE_KEY_SIZE = 50;
  private static final int BRANCH_KEY_SIZE = 255;
  private static final int RULE_KEY_SIZE = 255;
  private static final int COMPONENT_KEY_SIZE = 1000;
  private static final int SEVERITY_SIZE = 10;
  private static final int STATUS_SIZE = 20;
  private static final int CLEAN_CODE_ATTRIBUTE_SIZE = 40;
  private static final int AUTHOR_LOGIN_SIZE = 255;

  public CreateTrackedIssueTable(Database db) {
    super(db, TABLE_NAME);
  }

  @Override
  public void execute(Context context, String tableName) throws SQLException {
    context.execute(new CreateTableBuilder(getDialect(), tableName)
      .addPkColumn(newVarcharColumnDefBuilder().setColumnName(UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ORGANIZATION_UUID_COLUMN).setIsNullable(false).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(PROJECT_UUID_COLUMN).setIsNullable(false).setLimit(ENTITY_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_UUID_COLUMN).setIsNullable(true).setLimit(ENTITY_UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_KEY_COLUMN).setIsNullable(false).setLimit(BRANCH_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BRANCH_TYPE_COLUMN).setIsNullable(false).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(BASE_BRANCH_KEY_COLUMN).setIsNullable(true).setLimit(BRANCH_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ISSUE_KEY_COLUMN).setIsNullable(false).setLimit(ISSUE_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(RULE_UUID_COLUMN).setIsNullable(true).setLimit(UUID_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(RULE_KEY_COLUMN).setIsNullable(true).setLimit(RULE_KEY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(COMPONENT_KEY_COLUMN).setIsNullable(true).setLimit(COMPONENT_KEY_SIZE).build())
      .addColumn(newIntegerColumnDefBuilder().setColumnName(LINE_COLUMN).setIsNullable(true).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(MESSAGE_COLUMN).setIsNullable(true).setLimit(MAX_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(SEVERITY_COLUMN).setIsNullable(true).setLimit(SEVERITY_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ISSUE_TYPE_COLUMN).setIsNullable(true).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(CLEAN_CODE_ATTRIBUTE_COLUMN).setIsNullable(true).setLimit(CLEAN_CODE_ATTRIBUTE_SIZE).build())
      .addColumn(newClobColumnDefBuilder().setColumnName(IMPACTS_COLUMN).setIsNullable(true).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(TAGS_COLUMN).setIsNullable(true).setLimit(MAX_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(AUTHOR_LOGIN_COLUMN).setIsNullable(true).setLimit(AUTHOR_LOGIN_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ASSIGNEE_UUID_COLUMN).setIsNullable(true).setLimit(USER_UUID_SIZE).build())
      .addColumn(newIntegerColumnDefBuilder().setColumnName(EFFORT_COLUMN).setIsNullable(true).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(ISSUE_STATUS_COLUMN).setIsNullable(false).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(STATUS_COLUMN).setIsNullable(true).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(RESOLUTION_COLUMN).setIsNullable(true).setLimit(STATUS_SIZE).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(ISSUE_CREATION_DATE_COLUMN).setIsNullable(true).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(ISSUE_CLOSE_DATE_COLUMN).setIsNullable(true).build())
      .addColumn(newBigIntegerColumnDefBuilder().setColumnName(CAPTURED_AT_COLUMN).setIsNullable(false).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(CAPTURED_BY_COLUMN).setIsNullable(false).setLimit(STATUS_SIZE).build())
      .addColumn(newVarcharColumnDefBuilder().setColumnName(CAPTURED_BY_UUID_COLUMN).setIsNullable(true).setLimit(USER_UUID_SIZE).build())
      .build());
  }
}
