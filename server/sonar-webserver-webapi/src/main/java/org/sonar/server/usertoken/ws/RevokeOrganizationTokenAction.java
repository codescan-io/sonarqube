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

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTokenDto;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;

import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;

/**
 * Revokes the access token of a member of an organization.
 * Restricted to administrators of that organization (and super admins).
 * The token owner must be a member of the organization, and a project analysis token
 * must belong to a project of the organization; otherwise the token is reported as not found
 * so that nothing is disclosed about other organizations.
 * The existing {@code api/user_tokens/revoke} action is left unchanged.
 */
public class RevokeOrganizationTokenAction implements UserTokensWsAction {

  static final String ACTION_REVOKE_ORGANIZATION = "revoke_organization";
  static final String PARAM_ORGANIZATION = "organization";
  static final String PARAM_LOGIN = "login";
  static final String PARAM_NAME = "name";

  private static final Logger LOGGER = LoggerFactory.getLogger(RevokeOrganizationTokenAction.class);
  private static final String TOKEN_NOT_FOUND = "Token not found";

  private final DbClient dbClient;
  private final UserSession userSession;

  public RevokeOrganizationTokenAction(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION_REVOKE_ORGANIZATION)
      .setDescription("Revoke an access token of a member of an organization.<br>" +
        "Requires 'Administer' permission on the specified organization.")
      .setSince("10.6")
      .setPost(true)
      .setInternal(true)
      .setHandler(this);

    action.createParam(PARAM_ORGANIZATION)
      .setDescription("Organization key")
      .setRequired(true);

    action.createParam(PARAM_LOGIN)
      .setDescription("Login of the token owner")
      .setRequired(true)
      .setExampleValue("g.hopper");

    action.createParam(PARAM_NAME)
      .setDescription("Token name")
      .setRequired(true)
      .setExampleValue("Project scan on Travis");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String organizationKey = request.mandatoryParam(PARAM_ORGANIZATION);
    String login = request.mandatoryParam(PARAM_LOGIN);
    String name = request.mandatoryParam(PARAM_NAME);

    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = checkFoundWithOptional(dbClient.organizationDao().selectByKey(dbSession, organizationKey),
        "Organization '%s' is not found", organizationKey);
      userSession.checkLoggedIn().checkPermission(ADMINISTER, organization);

      UserDto user = dbClient.userDao().selectByLogin(dbSession, login);
      if (user == null || dbClient.organizationMemberDao().select(dbSession, organization.getUuid(), user.getUuid()).isEmpty()) {
        throw new NotFoundException(TOKEN_NOT_FOUND);
      }

      UserTokenDto token = dbClient.userTokenDao().selectByUserAndName(dbSession, user, name);
      if (token == null || !isTokenInOrganization(dbSession, token, organization)) {
        throw new NotFoundException(TOKEN_NOT_FOUND);
      }

      LOGGER.info("Revoke organization token :: organization: {}, tokenOwner: {}, tokenName: {}, revokedBy: {}",
        organization.getKey(), user.getLogin(), name, userSession.getLogin());
      dbClient.userTokenDao().deleteByUserAndName(dbSession, user, name);
      dbSession.commit();
    }
    response.noContent();
  }

  private boolean isTokenInOrganization(DbSession dbSession, UserTokenDto token, OrganizationDto organization) {
    String projectUuid = token.getProjectUuid();
    if (projectUuid == null) {
      return true;
    }
    Optional<ProjectDto> project = dbClient.projectDao().selectByUuid(dbSession, projectUuid);
    return project.isPresent() && organization.getUuid().equals(project.get().getOrganizationUuid());
  }
}
