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
package org.sonar.server.app;

import java.util.Properties;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.process.Props;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TomcatRemoteIpTest {

  private final Tomcat tomcat = mock(Tomcat.class, Mockito.RETURNS_DEEP_STUBS);

  @Test
  public void configure_adds_remote_ip_valve_on_engine_reading_x_forwarded_for() {
    new TomcatRemoteIp().configure(tomcat, new Props(new Properties()));

    RemoteIpValve valve = captureValve();
    assertThat(valve.getRemoteIpHeader()).isEqualToIgnoringCase("x-forwarded-for");
    assertThat(valve.getProtocolHeader()).isNull();
    assertThat(valve.getInternalProxies()).isEqualTo(new RemoteIpValve().getInternalProxies());
  }

  @Test
  public void configure_keeps_default_internal_proxies_when_property_is_blank() {
    Properties p = new Properties();
    p.setProperty("sonar.web.trustedProxiesRegex", "   ");

    new TomcatRemoteIp().configure(tomcat, new Props(p));

    assertThat(captureValve().getInternalProxies()).isEqualTo(new RemoteIpValve().getInternalProxies());
  }

  @Test
  public void configure_uses_trusted_proxies_property_when_set() {
    Properties p = new Properties();
    p.setProperty("sonar.web.trustedProxiesRegex", "10\\.1\\.\\d{1,3}\\.\\d{1,3}");

    new TomcatRemoteIp().configure(tomcat, new Props(p));

    assertThat(captureValve().getInternalProxies()).isEqualTo("10\\.1\\.\\d{1,3}\\.\\d{1,3}");
  }

  private RemoteIpValve captureValve() {
    ArgumentCaptor<Valve> captor = ArgumentCaptor.forClass(Valve.class);
    verify(tomcat.getEngine().getPipeline()).addValve(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RemoteIpValve.class);
    return (RemoteIpValve) captor.getValue();
  }
}
