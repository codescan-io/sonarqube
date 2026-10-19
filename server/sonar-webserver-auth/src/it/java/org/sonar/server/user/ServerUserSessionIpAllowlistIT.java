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
package org.sonar.server.user;

import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.web.UserRole;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDao;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.TokenType;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTokenDto;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ServerUserSessionIpAllowlistIT {

  private static final String ORG = "org-uuid";
  private static final String ORG_KEY = "org-key";
  private static final String OUTSIDE_IP = "192.0.2.1";
  private static final String INTERNAL_IP = "10.0.0.5";
  private static final String OTHER_ORG = "other-org-uuid";
  private static final String OTHER_ORG_KEY = "other-org-key";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.DEBUG);

  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);

  @Test
  public void member_outside_the_list_loses_membership_and_org_permissions() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.SCAN);
    blockUnlessAdmin(ORG);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isFalse();
    assertThat(session.hasMembership(ORG_KEY)).isFalse();
    assertThat(session.hasPermission(OrganizationPermission.SCAN, ORG)).isFalse();
    assertThat(session.isBlockedByIpAllowlist(ORG)).isTrue();
    assertThat(session.getClientIp()).isEqualTo(OUTSIDE_IP);
  }

  @Test
  public void member_inside_the_list_keeps_membership_and_org_permissions() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.SCAN);
    when(ipAccess.isBlocked(eq(ORG), anyString(), any(BooleanSupplier.class))).thenReturn(false);

    ServerUserSession session = session(user, "203.0.113.9");

    assertThat(session.hasMembership(org())).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.SCAN, ORG)).isTrue();
    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
  }

  @Test
  public void org_admin_is_exempt_when_the_allowlist_does_not_apply_to_admins() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.ADMINISTER);
    blockUnlessAdmin(ORG);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.ADMINISTER, ORG)).isTrue();
    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
  }

  @Test
  public void customer_admin_counts_as_org_admin() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.ADMINISTER_CUSTOMER);
    blockUnlessAdmin(ORG);

    assertThat(session(user, OUTSIDE_IP).hasMembership(org())).isTrue();
  }

  @Test
  public void org_admin_is_blocked_when_the_allowlist_applies_to_admins() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.ADMINISTER);
    when(ipAccess.isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class))).thenReturn(true);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isFalse();
    assertThat(session.hasPermission(OrganizationPermission.ADMINISTER, ORG)).isFalse();
  }

  @Test
  public void project_analysis_token_of_an_org_admin_is_not_exempt_when_admins_are_exempt() {
    UserDto admin = insertMember("u1");
    insertOrgPermission(admin, OrganizationPermission.ADMINISTER);
    blockUnlessAdmin(ORG);

    TokenUserSession session = tokenSession(admin, TokenType.PROJECT_ANALYSIS_TOKEN, OUTSIDE_IP);

    assertThat(session.isBlockedByIpAllowlist(ORG)).isTrue();
    assertThat(session.hasMembership(org())).isFalse();
  }

  @Test
  public void global_analysis_token_of_an_org_admin_is_not_exempt_when_admins_are_exempt() {
    UserDto admin = insertMember("u1");
    insertOrgPermission(admin, OrganizationPermission.ADMINISTER);
    insertOrgPermission(admin, OrganizationPermission.SCAN);
    blockUnlessAdmin(ORG);

    TokenUserSession session = tokenSession(admin, TokenType.GLOBAL_ANALYSIS_TOKEN, OUTSIDE_IP);

    assertThat(session.isBlockedByIpAllowlist(ORG)).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.SCAN, ORG)).isFalse();
  }

  @Test
  public void user_token_of_an_org_admin_is_exempt_when_admins_are_exempt() {
    UserDto admin = insertMember("u1");
    insertOrgPermission(admin, OrganizationPermission.ADMINISTER);
    blockUnlessAdmin(ORG);

    TokenUserSession session = tokenSession(admin, TokenType.USER_TOKEN, OUTSIDE_IP);

    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
    assertThat(session.hasMembership(org())).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.ADMINISTER, ORG)).isTrue();
  }

  @Test
  public void user_token_of_a_plain_member_is_blocked_when_admins_are_exempt() {
    UserDto member = insertMember("u1");
    insertOrgPermission(member, OrganizationPermission.SCAN);
    blockUnlessAdmin(ORG);

    assertThat(tokenSession(member, TokenType.USER_TOKEN, OUTSIDE_IP).isBlockedByIpAllowlist(ORG)).isTrue();
  }

  @Test
  public void blocked_member_gets_no_permission_on_the_projects_and_components_of_the_blocked_organization() {
    UserDto user = insertMember("u1");
    ProjectData project = insertPrivateProject(ORG, user);
    ComponentDto file = insertFile(project, ORG);
    blockEveryone(ORG);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.hasEntityPermission(UserRole.USER, project.getProjectDto())).isFalse();
    assertThat(session.hasEntityPermission(UserRole.USER, project.getProjectDto().getUuid())).isFalse();
    assertThat(session.hasComponentPermission(UserRole.USER, project.getMainBranchComponent())).isFalse();
    assertThat(session.hasComponentPermission(UserRole.USER, file)).isFalse();
  }

  @Test
  public void blocked_member_keeps_none_of_the_blocked_organizations_projects_or_components_and_all_of_an_allowed_ones() {
    UserDto user = insertMember("u1");
    insertMember("u1", OTHER_ORG, OTHER_ORG_KEY);
    ProjectData blockedPrivate = insertPrivateProject(ORG, user);
    ProjectData blockedPublic = insertPublicProject(ORG);
    ComponentDto blockedFile = insertFile(blockedPrivate, ORG);
    ProjectData allowedPrivate = insertPrivateProject(OTHER_ORG, user);
    ProjectData allowedPublic = insertPublicProject(OTHER_ORG);
    ComponentDto allowedFile = insertFile(allowedPrivate, OTHER_ORG);
    blockEveryone(ORG);
    when(ipAccess.isBlocked(eq(OTHER_ORG), anyString(), any(BooleanSupplier.class))).thenReturn(false);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.keepAuthorizedEntities(UserRole.USER, List.of(blockedPrivate.getProjectDto(), blockedPublic.getProjectDto(),
      allowedPrivate.getProjectDto(), allowedPublic.getProjectDto())))
      .containsExactly(allowedPrivate.getProjectDto(), allowedPublic.getProjectDto());
    assertThat(session.keepAuthorizedComponents(UserRole.USER, List.of(blockedPrivate.getMainBranchComponent(), blockedFile,
      blockedPublic.getMainBranchComponent(), allowedPrivate.getMainBranchComponent(), allowedFile, allowedPublic.getMainBranchComponent())))
      .containsExactly(allowedPrivate.getMainBranchComponent(), allowedFile, allowedPublic.getMainBranchComponent());
  }

  @Test
  public void member_of_an_organization_that_lets_the_request_in_keeps_its_project_and_component_permissions() {
    UserDto user = insertMember("u1");
    ProjectData project = insertPrivateProject(ORG, user);
    ComponentDto file = insertFile(project, ORG);
    when(ipAccess.isBlocked(eq(ORG), anyString(), any(BooleanSupplier.class))).thenReturn(false);

    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThat(session.hasEntityPermission(UserRole.USER, project.getProjectDto())).isTrue();
    assertThat(session.hasComponentPermission(UserRole.USER, file)).isTrue();
  }

  @Test
  public void root_is_never_blocked_and_the_allowlist_is_not_consulted() {
    UserDto root = user("root-uuid");
    root.setRoot(true);
    insertOrg();

    ServerUserSession session = session(root, OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.ADMINISTER, ORG)).isTrue();
    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
    assertThat(session.isBlockedByIpAllowlistForKey(ORG_KEY)).isFalse();
    assertThat(session.getIpBlockedOrganizationUuids()).isEmpty();
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void anonymous_loses_org_permissions_of_a_blocked_org() {
    insertOrg();
    db.executeInsert("group_roles", "uuid", "gr1", "organization_uuid", ORG, "role", "scan");
    when(ipAccess.isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class))).thenReturn(true);

    ServerUserSession anonymous = session(null, OUTSIDE_IP);

    assertThat(anonymous.hasPermission(OrganizationPermission.SCAN, ORG)).isFalse();
  }

  @Test
  public void anonymous_keeps_org_permissions_of_an_org_that_does_not_block_it() {
    insertOrg();
    db.executeInsert("group_roles", "uuid", "gr1", "organization_uuid", ORG, "role", "scan");
    when(ipAccess.isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class))).thenReturn(false);

    ServerUserSession anonymous = session(null, OUTSIDE_IP);

    assertThat(anonymous.hasPermission(OrganizationPermission.SCAN, ORG)).isTrue();
  }

  @Test
  public void anonymous_is_never_taken_for_an_admin() {
    insertOrg();
    db.executeInsert("group_roles", "uuid", "gr1", "organization_uuid", ORG, "role", "admin");
    blockUnlessAdmin(ORG);

    assertThat(session(null, OUTSIDE_IP).isBlockedByIpAllowlist(ORG)).isTrue();
  }

  @Test
  public void decision_is_made_once_per_organization_per_request() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.SCAN);
    blockUnlessAdmin(ORG);
    ServerUserSession session = session(user, OUTSIDE_IP);

    session.hasMembership(org());
    session.hasPermission(OrganizationPermission.SCAN, ORG);
    session.isBlockedByIpAllowlist(ORG);

    verify(ipAccess, times(1)).isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class));
  }

  @Test
  public void missing_or_unparseable_client_ip_is_passed_to_the_decision_as_is() {
    UserDto user = insertMember("u1");
    when(ipAccess.isBlocked(eq(ORG), eq("unknown"), any(BooleanSupplier.class))).thenReturn(true);
    when(ipAccess.isBlocked(eq(ORG), eq(""), any(BooleanSupplier.class))).thenReturn(true);

    assertThat(session(user, "unknown").hasMembership(org())).isFalse();
    assertThat(session(user, "").hasMembership(org())).isFalse();
  }

  @Test
  public void null_organization_is_not_blocked() {
    UserDto user = insertMember("u1");

    assertThat(session(user, OUTSIDE_IP).isBlockedByIpAllowlist(null)).isFalse();
  }

  @Test
  public void session_without_allowlist_context_behaves_as_before() {
    UserDto user = insertMember("u1");

    ServerUserSession session = new ServerUserSession(db.getDbClient(), user, false);

    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
    assertThat(session.getIpBlockedOrganizationUuids()).isEmpty();
    assertThat(session.getClientIp()).isNull();
    assertThat(session.hasMembership(org())).isTrue();
    verifyNoInteractions(ipAccess);
  }

  @Test
  public void service_login_from_the_internal_range_is_exempt_for_its_own_organization() {
    UserDto job = insertMember("job");
    insertOrgPermission(job, OrganizationPermission.SCAN);
    blockEveryone(ORG);
    when(ipAccess.isInternalServiceAddress(INTERNAL_IP)).thenReturn(true);

    ServerUserSession session = serviceSession(job, INTERNAL_IP, ORG_KEY);

    assertThat(session.hasMembership(org())).isTrue();
    assertThat(session.hasPermission(OrganizationPermission.SCAN, ORG)).isTrue();
    assertThat(session.isBlockedByIpAllowlist(ORG)).isFalse();
  }

  @Test
  public void service_login_from_the_internal_range_is_blocked_for_another_organization() {
    UserDto job = insertMember("job");
    insertMember("job", OTHER_ORG, OTHER_ORG_KEY);
    blockEveryone(ORG);
    blockEveryone(OTHER_ORG);
    when(ipAccess.isInternalServiceAddress(INTERNAL_IP)).thenReturn(true);

    ServerUserSession session = serviceSession(job, INTERNAL_IP, ORG_KEY);

    assertThat(session.hasMembership(OTHER_ORG_KEY)).isFalse();
    assertThat(session.isBlockedByIpAllowlist(OTHER_ORG)).isTrue();
    assertThat(session.hasMembership(ORG_KEY)).isTrue();
  }

  @Test
  public void service_login_from_outside_the_internal_range_is_blocked() {
    UserDto job = insertMember("job");
    blockEveryone(ORG);
    when(ipAccess.isInternalServiceAddress(OUTSIDE_IP)).thenReturn(false);

    assertThat(serviceSession(job, OUTSIDE_IP, ORG_KEY).hasMembership(ORG_KEY)).isFalse();
  }

  @Test
  public void service_login_without_an_organization_is_blocked() {
    UserDto job = insertMember("job");
    blockEveryone(ORG);
    when(ipAccess.isInternalServiceAddress(INTERNAL_IP)).thenReturn(true);

    assertThat(serviceSession(job, INTERNAL_IP, null).hasMembership(ORG_KEY)).isFalse();
    assertThat(serviceSession(job, INTERNAL_IP, " ").hasMembership(ORG_KEY)).isFalse();
  }

  @Test
  public void service_login_tied_to_an_unknown_organization_is_blocked() {
    UserDto job = insertMember("job");
    blockEveryone(ORG);
    when(ipAccess.isInternalServiceAddress(INTERNAL_IP)).thenReturn(true);

    assertThat(serviceSession(job, INTERNAL_IP, "no-such-org").hasMembership(ORG_KEY)).isFalse();
  }

  @Test
  public void blocked_check_by_key_answers_for_the_organization_with_that_key() {
    insertOrg();
    blockEveryone(ORG);

    ServerUserSession session = session(user("u1"), OUTSIDE_IP);

    assertThat(session.isBlockedByIpAllowlistForKey(ORG_KEY)).isTrue();
  }

  @Test
  public void blocked_check_by_key_is_false_for_an_unknown_or_missing_key() {
    insertOrg();
    blockEveryone(ORG);

    ServerUserSession session = session(user("u1"), OUTSIDE_IP);

    assertThat(session.isBlockedByIpAllowlistForKey("no-such-org")).isFalse();
    assertThat(session.isBlockedByIpAllowlistForKey(null)).isFalse();
  }

  @Test
  public void member_blocked_check_is_true_only_for_a_listed_member_of_an_organization_that_blocks_the_request() {
    UserDto member = insertMember("u1");
    insertOrg(OTHER_ORG, OTHER_ORG_KEY);
    blockEveryone(ORG);
    when(ipAccess.isBlocked(eq(OTHER_ORG), anyString(), any(BooleanSupplier.class))).thenReturn(false);

    ServerUserSession session = session(member, OUTSIDE_IP);

    assertThat(session.isMemberBlockedByIpAllowlistForKey(ORG_KEY)).isTrue();
    assertThat(session.isMemberBlockedByIpAllowlistForKey(OTHER_ORG_KEY)).isFalse();
    assertThat(session.isMemberBlockedByIpAllowlistForKey("no-such-org")).isFalse();
    assertThat(session.isMemberBlockedByIpAllowlistForKey(null)).isFalse();
  }

  @Test
  public void member_blocked_check_is_false_for_a_non_member_or_anonymous_even_when_the_organization_blocks_the_request() {
    insertOrg();
    blockEveryone(ORG);

    assertThat(session(user("outsider"), OUTSIDE_IP).isMemberBlockedByIpAllowlistForKey(ORG_KEY)).isFalse();
    assertThat(session(null, OUTSIDE_IP).isMemberBlockedByIpAllowlistForKey(ORG_KEY)).isFalse();
  }

  @Test
  public void member_blocked_check_is_false_for_an_archived_organization() {
    UserDto member = insertMember("u1");
    db.executeUpdateSql("update organizations set is_archived = true where uuid = '" + ORG + "'");
    blockEveryone(ORG);

    assertThat(session(member, OUTSIDE_IP).isMemberBlockedByIpAllowlistForKey(ORG_KEY)).isFalse();
  }

  @Test
  public void organization_key_is_looked_up_once_per_request() {
    UserDto member = insertMember("u1");
    blockEveryone(ORG);
    DbClient dbClient = spy(db.getDbClient());
    OrganizationDao organizationDao = spy(db.getDbClient().organizationDao());
    doReturn(organizationDao).when(dbClient).organizationDao();
    ServerUserSession session = new ServerUserSession(dbClient, member, false).withIpAllowlist(ipAccess, OUTSIDE_IP, null);

    assertThat(session.isBlockedByIpAllowlistForKey(ORG_KEY)).isTrue();
    assertThat(session.isBlockedByIpAllowlistForKey(ORG_KEY)).isTrue();
    assertThat(session.hasMembership(ORG_KEY)).isFalse();
    assertThat(session.isMemberBlockedByIpAllowlistForKey(ORG_KEY)).isTrue();
    assertThat(session.isBlockedByIpAllowlistForKey("no-such-org")).isFalse();
    assertThat(session.isBlockedByIpAllowlistForKey("no-such-org")).isFalse();

    verify(organizationDao, times(1)).selectByKey(any(), eq(ORG_KEY));
    verify(organizationDao, times(1)).selectByKey(any(), eq("no-such-org"));
  }

  @Test
  public void blocked_organizations_lists_every_organization_that_blocks_the_request() {
    insertOrg();
    insertOrg(OTHER_ORG, OTHER_ORG_KEY);
    blockEveryone(ORG);
    blockEveryone(OTHER_ORG);
    when(ipAccess.blockedOrganizationUuids(OUTSIDE_IP)).thenReturn(Set.of(ORG, OTHER_ORG));

    assertThat(session(user("u1"), OUTSIDE_IP).getIpBlockedOrganizationUuids()).containsExactlyInAnyOrder(ORG, OTHER_ORG);
  }

  @Test
  public void blocked_organizations_leaves_out_those_where_the_user_is_an_exempt_admin() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.ADMINISTER);
    insertOrg(OTHER_ORG, OTHER_ORG_KEY);
    blockUnlessAdmin(ORG);
    blockUnlessAdmin(OTHER_ORG);
    when(ipAccess.blockedOrganizationUuids(OUTSIDE_IP)).thenReturn(Set.of(ORG, OTHER_ORG));

    assertThat(session(user, OUTSIDE_IP).getIpBlockedOrganizationUuids()).containsExactly(OTHER_ORG);
  }

  @Test
  public void blocked_organizations_of_a_service_login_leaves_out_only_its_own_organization() {
    UserDto job = insertMember("job");
    insertOrg(OTHER_ORG, OTHER_ORG_KEY);
    blockEveryone(ORG);
    blockEveryone(OTHER_ORG);
    when(ipAccess.isInternalServiceAddress(INTERNAL_IP)).thenReturn(true);
    when(ipAccess.blockedOrganizationUuids(INTERNAL_IP)).thenReturn(Set.of(ORG, OTHER_ORG));

    assertThat(serviceSession(job, INTERNAL_IP, ORG_KEY).getIpBlockedOrganizationUuids()).containsExactly(OTHER_ORG);
  }

  @Test
  public void blocked_organizations_are_read_once_per_request() {
    insertOrg();
    blockEveryone(ORG);
    when(ipAccess.blockedOrganizationUuids(OUTSIDE_IP)).thenReturn(Set.of(ORG));
    ServerUserSession session = session(user("u1"), OUTSIDE_IP);

    session.getIpBlockedOrganizationUuids();
    session.getIpBlockedOrganizationUuids();

    verify(ipAccess, times(1)).blockedOrganizationUuids(OUTSIDE_IP);
  }

  @Test
  public void allowlist_that_cannot_be_read_fails_the_request_with_503_and_is_never_taken_as_allowed() {
    UserDto user = insertMember("u1");
    insertOrgPermission(user, OrganizationPermission.SCAN);
    OrganizationIpAccessUnavailableException failure = new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist",
      new IllegalStateException("connection refused to db-host:5432"));
    when(ipAccess.isBlocked(eq(ORG), anyString(), any(BooleanSupplier.class))).thenThrow(failure);
    when(ipAccess.blockedOrganizationUuids(OUTSIDE_IP)).thenThrow(failure);
    ServerUserSession session = session(user, OUTSIDE_IP);

    assertUnavailable(() -> session.hasMembership(org()), failure);
    assertUnavailable(() -> session.hasMembership(ORG_KEY), failure);
    assertUnavailable(() -> session.hasPermission(OrganizationPermission.SCAN, ORG), failure);
    assertUnavailable(() -> session.isBlockedByIpAllowlist(ORG), failure);
    assertUnavailable(() -> session.isBlockedByIpAllowlistForKey(ORG_KEY), failure);
    assertUnavailable(session::getIpBlockedOrganizationUuids, failure);
    assertThat(logTester.logs()).anyMatch(line -> line.contains("IP allowlist"));
  }

  @Test
  public void allowlist_failure_is_not_remembered_for_the_rest_of_the_request() {
    UserDto user = insertMember("u1");
    when(ipAccess.isBlocked(eq(ORG), anyString(), any(BooleanSupplier.class)))
      .thenThrow(new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", new IllegalStateException()))
      .thenReturn(false);
    ServerUserSession session = session(user, OUTSIDE_IP);

    assertThatThrownBy(() -> session.hasMembership(org())).isInstanceOf(ServerException.class);
    assertThat(session.hasMembership(org())).isTrue();
  }

  @Test
  public void membership_of_a_non_member_is_answered_without_asking_the_allowlist() {
    insertOrg();
    blockEveryone(ORG);
    ServerUserSession session = session(user("outsider"), OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isFalse();
    assertThat(session.hasMembership(ORG_KEY)).isFalse();

    verify(ipAccess, never()).isBlocked(any(), any(), any(BooleanSupplier.class));
  }

  @Test
  public void non_member_is_not_a_member_rather_than_failing_when_the_allowlist_cannot_be_read() {
    UserDto member = insertMember("u1");
    ProjectData project = insertPrivateProject(ORG, member);
    when(ipAccess.isBlocked(any(), any(), any(BooleanSupplier.class)))
      .thenThrow(new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", new IllegalStateException()));
    ServerUserSession session = session(user("outsider"), OUTSIDE_IP);

    assertThat(session.hasMembership(org())).isFalse();
    assertThat(session.hasMembership(ORG_KEY)).isFalse();
    assertThat(session.hasEntityPermission(UserRole.USER, project.getProjectDto())).isFalse();
  }

  private static void assertUnavailable(ThrowingCallable call, OrganizationIpAccessUnavailableException cause) {
    assertThatThrownBy(call)
      .isInstanceOfSatisfying(ServerException.class, e -> {
        assertThat(e.httpCode()).isEqualTo(503);
        assertThat(e.getCause()).isSameAs(cause);
      })
      .hasMessageContaining("IP allowlist")
      .hasMessageNotContaining("db-host");
  }

  private void blockEveryone(String organizationUuid) {
    when(ipAccess.isBlocked(eq(organizationUuid), anyString(), any(BooleanSupplier.class))).thenReturn(true);
  }

  private ServerUserSession serviceSession(UserDto user, String ip, @Nullable String serviceLoginOrganizationKey) {
    return new ServerUserSession(db.getDbClient(), user, false).withIpAllowlist(ipAccess, ip, serviceLoginOrganizationKey);
  }

  private void blockUnlessAdmin(String organizationUuid) {
    when(ipAccess.isBlocked(eq(organizationUuid), anyString(), any(BooleanSupplier.class)))
      .thenAnswer(invocation -> !invocation.<BooleanSupplier>getArgument(2).getAsBoolean());
  }

  private ProjectData insertPrivateProject(String organizationUuid, UserDto userWithBrowsePermission) {
    ProjectData project = db.components().insertPrivateProject(c -> c.setOrganizationUuid(organizationUuid), p -> p.setOrganizationUuid(organizationUuid));
    db.executeInsert("user_roles", "uuid", "ur-" + project.getProjectDto().getUuid(), "organization_uuid", organizationUuid,
      "role", UserRole.USER, "user_uuid", userWithBrowsePermission.getUuid(), "entity_uuid", project.getProjectDto().getUuid());
    return project;
  }

  private ProjectData insertPublicProject(String organizationUuid) {
    return db.components().insertPublicProject(c -> c.setOrganizationUuid(organizationUuid), p -> p.setOrganizationUuid(organizationUuid));
  }

  private ComponentDto insertFile(ProjectData project, String organizationUuid) {
    return db.components().insertComponent(ComponentTesting.newFileDto(project.getMainBranchComponent()).setOrganizationUuid(organizationUuid));
  }

  private TokenUserSession tokenSession(UserDto user, TokenType type, String ip) {
    UserTokenDto token = new UserTokenDto().setType(type.name()).setProjectUuid("project-uuid");
    TokenUserSession session = new TokenUserSession(db.getDbClient(), user, token);
    session.withIpAllowlist(ipAccess, ip, null);
    return session;
  }

  private ServerUserSession session(@Nullable UserDto user, @Nullable String ip) {
    return new ServerUserSession(db.getDbClient(), user, false).withIpAllowlist(ipAccess, ip, null);
  }

  private static UserDto user(String uuid) {
    return new UserDto().setUuid(uuid).setLogin("login-" + uuid).setActive(true);
  }

  private UserDto insertMember(String userUuid) {
    return insertMember(userUuid, ORG, ORG_KEY);
  }

  private UserDto insertMember(String userUuid, String organizationUuid, String organizationKey) {
    insertOrg(organizationUuid, organizationKey);
    db.executeInsert("organization_members", "organization_uuid", organizationUuid, "user_uuid", userUuid, "type", "STANDARD");
    return user(userUuid);
  }

  private void insertOrg() {
    insertOrg(ORG, ORG_KEY);
  }

  private void insertOrg(String organizationUuid, String organizationKey) {
    if (db.countSql("select count(*) from organizations where uuid = '" + organizationUuid + "'") == 0) {
      db.executeInsert("organizations",
        "uuid", organizationUuid, "kee", organizationKey, "name", "Org " + organizationKey, "default_quality_gate_uuid", "qg",
        "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L,
        "is_archived", false);
    }
  }

  private OrganizationDto org() {
    return db.getDbClient().organizationDao().selectByUuid(db.getSession(), ORG).orElseThrow();
  }

  private void insertOrgPermission(UserDto user, OrganizationPermission permission) {
    db.executeInsert("user_roles", "uuid", "ur-" + permission.getKey(), "organization_uuid", ORG,
      "role", permission.getKey(), "user_uuid", user.getUuid());
  }
}
