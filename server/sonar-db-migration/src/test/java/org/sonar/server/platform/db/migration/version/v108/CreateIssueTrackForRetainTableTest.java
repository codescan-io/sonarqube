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
import static java.sql.Types.TINYINT;
import static java.sql.Types.VARCHAR;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.ASSIGNEE_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.BRANCH_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.COMPONENT_KEY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.FIXED_AT_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.ISSUE_CREATION_DATE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.ISSUE_KEE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.ISSUE_TYPE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.MESSAGE_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.RETAINED_STATUS_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.RULE_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.SEVERITY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.STATUS_MARKED_BY_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.TABLE_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.TAGS_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.UUID_COLUMN;

class CreateIssueTrackForRetainTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIssueTrackForRetainTable.class);

  private final CreateIssueTrackForRetainTable underTest = new CreateIssueTrackForRetainTable(db.database());

  @Test
  void execute_createsTheTableWithEveryColumnTheExportNeeds() throws SQLException {
    db.assertTableDoesNotExist(TABLE_NAME);

    underTest.execute();

    db.assertTableExists(TABLE_NAME);
    db.assertPrimaryKey(TABLE_NAME, "pk_cs_issue_track_for_retain", UUID_COLUMN);
    db.assertColumnDefinition(TABLE_NAME, UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, BRANCH_UUID_COLUMN, VARCHAR, 50, false);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_KEE_COLUMN, VARCHAR, 50, false);
    db.assertColumnDefinition(TABLE_NAME, RETAINED_STATUS_COLUMN, VARCHAR, 20, false);
    db.assertColumnDefinition(TABLE_NAME, RULE_UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, COMPONENT_KEY_COLUMN, VARCHAR, 1000, false);
    db.assertColumnDefinition(TABLE_NAME, MESSAGE_COLUMN, VARCHAR, 4000, true);
    db.assertColumnDefinition(TABLE_NAME, SEVERITY_COLUMN, VARCHAR, 10, true);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_TYPE_COLUMN, TINYINT, null, true);
    db.assertColumnDefinition(TABLE_NAME, TAGS_COLUMN, VARCHAR, 4000, true);
    db.assertColumnDefinition(TABLE_NAME, ASSIGNEE_UUID_COLUMN, VARCHAR, 255, true);
    db.assertColumnDefinition(TABLE_NAME, STATUS_MARKED_BY_UUID_COLUMN, VARCHAR, 255, true);
    db.assertColumnDefinition(TABLE_NAME, ISSUE_CREATION_DATE_COLUMN, BIGINT, null, true);
    db.assertColumnDefinition(TABLE_NAME, FIXED_AT_COLUMN, BIGINT, null, false);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertTableExists(TABLE_NAME);
  }
}
