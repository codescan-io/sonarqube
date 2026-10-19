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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.server.authentication.BaseIdentityProvider;
import org.sonar.api.server.http.Cookie;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.server.http.HttpResponse;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTokenDto;
import org.sonar.server.authentication.event.AuthenticationEvent;
import org.sonar.server.authentication.event.AuthenticationEvent.Method;
import org.sonar.server.authentication.event.AuthenticationEvent.Source;
import org.sonar.server.authentication.event.AuthenticationException;
import org.sonar.server.http.JavaxHttpRequest;
import org.sonar.server.organization.IpAllowlistUnavailableException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.tester.AnonymousMockUserSession;
import org.sonar.server.tester.MockUserSession;
import org.sonar.server.user.ServerUserSession;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.TokenUserSession;
import org.sonar.server.user.UserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonar.api.utils.DateUtils.formatDateTime;

public class UserSessionInitializerIT {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private ThreadLocalUserSession threadLocalSession = mock(ThreadLocalUserSession.class);
  private HttpRequest request = mock(HttpRequest.class);
  private HttpResponse response = mock(HttpResponse.class);
  private RequestAuthenticator authenticator = mock(RequestAuthenticator.class);
  private AuthenticationEvent authenticationEvent = mock(AuthenticationEvent.class);
  private MapSettings settings = new MapSettings();
  private ArgumentCaptor<Cookie> cookieArgumentCaptor = ArgumentCaptor.forClass(Cookie.class);

  private OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);

  private UserSessionInitializer underTest = new UserSessionInitializer(settings.asConfig(), threadLocalSession, authenticationEvent, authenticator, ipAccess);

  @Before
  public void setUp() {
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/measures");
  }

  @Test
  public void check_urls() {
    assertPathIsNotIgnored("/");
    assertPathIsNotIgnored("/foo");
    assertPathIsNotIgnored("/api/server_id/show");

    assertPathIsIgnored("/api/authentication/login");
    assertPathIsIgnored("/api/authentication/logout");
    assertPathIsIgnored("/api/authentication/validate");
    assertPathIsIgnored("/batch/index");
    assertPathIsIgnored("/batch/file");
    assertPathIsIgnored("/maintenance/index");
    assertPathIsIgnored("/setup/index");
    assertPathIsIgnored("/sessions/new");
    assertPathIsIgnored("/sessions/logout");
    assertPathIsIgnored("/sessions/unauthorized");
    assertPathIsIgnored("/oauth2/callback/github");
    assertPathIsIgnored("/oauth2/callback/foo");
    assertPathIsIgnored("/api/system/db_migration_status");
    assertPathIsIgnored("/api/system/status");
    assertPathIsIgnored("/api/system/migrate_db");
    assertPathIsIgnored("/api/server/version");
    assertPathIsIgnored("/api/users/identity_providers");
    assertPathIsIgnored("/api/l10n/index");

    // exclude project_badge url, as they can be auth. by a token as queryparam
    assertPathIsIgnored("/api/project_badges/measure");
    assertPathIsIgnored("/api/project_badges/quality_gate");
    assertPathIsIgnored("/api/project_badges/ai_code_assurance");

    // exlude passcode urls
    assertPathIsIgnoredWithAnonymousAccess("/api/ce/info");
    assertPathIsIgnoredWithAnonymousAccess("/api/ce/pause");
    assertPathIsIgnoredWithAnonymousAccess("/api/ce/resume");
    assertPathIsIgnoredWithAnonymousAccess("/api/system/health");
    assertPathIsIgnoredWithAnonymousAccess("/api/system/liveness");
    assertPathIsIgnoredWithAnonymousAccess("/api/monitoring/metrics");

    // exclude static resources
    assertPathIsIgnored("/css/style.css");
    assertPathIsIgnored("/images/logo.png");
    assertPathIsIgnored("/js/jquery.js");
  }

  @Test
  public void return_code_401_when_not_authenticated_and_with_force_authentication() {
    ArgumentCaptor<AuthenticationException> exceptionArgumentCaptor = ArgumentCaptor.forClass(AuthenticationException.class);
    when(threadLocalSession.isLoggedIn()).thenReturn(false);
    when(authenticator.authenticate(request, response)).thenReturn(new AnonymousMockUserSession());

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verifyNoMoreInteractions(response);
    verify(authenticationEvent).loginFailure(eq(request), exceptionArgumentCaptor.capture());
    verifyNoMoreInteractions(threadLocalSession);
    AuthenticationException authenticationException = exceptionArgumentCaptor.getValue();
    assertThat(authenticationException.getSource()).isEqualTo(Source.local(Method.BASIC));
    assertThat(authenticationException.getLogin()).isNull();
    assertThat(authenticationException.getMessage()).isEqualTo("User must be authenticated");
    assertThat(authenticationException.getPublicMessage()).isNull();
  }

  @Test
  public void does_not_return_code_401_when_not_authenticated_and_with_force_authentication_off() {
    when(threadLocalSession.isLoggedIn()).thenReturn(false);
    when(authenticator.authenticate(request, response)).thenReturn(new AnonymousMockUserSession());
    settings.setProperty("sonar.forceAuthentication", false);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verifyNoMoreInteractions(response);
  }

  @Test
  public void return_401_and_stop_on_ws() {
    when(request.getRequestURI()).thenReturn("/api/issues");
    AuthenticationException authenticationException = AuthenticationException.newBuilder().setSource(Source.jwt()).setMessage("Token id hasn't been found").build();
    doThrow(authenticationException).when(authenticator).authenticate(request, response);

    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response).setStatus(401);
    verify(authenticationEvent).loginFailure(request, authenticationException);
    verifyNoMoreInteractions(threadLocalSession);
  }

  @Test
  public void return_401_and_stop_on_batch_ws() {
    when(request.getRequestURI()).thenReturn("/batch/global");
    doThrow(AuthenticationException.newBuilder().setSource(Source.jwt()).setMessage("Token id hasn't been found").build())
      .when(authenticator).authenticate(request, response);

    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response).setStatus(401);
    verifyNoMoreInteractions(threadLocalSession);
  }

  @Test
  public void return_to_session_unauthorized_when_error_on_from_external_provider() throws Exception {
    doThrow(AuthenticationException.newBuilder().setSource(Source.external(newBasicIdentityProvider("failing"))).setPublicMessage("Token id hasn't been found").build())
      .when(authenticator).authenticate(request, response);

    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response).sendRedirect("/sessions/unauthorized");
    verify(response).addCookie(cookieArgumentCaptor.capture());
    Cookie cookie = cookieArgumentCaptor.getValue();
    assertThat(cookie.getName()).isEqualTo("AUTHENTICATION-ERROR");
    assertThat(cookie.getValue()).isEqualTo("Token%20id%20hasn%27t%20been%20found");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isHttpOnly()).isFalse();
    assertThat(cookie.getMaxAge()).isEqualTo(300);
    assertThat(cookie.isSecure()).isFalse();
  }

  @Test
  public void return_to_session_unauthorized_when_error_on_from_external_provider_with_context_path() throws Exception {
    when(request.getContextPath()).thenReturn("/sonarqube");
    doThrow(AuthenticationException.newBuilder().setSource(Source.external(newBasicIdentityProvider("failing"))).setPublicMessage("Token id hasn't been found").build())
      .when(authenticator).authenticate(request, response);

    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response).sendRedirect("/sonarqube/sessions/unauthorized");
  }

  @Test
  public void expiration_header_added_when_authenticating_with_an_expiring_token() {
    long expirationTimestamp = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
    UserDto userDto = new UserDto();
    UserTokenDto userTokenDto = new UserTokenDto().setExpirationDate(expirationTimestamp);
    UserSession session = new TokenUserSession(DbTester.create().getDbClient(), userDto, userTokenDto);

    when(authenticator.authenticate(request, response)).thenReturn(session);
    when(threadLocalSession.isLoggedIn()).thenReturn(true);

    assertThat(underTest.initUserSession(request, response)).isTrue();
    verify(response).addHeader("SonarQube-Authentication-Token-Expiration", formatDateTime(expirationTimestamp));
  }

  @Test
  public void initUserSession_shouldPutLoginInMDC() {
    when(threadLocalSession.isLoggedIn()).thenReturn(false);
    when(authenticator.authenticate(request, response)).thenReturn(new MockUserSession("user"));

    underTest.initUserSession(request, response);

    assertThat(MDC.get("LOGIN")).isEqualTo("user");
  }

  @Test
  public void initUserSession_whenSessionLoginIsNull_shouldPutDefaultLoginValueInMDC() {
    when(threadLocalSession.isLoggedIn()).thenReturn(false);
    when(authenticator.authenticate(request, response)).thenReturn(new AnonymousMockUserSession());

    underTest.initUserSession(request, response);

    assertThat(MDC.get("LOGIN")).isEqualTo("-");
  }

  @Test
  public void removeUserSession_shoudlRemoveMDCLogin() {
    when(threadLocalSession.isLoggedIn()).thenReturn(false);
    when(authenticator.authenticate(request, response)).thenReturn(new MockUserSession("user"));
    underTest.initUserSession(request, response);

    underTest.removeUserSession();

    assertThat(MDC.get("LOGIN")).isNull();
  }

  @Test
  public void server_session_gets_the_client_address_and_the_service_login_organization() {
    JavaxHttpRequest javaxRequest = javaxRequest("/api/issues/search", "10.0.0.5", true, "org-key");
    ServerUserSession session = loggedInServerSession(javaxRequest);

    assertThat(underTest.initUserSession(javaxRequest, response)).isTrue();

    verify(session).withIpAllowlist(ipAccess, "10.0.0.5", "org-key");
    verify(threadLocalSession).set(session);
  }

  @Test
  public void service_flag_without_an_organization_gives_no_service_organization() {
    JavaxHttpRequest javaxRequest = javaxRequest("/api/issues/search", "10.0.0.5", true, null);
    ServerUserSession session = loggedInServerSession(javaxRequest);

    underTest.initUserSession(javaxRequest, response);

    verify(session).withIpAllowlist(ipAccess, "10.0.0.5", null);
  }

  @Test
  public void organization_without_the_service_flag_gives_no_service_organization() {
    JavaxHttpRequest javaxRequest = javaxRequest("/api/issues/search", "10.0.0.5", false, "org-key");
    ServerUserSession session = loggedInServerSession(javaxRequest);

    underTest.initUserSession(javaxRequest, response);

    verify(session).withIpAllowlist(ipAccess, "10.0.0.5", null);
  }

  @Test
  public void blank_or_non_text_service_organization_is_ignored() {
    JavaxHttpRequest javaxRequest = javaxRequest("/api/issues/search", "10.0.0.5", true, " ");
    ServerUserSession session = loggedInServerSession(javaxRequest);
    underTest.initUserSession(javaxRequest, response);
    verify(session).withIpAllowlist(ipAccess, "10.0.0.5", null);

    JavaxHttpRequest numberOrgRequest = javaxRequest("/api/issues/search", "10.0.0.5", true, 42);
    ServerUserSession other = loggedInServerSession(numberOrgRequest);
    underTest.initUserSession(numberOrgRequest, response);
    verify(other).withIpAllowlist(ipAccess, "10.0.0.5", null);
  }

  @Test
  public void request_that_is_not_a_javax_request_gives_no_service_organization() {
    when(request.getRemoteAddr()).thenReturn("192.0.2.1");
    ServerUserSession session = loggedInServerSession(request);

    underTest.initUserSession(request, response);

    verify(session).withIpAllowlist(ipAccess, "192.0.2.1", null);
  }

  @Test
  public void missing_client_address_is_passed_on_as_is() {
    when(request.getRemoteAddr()).thenReturn(null);
    ServerUserSession session = loggedInServerSession(request);

    underTest.initUserSession(request, response);

    verify(session).withIpAllowlist(ipAccess, null, null);
  }

  @Test
  public void blocked_member_on_an_organization_url_is_not_logged_out() {
    insertOrganization();
    UserDto member = user("member");
    insertMembership(member);
    ServerUserSession session = realServerSessionOnOrganizationUrl(member, BLOCKED_IP);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verify(threadLocalSession).set(session);
    verifyNoInteractions(authenticationEvent);
    verify(response, never()).setStatus(anyInt());
  }

  @Test
  public void non_member_from_a_blocked_address_gets_the_same_401_as_from_an_allowed_address() {
    insertOrganization();
    UserDto outsider = user("outsider");

    realServerSessionOnOrganizationUrl(outsider, ALLOWED_IP);
    assertThat(underTest.initUserSession(request, response)).isFalse();
    realServerSessionOnOrganizationUrl(outsider, BLOCKED_IP);
    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response, times(2)).setStatus(401);
    verify(authenticationEvent, times(2)).loginFailure(eq(request), any(AuthenticationException.class));
    verify(threadLocalSession, never()).set(any());
  }

  @Test
  public void member_from_an_allowed_address_is_let_in() {
    insertOrganization();
    UserDto member = user("member");
    insertMembership(member);
    ServerUserSession session = realServerSessionOnOrganizationUrl(member, ALLOWED_IP);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verify(threadLocalSession).set(session);
    verifyNoInteractions(authenticationEvent);
  }

  @Test
  public void allowlist_that_cannot_be_read_answers_503_and_does_not_log_out() throws Exception {
    when(request.getRequestURI()).thenReturn("/api/organizations/search");
    when(request.getParameterValues("organization")).thenReturn(new String[] {"org-key"});
    ServerUserSession session = loggedInServerSession(request);
    IpAllowlistUnavailableException unavailable = new IpAllowlistUnavailableException(
      new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", new IllegalStateException("db-host down")));
    when(session.hasMembership("org-key")).thenThrow(unavailable);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    assertThat(underTest.initUserSession(request, response)).isFalse();

    verify(response).setStatus(503);
    verify(response).setContentType("application/json");
    assertThat(body.toString())
      .startsWith("{\"errors\":[{\"msg\":\"")
      .contains("IP allowlist")
      .doesNotContain("db-host");
    verifyNoInteractions(authenticationEvent);
    verify(threadLocalSession, never()).set(any());
  }

  private static final String ORG_UUID = "org-uuid";
  private static final String ORG_KEY = "org-key";
  private static final String BLOCKED_IP = "192.0.2.1";
  private static final String ALLOWED_IP = "203.0.113.9";

  /** A real session, so membership comes from the members table and the allowlist decision from the mocked access. */
  private ServerUserSession realServerSessionOnOrganizationUrl(UserDto user, String clientIp) {
    when(ipAccess.isBlocked(eq(ORG_UUID), eq(BLOCKED_IP), any())).thenReturn(true);
    when(ipAccess.isBlocked(eq(ORG_UUID), eq(ALLOWED_IP), any())).thenReturn(false);
    when(request.getRequestURI()).thenReturn("/api/organizations/search");
    when(request.getParameterValues("organization")).thenReturn(new String[] {ORG_KEY});
    when(request.getRemoteAddr()).thenReturn(clientIp);
    ServerUserSession session = new ServerUserSession(dbTester.getDbClient(), user, false);
    when(authenticator.authenticate(request, response)).thenReturn(session);
    return session;
  }

  private void insertOrganization() {
    dbTester.executeInsert("organizations",
      "uuid", ORG_UUID, "kee", ORG_KEY, "name", "Org", "default_quality_gate_uuid", "qg",
      "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L, "is_archived", false);
  }

  private static UserDto user(String uuid) {
    return new UserDto().setUuid(uuid).setLogin("login-" + uuid).setActive(true);
  }

  private void insertMembership(UserDto user) {
    dbTester.executeInsert("organization_members", "organization_uuid", ORG_UUID, "user_uuid", user.getUuid(), "type", "STANDARD");
  }

  private ServerUserSession loggedInServerSession(HttpRequest forRequest) {
    ServerUserSession session = mock(ServerUserSession.class);
    when(session.isLoggedIn()).thenReturn(true);
    when(session.getLogin()).thenReturn("job");
    when(authenticator.authenticate(forRequest, response)).thenReturn(session);
    return session;
  }

  private static JavaxHttpRequest javaxRequest(String path, String remoteAddr, boolean serviceLogin, Object serviceOrganization) {
    javax.servlet.http.HttpServletRequest servletRequest = mock(javax.servlet.http.HttpServletRequest.class);
    when(servletRequest.getContextPath()).thenReturn("");
    when(servletRequest.getRequestURI()).thenReturn(path);
    when(servletRequest.getRemoteAddr()).thenReturn(remoteAddr);
    when(servletRequest.getAttribute(JwtHttpHandler.SERVICE_LOGIN_ATTRIBUTE)).thenReturn(serviceLogin ? Boolean.TRUE : null);
    when(servletRequest.getAttribute(JwtHttpHandler.SERVICE_LOGIN_ORG_ATTRIBUTE)).thenReturn(serviceOrganization);
    return new JavaxHttpRequest(servletRequest);
  }

  private void assertPathIsIgnored(String path) {
    when(request.getRequestURI()).thenReturn(path);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verifyNoMoreInteractions(threadLocalSession, authenticator);
    reset(threadLocalSession, authenticator);
  }

  private void assertPathIsIgnoredWithAnonymousAccess(String path) {
    when(request.getRequestURI()).thenReturn(path);
    UserSession session = new AnonymousMockUserSession();
    when(authenticator.authenticate(request, response)).thenReturn(session);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verify(threadLocalSession).set(session);
    reset(threadLocalSession, authenticator);
  }

  private void assertPathIsNotIgnored(String path) {
    when(request.getRequestURI()).thenReturn(path);
    UserSession session = new MockUserSession("foo");
    when(authenticator.authenticate(request, response)).thenReturn(session);

    assertThat(underTest.initUserSession(request, response)).isTrue();

    verify(threadLocalSession).set(session);
    reset(threadLocalSession, authenticator);
  }

  private static BaseIdentityProvider newBasicIdentityProvider(String name) {
    BaseIdentityProvider mock = mock(BaseIdentityProvider.class);
    when(mock.getName()).thenReturn(name);
    return mock;
  }
}
