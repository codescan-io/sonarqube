/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonar.server.user.ws;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.Paging;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationMemberDao;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserQuery;
import org.sonar.server.es.SearchOptions;
import org.sonar.server.issue.AvatarResolver;
import org.sonar.server.management.ManagedInstanceService;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.Users;
import org.sonarqube.ws.Users.SearchWsResponse;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.Comparator.comparing;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static org.sonar.api.server.ws.WebService.Param.PAGE;
import static org.sonar.api.server.ws.WebService.Param.PAGE_SIZE;
import static org.sonar.api.server.ws.WebService.Param.TEXT_QUERY;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.api.utils.Paging.forPageIndex;
import static org.sonar.core.util.stream.MoreCollectors.toList;
import static org.sonar.server.user.AbstractUserSession.insufficientPrivilegesException;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.Users.SearchWsResponse.Groups;
import static org.sonarqube.ws.Users.SearchWsResponse.ScmAccounts;
import static org.sonarqube.ws.Users.SearchWsResponse.User;
import static org.sonarqube.ws.Users.SearchWsResponse.newBuilder;

public class SearchAction implements UsersWsAction {
  private static final String DEACTIVATED_PARAM = "deactivated";
  private static final int MAX_PAGE_SIZE = 500;

  private final UserSession userSession;
  private final DbClient dbClient;
  private final AvatarResolver avatarResolver;
  private final ManagedInstanceService managedInstanceService;
  private final OrganizationMemberDao organizationMemberDao;

  public SearchAction(UserSession userSession, DbClient dbClient, AvatarResolver avatarResolver,
    ManagedInstanceService managedInstanceService, OrganizationMemberDao organizationMemberDao) {
    this.userSession = userSession;
    this.dbClient = dbClient;
    this.avatarResolver = avatarResolver;
    this.managedInstanceService = managedInstanceService;
    this.organizationMemberDao = organizationMemberDao;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("search")
      .setDescription("Get a list of users. By default, only active users are returned.<br/>" +
        "Requires 'Administer System' permission at an Organization Level or at Global Level." +
        " For Organization Admins, list of users part of the organization(s) are returned")
      .setSince("3.6")
      .setChangelog(
        new Change("10.0", "'q' parameter values is now always performing a case insensitive match"),
        new Change("10.0", "Response includes 'managed' field."),
        new Change("9.9", "Organization Admin can access Email and Last Connection Info of all members of the "
          + "organization. API is accessible only for System Administrators or Organization Administrators"),
        new Change("9.7", "New parameter 'deactivated' to optionally search for deactivated users"),
        new Change("7.7", "New field 'lastConnectionDate' is added to response"),
        new Change("7.4", "External identity is only returned to system administrators"),
        new Change("6.4", "Paging response fields moved to a Paging object"),
        new Change("6.4", "Avatar has been added to the response"),
        new Change("6.4", "Email is only returned when user has Administer System permission"))
      .setHandler(this)
      .setResponseExample(getClass().getResource("search-example.json"));

    action.addPagingParams(50, SearchOptions.MAX_PAGE_SIZE);

    action.createParam(TEXT_QUERY)
      .setMinimumLength(2)
      .setDescription("Filter on login, name and email.<br />" +
        "This parameter can either perform an exact match, or a partial match (contains), it is case insensitive.");
    action.createParam(DEACTIVATED_PARAM)
      .setSince("9.7")
      .setDescription("Return deactivated users instead of active users")
      .setRequired(false)
      .setDefaultValue(false)
      .setBooleanPossibleValues();
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    Users.SearchWsResponse wsResponse = doHandle(toSearchRequest(request));
    writeProtobuf(wsResponse, request, response);
  }

  private Users.SearchWsResponse doHandle(SearchRequest request) {
    UserQuery.UserQueryBuilder userQueryBuilder = buildUserQuery(request);
    try (DbSession dbSession = dbClient.openSession(false)) {
      boolean isSystemAdmin = userSession.checkLoggedIn().isSystemAdministrator();
      boolean showEmailAndLastConnectionInfo = false;
      if (!isSystemAdmin) {
        Set<String> userOrganizations = organizationMemberDao.selectOrganizationUuidsByUser(dbSession, userSession.getUuid());
        var orgsWithUserAsAdmin = userOrganizations.stream()
            .filter(o -> userSession.hasPermission(OrganizationPermission.ADMINISTER, o))
            .toList();
        if (!orgsWithUserAsAdmin.isEmpty()) {
          userQueryBuilder.addOrganizationUuids(orgsWithUserAsAdmin);
          showEmailAndLastConnectionInfo = true;
        } else {
          throw insufficientPrivilegesException();
        }
      }

      UserQuery userQuery = userQueryBuilder.build();
      List<UserDto> users = fetchUsersAndSortByLogin(request, dbSession, userQuery);
      int totalUsers = dbClient.userDao().countUsers(dbSession, userQuery);

      List<String> logins = users.stream().map(UserDto::getLogin).collect(toList());
      Multimap<String, String> groupsByLogin = dbClient.groupMembershipDao().selectGroupsByLogins(dbSession, logins);
      Map<String, Integer> tokenCountsByLogin = dbClient.userTokenDao().countTokensByUsers(dbSession, users);
      Map<String, Boolean> userUuidToIsManaged = managedInstanceService.getUserUuidToManaged(dbSession, getUserUuids(users));
      Paging paging = forPageIndex(request.getPage()).withPageSize(request.getPageSize()).andTotal(totalUsers);
      return buildResponse(users, groupsByLogin, tokenCountsByLogin, userUuidToIsManaged, paging, showEmailAndLastConnectionInfo);
    }
  }

  private static Set<String> getUserUuids(List<UserDto> users) {
    return users.stream().map(UserDto::getUuid).collect(Collectors.toSet());
  }

  private static UserQuery.UserQueryBuilder buildUserQuery(SearchRequest request) {
    return UserQuery.builder()
        .isActive(!request.isDeactivated())
        .searchText(request.getQuery());
  }

  private List<UserDto> fetchUsersAndSortByLogin(SearchRequest request, DbSession dbSession, UserQuery userQuery) {
    return dbClient.userDao().selectUsers(dbSession, userQuery, request.getPage(), request.getPageSize())
        .stream()
        .sorted(comparing(UserDto::getLogin))
        .toList();
  }

  private SearchWsResponse buildResponse(List<UserDto> users, Multimap<String, String> groupsByLogin, Map<String, Integer> tokenCountsByLogin, Map<String, Boolean> userUuidToIsManaged, Paging paging, boolean showEmailAndLastConnectionInfo) {
    SearchWsResponse.Builder responseBuilder = newBuilder();
    users.forEach(user -> responseBuilder.addUsers(
        towsUser(user, firstNonNull(tokenCountsByLogin.get(user.getUuid()), 0), groupsByLogin.get(user.getLogin()), userUuidToIsManaged.get(user.getUuid()), showEmailAndLastConnectionInfo)
    ));
    responseBuilder.getPagingBuilder()
      .setPageIndex(paging.pageIndex())
      .setPageSize(paging.pageSize())
      .setTotal(paging.total())
      .build();
    return responseBuilder.build();
  }

  private User towsUser(UserDto user, @Nullable Integer tokensCount, Collection<String> groups, Boolean isManaged, boolean showEmailAndLastConnectionInfo) {
    User.Builder userBuilder = User.newBuilder().setLogin(user.getLogin());
    ofNullable(user.getName()).ifPresent(userBuilder::setName);
    if (userSession.isLoggedIn()) {
      ofNullable(emptyToNull(user.getEmail())).ifPresent(u -> userBuilder.setAvatar(avatarResolver.create(user)));
      userBuilder.setActive(user.isActive());
      userBuilder.setLocal(user.isLocal());
      ofNullable(user.getExternalIdentityProvider()).ifPresent(userBuilder::setExternalProvider);
      if (!user.getSortedScmAccounts().isEmpty()) {
        userBuilder.setScmAccounts(ScmAccounts.newBuilder().addAllScmAccounts(user.getSortedScmAccounts()));
      }
    }
    if (userSession.isSystemAdministrator() || showEmailAndLastConnectionInfo) {
      ofNullable(user.getEmail()).ifPresent(userBuilder::setEmail);
      if (!groups.isEmpty()) {
        userBuilder.setGroups(Groups.newBuilder().addAllGroups(groups));
      }
      ofNullable(user.getExternalLogin()).ifPresent(userBuilder::setExternalIdentity);
      ofNullable(tokensCount).ifPresent(userBuilder::setTokensCount);
      ofNullable(user.getLastConnectionDate()).ifPresent(date -> userBuilder.setLastConnectionDate(formatDateTime(date)));
      userBuilder.setManaged(TRUE.equals(isManaged));
    }
    return userBuilder.build();
  }

  private static SearchRequest toSearchRequest(Request request) {
    int pageSize = request.mandatoryParamAsInt(PAGE_SIZE);
    checkArgument(pageSize <= MAX_PAGE_SIZE, "The '%s' parameter must be less than %s", PAGE_SIZE, MAX_PAGE_SIZE);
    return SearchRequest.builder()
      .setQuery(request.param(TEXT_QUERY))
      .setDeactivated(request.mandatoryParamAsBoolean(DEACTIVATED_PARAM))
      .setPage(request.mandatoryParamAsInt(PAGE))
      .setPageSize(pageSize)
      .build();
  }

  private static class SearchRequest {
    private final Integer page;
    private final Integer pageSize;
    private final String query;
    private final boolean deactivated;

    private SearchRequest(Builder builder) {
      this.page = builder.page;
      this.pageSize = builder.pageSize;
      this.query = builder.query;
      this.deactivated = builder.deactivated;
    }

    public Integer getPage() {
      return page;
    }

    public Integer getPageSize() {
      return pageSize;
    }

    @CheckForNull
    public String getQuery() {
      return query;
    }

    public boolean isDeactivated() {
      return deactivated;
    }

    public static Builder builder() {
      return new Builder();
    }
  }

  private static class Builder {
    private Integer page;
    private Integer pageSize;
    private String query;
    private boolean deactivated;

    private Builder() {
      // enforce factory method use
    }

    public Builder setPage(Integer page) {
      this.page = page;
      return this;
    }

    public Builder setPageSize(Integer pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    public Builder setQuery(@Nullable String query) {
      this.query = query;
      return this;
    }

    public Builder setDeactivated(boolean deactivated) {
      this.deactivated = deactivated;
      return this;
    }

    public SearchRequest build() {
      return new SearchRequest(this);
    }
  }
}
