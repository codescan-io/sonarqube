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
package org.sonar.server.authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaimsBuilder;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.http.Cookie;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.server.http.HttpResponse;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.user.SessionTokenDto;
import org.sonar.db.user.SessionTokensDao;
import org.sonar.db.user.UserDao;
import org.sonar.db.user.UserDto;
import org.sonar.server.http.JavaxHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtHttpHandlerServiceLoginTest {

  private static final long NOW = 10_000_000_000L;
  private static final String JWT = "TOKEN";
  private static final String SESSION_UUID = "session-uuid";
  private static final String USER_UUID = "user-uuid";

  private final System2 system2 = mock(System2.class);
  private final DbClient dbClient = mock(DbClient.class);
  private final SessionTokensDao sessionTokensDao = mock(SessionTokensDao.class);
  private final UserDao userDao = mock(UserDao.class);
  private final JwtSerializer jwtSerializer = mock(JwtSerializer.class);
  private final HttpRequest request = mock(HttpRequest.class);
  private final HttpResponse response = mock(HttpResponse.class);
  private final UserDto user = new UserDto().setUuid(USER_UUID).setLogin("job").setActive(true);

  private final JwtHttpHandler underTest = new JwtHttpHandler(system2, dbClient, new MapSettings().asConfig(), jwtSerializer,
    mock(JwtCsrfVerifier.class));

  @Before
  public void setUp() {
    when(system2.now()).thenReturn(NOW);
    when(dbClient.openSession(false)).thenReturn(mock(DbSession.class));
    when(dbClient.sessionTokensDao()).thenReturn(sessionTokensDao);
    when(dbClient.userDao()).thenReturn(userDao);
    when(sessionTokensDao.selectByUuid(any(), eq(SESSION_UUID)))
      .thenReturn(Optional.of(new SessionTokenDto().setUserUuid(USER_UUID).setExpirationDate(NOW + 60_000L)));
    when(userDao.selectByUuid(any(), eq(USER_UUID))).thenReturn(user);
    Cookie cookie = new JavaxHttpRequest.JavaxCookie(new javax.servlet.http.Cookie("JWT-SESSION", JWT));
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});
  }

  @Test
  public void service_login_records_the_claim_and_its_organization_on_the_request() {
    tokenWithClaims(Map.of("codescanService", true, "codescanServiceOrg", "acme"));

    assertThat(underTest.validateToken(request, response)).contains(user);

    verify(request).setAttribute(JwtHttpHandler.SERVICE_LOGIN_ATTRIBUTE, Boolean.TRUE);
    verify(request).setAttribute(JwtHttpHandler.SERVICE_LOGIN_ORG_ATTRIBUTE, "acme");
  }

  @Test
  public void service_claim_without_organization_records_no_organization() {
    tokenWithClaims(Map.of("codescanService", true));

    underTest.validateToken(request, response);

    verify(request).setAttribute(JwtHttpHandler.SERVICE_LOGIN_ATTRIBUTE, Boolean.TRUE);
    verify(request, never()).setAttribute(eq(JwtHttpHandler.SERVICE_LOGIN_ORG_ATTRIBUTE), any());
  }

  @Test
  public void organization_claim_without_service_claim_records_nothing() {
    tokenWithClaims(Map.of("codescanServiceOrg", "acme"));

    underTest.validateToken(request, response);

    verifyNothingRecorded();
  }

  @Test
  public void service_claim_that_is_not_the_boolean_true_records_nothing() {
    tokenWithClaims(Map.of("codescanService", "true", "codescanServiceOrg", "acme"));

    underTest.validateToken(request, response);

    verifyNothingRecorded();
  }

  @Test
  public void blank_or_non_text_organization_claim_is_not_recorded() {
    tokenWithClaims(Map.of("codescanService", true, "codescanServiceOrg", "  "));
    underTest.validateToken(request, response);

    tokenWithClaims(Map.of("codescanService", true, "codescanServiceOrg", 42));
    underTest.validateToken(request, response);

    verify(request, never()).setAttribute(eq(JwtHttpHandler.SERVICE_LOGIN_ORG_ATTRIBUTE), any());
  }

  @Test
  public void ordinary_login_records_nothing() {
    tokenWithClaims(Map.of());

    assertThat(underTest.validateToken(request, response)).contains(user);

    verifyNothingRecorded();
  }

  @Test
  public void token_of_a_disabled_user_records_nothing() {
    user.setActive(false);
    tokenWithClaims(Map.of("codescanService", true, "codescanServiceOrg", "acme"));

    assertThat(underTest.validateToken(request, response)).isEmpty();

    verifyNothingRecorded();
  }

  private void tokenWithClaims(Map<String, Object> extraClaims) {
    DefaultClaimsBuilder builder = new DefaultClaimsBuilder();
    builder.id(SESSION_UUID)
      .subject(USER_UUID)
      .issuedAt(new Date(NOW))
      .expiration(new Date(NOW + 60_000L))
      .add("lastRefreshTime", NOW)
      .add(extraClaims);
    Claims claims = builder.build();
    when(jwtSerializer.decode(JWT)).thenReturn(Optional.of(claims));
  }

  private void verifyNothingRecorded() {
    verify(request, never()).setAttribute(eq(JwtHttpHandler.SERVICE_LOGIN_ATTRIBUTE), any());
    verify(request, never()).setAttribute(eq(JwtHttpHandler.SERVICE_LOGIN_ORG_ATTRIBUTE), any());
  }
}
