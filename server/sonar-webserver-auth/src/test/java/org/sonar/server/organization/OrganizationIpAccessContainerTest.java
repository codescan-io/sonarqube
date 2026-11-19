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
package org.sonar.server.organization;

import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;
import org.sonar.core.platform.SpringComponentContainer;
import org.sonar.db.DbClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class OrganizationIpAccessContainerTest {

  @Test
  public void container_creates_the_bean_through_its_public_constructor() {
    SpringComponentContainer container = new SpringComponentContainer();
    try {
      container.add(mock(DbClient.class), System2.INSTANCE, new MapSettings().asConfig(), OrganizationIpAccess.class);

      container.startComponents();

      assertThat(container.getComponentByType(OrganizationIpAccess.class)).isNotNull();
    } finally {
      container.stopComponents();
    }
  }
}
