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
package org.sonar.server.usergroups.ws;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.security.DefaultGroups;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.NotFoundException;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;

@ServerSide
public class GroupService {

  private static final Logger logger = LoggerFactory.getLogger(GroupService.class);

  private final DbClient dbClient;

  public GroupService(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  /**
   * Loads organization from database by its key.
   * @param dbSession db session
   * @param key the organization key
   * @return non-null organization
   * @throws NotFoundException if no organizations match the provided key
   */
  public OrganizationDto findOrganizationByKey(DbSession dbSession, String key) {
    return checkFoundWithOptional(dbClient.organizationDao().selectByKey(dbSession, key), "No organization with key '%s'", key);
  }

  public GroupDto findGroupDtoOrThrow(DbSession dbSession, OrganizationDto organization, String groupName) {
    return dbClient.groupDao()
      .selectByName(dbSession, organization.getUuid(), groupName)
      .orElseThrow(() -> new NotFoundException(format("No group with name '%s'", groupName)));
  }

  public void delete(DbSession dbSession, GroupDto group) {
    OrganizationDto organization = dbClient.organizationDao().selectByUuid(dbSession, group.getOrganizationUuid())
        .orElseThrow(() -> new IllegalStateException("No org found: " + group.getOrganizationUuid()));
    logger.info("Delete Group Request :: groupName: {} and organization: {}, orgId: {}", group.getName(),
        organization.getKey(), organization.getUuid());

    checkNotTryingToDeleteLastAdminGroup(dbSession, group);

    removeGroupPermissions(dbSession, group);
    removeGroupFromPermissionTemplates(dbSession, group);
    removeGroupMembers(dbSession, group);
    removeGroupFromQualityProfileEdit(dbSession, group);
    removeGroupFromQualityGateEdit(dbSession, group);
    removeGroupScimLink(dbSession, group);
    removeGroup(dbSession, group);
  }

  void checkGroupIsNotDefault(DbSession dbSession, GroupDto groupDto) {
    GroupDto defaultGroup = findDefaultGroup(dbSession, groupDto.getOrganizationUuid());
    checkArgument(!defaultGroup.getUuid().equals(groupDto.getUuid()), "Default group '%s' cannot be used to perform this action", groupDto.getName());
  }

  private GroupDto findDefaultGroup(DbSession dbSession, String organizationUuid) {
    return dbClient.groupDao().selectByName(dbSession, organizationUuid, DefaultGroups.USERS)
      .orElseThrow(() -> new IllegalStateException("Default group cannot be found"));
  }

  private void checkNotTryingToDeleteLastAdminGroup(DbSession dbSession, GroupDto group) {
    int remaining = dbClient.authorizationDao().countUsersWithGlobalPermissionExcludingGroup(dbSession,
        group.getOrganizationUuid(), OrganizationPermission.ADMINISTER.getKey(), group.getUuid());

    checkArgument(remaining > 0, "The last system admin group cannot be deleted");
  }

  private void removeGroupPermissions(DbSession dbSession, GroupDto group) {
    logger.debug("Removing group permissions for group: {}", group.getName());
    dbClient.roleDao().deleteGroupRolesByGroupUuid(dbSession, group.getUuid());
  }

  private void removeGroupFromPermissionTemplates(DbSession dbSession, GroupDto group) {
    logger.debug("Removing group from permission template for group: {}", group.getName());
    dbClient.permissionTemplateDao().deleteByGroup(dbSession, group.getUuid(), group.getName());
  }

  private void removeGroupMembers(DbSession dbSession, GroupDto group) {
    logger.debug("Removing group members for group: {}", group.getName());
    dbClient.userGroupDao().deleteByGroupUuid(dbSession, group.getUuid(), group.getName());
  }

  private void removeGroupFromQualityProfileEdit(DbSession dbSession, GroupDto group) {
    dbClient.qProfileEditGroupsDao().deleteByGroup(dbSession, group);
  }

  private void removeGroupFromQualityGateEdit(DbSession dbSession, GroupDto group) {
    dbClient.qualityGateGroupPermissionsDao().deleteByGroup(dbSession, group);
  }

  private void removeGroupScimLink(DbSession dbSession, GroupDto group) {
    dbClient.scimGroupDao().deleteByGroupUuid(dbSession, group.getUuid());
  }

  private void removeGroup(DbSession dbSession, GroupDto group) {
    dbClient.groupDao().deleteByUuid(dbSession, group.getUuid(), group.getName());
  }

  public void deleteScimMembersByGroup(DbSession dbSession, GroupDto groupDto) {
    Set<UserDto> scimUsers = dbClient.userGroupDao().selectScimMembersByGroupUuid(dbSession, groupDto);
    dbClient.userGroupDao().deleteFromGroupByUserUuids(dbSession, groupDto, scimUsers);
  }
}
