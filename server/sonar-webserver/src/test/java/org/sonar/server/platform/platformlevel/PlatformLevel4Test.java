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
import java.util.Optional;
import org.junit.Test;
import org.sonar.core.platform.SpringComponentContainer;
import org.sonar.server.platform.NodeInformation;
import org.sonar.server.platform.web.IpAllowlistActionInterceptor;
import org.sonar.server.platform.web.IpAllowlistApiV2Filter;
import org.sonar.server.platform.web.IpAllowlistRequestCheck;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlatformLevel4Test {

  @Test
  public void registers_the_ip_allowlist_checks_of_web_service_calls() {
    var parentContainer = mock(SpringComponentContainer.class);
    var container = mock(SpringComponentContainer.class);
    var platform = mock(PlatformLevel.class);
    when(parentContainer.createChild()).thenReturn(container);
    when(platform.getContainer()).thenReturn(parentContainer);
    when(container.getOptionalComponentByType(NodeInformation.class)).thenReturn(Optional.of(mock(NodeInformation.class)));

    new PlatformLevel4(platform, List.of()).configure();

    verify(container).add(IpAllowlistRequestCheck.class);
    verify(container).add(IpAllowlistActionInterceptor.class);
    verify(container).add(IpAllowlistApiV2Filter.class);
  }
}
