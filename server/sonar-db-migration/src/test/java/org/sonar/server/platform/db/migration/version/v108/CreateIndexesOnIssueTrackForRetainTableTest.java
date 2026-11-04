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
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnIssueTrackForRetainTable.BRANCH_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnIssueTrackForRetainTable.UNIQUE_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIssueTrackForRetainTable.TABLE_NAME;

class CreateIndexesOnIssueTrackForRetainTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIndexesOnIssueTrackForRetainTable.class);

  private final CreateIndexesOnIssueTrackForRetainTable underTest = new CreateIndexesOnIssueTrackForRetainTable(db.database());

  @Test
  void execute_createsTheUniqueAndTheBranchIndexes() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, UNIQUE_INDEX_NAME);

    underTest.execute();

    db.assertUniqueIndex(TABLE_NAME, UNIQUE_INDEX_NAME, "issue_kee", "retained_status");
    db.assertIndex(TABLE_NAME, BRANCH_INDEX_NAME, "branch_uuid", "fixed_at");
  }

  /**
   * One row per issue and status, whatever branch the row claims: an issue key belongs to exactly one branch, so a
   * second row for the same key and status is always a duplicate capture.
   */
  @Test
  void execute_rejectsASecondRowForTheSameIssueAndStatus() throws SQLException {
    underTest.execute();
    insertRow("t1", "branch-1", "ISSUE-1", "FIXED");
    insertRow("t2", "branch-1", "ISSUE-1", "ACCEPTED");
    insertRow("t3", "branch-1", "ISSUE-2", "FIXED");

    assertThatThrownBy(() -> insertRow("t4", "branch-2", "ISSUE-1", "FIXED")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();
  }

  private void insertRow(String uuid, String branchUuid, String issueKee, String retainedStatus) {
    db.executeInsert(TABLE_NAME,
      "uuid", uuid,
      "branch_uuid", branchUuid,
      "issue_kee", issueKee,
      "retained_status", retainedStatus,
      "rule_uuid", "rule-1",
      "component_key", "project-1:src/Foo.cls",
      "fixed_at", 1L);
  }
}
