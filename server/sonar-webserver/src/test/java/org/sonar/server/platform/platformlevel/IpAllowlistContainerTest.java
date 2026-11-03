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
package org.sonar.server.platform.platformlevel;

import java.util.List;
import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.System2;
import org.sonar.core.platform.SpringComponentContainer;
import org.sonar.db.DbClient;
import org.sonar.db.organization.IpAllowlistDao;
import org.sonar.server.authentication.RequestAuthenticator;
import org.sonar.server.authentication.UserSessionInitializer;
import org.sonar.server.authentication.event.AuthenticationEvent;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.platform.web.IpAllowlistActionInterceptor;
import org.sonar.server.platform.web.IpAllowlistApiV2Filter;
import org.sonar.server.platform.web.IpAllowlistRequestCheck;
import org.sonar.server.pushapi.sonarlint.SonarLintClientPermissionsValidator;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.UserSessionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The web server's container builds each component through its constructors, and a component it cannot build stops
 * the server at startup. Unit tests call the constructors directly, so only this test sees such a failure, for
 * example a second public constructor without {@code @Inject}.
 */
public class IpAllowlistContainerTest {

  /** Components the IP allowlist adds to the web server's container, or whose constructor it changes. */
  private static final List<Class<?>> COMPONENTS = List.of(
    IpAllowlistDao.class,
    OrganizationIpAccess.class,
    UserSessionInitializer.class,
    IpAllowlistRequestCheck.class,
    IpAllowlistActionInterceptor.class,
    IpAllowlistApiV2Filter.class,
    SonarLintClientPermissionsValidator.class);

  @Test
  public void container_creates_every_ip_allowlist_component() {
    SpringComponentContainer container = new SpringComponentContainer();
    try {
      container.add(mock(DbClient.class), System2.INSTANCE, new MapSettings().asConfig(), new ThreadLocalUserSession(),
        mock(UserSessionFactory.class), mock(AuthenticationEvent.class), mock(RequestAuthenticator.class));
      COMPONENTS.forEach(container::add);

      container.startComponents();

      COMPONENTS.forEach(type -> assertThat(container.getComponentByType(type)).as(type.getName()).isNotNull());
    } finally {
      container.stopComponents();
    }
  }
}
