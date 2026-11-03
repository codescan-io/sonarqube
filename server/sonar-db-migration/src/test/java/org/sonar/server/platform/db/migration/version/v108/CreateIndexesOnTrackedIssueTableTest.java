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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.server.platform.db.migration.version.v108.CreateTrackedIssueTable.TABLE_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnTrackedIssueTable.STATUS_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnTrackedIssueTable.ORGANIZATION_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnTrackedIssueTable.PROJECT_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnTrackedIssueTable.UNIQUE_INDEX_NAME;

class CreateIndexesOnTrackedIssueTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIndexesOnTrackedIssueTable.class);

  private final CreateIndexesOnTrackedIssueTable underTest = new CreateIndexesOnTrackedIssueTable(db.database());

  @Test
  void execute_createsTheUniqueAndTheLookupIndexes() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, UNIQUE_INDEX_NAME);

    underTest.execute();

    db.assertUniqueIndex(TABLE_NAME, UNIQUE_INDEX_NAME, "project_uuid", "branch_key", "issue_key", "issue_status");
    db.assertIndex(TABLE_NAME, PROJECT_INDEX_NAME, "project_uuid", "captured_at");
    db.assertIndex(TABLE_NAME, STATUS_INDEX_NAME, "project_uuid", "issue_status", "issue_close_date");
    db.assertIndex(TABLE_NAME, ORGANIZATION_INDEX_NAME, "organization_uuid");
  }

  /**
   * The unique index is what makes capture idempotent: a retried compute engine task, or the same transition applied
   * twice, must not add a second row for the same issue in the same status.
   */
  @Test
  void execute_rejectsASecondRowForTheSameIssueInTheSameStatus() throws SQLException {
    underTest.execute();
    insertHistoryRow("h1", "main", "ISSUE-1", "FIXED");
    insertHistoryRow("h2", "main", "ISSUE-1", "ACCEPTED");
    insertHistoryRow("h3", "feature", "ISSUE-1", "FIXED");

    assertThatThrownBy(() -> insertHistoryRow("h4", "main", "ISSUE-1", "FIXED")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();
  }

  private void insertHistoryRow(String uuid, String branchKey, String issueKey, String issueStatus) {
    db.executeInsert(TABLE_NAME,
      "uuid", uuid,
      "organization_uuid", "org-1",
      "project_uuid", "project-1",
      "branch_key", branchKey,
      "branch_type", "BRANCH",
      "issue_key", issueKey,
      "issue_status", issueStatus,
      "captured_at", 1L,
      "captured_by", "ANALYSIS");
  }
}
