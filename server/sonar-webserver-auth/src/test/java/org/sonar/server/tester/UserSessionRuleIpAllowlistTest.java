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
package org.sonar.server.tester;

import org.junit.Test;
import org.sonar.db.organization.OrganizationDto;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSessionRuleIpAllowlistTest {

  private final UserSessionRule userSession = UserSessionRule.standalone();

  @Test
  public void logged_in_session_is_blocked_only_for_the_organizations_set_on_it() {
    userSession.logIn().setIpBlockedOrganizationUuids("org-1", "org-2");

    assertThat(userSession.isBlockedByIpAllowlist("org-1")).isTrue();
    assertThat(userSession.isBlockedByIpAllowlist("org-3")).isFalse();
    assertThat(userSession.getIpBlockedOrganizationUuids()).containsExactlyInAnyOrder("org-1", "org-2");
  }

  @Test
  public void anonymous_session_can_be_blocked_too() {
    userSession.anonymous().setIpBlockedOrganizationUuids("org-1");

    assertThat(userSession.isBlockedByIpAllowlist("org-1")).isTrue();
    assertThat(userSession.getIpBlockedOrganizationUuids()).containsExactly("org-1");
  }

  @Test
  public void client_ip_set_on_the_rule_is_reported() {
    userSession.logIn().setClientIp("192.0.2.1");

    assertThat(userSession.getClientIp()).isEqualTo("192.0.2.1");
  }

  @Test
  public void a_new_login_starts_with_nothing_blocked() {
    userSession.logIn().setIpBlockedOrganizationUuids("org-1").setClientIp("192.0.2.1");

    userSession.logIn();

    assertThat(userSession.isBlockedByIpAllowlist("org-1")).isFalse();
    assertThat(userSession.getIpBlockedOrganizationUuids()).isEmpty();
    assertThat(userSession.getClientIp()).isNull();
  }

  @Test
  public void mock_session_answers_on_its_own() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizationUuids("org-1").setClientIp("192.0.2.1");

    assertThat(session.isBlockedByIpAllowlist("org-1")).isTrue();
    assertThat(session.isBlockedByIpAllowlist(null)).isFalse();
    assertThat(session.getIpBlockedOrganizationUuids()).containsExactly("org-1");
    assertThat(session.getClientIp()).isEqualTo("192.0.2.1");
  }

  private final OrganizationDto blockedOrg = new OrganizationDto().setUuid("org-1").setKey("org-key-1");
  private final OrganizationDto otherOrg = new OrganizationDto().setUuid("org-3").setKey("org-key-3");

  @Test
  public void organizations_set_on_the_mock_session_are_blocked_by_uuid_and_by_key() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizations(blockedOrg);

    assertThat(session.isBlockedByIpAllowlist("org-1")).isTrue();
    assertThat(session.isBlockedByIpAllowlistForKey("org-key-1")).isTrue();
    assertThat(session.isBlockedByIpAllowlistForKey("org-key-3")).isFalse();
    assertThat(session.isBlockedByIpAllowlistForKey(null)).isFalse();
    assertThat(session.getIpBlockedOrganizationUuids()).containsExactly("org-1");
  }

  @Test
  public void organizations_set_on_the_rule_are_blocked_by_key() {
    userSession.logIn().setIpBlockedOrganizations(blockedOrg);

    assertThat(userSession.isBlockedByIpAllowlistForKey("org-key-1")).isTrue();
    assertThat(userSession.isBlockedByIpAllowlist("org-1")).isTrue();
  }

  @Test
  public void organizations_blocked_by_uuid_only_have_no_key_to_answer_for() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizationUuids("org-1");

    assertThat(session.isBlockedByIpAllowlistForKey("org-key-1")).isFalse();
  }

  @Test
  public void blocked_organization_gives_no_membership_like_the_real_session() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizations(blockedOrg);

    assertThat(session.hasMembership(blockedOrg)).isFalse();
    assertThat(session.hasMembership("org-key-1")).isFalse();
  }

  @Test
  public void blocked_organization_uuid_alone_gives_no_membership_for_that_organization() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizationUuids("org-1");

    assertThat(session.hasMembership(blockedOrg)).isFalse();
  }

  @Test
  public void a_new_login_forgets_the_blocked_keys() {
    userSession.logIn().setIpBlockedOrganizations(blockedOrg);

    userSession.logIn();

    assertThat(userSession.isBlockedByIpAllowlistForKey("org-key-1")).isFalse();
  }

  @Test
  public void setting_the_blocked_organizations_again_replaces_the_previous_ones() {
    MockUserSession session = new MockUserSession("login").setIpBlockedOrganizations(blockedOrg);

    session.setIpBlockedOrganizations(otherOrg);

    assertThat(session.isBlockedByIpAllowlistForKey("org-key-1")).isFalse();
    assertThat(session.isBlockedByIpAllowlist("org-1")).isFalse();
    assertThat(session.isBlockedByIpAllowlistForKey("org-key-3")).isTrue();
  }
}
