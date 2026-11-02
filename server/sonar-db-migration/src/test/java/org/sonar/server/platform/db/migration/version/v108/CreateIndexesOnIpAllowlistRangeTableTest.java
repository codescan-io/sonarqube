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
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnIpAllowlistRangeTable.ORGANIZATION_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIndexesOnIpAllowlistRangeTable.UNIQUE_INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v108.CreateIpAllowlistRangeTable.TABLE_NAME;

class CreateIndexesOnIpAllowlistRangeTableTest {

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(CreateIndexesOnIpAllowlistRangeTable.class);

  private final CreateIndexesOnIpAllowlistRangeTable underTest = new CreateIndexesOnIpAllowlistRangeTable(db.database());

  @Test
  void execute_createsTheUniqueAndTheOrganizationIndexes() throws SQLException {
    db.assertIndexDoesNotExist(TABLE_NAME, UNIQUE_INDEX_NAME);
    db.assertIndexDoesNotExist(TABLE_NAME, ORGANIZATION_INDEX_NAME);

    underTest.execute();

    db.assertUniqueIndex(TABLE_NAME, UNIQUE_INDEX_NAME, "organization_uuid", "cidr");
    db.assertIndex(TABLE_NAME, ORGANIZATION_INDEX_NAME, "organization_uuid");
  }

  @Test
  void execute_makesTheRangeOfAnOrganizationUnique() throws SQLException {
    underTest.execute();
    insertRange("r1", "org-1", "10.0.0.0/8");
    insertRange("r2", "org-2", "10.0.0.0/8");

    assertThatThrownBy(() -> insertRange("r3", "org-1", "10.0.0.0/8")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void execute_whenCodescanngMigrationAlreadyCreatedTheOrganizationIndex_skipsIt() throws SQLException {
    db.executeDdl("CREATE INDEX IF NOT EXISTS ix_cs_ip_allowlist_range_org ON cs_ip_allowlist_range (organization_uuid)");

    assertThatCode(underTest::execute).doesNotThrowAnyException();

    db.assertIndex(TABLE_NAME, ORGANIZATION_INDEX_NAME, "organization_uuid");
  }

  @Test
  void execute_isReentrant() throws SQLException {
    underTest.execute();

    assertThatCode(underTest::execute).doesNotThrowAnyException();
  }

  private void insertRange(String uuid, String organizationUuid, String cidr) {
    db.executeInsert(TABLE_NAME, "uuid", uuid, "organization_uuid", organizationUuid, "cidr", cidr, "description", "d", "created_at", 1L);
  }
}
