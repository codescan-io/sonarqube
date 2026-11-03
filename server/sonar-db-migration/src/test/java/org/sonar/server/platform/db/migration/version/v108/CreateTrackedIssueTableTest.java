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
import static java.sql.Types.INTEGER;
import static java.sql.Types.VARCHAR;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.BASE_BRANCH_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.BRANCH_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.BRANCH_TYPE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.BRANCH_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.CAPTURED_AT_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_CLOSE_DATE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ISSUE_STATUS_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.ORGANIZATION_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.PROJECT_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.TABLE_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.UUID_COLUMN;

class CreateTrackedIssueTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateTrackedIssueTable.class);

  private final CreateTrackedIssueTable underTest = new CreateTrackedIssueTable(db.database());

  @Test
  void execute_createsTheTable() throws SQLException {
    db.assertTableDoesNotExist(TABLE_NAME);

    underTest.execute();

    db.assertTableExists(TABLE_NAME);
    db.assertPrimaryKey(TABLE_NAME, "pk_cs_tracked_issue", UUID_COLUMN);
    db.assertColumnDefinition(TABLE_NAME, UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, ORGANIZATION_UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, PROJECT_UUID_COLUMN, VARCHAR, 50, false);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_KEY_COLUMN, VARCHAR, 50, false);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_STATUS_COLUMN, VARCHAR, 20, false);
    db.assertColumnDefinition(TABLE_NAME, CAPTURED_AT_COLUMN, BIGINT, null, false);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_CLOSE_DATE_COLUMN, BIGINT, null, true);
    db.assertColumnDefinition(TABLE_NAME, "line", INTEGER, null, true);
    db.assertColumnDefinition(TABLE_NAME, "message", VARCHAR, 4000, true);
  }

  /**
   * The uuid of a branch is the only reference the row may lose, because the branch row is deleted while the history
   * survives it. Everything a reader needs about the branch is kept by value.
   */
  @Test
  void execute_keepsTheBranchByValueAndOnlyItsUuidNullable() throws SQLException {
    underTest.execute();

    db.assertColumnDefinition(TABLE_NAME, BRANCH_UUID_COLUMN, VARCHAR, 50, true);
    db.assertColumnDefinition(TABLE_NAME, BRANCH_KEY_COLUMN, VARCHAR, 255, false);
    db.assertColumnDefinition(TABLE_NAME, BRANCH_TYPE_COLUMN, VARCHAR, 20, false);
    db.assertColumnDefinition(TABLE_NAME, BASE_BRANCH_KEY_COLUMN, VARCHAR, 255, true);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertTableExists(TABLE_NAME);
  }
}
