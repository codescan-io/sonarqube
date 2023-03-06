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
import javax.annotation.Nullable;
import org.sonar.api.security.DefaultGroups;
import org.sonar.api.server.ServerSide;
import org.sonar.api.user.UserGroupValidation;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.user.GroupDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;
import static org.sonar.server.exceptions.BadRequestException.checkRequest;

@ServerSide
public class GroupService {

  private static final Logger logger = LoggerFactory.getLogger(GroupService.class);

  private final DbClient dbClient;
  private final UuidFactory uuidFactory;

  public GroupService(DbClient dbClient, UuidFactory uuidFactory) {
    this.dbClient = dbClient;
    this.uuidFactory = uuidFactory;
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

  public GroupDto updateGroup(DbSession dbSession, OrganizationDto organization, GroupDto group, @Nullable String newName, @Nullable String newDescription) {
    logger.info("Update Group Request :: groupName: {} and organization: {}, orgId: {}, newName: {}", group.getName(),
        organization.getKey(), organization.getUuid(), newName);

    GroupDto withUpdatedName = updateName(dbSession, organization, group, newName);
    return updateDescription(dbSession, withUpdatedName, newDescription);
  }

  public GroupDto createGroup(DbSession dbSession, OrganizationDto organization, String name, @Nullable String description) {
    validateGroupName(name);
    checkNameDoesNotExist(dbSession, organization.getUuid(), name);

    logger.info("Create Group Request :: groupName: {} and organization: {}, orgId: {}", name,
        organization.getKey(), organization.getUuid());

    GroupDto group = new GroupDto()
      .setUuid(uuidFactory.create())
      .setName(name)
      .setOrganizationUuid(organization.getUuid())
      .setDescription(description);
    return dbClient.groupDao().insert(dbSession, group);
  }

  private GroupDto updateName(DbSession dbSession, OrganizationDto organization, GroupDto group, @Nullable String newName) {
    if (newName != null && !newName.equals(group.getName())) {
      validateGroupName(newName);
      checkNameDoesNotExist(dbSession, organization.getUuid(), newName);
      group.setName(newName);
      return dbClient.groupDao().update(dbSession, group);
    }
    return group;
  }

  private static void validateGroupName(String name) {
    try {
      UserGroupValidation.validateGroupName(name);
    } catch (IllegalArgumentException e) {
      BadRequestException.throwBadRequestException(e.getMessage());
    }
  }

  private void checkNameDoesNotExist(DbSession dbSession, String organizationUuid, String name) {
    // There is no database constraint on column groups.name
    // because MySQL cannot create a unique index
    // on a UTF-8 VARCHAR larger than 255 characters on InnoDB
    checkRequest(!dbClient.groupDao().selectByName(dbSession, organizationUuid, name).isPresent(), "Group '%s' already exists", name);
  }

  private GroupDto updateDescription(DbSession dbSession, GroupDto group, @Nullable String newDescription) {
    if (newDescription != null) {
      group.setDescription(newDescription);
      return dbClient.groupDao().update(dbSession, group);
    }
    return group;
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

}
