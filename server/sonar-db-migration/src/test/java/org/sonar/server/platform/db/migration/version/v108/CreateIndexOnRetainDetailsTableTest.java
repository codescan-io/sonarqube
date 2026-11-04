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
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexOnRetainDetailsTable.PROJECT_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateRetainDetailsTable.TABLE_NAME;

class CreateIndexOnRetainDetailsTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIndexOnRetainDetailsTable.class);

  private final CreateIndexOnRetainDetailsTable underTest = new CreateIndexOnRetainDetailsTable(db.database());

  @Test
  void execute_createsTheProjectIndex() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, PROJECT_INDEX_NAME);

    underTest.execute();

    db.assertIndex(TABLE_NAME, PROJECT_INDEX_NAME, "project_uuid");
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();
  }
}
