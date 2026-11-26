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
package org.sonar.server.usertoken.ws;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.user.OrganizationUserTokenDto;
import org.sonar.server.user.UserSession;

import static java.util.Optional.ofNullable;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.api.utils.DateUtils.parseDateOrDateTime;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;

/**
 * Lists the access tokens of all members of an organization.
 * Restricted to administrators of that organization (and super admins).
 * The existing {@code api/user_tokens/search} action is left unchanged.
 */
public class SearchOrganizationTokensAction implements UserTokensWsAction {

  static final String ACTION_SEARCH_ORGANIZATION = "search_organization";
  static final String PARAM_ORGANIZATION = "organization";
  static final String PARAM_QUERY = "q";
  static final String PARAM_CREATED_BEFORE = "createdBefore";
  static final String PARAM_PAGE = "p";
  static final String PARAM_PAGE_SIZE = "ps";
  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 500;

  private final DbClient dbClient;
  private final UserSession userSession;

  public SearchOrganizationTokensAction(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION_SEARCH_ORGANIZATION)
      .setDescription("List the access tokens of the members of an organization, oldest first.<br>" +
        "Requires 'Administer' permission on the specified organization.")
      .setSince("10.6")
      .setInternal(true)
      .setHandler(this);

    action.createParam(PARAM_ORGANIZATION)
      .setDescription("Organization key")
      .setRequired(true);

    action.createParam(PARAM_QUERY)
      .setDescription("Limit search to user logins, user names or token names that contain the supplied string")
      .setMinimumLength(2)
      .setExampleValue("g.hopper");

    action.createParam(PARAM_CREATED_BEFORE)
      .setDescription("Only return tokens created before this date or datetime. Used to find old tokens.")
      .setExampleValue("2024-01-31");

    action.createParam(PARAM_PAGE)
      .setDescription("1-based page number")
      .setDefaultValue("1");

    action.createParam(PARAM_PAGE_SIZE)
      .setDescription("Page size")
      .setDefaultValue(String.valueOf(DEFAULT_PAGE_SIZE))
      .setMaximumValue(MAX_PAGE_SIZE);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String organizationKey = request.mandatoryParam(PARAM_ORGANIZATION);
    String searchText = request.param(PARAM_QUERY);
    Long createdBefore = parseCreatedBefore(request.param(PARAM_CREATED_BEFORE));
    int page = Math.max(1, request.mandatoryParamAsInt(PARAM_PAGE));
    int pageSize = request.mandatoryParamAsInt(PARAM_PAGE_SIZE);

    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = checkFoundWithOptional(dbClient.organizationDao().selectByKey(dbSession, organizationKey),
        "Organization '%s' is not found", organizationKey);
      userSession.checkLoggedIn().checkPermission(ADMINISTER, organization);

      String organizationUuid = organization.getUuid();
      int total = dbClient.userTokenDao().countByOrganization(dbSession, organizationUuid, searchText, createdBefore);
      List<OrganizationUserTokenDto> tokens = dbClient.userTokenDao().selectByOrganization(dbSession, organizationUuid, searchText,
        createdBefore, Pagination.forPage(page).andSize(pageSize));

      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        json.name("paging").beginObject()
          .prop("pageIndex", page)
          .prop("pageSize", pageSize)
          .prop("total", total)
          .endObject();
        json.name("userTokens").beginArray();
        tokens.forEach(token -> writeToken(json, token));
        json.endArray();
        json.endObject();
      }
    }
  }

  private static void writeToken(JsonWriter json, OrganizationUserTokenDto token) {
    json.beginObject()
      .prop("login", token.getLogin())
      .prop("userName", token.getUserName())
      .prop("name", token.getName())
      .prop("type", token.getType())
      .prop("createdAt", formatDateTime(token.getCreatedAt()));
    ofNullable(token.getLastConnectionDate()).ifPresent(date -> json.prop("lastConnectionDate", formatDateTime(date)));
    ofNullable(token.getExpirationDate()).ifPresent(date -> json.prop("expirationDate", formatDateTime(date)));
    json.prop("isExpired", token.getExpirationDate() != null && token.isExpired());
    if (token.getProjectKey() != null && token.getProjectName() != null) {
      json.name("project").beginObject()
        .prop("key", token.getProjectKey())
        .prop("name", token.getProjectName())
        .endObject();
    }
    json.endObject();
  }

  @CheckForNull
  private static Long parseCreatedBefore(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return parseDateOrDateTime(value).getTime();
  }
}
