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
package org.sonar.server.platform.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class CspFilter implements Filter {
  
  private final List<String> cspHeaders = new ArrayList<>();
  private String policies = null;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    cspHeaders.add("Content-Security-Policy");
    cspHeaders.add("X-Content-Security-Policy");
    cspHeaders.add("X-WebKit-CSP");

    List<String> cspPolicies = new ArrayList<>();

    // Directives not specified default to this one.
    cspPolicies.add("default-src 'self'");

    cspPolicies.add("base-uri 'none'");
    cspPolicies.add("img-src * data: blob:");
    cspPolicies.add("object-src 'none'");

    // Allow list for Google Tag Manager, Pendo and FullStory Scripts
    cspPolicies.add("connect-src 'self' https://edge.fullstory.com https://rs.fullstory.com http: https:");
    cspPolicies.add(
            "script-src 'self' https://www.googletagmanager.com https://pendo-io-static.storage.googleapis.com "
                    + "https://app.pendo.io https://cdn.pendo.io https://data.pendo.io https://edge.fullstory.com "
                    + "https://rs.fullstory.com https://ssl.google-analytics.com/ga.js "
                    + "https://static.zdassets.com/ekr/snippet.js?key=98ba4b78-5aed-46c5-af86-5e07d588c632 "
                    + "https://connect.facebook.net/en_US/fbevents.js "
                    + "https://snap.licdn.com/li.lms-analytics/insight.min.js 'unsafe-inline' 'unsafe-eval'");

    cspPolicies.add("style-src 'self' 'unsafe-inline'");
    cspPolicies.add("worker-src 'none'");
    this.policies = String.join("; ", cspPolicies).trim();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    // Add policies to all HTTP headers
    for (String header : this.cspHeaders) {
      ((HttpServletResponse) response).setHeader(header, this.policies);
    }

    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // Not used
  }

}
