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
}
