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
package org.sonar.server.pushapi.sonarlint;

import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbTester;
import org.sonar.db.component.ProjectData;
import org.sonar.db.user.UserDto;
import org.sonar.server.authentication.UserLastConnectionDatesUpdater;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.user.UserSessionFactoryImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Push events are checked on the poll thread, outside any web request, so the address the push connection was opened
 * from is what the organizations' IP allowlists are applied to.
 */
public class SonarLintClientPermissionsValidatorIpAllowlistIT {

  private static final String BLOCKED_ORG = "blocked-org-uuid";
  private static final String ALLOWED_ORG = "allowed-org-uuid";
  private static final String CLIENT_IP = "192.0.2.1";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);
  private final SonarLintClientPermissionsValidator underTest = new SonarLintClientPermissionsValidator(db.getDbClient(),
    new UserSessionFactoryImpl(db.getDbClient(), mock(UserLastConnectionDatesUpdater.class)), ipAccess);

  @Test
  public void blocked_member_gets_no_event_for_a_project_of_the_blocked_organization() {
    UserDto user = insertUser();
    ProjectData project = insertProjectBrowsableBy(BLOCKED_ORG, user);
    when(ipAccess.isBlocked(eq(BLOCKED_ORG), eq(CLIENT_IP), any(BooleanSupplier.class))).thenReturn(true);

    Set<String> projectUuids = Set.of(project.getProjectDto().getUuid());
    assertThatThrownBy(() -> underTest.validateUserCanReceivePushEventForProjectUuids(user.getUuid(), CLIENT_IP, projectUuids))
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void blocked_member_still_gets_events_for_a_project_of_an_allowed_organization() {
    UserDto user = insertUser();
    insertProjectBrowsableBy(BLOCKED_ORG, user);
    ProjectData allowedProject = insertProjectBrowsableBy(ALLOWED_ORG, user);
    when(ipAccess.isBlocked(eq(BLOCKED_ORG), eq(CLIENT_IP), any(BooleanSupplier.class))).thenReturn(true);

    Set<String> projectUuids = Set.of(allowedProject.getProjectDto().getUuid());
    assertThatCode(() -> underTest.validateUserCanReceivePushEventForProjectUuids(user.getUuid(), CLIENT_IP, projectUuids))
      .doesNotThrowAnyException();
  }

  @Test
  public void no_event_when_the_allowlist_cannot_be_read() {
    UserDto user = insertUser();
    ProjectData project = insertProjectBrowsableBy(BLOCKED_ORG, user);
    when(ipAccess.isBlocked(anyString(), any(), any(BooleanSupplier.class)))
      .thenThrow(new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", new IllegalStateException()));

    Set<String> projectUuids = Set.of(project.getProjectDto().getUuid());
    assertThatThrownBy(() -> underTest.validateUserCanReceivePushEventForProjectUuids(user.getUuid(), CLIENT_IP, projectUuids))
      .isInstanceOfSatisfying(ServerException.class, e -> assertThat(e.httpCode()).isEqualTo(503));
  }

  // inserted by hand: the users table of this schema has a required is_root column the user test helpers leave out
  private UserDto insertUser() {
    db.executeInsert("users", "uuid", "user-uuid", "login", "user-login", "external_login", "user-login", "external_identity_provider", "sonarqube",
      "external_id", "user-login", "is_root", false, "user_local", true, "onboarded", true, "reset_password", false, "active", true,
      "created_at", 1L, "updated_at", 1L);
    return db.getDbClient().userDao().selectByUuid(db.getSession(), "user-uuid");
  }

  private ProjectData insertProjectBrowsableBy(String organizationUuid, UserDto user) {
    insertOrganization(organizationUuid);
    db.executeInsert("organization_members", "organization_uuid", organizationUuid, "user_uuid", user.getUuid(), "type", "STANDARD");
    ProjectData project = db.components().insertPrivateProject(c -> c.setOrganizationUuid(organizationUuid), p -> p.setOrganizationUuid(organizationUuid));
    db.executeInsert("user_roles", "uuid", "ur-" + project.getProjectDto().getUuid(), "organization_uuid", organizationUuid,
      "role", UserRole.USER, "user_uuid", user.getUuid(), "entity_uuid", project.getProjectDto().getUuid());
    return project;
  }

  private void insertOrganization(String organizationUuid) {
    db.executeInsert("organizations",
      "uuid", organizationUuid, "kee", "key-" + organizationUuid, "name", "Org " + organizationUuid, "default_quality_gate_uuid", "qg",
      "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L, "is_archived", false);
  }
}
