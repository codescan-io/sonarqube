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
import static java.sql.Types.VARCHAR;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.CIDR_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.CREATED_AT_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.CREATED_BY_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.DESCRIPTION_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.ORGANIZATION_UUID_COLUMN;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.TABLE_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.UUID_COLUMN;

class CreateIpAllowlistRangeTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIpAllowlistRangeTable.class);

  private final CreateIpAllowlistRangeTable underTest = new CreateIpAllowlistRangeTable(db.database());

  @Test
  void execute_createsTheTableWithTheColumnsOfCodescanngMigration() throws SQLException {
    db.assertTableDoesNotExist(TABLE_NAME);

    underTest.execute();

    db.assertTableExists(TABLE_NAME);
    db.assertPrimaryKey(TABLE_NAME, "pk_cs_ip_allowlist_range", UUID_COLUMN);
    db.assertColumnDefinition(TABLE_NAME, UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, ORGANIZATION_UUID_COLUMN, VARCHAR, 40, false);
    db.assertColumnDefinition(TABLE_NAME, CIDR_COLUMN, VARCHAR, 43, false);
    db.assertColumnDefinition(TABLE_NAME, DESCRIPTION_COLUMN, VARCHAR, 255, false);
    db.assertColumnDefinition(TABLE_NAME, CREATED_BY_COLUMN, VARCHAR, 255, true);
    db.assertColumnDefinition(TABLE_NAME, CREATED_AT_COLUMN, BIGINT, null, false);
  }

  @Test
  void execute_whenCodescanngMigrationAlreadyCreatedTheTable_doesNothing() throws SQLException {
    db.executeDdl("CREATE TABLE IF NOT EXISTS cs_ip_allowlist_range (uuid VARCHAR(40) PRIMARY KEY, organization_uuid VARCHAR(40) NOT NULL, "
      + "cidr VARCHAR(43) NOT NULL, description VARCHAR(255) NOT NULL, created_by VARCHAR(255), created_at BIGINT NOT NULL, "
      + "CONSTRAINT uq_cs_ip_allowlist_range UNIQUE (organization_uuid, cidr))");

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertTableExists(TABLE_NAME);
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();
  }
}
