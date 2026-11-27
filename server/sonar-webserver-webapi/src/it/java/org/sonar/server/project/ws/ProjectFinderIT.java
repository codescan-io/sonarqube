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
package org.sonar.server.project.ws;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbTester;
import org.sonar.db.component.ProjectData;
import org.sonar.db.permission.GlobalPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.organization.IpAllowlistUnavailableException;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.project.ws.ProjectFinder.Project;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.user.UserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.web.UserRole.SCAN;
import static org.sonar.server.project.ws.ProjectFinder.SearchResult;

public class ProjectFinderIT {
  @Rule
  public DbTester db = DbTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final ProjectFinder underTest = new ProjectFinder(db.getDbClient(), userSession);

  @Test
  public void selected_projects() {
    ProjectDto project1 = db.components().insertPrivateProject().getProjectDto();
    ProjectDto project2 = db.components().insertPrivateProject().getProjectDto();
    ProjectDto project3 = db.components().insertPrivateProject().getProjectDto();
    userSession.addProjectPermission(SCAN, project1, project2);
    List<Project> projects = underTest.search(db.getSession(), "").getProjects();

    assertThat(projects)
      .extracting(Project::getKey, Project::getName)
      .containsExactlyInAnyOrder(
        tuple(project1.getKey(), project1.getName()),
        tuple(project2.getKey(), project2.getName()));
  }

  @Test
  public void sort_project_by_name() {
    ProjectDto project1 = db.components().insertPrivateProject(p -> p.setKey("project:one").setName("Projet Un")).getProjectDto();
    ProjectDto project2 = db.components().insertPrivateProject(p -> p.setKey("project:two").setName("Projet Deux")).getProjectDto();
    ProjectDto project3 = db.components().insertPrivateProject(p -> p.setKey("project:three").setName("Projet Trois")).getProjectDto();
    ProjectDto project4 = db.components().insertPrivateProject(p -> p.setKey("project:four").setName("Projet Quatre")).getProjectDto();
    userSession.addProjectPermission(SCAN, project1, project2, project3, project4);

    assertThat(underTest.search(db.getSession(), "projet")
      .getProjects())
      .extracting(Project::getName)
      .containsExactly("Projet Deux", "Projet Quatre", "Projet Trois", "Projet Un");
  }

  @Test
  public void projects_are_filtered_by_permissions() {
    ProjectDto project1 = db.components().insertPrivateProject(p -> p.setKey("project:one").setName("Projet Un")).getProjectDto();
    ProjectDto project2 = db.components().insertPrivateProject(p -> p.setKey("project:two").setName("Projet Deux")).getProjectDto();
    ProjectDto project3 = db.components().insertPrivateProject(p -> p.setKey("project:three").setName("Projet Trois")).getProjectDto();
    ProjectDto project4 = db.components().insertPrivateProject(p -> p.setKey("project:four").setName("Projet Quatre")).getProjectDto();
    db.components().insertPrivateProject(p -> p.setKey("project:five").setName("Projet Cinq")).getProjectDto();

    userSession.addProjectPermission(SCAN, project1, project2, project3, project4);

    SearchResult result = underTest.search(db.getSession(), null);

    assertThat(result.getProjects())
      .extracting(Project::getKey, Project::getName)
      .containsExactlyInAnyOrder(
        tuple("project:one", "Projet Un"),
        tuple("project:two", "Projet Deux"),
        tuple("project:three", "Projet Trois"),
        tuple("project:four", "Projet Quatre"));
  }

  @Test
  public void projects_are_not_filtered_due_to_global_scan_permission() {
    db.components().insertPrivateProject(p -> p.setKey("project:one").setName("Projet Un")).getProjectDto();
    db.components().insertPrivateProject(p -> p.setKey("project:two").setName("Projet Deux")).getProjectDto();
    db.components().insertPrivateProject(p -> p.setKey("project:three").setName("Projet Trois")).getProjectDto();
    db.components().insertPrivateProject(p -> p.setKey("project:four").setName("Projet Quatre")).getProjectDto();
    db.components().insertPrivateProject(p -> p.setKey("project:five").setName("Projet Cinq")).getProjectDto();

    userSession.addPermission(GlobalPermission.SCAN);

    SearchResult result = underTest.search(db.getSession(), null);

    assertThat(result.getProjects())
      .extracting(Project::getKey, Project::getName)
      .containsExactlyInAnyOrder(
        tuple("project:one", "Projet Un"),
        tuple("project:two", "Projet Deux"),
        tuple("project:three", "Projet Trois"),
        tuple("project:four", "Projet Quatre"),
        tuple("project:five", "Projet Cinq"));
  }

  @Test
  public void projects_of_organizations_blocked_by_their_ip_allowlist_are_left_out() {
    UserDto user = insertUserByHand();
    ProjectData allowed = db.components().insertPrivateProject(
      c -> c.setKey("project:allowed").setName("Allowed").setOrganizationUuid("org-allowed"), p -> p.setOrganizationUuid("org-allowed"));
    ProjectData blocked = db.components().insertPrivateProject(
      c -> c.setKey("project:blocked").setName("Blocked").setOrganizationUuid("org-blocked"), p -> p.setOrganizationUuid("org-blocked"));
    insertProjectPermissionByHand(user, SCAN, allowed.getProjectDto());
    insertProjectPermissionByHand(user, SCAN, blocked.getProjectDto());

    userSession.logIn(user).setIpBlockedOrganizationUuids("org-blocked");
    assertThat(underTest.search(db.getSession(), null).getProjects())
      .extracting(Project::getKey)
      .containsExactly("project:allowed");

    userSession.logIn(user);
    assertThat(underTest.search(db.getSession(), null).getProjects())
      .extracting(Project::getKey)
      .containsExactlyInAnyOrder("project:allowed", "project:blocked");
  }

  @Test
  public void search_fails_when_the_ip_allowlist_cannot_be_read() {
    UserDto user = insertUserByHand();
    ProjectData project = db.components().insertPrivateProject(c -> c.setOrganizationUuid("org-1"), p -> p.setOrganizationUuid("org-1"));
    insertProjectPermissionByHand(user, SCAN, project.getProjectDto());
    UserSession session = mock(UserSession.class);
    when(session.getUuid()).thenReturn(user.getUuid());
    IpAllowlistUnavailableException unavailable = new IpAllowlistUnavailableException(
      new OrganizationIpAccessUnavailableException("database down", null));
    when(session.isBlockedByIpAllowlist("org-1")).thenThrow(unavailable);
    ProjectFinder underTestWithFailingAllowlist = new ProjectFinder(db.getDbClient(), session);

    assertThatThrownBy(() -> underTestWithFailingAllowlist.search(db.getSession(), null))
      .isSameAs(unavailable);
  }

  @Test
  public void search_by_query_on_name_case_insensitive() {
    ProjectDto project1 = db.components().insertPrivateProject(p -> p.setKey("project:one").setName("Projet Un")).getProjectDto();
    ProjectDto project2 = db.components().insertPrivateProject(p -> p.setKey("project:two").setName("Projet Deux")).getProjectDto();
    ProjectDto project3 = db.components().insertPrivateProject(p -> p.setKey("project:three").setName("Projet Trois")).getProjectDto();
    ProjectDto project4 = db.components().insertPrivateProject(p -> p.setKey("project:four").setName("Projet Quatre")).getProjectDto();

    userSession.addProjectPermission(SCAN, project1, project2, project3, project4);

    assertThat(underTest.search(db.getSession(), "projet")
      .getProjects())
      .extracting(Project::getKey)
      .containsExactlyInAnyOrder("project:one", "project:two", "project:three", "project:four");

    assertThat(underTest.search(db.getSession(), "un")
      .getProjects())
      .extracting(Project::getKey)
      .containsExactlyInAnyOrder("project:one");

    assertThat(underTest.search(db.getSession(), "TROIS")
      .getProjects())
      .extracting(Project::getKey)
      .containsExactlyInAnyOrder("project:three");
  }

  private UserDto insertUserByHand() {
    db.executeInsert("users", "uuid", "user-uuid", "login", "user-login", "name", "User Name", "external_login", "user-login", "external_identity_provider", "sonarqube",
      "external_id", "user-login", "is_root", false, "user_local", true, "onboarded", true, "reset_password", false, "active", true,
      "created_at", 1L, "updated_at", 1L);
    return db.getDbClient().userDao().selectByUuid(db.getSession(), "user-uuid");
  }

  private void insertProjectPermissionByHand(UserDto user, String permission, ProjectDto project) {
    db.executeInsert("user_roles", "uuid", "ur-" + project.getUuid(), "organization_uuid", project.getOrganizationUuid(),
      "role", permission, "user_uuid", user.getUuid(), "entity_uuid", project.getUuid());
  }
}
