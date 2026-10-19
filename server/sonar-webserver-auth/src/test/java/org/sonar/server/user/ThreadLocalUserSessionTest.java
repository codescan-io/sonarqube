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

import org.junit.After;
import org.junit.Before;
import java.util.Set;
import org.junit.Test;
import org.sonar.db.component.ComponentQualifiers;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.entity.EntityDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.GroupTesting;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.ResourceForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.tester.AnonymousMockUserSession;
import org.sonar.server.tester.MockUserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.web.UserRole.USER;

public class ThreadLocalUserSessionTest {

  private final ThreadLocalUserSession threadLocalUserSession = new ThreadLocalUserSession();

  @Before
  public void setUp() {
    // for test isolation
    threadLocalUserSession.unload();
  }

  @After
  public void tearDown() {
    // clean up for next test
    threadLocalUserSession.unload();
  }

  @Test
  public void get_session_for_user() {
    GroupDto group = GroupTesting.newGroupDto();
    MockUserSession expected = new MockUserSession("karadoc")
      .setUuid("karadoc-uuid")
      .setResetPassword(true)
      .setLastSonarlintConnectionDate(1000L)
      .setGroups(group);
    threadLocalUserSession.set(expected);

    UserSession session = threadLocalUserSession.get();
    assertThat(session).isSameAs(expected);
    assertThat(threadLocalUserSession.getLastSonarlintConnectionDate()).isEqualTo(1000L);
    assertThat(threadLocalUserSession.getLogin()).isEqualTo("karadoc");
    assertThat(threadLocalUserSession.getUuid()).isEqualTo("karadoc-uuid");
    assertThat(threadLocalUserSession.isLoggedIn()).isTrue();
    assertThat(threadLocalUserSession.isActive()).isTrue();
    assertThat(threadLocalUserSession.shouldResetPassword()).isTrue();
    assertThat(threadLocalUserSession.getGroups()).extracting(GroupDto::getUuid).containsOnly(group.getUuid());
    assertThat(threadLocalUserSession.hasChildProjectsPermission(USER, new ComponentDto())).isFalse();
    assertThat(threadLocalUserSession.hasChildProjectsPermission(USER, new ProjectDto())).isFalse();
    assertThat(threadLocalUserSession.hasPortfolioChildProjectsPermission(USER, new ComponentDto())).isFalse();
    assertThat(threadLocalUserSession.hasEntityPermission(USER, new ProjectDto().getUuid())).isFalse();
    assertThat(threadLocalUserSession.isAuthenticatedBrowserSession()).isFalse();
  }

  @Test
  public void get_session_for_anonymous() {
    AnonymousMockUserSession expected = new AnonymousMockUserSession();
    threadLocalUserSession.set(expected);

    UserSession session = threadLocalUserSession.get();
    assertThat(session).isSameAs(expected);
    assertThat(threadLocalUserSession.getLogin()).isNull();
    assertThat(threadLocalUserSession.isLoggedIn()).isFalse();
    assertThat(threadLocalUserSession.shouldResetPassword()).isFalse();
    assertThat(threadLocalUserSession.getGroups()).isEmpty();
  }

  @Test
  public void throw_UnauthorizedException_when_no_session() {
    assertThatThrownBy(threadLocalUserSession::get)
      .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  public void checkEntityPermissionOrElseThrowResourceForbiddenException_returns_session_when_permission_to_entity() {
    MockUserSession expected = new MockUserSession("jean-michel");

    ProjectDto subProjectDto = new ProjectDto().setQualifier(ComponentQualifiers.PROJECT).setUuid("subproject-uuid");
    ProjectDto applicationAsProjectDto = new ProjectDto().setQualifier(ComponentQualifiers.APP).setUuid("application-project-uuid");

    expected.registerProjects(subProjectDto);
    expected.registerApplication(applicationAsProjectDto, subProjectDto);
    threadLocalUserSession.set(expected);

    assertThat(threadLocalUserSession.checkEntityPermissionOrElseThrowResourceForbiddenException(USER, applicationAsProjectDto)).isEqualTo(threadLocalUserSession);
  }

  @Test
  public void checkEntityPermissionOrElseThrowResourceForbiddenException_throws_ResourceForbiddenException_when_no_permission_to_entity() {
    MockUserSession expected = new MockUserSession("jean-michel");
    threadLocalUserSession.set(expected);
    EntityDto entity = new ProjectDto();

    assertThatThrownBy(() -> threadLocalUserSession.checkEntityPermissionOrElseThrowResourceForbiddenException(USER, entity))
      .isInstanceOf(ResourceForbiddenException.class);
  }

  @Test
  public void throw_ForbiddenException_when_no_access_to_applications_projects() {
    GroupDto group = GroupTesting.newGroupDto();
    MockUserSession expected = new MockUserSession("karadoc")
      .setUuid("karadoc-uuid")
      .setResetPassword(true)
      .setLastSonarlintConnectionDate(1000L)
      .setGroups(group);
    threadLocalUserSession.set(expected);

    ComponentDto componentDto = new ComponentDto().setQualifier(ComponentQualifiers.APP);
    ProjectDto projectDto = new ProjectDto().setQualifier(ComponentQualifiers.APP).setUuid("project-uuid");
    assertThatThrownBy(() -> threadLocalUserSession.checkChildProjectsPermission(USER, componentDto))
      .isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> threadLocalUserSession.checkChildProjectsPermission(USER, projectDto))
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  public void checkChildProjectsPermission_gets_session_when_user_has_access_to_applications_projects() {
    GroupDto group = GroupTesting.newGroupDto();
    MockUserSession expected = new MockUserSession("karadoc")
      .setUuid("karadoc-uuid")
      .setResetPassword(true)
      .setLastSonarlintConnectionDate(1000L)
      .setGroups(group);

    ProjectDto subProjectDto = new ProjectDto().setQualifier(ComponentQualifiers.PROJECT).setUuid("subproject-uuid");
    ProjectDto applicationAsProjectDto = new ProjectDto().setQualifier(ComponentQualifiers.APP).setUuid("application-project-uuid");

    expected.registerProjects(subProjectDto);
    expected.registerApplication(applicationAsProjectDto, subProjectDto);
    threadLocalUserSession.set(expected);

    assertThat(threadLocalUserSession.checkChildProjectsPermission(USER, applicationAsProjectDto)).isEqualTo(threadLocalUserSession);
  }

  @Test
  public void isBlockedByIpAllowlist_is_answered_by_the_session_of_the_request() {
    UserSession session = mock(UserSession.class);
    when(session.isBlockedByIpAllowlist("org-uuid")).thenReturn(true);
    threadLocalUserSession.set(session);

    assertThat(threadLocalUserSession.isBlockedByIpAllowlist("org-uuid")).isTrue();
  }

  @Test
  public void isBlockedByIpAllowlistForKey_is_answered_by_the_session_of_the_request() {
    UserSession session = mock(UserSession.class);
    when(session.isBlockedByIpAllowlistForKey("org-key")).thenReturn(true);
    threadLocalUserSession.set(session);

    assertThat(threadLocalUserSession.isBlockedByIpAllowlistForKey("org-key")).isTrue();
  }

  @Test
  public void getIpBlockedOrganizationUuids_is_answered_by_the_session_of_the_request() {
    UserSession session = mock(UserSession.class);
    when(session.getIpBlockedOrganizationUuids()).thenReturn(Set.of("org-1", "org-2"));
    threadLocalUserSession.set(session);

    assertThat(threadLocalUserSession.getIpBlockedOrganizationUuids()).containsExactlyInAnyOrder("org-1", "org-2");
  }

  @Test
  public void getClientIp_is_answered_by_the_session_of_the_request() {
    UserSession session = mock(UserSession.class);
    when(session.getClientIp()).thenReturn("192.0.2.1");
    threadLocalUserSession.set(session);

    assertThat(threadLocalUserSession.getClientIp()).isEqualTo("192.0.2.1");
  }
}
