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

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDao;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationMemberDao;
import org.sonar.db.organization.OrganizationMemberDto;
import org.sonar.server.organization.IpAllowlistBlockedException;
import org.sonar.server.organization.IpAllowlistUnavailableException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.user.UserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IpAllowlistRequestCheckTest {

  private static final OrganizationDto ACME = new OrganizationDto().setUuid("org-1").setKey("acme");
  private static final String OUTSIDE_IP = "192.0.2.1";

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = mock(DbClient.class);
  private final OrganizationDao organizationDao = mock(OrganizationDao.class);
  private final OrganizationMemberDao organizationMemberDao = mock(OrganizationMemberDao.class);
  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);
  private final IpAllowlistRequestCheck underTest = new IpAllowlistRequestCheck(dbClient, userSession, ipAccess);

  {
    when(dbClient.openSession(false)).thenReturn(mock(DbSession.class));
    when(dbClient.organizationDao()).thenReturn(organizationDao);
    when(dbClient.organizationMemberDao()).thenReturn(organizationMemberDao);
    when(organizationDao.selectByKey(any(), eq("acme"))).thenReturn(Optional.of(ACME));
    when(organizationDao.selectByUuid(any(), eq("org-1"))).thenReturn(Optional.of(ACME));
  }

  @Test
  public void member_of_a_blocked_organization_is_told_the_organization_and_the_address() {
    userSession.logIn("jdoe").setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);
    listAsMember(userSession.getUuid());

    assertThatThrownBy(() -> underTest.checkOrganizationKey("acme"))
      .isInstanceOf(IpAllowlistBlockedException.class)
      .hasMessage("Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.");
    verify(ipAccess).logBlocked("acme", OUTSIDE_IP, "jdoe");
  }

  @Test
  public void organization_that_does_not_block_the_request_is_let_through_without_a_lookup() {
    userSession.logIn("jdoe").setClientIp(OUTSIDE_IP);
    listAsMember(userSession.getUuid());

    assertThatCode(() -> underTest.checkOrganizationKey("acme")).doesNotThrowAnyException();
    verifyNoInteractions(organizationDao, ipAccess);
  }

  @Test
  public void organization_found_by_uuid_is_named_by_its_key() {
    userSession.logIn("jdoe").setIpBlockedOrganizationUuids("org-1").setClientIp(OUTSIDE_IP);
    listAsMember(userSession.getUuid());

    assertThatThrownBy(() -> underTest.checkOrganizationUuid("org-1"))
      .isInstanceOf(IpAllowlistBlockedException.class)
      .hasMessage("Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.");
    verify(ipAccess).logBlocked("acme", OUTSIDE_IP, "jdoe");
  }

  @Test
  public void organization_uuid_that_does_not_block_the_request_is_let_through_without_a_lookup() {
    userSession.logIn("jdoe").setIpBlockedOrganizationUuids("org-2").setClientIp(OUTSIDE_IP);
    listAsMember(userSession.getUuid());

    assertThatCode(() -> underTest.checkOrganizationUuid("org-1")).doesNotThrowAnyException();
    verifyNoInteractions(organizationDao, ipAccess);
  }

  @Test
  public void non_member_is_not_told_about_the_allowlist_of_an_organization_found_by_uuid() {
    userSession.logIn("outsider").setIpBlockedOrganizationUuids("org-1").setClientIp(OUTSIDE_IP);

    assertThatCode(() -> underTest.checkOrganizationUuid("org-1")).doesNotThrowAnyException();
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void missing_address_is_shown_as_unknown_in_the_same_sentence() {
    userSession.logIn("jdoe").setIpBlockedOrganizations(ACME).setClientIp(null);
    listAsMember(userSession.getUuid());

    assertThatThrownBy(() -> underTest.checkOrganizationKey("acme"))
      .isInstanceOf(IpAllowlistBlockedException.class)
      .hasMessage("Access to organization 'acme' is blocked by its IP allowlist. Your IP address is unknown.");
    verify(ipAccess).logBlocked("acme", null, "jdoe");
  }

  @Test
  public void blank_address_is_shown_as_unknown() {
    userSession.logIn("jdoe").setIpBlockedOrganizations(ACME).setClientIp(" ");
    listAsMember(userSession.getUuid());

    assertThatThrownBy(() -> underTest.checkOrganizationKey("acme"))
      .hasMessage("Access to organization 'acme' is blocked by its IP allowlist. Your IP address is unknown.");
  }

  /**
   * An outsider gets the same answer from every address, so they cannot learn that the organization has an allowlist.
   * The user session still withholds the organization's data.
   */
  @Test
  public void non_member_is_not_told_about_the_allowlist() {
    userSession.logIn("outsider").setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);

    assertThatCode(() -> underTest.checkOrganizationKey("acme")).doesNotThrowAnyException();
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void anonymous_visitor_is_not_told_about_the_allowlist() {
    userSession.anonymous().setIpBlockedOrganizations(ACME).setClientIp(OUTSIDE_IP);

    assertThatCode(() -> underTest.checkOrganizationKey("acme")).doesNotThrowAnyException();
    verifyNoInteractions(organizationMemberDao, ipAccess);
  }

  @Test
  public void unreadable_allowlist_stays_a_503_by_key_and_by_uuid() {
    UserSession session = mock(UserSession.class);
    IpAllowlistUnavailableException unavailable = new IpAllowlistUnavailableException(new OrganizationIpAccessUnavailableException("db down", null));
    when(session.isBlockedByIpAllowlistForKey("acme")).thenThrow(unavailable);
    when(session.isBlockedByIpAllowlist("org-1")).thenThrow(unavailable);
    IpAllowlistRequestCheck check = new IpAllowlistRequestCheck(dbClient, session, ipAccess);

    assertThatThrownBy(() -> check.checkOrganizationKey("acme")).isSameAs(unavailable);
    assertThatThrownBy(() -> check.checkOrganizationUuid("org-1")).isSameAs(unavailable);
    assertThat(unavailable.httpCode()).isEqualTo(503);
  }

  private void listAsMember(String userUuid) {
    when(organizationMemberDao.select(any(), eq("org-1"), eq(userUuid)))
      .thenReturn(Optional.of(new OrganizationMemberDto().setOrganizationUuid("org-1").setUserUuid(userUuid)));
  }
}
