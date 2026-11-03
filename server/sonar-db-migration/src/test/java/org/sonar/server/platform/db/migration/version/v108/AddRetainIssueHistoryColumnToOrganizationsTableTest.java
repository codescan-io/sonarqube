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

import static java.sql.Types.BOOLEAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sonar.server.platform.db.migration.version.v108.AddRetainIssueHistoryColumnToOrganizationsTable.RETAIN_ISSUE_HISTORY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.AddRetainIssueHistoryColumnToOrganizationsTable.TABLE_NAME;

class AddRetainIssueHistoryColumnToOrganizationsTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(AddRetainIssueHistoryColumnToOrganizationsTable.class);

  private final AddRetainIssueHistoryColumnToOrganizationsTable underTest = new AddRetainIssueHistoryColumnToOrganizationsTable(db.database());

  @Test
  void execute_whenColumnDoesNotExist_createsItOffForExistingOrganizations() throws SQLException {
    db.executeInsert(TABLE_NAME, "uuid", "org-1", "kee", "org-1", "name", "Org 1", "default_quality_gate_uuid", "qg-1",
      "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L);
    db.assertColumnDoesNotExist(TABLE_NAME, RETAIN_ISSUE_HISTORY_COLUMN);

    underTest.execute();

    db.assertColumnDefinition(TABLE_NAME, RETAIN_ISSUE_HISTORY_COLUMN, BOOLEAN, null, false);
    assertThat(db.selectFirst("select retain_issue_history_enabled as \"enabled\" from organizations where uuid = 'org-1'"))
      .containsEntry("enabled", false);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertColumnDefinition(TABLE_NAME, RETAIN_ISSUE_HISTORY_COLUMN, BOOLEAN, null, false);
  }
}
