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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import javax.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.http.MimeHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.process.Props;

/**
 * Makes getRemoteAddr() return the client address: X-Forwarded-For is read right to left and
 * addresses matching the internal proxies are skipped, so a value the caller prepends is ignored.
 * <p>
 * The stock valve rewrites X-Forwarded-For, but audit and request logs record the raw chain. So the
 * original header lines are remembered before the stock valve runs and written back straight after
 * it. The valve only removes or replaces header lines, and the write-back never adds more lines than
 * the request arrived with, so the request's header count never grows (Tomcat's maxHeaderCount
 * would otherwise turn a request that has many headers into a 500).
 */
class TomcatRemoteIp {

  private static final Logger LOGGER = LoggerFactory.getLogger(TomcatRemoteIp.class);

  static final String TRUSTED_PROXIES_PROPERTY = "sonar.web.trustedProxiesRegex";
  static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
  // a Catalina request note is internal to the container and not visible to servlets
  static final String ORIGINAL_FORWARDED_FOR_NOTE = "org.sonar.server.app.originalForwardedFor";

  void configure(Tomcat tomcat, Props props) {
    RemoteIpValve valve = new ForwardedForRememberingValve();
    valve.setRemoteIpHeader(FORWARDED_FOR_HEADER);
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
    tomcat.getEngine().getPipeline().addValve(new ForwardedForRestoringValve());
  }

  static class ForwardedForRememberingValve extends RemoteIpValve {
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
      request.setNote(ORIGINAL_FORWARDED_FOR_NOTE, new ArrayList<>(Collections.list(request.getHeaders(FORWARDED_FOR_HEADER))));
      super.invoke(request, response);
    }
  }

  static class ForwardedForRestoringValve extends ValveBase {
    ForwardedForRestoringValve() {
      super(true);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
      try {
        restore(request);
      } catch (RuntimeException e) {
        // the client address is already resolved, only the raw header seen by logs is affected
        LOGGER.debug("Could not restore the {} header", FORWARDED_FOR_HEADER, e);
      }
      getNext().invoke(request, response);
    }

    @SuppressWarnings("unchecked")
    private static void restore(Request request) {
      List<String> original = (List<String>) request.getNote(ORIGINAL_FORWARDED_FOR_NOTE);
      if (original == null) {
        return;
      }
      request.removeNote(ORIGINAL_FORWARDED_FOR_NOTE);
      MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
      headers.removeHeader(FORWARDED_FOR_HEADER);
      original.forEach(value -> headers.addValue(FORWARDED_FOR_HEADER).setString(value));
    }
  }
}
