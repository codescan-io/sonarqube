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

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.sonar.process.Props;

/**
 * Makes getRemoteAddr() return the client address: X-Forwarded-For is read right to left and
 * addresses matching the internal proxies are skipped, so a value the caller prepends is ignored.
 */
class TomcatRemoteIp {

  static final String TRUSTED_PROXIES_PROPERTY = "sonar.web.trustedProxiesRegex";

  void configure(Tomcat tomcat, Props props) {
    RemoteIpValve valve = new RemoteIpValve();
    valve.setRemoteIpHeader("x-forwarded-for");
    // Tomcat's default is X-Forwarded-Proto, which would rewrite scheme/secure on proxied requests
    valve.setProtocolHeader(null);
    String trustedProxies = props.value(TRUSTED_PROXIES_PROPERTY);
    if (trustedProxies != null && !trustedProxies.isBlank()) {
      valve.setInternalProxies(trustedProxies);
    }
    tomcat.getEngine().getPipeline().addValve(valve);
  }
}
