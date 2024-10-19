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
package org.sonar.server.platform.db.migration.version.v104;

import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.db.MigrationDbTester;
import org.sonar.server.platform.db.migration.step.DdlChange;

class DropSystemTagsInRulesIT {
  private static final String TABLE_NAME = "rules";
  private static final String COLUMN_NAME = "system_tags";

  @RegisterExtension
  public final MigrationDbTester db = MigrationDbTester.createForMigrationStep(DropSystemTagsInRules.class);
  private final DdlChange underTest = new DropSystemTagsInRules(db.database());

  @Test
  void executed_whenRun_shouldDropSystemTagsColumn() throws SQLException {
    db.assertColumnDefinition(TABLE_NAME, COLUMN_NAME, Types.VARCHAR, 4000, true);
    underTest.execute();
    db.assertColumnDoesNotExist(TABLE_NAME, COLUMN_NAME);
  }

  @Test
  void execute_whenRunMoreThanOnce_shouldBeReentrant() throws SQLException {
    db.assertColumnDefinition(TABLE_NAME, COLUMN_NAME, Types.VARCHAR, 4000, true);
    underTest.execute();
    underTest.execute();
    db.assertColumnDoesNotExist(TABLE_NAME, COLUMN_NAME);
  }
}
