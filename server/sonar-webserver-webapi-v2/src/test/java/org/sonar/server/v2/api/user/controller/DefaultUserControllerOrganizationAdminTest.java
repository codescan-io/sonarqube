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
package org.sonar.server.v2.api.user.controller;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.UserDto;
import org.sonar.server.common.SearchResults;
import org.sonar.server.common.organization.OrganizationService;
import org.sonar.server.common.user.service.UserInformation;
import org.sonar.server.common.user.service.UserService;
import org.sonar.server.common.user.service.UsersSearchRequest;
import org.sonar.server.user.UserSession;
import org.sonar.server.v2.api.model.RestPage;
import org.sonar.server.v2.api.user.converter.UsersSearchRestResponseGenerator;
import org.sonar.server.v2.api.user.request.UsersSearchRestRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers visibility of restricted fields (last connection dates, external login, SCM accounts...)
 * for organization administrators who are not system administrators (root).
 */
public class DefaultUserControllerOrganizationAdminTest {

  private static final String ORG_KEY = "my-org";
  private static final String ORG_UUID = "org-uuid";
  private static final String OTHER_ORG_UUID = "other-org-uuid";
  private static final RestPage PAGE = new RestPage(50, 1);

  private final OrganizationService organizationService = mock(OrganizationService.class);
  private final UserSession userSession = mock(UserSession.class);
  private final UserService userService = mock(UserService.class);
  private final UsersSearchRestResponseGenerator responseGenerator = mock(UsersSearchRestResponseGenerator.class);
  private final DefaultUserController controller = new DefaultUserController(organizationService, userSession, userService, responseGenerator);

  private final OrganizationDto organization = new OrganizationDto().setUuid(ORG_UUID).setKey(ORG_KEY);
  private final UserInformation otherUser = userInformation("other-user-uuid");

  @Before
  public void setUp() {
    when(organizationService.getOrganizationByKey(ORG_KEY)).thenReturn(organization);
    when(userSession.isLoggedIn()).thenReturn(true);
    when(userSession.isSystemAdministrator()).thenReturn(false);
    when(userSession.getUuid()).thenReturn("caller-uuid");
  }

  @Test
  public void search_whenOrganizationAdmin_shouldShowRestrictedFieldsOfAllUsers() {
    when(userSession.hasPermission(OrganizationPermission.ADMINISTER, organization)).thenReturn(true);
    List<UserInformation> users = List.of(otherUser);
    when(userService.findUsers(any())).thenReturn(new SearchResults<>(users, 1));

    controller.search(searchRequest(ORG_KEY), null, PAGE);

    verify(userSession).checkPermission(OrganizationPermission.ADMINISTER, organization);
    verify(responseGenerator).toUsersForResponse(eq(users), any(), eq(true));
  }

  @Test
  public void search_whenOrganizationGiven_shouldOnlyReturnGroupsOfThisOrganization() {
    when(userSession.hasPermission(OrganizationPermission.ADMINISTER, organization)).thenReturn(true);
    when(userService.findUsers(any())).thenReturn(new SearchResults<>(List.of(otherUser), 1));

    controller.search(searchRequest(ORG_KEY), null, PAGE);

    ArgumentCaptor<UsersSearchRequest> requestCaptor = ArgumentCaptor.forClass(UsersSearchRequest.class);
    verify(userService).findUsers(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getOrganizationUuids()).containsExactly(ORG_UUID);
    assertThat(requestCaptor.getValue().getGroupsOrganizationUuid()).contains(ORG_UUID);
  }

  @Test
  public void search_whenRootWithoutOrganization_shouldNotRelyOnOrganizationAdminFlag() {
    when(userSession.isRoot()).thenReturn(true);
    when(userService.findUsers(any())).thenReturn(new SearchResults<>(List.of(otherUser), 1));

    controller.search(searchRequest(null), null, PAGE);

    ArgumentCaptor<UsersSearchRequest> requestCaptor = ArgumentCaptor.forClass(UsersSearchRequest.class);
    verify(userService).findUsers(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getGroupsOrganizationUuid()).isEmpty();
    // system administrators see all fields through UserSession#isSystemAdministrator in the response generator
    verify(responseGenerator).toUsersForResponse(any(), any(), eq(false));
  }

  @Test
  public void fetchUser_whenAdminOfAnOrganizationOfTheUser_shouldShowRestrictedFieldsWithGroupsOfAdministeredOrganizationsOnly() {
    when(userService.fetchUser("other-user-uuid", List.of(ORG_UUID))).thenReturn(otherUser);
    when(organizationService.getOrganizationUuidsOfMember("other-user-uuid")).thenReturn(Set.of(OTHER_ORG_UUID, ORG_UUID));
    when(userSession.hasPermission(OrganizationPermission.ADMINISTER, ORG_UUID)).thenReturn(true);

    controller.fetchUser("other-user-uuid");

    verify(userService).fetchUser("other-user-uuid", List.of(ORG_UUID));
    verify(userService, never()).fetchUser("other-user-uuid");
    verify(responseGenerator).toRestUser(otherUser, true);
  }

  @Test
  public void fetchUser_whenFetchingOwnAccount_shouldReturnAllOwnGroupsWithoutLookingUpOrganizations() {
    UserInformation caller = userInformation("caller-uuid");
    when(userService.fetchUser("caller-uuid")).thenReturn(caller);

    controller.fetchUser("caller-uuid");

    verify(responseGenerator).toRestUser(caller, false);
    verify(organizationService, never()).getOrganizationUuidsOfMember(anyString());
  }

  @Test
  public void fetchUser_whenNotAdminOfAnyOrganizationOfTheUser_shouldHideRestrictedFields() {
    when(userService.fetchUser("other-user-uuid")).thenReturn(otherUser);
    when(organizationService.getOrganizationUuidsOfMember("other-user-uuid")).thenReturn(Set.of(OTHER_ORG_UUID));
    when(userSession.hasPermission(OrganizationPermission.ADMINISTER, ORG_UUID)).thenReturn(true);

    controller.fetchUser("other-user-uuid");

    verify(userService).fetchUser("other-user-uuid");
    verify(responseGenerator).toRestUser(otherUser, false);
  }

  @Test
  public void fetchUser_whenAnonymous_shouldHideRestrictedFieldsWithoutLookingUpOrganizations() {
    when(userSession.isLoggedIn()).thenReturn(false);
    when(userService.fetchUser("other-user-uuid")).thenReturn(otherUser);

    controller.fetchUser("other-user-uuid");

    verify(responseGenerator).toRestUser(otherUser, false);
    verify(organizationService, never()).getOrganizationUuidsOfMember(anyString());
  }

  @Test
  public void fetchUser_whenSystemAdministrator_shouldNotLookUpOrganizations() {
    when(userSession.isSystemAdministrator()).thenReturn(true);
    when(userService.fetchUser("other-user-uuid")).thenReturn(otherUser);

    controller.fetchUser("other-user-uuid");

    verify(responseGenerator).toRestUser(eq(otherUser), anyBoolean());
    verify(organizationService, never()).getOrganizationUuidsOfMember(anyString());
  }

  private static UsersSearchRestRequest searchRequest(String organizationKey) {
    return new UsersSearchRestRequest(organizationKey, null, null, null, null, null, null, null, null, null);
  }

  private static UserInformation userInformation(String uuid) {
    UserDto userDto = new UserDto().setUuid(uuid).setLogin("login_" + uuid).setName("name_" + uuid);
    return new UserInformation(userDto, false, Optional.empty(), List.of(), 0);
  }
}
