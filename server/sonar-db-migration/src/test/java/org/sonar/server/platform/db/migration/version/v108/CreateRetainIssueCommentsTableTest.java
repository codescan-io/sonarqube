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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.db.MigrationDbTester;

import static java.sql.Types.BIGINT;
import static java.sql.Types.CLOB;
import static java.sql.Types.VARCHAR;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.ISSUE_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.CHANGE_DATA_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.ISSUE_CHANGE_CREATION_DATE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.ISSUE_KEE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.TABLE_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.USER_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainIssueCommentsTable.UUID_COLUMN;

class CreateRetainIssueCommentsTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateRetainIssueCommentsTable.class);

  private final CreateRetainIssueCommentsTable underTest = new CreateRetainIssueCommentsTable(db.database());

  @Test
  void execute_createsTheTable() throws SQLException {
    db.assertTableDoesNotExist(TABLE_NAME);

    underTest.execute();

    db.assertTableExists(TABLE_NAME);
    db.assertPrimaryKey(TABLE_NAME, "pk_cs_retain_issue_comments", UUID_COLUMN);
    db.assertIndex(TABLE_NAME, ISSUE_INDEX_NAME, "issue_kee");
    db.assertColumnDefinition(TABLE_NAME, UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_KEE_COLUMN, VARCHAR, 50, false);
    db.assertColumnDefinition(TABLE_NAME, CHANGE_DATA_COLUMN, CLOB, null, true);
    db.assertColumnDefinition(TABLE_NAME, USER_UUID_COLUMN, VARCHAR, 255, true);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_CHANGE_CREATION_DATE_COLUMN, BIGINT, null, true);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertTableExists(TABLE_NAME);
  }
}
