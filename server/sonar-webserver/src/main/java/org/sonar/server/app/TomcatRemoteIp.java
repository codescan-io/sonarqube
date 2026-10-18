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

import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.PatternSyntaxException;
import javax.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.tomcat.util.http.MimeHeaders;
import org.sonar.process.Props;

/**
 * Makes getRemoteAddr() return the client address: X-Forwarded-For is read right to left and
 * addresses matching the internal proxies are skipped, so a value the caller prepends is ignored.
 * <p>
 * The X-Forwarded-For header itself is left as the caller sent it, because audit and request logs
 * record that raw chain. The valve works on a private copy of the header instead.
 */
class TomcatRemoteIp {

  static final String TRUSTED_PROXIES_PROPERTY = "sonar.web.trustedProxiesRegex";
  static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
  // internal, rewritten from X-Forwarded-For on every request so a client-sent value never reaches the valve
  static final String CLIENT_CHAIN_HEADER = "X-Codescan-Client-Chain";

  void configure(Tomcat tomcat, Props props) {
    RemoteIpValve valve = new ForwardedForPreservingValve();
    valve.setRemoteIpHeader(CLIENT_CHAIN_HEADER);
    // Tomcat's default is X-Forwarded-Proto, which would rewrite scheme/secure on proxied requests
    valve.setProtocolHeader(null);
    String trustedProxies = props.value(TRUSTED_PROXIES_PROPERTY);
    if (trustedProxies != null && !trustedProxies.isBlank()) {
      try {
        valve.setInternalProxies(trustedProxies.trim());
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException("Invalid regular expression in property " + TRUSTED_PROXIES_PROPERTY + ": " + e.getMessage(), e);
      }
    }
    tomcat.getEngine().getPipeline().addValve(valve);
  }

  private static class ForwardedForPreservingValve extends RemoteIpValve {
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
      MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
      headers.removeHeader(CLIENT_CHAIN_HEADER);
      Enumeration<String> forwardedFor = request.getHeaders(FORWARDED_FOR_HEADER);
      while (forwardedFor.hasMoreElements()) {
        headers.addValue(CLIENT_CHAIN_HEADER).setString(forwardedFor.nextElement());
      }
      super.invoke(request, response);
    }
  }
}
