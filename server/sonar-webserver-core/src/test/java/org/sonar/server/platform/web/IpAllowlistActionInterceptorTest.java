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
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDao;
import org.sonar.db.component.ComponentDto;
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
import org.sonar.server.ws.ActionInterceptor;
import org.sonar.server.ws.DumbResponse;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WebServiceEngine;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(DataProviderRunner.class)
public class IpAllowlistActionInterceptorTest {

  private static final OrganizationDto ACME = new OrganizationDto().setUuid("org-1").setKey("acme");
  private static final String OUTSIDE_IP = "192.0.2.1";
  /** The contract's body. The engine escapes the apostrophes as \u0027, like every other error it sends. */
  private static final JsonElement ACME_BLOCKED_BODY = JsonParser.parseString("{\"errors\":[{\"msg\":\"Access to organization 'acme' is blocked by"
    + " its IP allowlist. Your IP address is 192.0.2.1.\"}]}");
  private static final String HANDLED = "handled";

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = mock(DbClient.class);
  private final OrganizationDao organizationDao = mock(OrganizationDao.class);
  private final OrganizationMemberDao organizationMemberDao = mock(OrganizationMemberDao.class);
  private final ComponentDao componentDao = mock(ComponentDao.class);
  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);

  {
    when(dbClient.openSession(false)).thenReturn(mock(DbSession.class));
    when(dbClient.organizationDao()).thenReturn(organizationDao);
    when(dbClient.organizationMemberDao()).thenReturn(organizationMemberDao);
    when(dbClient.componentDao()).thenReturn(componentDao);
    when(organizationDao.selectByKey(any(), eq("acme"))).thenReturn(Optional.of(ACME));
    when(organizationDao.selectByUuid(any(), eq("org-1"))).thenReturn(Optional.of(ACME));
    when(componentDao.selectByKey(any(), eq("acme-project"))).thenReturn(Optional.of(new ComponentDto().setKey("acme-project").setOrganizationUuid("org-1")));
    when(componentDao.selectByKey(any(), eq("other-project"))).thenReturn(Optional.of(new ComponentDto().setKey("other-project").setOrganizationUuid("org-2")));
  }

  @Test
  public void blocked_member_naming_the_organization_gets_a_403_that_names_the_organization_and_the_address() {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam("organization", "acme"));

    assertThat(response.status()).isEqualTo(403);
    assertThat(body(response)).isEqualTo(ACME_BLOCKED_BODY);
    verify(ipAccess).logBlocked("acme", OUTSIDE_IP, "jdoe");
  }

  @DataProvider
  public static Object[][] componentParams() {
    return new Object[][] {{"project"}, {"projectKey"}, {"component"}, {"componentKey"}, {"application"}, {"portfolio"}};
  }

  @Test
  @UseDataProvider("componentParams")
  public void blocked_member_naming_a_project_of_the_organization_gets_the_same_403(String param) {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam(param, "acme-project"));

    assertThat(response.status()).isEqualTo(403);
    assertThat(body(response)).isEqualTo(ACME_BLOCKED_BODY);
  }

  @Test
  public void project_of_a_blocked_organization_is_refused_even_when_the_named_organization_is_allowed() {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam("organization", "other").setParam("project", "acme-project"));

    assertThat(response.status()).isEqualTo(403);
    assertThat(body(response)).isEqualTo(ACME_BLOCKED_BODY);
  }

  @Test
  public void value_with_surrounding_spaces_is_read_as_the_action_reads_it() {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam("project", " acme-project "));

    assertThat(response.status()).isEqualTo(403);
  }

  @Test
  public void organization_that_does_not_block_the_request_lets_the_action_run() {
    userSession.logIn("jdoe").setIpBlockedOrganizationUuids("org-2").setClientIp(OUTSIDE_IP);

    DumbResponse response = call(userSession, request().setParam("organization", "acme").setParam("project", "acme-project"));

    assertThat(response.stream().outputAsString()).isEqualTo(HANDLED);
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void call_that_names_no_organization_reads_nothing_from_the_database() {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam("key", "acme-project"));

    assertThat(response.stream().outputAsString()).isEqualTo(HANDLED);
    verifyNoInteractions(dbClient);
  }

  @Test
  public void blank_values_are_ignored() {
    UserSession session = mock(UserSession.class);

    DumbResponse response = call(session, request().setParam("organization", " ").setParam("project", ""));

    assertThat(response.stream().outputAsString()).isEqualTo(HANDLED);
    verifyNoInteractions(session, dbClient);
  }

  @Test
  public void unknown_project_is_left_to_the_action() {
    blockedMemberOfAcme();

    DumbResponse response = call(userSession, request().setParam("project", "missing"));

    assertThat(response.stream().outputAsString()).isEqualTo(HANDLED);
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void request_without_parameters_is_let_through() {
    Request request = mock(Request.class);
    when(request.getParams()).thenReturn(null);
    blockedMemberOfAcme();

    assertThatCode(() -> interceptor(userSession).preAction(mock(WebService.Action.class), request)).doesNotThrowAnyException();
    verifyNoInteractions(dbClient);
  }

  @Test
  public void url_that_carries_no_user_session_is_let_through() {
    ThreadLocalUserSession noSession = new ThreadLocalUserSession();
    noSession.unload();

    DumbResponse response = call(noSession, request().setParam("organization", "acme").setParam("project", "acme-project"));

    assertThat(response.stream().outputAsString()).isEqualTo(HANDLED);
    verifyNoInteractions(dbClient);
  }

  @Test
  public void unreadable_allowlist_answers_503_never_403_401_500_or_allowed() {
    UserSession session = mock(UserSession.class);
    IpAllowlistUnavailableException unavailable = new IpAllowlistUnavailableException(new OrganizationIpAccessUnavailableException("db down", null));
    when(session.isBlockedByIpAllowlistForKey(anyString())).thenThrow(unavailable);
    when(session.isBlockedByIpAllowlist(anyString())).thenThrow(unavailable);
    JsonElement unavailableBody = JsonParser.parseString("{\"errors\":[{\"msg\":\"" + unavailable.getMessage() + "\"}]}");

    DumbResponse byOrganization = call(session, request().setParam("organization", "acme"));
    DumbResponse byProject = call(session, request().setParam("project", "acme-project"));

    assertThat(byOrganization.status()).isEqualTo(503);
    assertThat(body(byOrganization)).isEqualTo(unavailableBody);
    assertThat(byProject.status()).isEqualTo(503);
    assertThat(body(byProject)).isEqualTo(unavailableBody);
    verify(ipAccess, never()).logBlocked(any(), any(), any());
  }

  private void blockedMemberOfAcme() {
    userSession.logIn("jdoe").setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);
    String userUuid = userSession.getUuid();
    when(organizationMemberDao.select(any(), eq("org-1"), eq(userUuid)))
      .thenReturn(Optional.of(new OrganizationMemberDto().setOrganizationUuid("org-1").setUserUuid(userUuid)));
  }

  private static JsonElement body(DumbResponse response) {
    return JsonParser.parseString(response.stream().outputAsString());
  }

  private IpAllowlistActionInterceptor interceptor(UserSession session) {
    return new IpAllowlistActionInterceptor(dbClient, new IpAllowlistRequestCheck(dbClient, session, ipAccess));
  }

  private static TestRequest request() {
    return new TestRequest().setPath("api/foo/bar");
  }

  /** Runs the request through the real web service engine, which turns an exception into the HTTP answer. */
  private DumbResponse call(UserSession session, TestRequest request) {
    WebService webService = context -> {
      WebService.NewController controller = context.createController("api/foo");
      controller.createAction("bar")
        .setDescription("Dummy")
        .setSince("1.0")
        .setHandler((req, resp) -> resp.stream().output().write(HANDLED.getBytes(UTF_8)));
      controller.done();
    };
    WebServiceEngine engine = new WebServiceEngine(new WebService[] {webService}, new ActionInterceptor[] {interceptor(session)});
    engine.start();
    try {
      DumbResponse response = new DumbResponse();
      engine.execute(request, response);
      return response;
    } finally {
      engine.stop();
    }
  }
}
