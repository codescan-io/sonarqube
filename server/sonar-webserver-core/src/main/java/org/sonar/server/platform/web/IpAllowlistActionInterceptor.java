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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.ws.ActionInterceptor;

import static java.util.stream.Collectors.toCollection;

/**
 * Gives a blocked member of an organization the IP allowlist's 403 before a web service runs, when the request names
 * the organization or one of its projects. Requests that name neither are left to the user session, which already
 * withholds a blocked organization's data.
 * <p>
 * A request that no organization's allowlist can block costs no lookup at all. Otherwise only the organizations the
 * request names are checked, one by one. Only these ways of naming an organization are resolved:
 * the {@code organization} parameter and the single-key project, component, application and portfolio parameters.
 * Others are not, and a blocked caller still gets the user session's generic 403: the plural parameters
 * ({@code projects}, {@code components}, {@code componentKeys}, {@code key}).
 * <p>
 * Reads the raw parameters: {@link Request#param} throws for a parameter the action does not declare. Only the first
 * value of each parameter counts, as the action reads it.
 */
public class IpAllowlistActionInterceptor implements ActionInterceptor {

  private static final String ORGANIZATION_PARAM = "organization";
  private static final List<String> COMPONENT_KEY_PARAMS = List.of("project", "projectKey", "component", "componentKey", "application", "portfolio");

  private final DbClient dbClient;
  private final IpAllowlistRequestCheck check;

  public IpAllowlistActionInterceptor(DbClient dbClient, IpAllowlistRequestCheck check) {
    this.dbClient = dbClient;
    this.check = check;
  }

  @Override
  public void preAction(WebService.Action action, Request request) {
    Map<String, String[]> params = request.getParams();
    if (params == null || params.isEmpty() || !check.hasUserSession()) {
      return;
    }
    String organizationKey = firstValue(params, ORGANIZATION_PARAM);
    List<String> componentKeys = COMPONENT_KEY_PARAMS.stream()
      .map(param -> firstValue(params, param))
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (organizationKey == null && componentKeys.isEmpty()) {
      return;
    }
    if (!check.mayBeBlocked()) {
      return;
    }
    if (organizationKey != null) {
      check.checkOrganizationKey(organizationKey);
    }
    if (!componentKeys.isEmpty()) {
      organizationUuidsOf(componentKeys).forEach(check::checkOrganizationUuid);
    }
  }

  private Set<String> organizationUuidsOf(List<String> componentKeys) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      return componentKeys.stream()
        .flatMap(key -> dbClient.componentDao().selectByKey(dbSession, key).stream())
        .map(ComponentDto::getOrganizationUuid)
        .filter(Objects::nonNull)
        .collect(toCollection(LinkedHashSet::new));
    }
  }

  /** Stripped of surrounding spaces, as the action reads it; null when missing or blank. */
  @CheckForNull
  private static String firstValue(Map<String, String[]> params, String name) {
    String[] values = params.get(name);
    if (values == null || values.length == 0 || values[0] == null) {
      return null;
    }
    String value = values[0].strip();
    return value.isEmpty() ? null : value;
  }
}
