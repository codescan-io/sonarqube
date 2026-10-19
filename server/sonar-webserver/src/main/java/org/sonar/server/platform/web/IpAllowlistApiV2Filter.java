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
package org.sonar.server.platform.web;

import java.io.IOException;
import java.util.List;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.server.http.HttpResponse;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.FilterChain;
import org.sonar.api.web.HttpFilter;
import org.sonar.api.web.UrlPattern;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.ws.WebServiceEngine;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The {@code /api/v2} counterpart of {@link IpAllowlistActionInterceptor}, for the endpoints that take the organization
 * key as the {@code organization} query parameter. The answer has the {@code /api} error body, not the {@code /api/v2}
 * one, so the web app recognises a block the same way on both.
 */
public class IpAllowlistApiV2Filter extends HttpFilter {

  private static final UrlPattern URL_PATTERN = UrlPattern.builder().includes("/api/v2/*").build();

  private final IpAllowlistRequestCheck check;

  public IpAllowlistApiV2Filter(IpAllowlistRequestCheck check) {
    this.check = check;
  }

  @Override
  public UrlPattern doGetPattern() {
    return URL_PATTERN;
  }

  @Override
  public void doFilter(HttpRequest request, HttpResponse response, FilterChain chain) throws IOException {
    String organizationKey = request.getParameter("organization");
    if (organizationKey != null && !organizationKey.isBlank() && check.hasUserSession()) {
      try {
        if (!check.blockedOrganizationUuids().isEmpty()) {
          check.checkOrganizationKey(organizationKey.strip());
        }
      } catch (ServerException e) {
        // a blocked member's 403, or the 503 of an allowlist that cannot be read
        answer(response, e);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static void answer(HttpResponse response, ServerException e) throws IOException {
    response.setStatus(e.httpCode());
    response.setContentType("application/json");
    response.setCharacterEncoding(UTF_8.name());
    try (JsonWriter json = JsonWriter.of(response.getWriter())) {
      json.beginObject();
      WebServiceEngine.writeErrors(json, List.of(e.getMessage()));
      json.endObject();
    }
  }
}
