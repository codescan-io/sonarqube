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
package org.sonar.server.analytics.ws;

import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.user.UserSession;

import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;

/**
 * Analytics has the same audience as the organization Administration tab: organization administrators,
 * and root users through {@link UserSession#checkPermission}.
 */
public class AnalyticsWsSupport {

  static final String PARAM_ORGANIZATION = "organization";

  private final DbClient dbClient;
  private final UserSession userSession;

  public AnalyticsWsSupport(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  static void defineOrganizationParam(WebService.NewAction action) {
    action.createParam(PARAM_ORGANIZATION)
      .setDescription("Organization key")
      .setRequired(true)
      .setExampleValue("my-org");
  }

  OrganizationDto checkCanViewAnalytics(DbSession dbSession, String organizationKey) {
    userSession.checkLoggedIn();
    OrganizationDto organization = checkFoundWithOptional(
      dbClient.organizationDao().selectByKey(dbSession, organizationKey),
      "No organization with key '%s'", organizationKey);
    userSession.checkPermission(ADMINISTER, organization);
    return organization;
  }
}
