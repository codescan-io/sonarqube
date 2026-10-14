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
import org.sonar.server.platform.db.migration.step.DdlChange;

import static org.sonar.server.platform.db.migration.version.v108.CreateIndexOnIssueChangesProjectUuidAndCreatedAt.COLUMN_PROJECT_UUID;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexOnIssueChangesProjectUuidAndCreatedAt.COLUMN_CREATED_AT;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexOnIssueChangesProjectUuidAndCreatedAt.INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexOnIssueChangesProjectUuidAndCreatedAt.TABLE_NAME;

class CreateIndexOnIssueChangesProjectUuidAndCreatedAtIT {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIndexOnIssueChangesProjectUuidAndCreatedAt.class);

  private final DdlChange underTest = new CreateIndexOnIssueChangesProjectUuidAndCreatedAt(db.database());

  @Test
  void migration_should_create_index() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, INDEX_NAME);

    underTest.execute();

    db.assertIndex(TABLE_NAME, INDEX_NAME, COLUMN_PROJECT_UUID, COLUMN_CREATED_AT);
  }

  @Test
  void migration_should_be_reentrant() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, INDEX_NAME);

    underTest.execute();
    underTest.execute();

    db.assertIndex(TABLE_NAME, INDEX_NAME, COLUMN_PROJECT_UUID, COLUMN_CREATED_AT);
  }
}
