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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.server.http.HttpResponse;
import org.sonar.api.web.FilterChain;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDao;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationMemberDao;
import org.sonar.db.organization.OrganizationMemberDto;
import org.sonar.server.organization.IpAllowlistUnavailableException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.UserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IpAllowlistApiV2FilterTest {

  private static final OrganizationDto ACME = new OrganizationDto().setUuid("org-1").setKey("acme");
  private static final String OUTSIDE_IP = "192.0.2.1";

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = mock(DbClient.class);
  private final OrganizationDao organizationDao = mock(OrganizationDao.class);
  private final OrganizationMemberDao organizationMemberDao = mock(OrganizationMemberDao.class);
  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);
  private final HttpRequest request = mock(HttpRequest.class);
  private final HttpResponse response = mock(HttpResponse.class);
  private final FilterChain chain = mock(FilterChain.class);
  private final StringWriter body = new StringWriter();

  {
    when(dbClient.openSession(false)).thenReturn(mock(DbSession.class));
    when(dbClient.organizationDao()).thenReturn(organizationDao);
    when(dbClient.organizationMemberDao()).thenReturn(organizationMemberDao);
    when(organizationDao.selectByKey(any(), eq("acme"))).thenReturn(Optional.of(ACME));
    try {
      when(response.getWriter()).thenReturn(new PrintWriter(body));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void pattern_covers_api_v2_only() {
    IpAllowlistApiV2Filter underTest = filter(userSession);

    assertThat(underTest.doGetPattern().matches("/api/v2/users-management/users")).isTrue();
    assertThat(underTest.doGetPattern().matches("/api/v2/authorizations/groups")).isTrue();
    assertThat(underTest.doGetPattern().matches("/api/issues/search")).isFalse();
    assertThat(underTest.doGetPattern().matches("/project/issues")).isFalse();
  }

  @Test
  public void blocked_member_gets_the_web_service_403_body_and_the_request_goes_no_further() throws Exception {
    blockedMemberOfAcme();
    when(request.getParameter("organization")).thenReturn("acme");

    filter(userSession).doFilter(request, response, chain);

    verify(response).setStatus(403);
    verify(response).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    assertThat(JsonParser.parseString(body.toString())).isEqualTo(JsonParser.parseString(
      "{\"errors\":[{\"msg\":\"Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.\"}]}"));
    verify(ipAccess).logBlocked("acme", OUTSIDE_IP, "jdoe");
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  public void organization_that_does_not_block_the_request_lets_it_through() throws Exception {
    userSession.logIn("jdoe").setClientIp(OUTSIDE_IP);
    when(request.getParameter("organization")).thenReturn("acme");

    filter(userSession).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(response, never()).setStatus(any(Integer.class));
  }

  @Test
  public void request_that_names_no_organization_is_let_through_without_a_lookup() throws Exception {
    blockedMemberOfAcme();

    filter(userSession).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(dbClient);
  }

  @Test
  public void blank_organization_is_let_through_without_a_lookup() throws Exception {
    UserSession session = mock(UserSession.class);
    when(request.getParameter("organization")).thenReturn(" ");

    filter(session).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(session, dbClient);
  }

  @Test
  public void non_member_is_let_through_to_the_answer_any_address_gets() throws Exception {
    userSession.logIn("outsider").setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);
    when(request.getParameter("organization")).thenReturn("acme");

    filter(userSession).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void url_that_carries_no_user_session_is_let_through() throws Exception {
    ThreadLocalUserSession noSession = new ThreadLocalUserSession();
    noSession.unload();
    when(request.getParameter("organization")).thenReturn("acme");

    filter(noSession).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verifyNoInteractions(dbClient);
  }

  @Test
  public void organization_is_not_resolved_when_no_organization_blocks_the_caller() throws Exception {
    UserSession session = mock(UserSession.class);
    when(session.mayBeBlockedByIpAllowlist()).thenReturn(false);
    when(session.getIpBlockedOrganizationUuids()).thenThrow(new AssertionError("must not list every blocking organization"));
    when(request.getParameter("organization")).thenReturn("acme");

    filter(session).doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(session, never()).isBlockedByIpAllowlistForKey(any());
    verifyNoInteractions(dbClient);
  }

  @Test
  public void blocked_member_is_refused_by_asking_about_the_named_organization_only() throws Exception {
    UserSession session = mock(UserSession.class);
    when(session.mayBeBlockedByIpAllowlist()).thenReturn(true);
    when(session.getIpBlockedOrganizationUuids()).thenThrow(new AssertionError("must not list every blocking organization"));
    when(session.isBlockedByIpAllowlistForKey("acme")).thenReturn(true);
    when(session.isLoggedIn()).thenReturn(true);
    when(session.getUuid()).thenReturn("u1");
    when(session.getLogin()).thenReturn("jdoe");
    when(session.getClientIp()).thenReturn(OUTSIDE_IP);
    when(organizationMemberDao.select(any(), eq("org-1"), eq("u1")))
      .thenReturn(Optional.of(new OrganizationMemberDto().setOrganizationUuid("org-1").setUserUuid("u1")));
    when(request.getParameter("organization")).thenReturn("acme");

    filter(session).doFilter(request, response, chain);

    verify(response).setStatus(403);
    verify(chain, never()).doFilter(any(), any());
    verify(session, never()).getIpBlockedOrganizationUuids();
  }

  @Test
  public void unreadable_allowlist_answers_503_never_403_401_500_or_allowed() throws Exception {
    UserSession session = mock(UserSession.class);
    IpAllowlistUnavailableException unavailable = unavailable();
    when(session.mayBeBlockedByIpAllowlist()).thenThrow(unavailable);
    when(request.getParameter("organization")).thenReturn("acme");

    filter(session).doFilter(request, response, chain);

    assertUnavailableAnswer(unavailable);
  }

  @Test
  public void allowlist_that_cannot_be_read_for_the_named_organization_answers_503_not_allowed() throws Exception {
    UserSession session = mock(UserSession.class);
    IpAllowlistUnavailableException unavailable = unavailable();
    when(session.mayBeBlockedByIpAllowlist()).thenReturn(true);
    when(session.isBlockedByIpAllowlistForKey("acme")).thenThrow(unavailable);
    when(request.getParameter("organization")).thenReturn("acme");

    filter(session).doFilter(request, response, chain);

    assertUnavailableAnswer(unavailable);
  }

  private static IpAllowlistUnavailableException unavailable() {
    return new IpAllowlistUnavailableException(new OrganizationIpAccessUnavailableException("db down", null));
  }

  private void assertUnavailableAnswer(IpAllowlistUnavailableException unavailable) throws Exception {
    verify(response).setStatus(503);
    verify(response).setContentType("application/json");
    JsonElement expected = JsonParser.parseString("{\"errors\":[{\"msg\":\"" + unavailable.getMessage() + "\"}]}");
    assertThat(JsonParser.parseString(body.toString())).isEqualTo(expected);
    verify(chain, never()).doFilter(any(), any());
    verify(ipAccess, never()).logBlocked(any(), any(), any());
  }

  private void blockedMemberOfAcme() {
    userSession.logIn("jdoe").setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);
    String userUuid = userSession.getUuid();
    when(organizationMemberDao.select(any(), eq("org-1"), eq(userUuid)))
      .thenReturn(Optional.of(new OrganizationMemberDto().setOrganizationUuid("org-1").setUserUuid(userUuid)));
  }

  private IpAllowlistApiV2Filter filter(UserSession session) {
    return new IpAllowlistApiV2Filter(new IpAllowlistRequestCheck(dbClient, session, ipAccess));
  }
}
