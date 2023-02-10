/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonar.server.platform.db.migration.version.v97;

import java.sql.SQLException;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.CoreDbTester;

import static org.sonar.server.platform.db.migration.version.v97.DropNonUniqueIndexForComponentsUuid.INDEX_NAME;
import static org.sonar.server.platform.db.migration.version.v97.DropNonUniqueIndexForComponentsUuid.TABLE;

public class DropNonUniqueIndexForComponentsUuidTest {

  private static final String COLUMN_NAME = "uuid";
  @Rule
  public final CoreDbTester db = CoreDbTester.createForSchema(DropNonUniqueIndexForComponentsUuidTest.class, "schema.sql");

  private final DropNonUniqueIndexForComponentsUuid dropNonUniqueIndexForComponentsUuid = new DropNonUniqueIndexForComponentsUuid(db.database());

  @Test
  public void migration_should_drop_unique_index() throws SQLException {
    db.assertIndex(TABLE, INDEX_NAME, COLUMN_NAME);

    dropNonUniqueIndexForComponentsUuid.execute();

    db.assertIndexDoesNotExist(TABLE, INDEX_NAME);
  }


  @Test
  public void migration_should_be_reentrant() throws SQLException {
    db.assertIndex(TABLE, INDEX_NAME, COLUMN_NAME);

    dropNonUniqueIndexForComponentsUuid.execute();
    dropNonUniqueIndexForComponentsUuid.execute();

    db.assertIndexDoesNotExist(TABLE, INDEX_NAME);
  }

}