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
package org.sonar.server.authentication;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sonar.db.user.UserDto;
import org.sonar.server.user.UserSession;
import org.sonar.server.user.UserSessionFactory;
import org.sonar.server.usertoken.UserTokenAuthentication;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.Objects.nonNull;
import static org.sonar.server.authentication.UserAuthResult.AuthType.BASIC;
import static org.sonar.server.authentication.UserAuthResult.AuthType.GITHUB_WEBHOOK;
import static org.sonar.server.authentication.UserAuthResult.AuthType.JWT;
import static org.sonar.server.authentication.UserAuthResult.AuthType.SSO;
import static org.sonar.server.authentication.UserAuthResult.AuthType.TOKEN;

public class RequestAuthenticatorImpl implements RequestAuthenticator {

  private final JwtHttpHandler jwtHttpHandler;
  private final BasicAuthentication basicAuthentication;
  private final UserTokenAuthentication userTokenAuthentication;
  private final HttpHeadersAuthentication httpHeadersAuthentication;
  private final GithubWebhookAuthentication githubWebhookAuthentication;
  private final UserSessionFactory userSessionFactory;
  private final List<CustomAuthentication> customAuthentications;

  @Autowired(required = false)
  public RequestAuthenticatorImpl(JwtHttpHandler jwtHttpHandler, BasicAuthentication basicAuthentication, UserTokenAuthentication userTokenAuthentication,
    HttpHeadersAuthentication httpHeadersAuthentication,
    GithubWebhookAuthentication githubWebhookAuthentication, UserSessionFactory userSessionFactory, CustomAuthentication[] customAuthentications) {
    this.jwtHttpHandler = jwtHttpHandler;
    this.basicAuthentication = basicAuthentication;
    this.userTokenAuthentication = userTokenAuthentication;
    this.httpHeadersAuthentication = httpHeadersAuthentication;
    this.githubWebhookAuthentication = githubWebhookAuthentication;
    this.userSessionFactory = userSessionFactory;
    this.customAuthentications = Arrays.asList(customAuthentications);
  }

  @Autowired(required = false)
  public RequestAuthenticatorImpl(JwtHttpHandler jwtHttpHandler, BasicAuthentication basicAuthentication, UserTokenAuthentication userTokenAuthentication,
    HttpHeadersAuthentication httpHeadersAuthentication,
    UserSessionFactory userSessionFactory, GithubWebhookAuthentication githubWebhookAuthentication) {
    this(jwtHttpHandler, basicAuthentication, userTokenAuthentication, httpHeadersAuthentication, githubWebhookAuthentication, userSessionFactory, new CustomAuthentication[0]);
  }

  @Override
  public UserSession authenticate(HttpServletRequest request, HttpServletResponse response) {
    for (CustomAuthentication customAuthentication : customAuthentications) {
      Optional<UserSession> session = customAuthentication.authenticate(request, response);
      if (session.isPresent()) {
        return session.get();
      }
    }

    UserAuthResult userAuthResult = loadUser(request, response);
    if (nonNull(userAuthResult.getUserDto())) {
      if (TOKEN.equals(userAuthResult.getAuthType())) {
        return userSessionFactory.create(userAuthResult.getUserDto(), userAuthResult.getTokenDto());
      }
      return userSessionFactory.create(userAuthResult.getUserDto());
    } else if (GITHUB_WEBHOOK.equals(userAuthResult.getAuthType())) {
      return userSessionFactory.createGithubWebhookUserSession();
    }
    return userSessionFactory.createAnonymous();
  }

  private UserAuthResult loadUser(HttpServletRequest request, HttpServletResponse response) {
    Function<UserAuthResult.AuthType, Function<UserDto, UserAuthResult>> createUserAuthResult = type -> userDto -> new UserAuthResult(userDto, type);
    // SSO authentication should come first in order to update JWT if user from header is not the same is user from JWT
    return httpHeadersAuthentication.authenticate(request, response).map(createUserAuthResult.apply(SSO))
      .orElseGet(() -> jwtHttpHandler.validateToken(request, response).map(createUserAuthResult.apply(JWT))
      .orElseGet(() -> userTokenAuthentication.authenticate(request)
        .or(() -> githubWebhookAuthentication.authenticate(request))
        .or(() -> basicAuthentication.authenticate(request).map(createUserAuthResult.apply(BASIC)))
        .orElseGet(UserAuthResult::new)));
  }

}
