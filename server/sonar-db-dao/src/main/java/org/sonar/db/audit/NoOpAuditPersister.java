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
package org.sonar.db.audit;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import org.sonar.db.DbSession;
import org.sonar.db.audit.model.ComponentKeyNewValue;
import org.sonar.db.audit.model.ComponentNewValue;
import org.sonar.db.audit.model.DevOpsPlatformSettingNewValue;
import org.sonar.db.audit.model.AbstractEditorNewValue;
import org.sonar.db.audit.model.GroupPermissionNewValue;
import org.sonar.db.audit.model.LicenseNewValue;
import org.sonar.db.audit.model.NewValue;
import org.sonar.db.audit.model.PermissionTemplateNewValue;
import org.sonar.db.audit.model.PersonalAccessTokenNewValue;
import org.sonar.db.audit.model.PluginNewValue;
import org.sonar.db.audit.model.ProjectBadgeTokenNewValue;
import org.sonar.db.audit.model.PropertyNewValue;
import org.sonar.db.audit.model.SecretNewValue;
import org.sonar.db.audit.model.UserGroupNewValue;
import org.sonar.db.audit.model.UserNewValue;
import org.sonar.db.audit.model.UserPermissionNewValue;
import org.sonar.db.audit.model.UserTokenNewValue;
import org.sonar.db.audit.model.WebhookNewValue;
import org.sonar.server.user.ThreadLocalUserSession;

@Priority(2)
public class NoOpAuditPersister implements AuditPersister {

  private static final Set<String> TRACKED_PROPERTIES = Set.of(
          "sonar.forceAuthentication",
          "sonar.developerAggregatedInfo.disabled",
          "sonar.scm.disabled",
          "sonar.core.serverBaseURL",
          "sonar.validateWebhooks",
          "sonar.plugins.risk.consent",
          "email.smtp_host.secured",
          "email.smtp_password.secured",
          "email.smtp_port.secured",
          "email.smtp_secure_connection.secured",
          "email.smtp_username.secured",
          "sonar.auth.github.allowUsersToSignUp",
          "sonar.auth.github.apiUrl",
          "sonar.auth.github.clientId.secured",
          "sonar.auth.github.clientSecret.secured",
          "sonar.auth.github.enabled",
          "sonar.auth.github.groupsSync",
          "sonar.auth.github.organizations",
          "sonar.auth.github.webUrl",
          "sonar.auth.gitlab.allowUsersToSignUp",
          "sonar.auth.gitlab.applicationId.secured",
          "sonar.auth.gitlab.enabled",
          "sonar.auth.gitlab.secret.secured",
          "sonar.auth.gitlab.groupsSync",
          "sonar.auth.gitlab.url",
          "defaultTemplate.prj",
          "defaultTemplate.app",
          "defaultTemplate.port",
          "sonar.auth.saml.applicationId",
          "sonar.auth.saml.certificate.secured",
          "sonar.auth.saml.enabled",
          "sonar.auth.saml.group.name",
          "sonar.auth.saml.loginUrl",
          "sonar.auth.saml.providerId",
          "sonar.auth.saml.providerName",
          "sonar.auth.saml.user.email",
          "sonar.auth.saml.user.login",
          "sonar.auth.saml.user.name",
          "sonar.auth.saml.signature.enabled",
          "sonar.auth.saml.sp.certificate.secured",
          "sonar.auth.saml.sp.privateKey.secured",
          "sonar.auth.token.max.allowed.lifetime"
  );

  private static final Map<String, AuditCategory> COMPONENT_QUALIFIERS = Map.of(
          "VW", AuditCategory.PORTFOLIO,
          "APP", AuditCategory.APPLICATION,
          "TRK", AuditCategory.PROJECT);

  private final AuditDao dao;

  private final ThreadLocalUserSession userSession;

//  public DbAuditPersister(AuditDao dao, ThreadLocalUserSession paramThreadLocalUserSession) {
//    this.dao = dao;
//    this.userSession = paramThreadLocalUserSession;
//  }
//
//  public DbAuditPersister(AuditDao dao) {
//    this.dao = dao;
//    this.userSession = null;
//  }

  public NoOpAuditPersister(AuditDao dao, ThreadLocalUserSession userSession) {
    this.dao = dao;
    this.userSession = userSession;
  }

  @Override
  public boolean isTrackedProperty(String paramString) {
    return TRACKED_PROPERTIES.contains(paramString);
  }

  @Override
  public void addUserGroup(DbSession dbSession, String organizationUuid, UserGroupNewValue newValue) {
    // no op
  }

  @Override
  public void updateUserGroup(DbSession dbSession, String organizationUuid, UserGroupNewValue newValue) {
    // no op
  }

  @Override
  public void deleteUserGroup(DbSession dbSession, String organizationUuid, UserGroupNewValue newValue) {
    persist(dbSession, organizationUuid, AuditCategory.USER_GROUP, AuditOperation.DELETE, newValue);
    // no op
  }

  @Override
  public void addUser(DbSession dbSession, UserNewValue newValue) {
    // no op
  }

  @Override
  public void updateUser(DbSession dbSession, UserNewValue newValue) {
    // no op
  }

  @Override
  public void updateUserPassword(DbSession dbSession, SecretNewValue newValue) {
    // no op
  }

  @Override
  public void updateWebhookSecret(DbSession dbSession, SecretNewValue newValue) {
    // no op
  }

  @Override
  public void updateDevOpsPlatformSecret(DbSession dbSession, SecretNewValue newValue) {
    // no op
  }

  @Override
  public void deactivateUser(DbSession dbSession, UserNewValue newValue) {
    // no op
  }

  @Override
  public void addUserToGroup(DbSession dbSession, UserGroupNewValue newValue) {
    // no op
  }

  @Override
  public void deleteUserFromGroup(DbSession dbSession, UserGroupNewValue newValue) {
    // no op
  }

  @Override
  public void addProperty(DbSession dbSession, PropertyNewValue newValue, boolean isUserProperty) {
    // no op
  }

  @Override
  public void updateProperty(DbSession dbSession, PropertyNewValue newValue, boolean isUserProperty) {
    // no op
  }

  @Override
  public void deleteProperty(DbSession dbSession, PropertyNewValue newValue, boolean isUserProperty) {
    // no op
  }

  @Override
  public void addUserToken(DbSession dbSession, UserTokenNewValue newValue) {
    // no op
  }

  @Override
  public void addProjectBadgeToken(DbSession dbSession, ProjectBadgeTokenNewValue newValue) {
    // no op
  }

  @Override
  public void updateProjectBadgeToken(DbSession session, ProjectBadgeTokenNewValue projectBadgeTokenNewValue) {
    // no op
  }

  @Override
  public void updateUserToken(DbSession dbSession, UserTokenNewValue newValue) {
    // no op
  }

  @Override
  public void deleteUserToken(DbSession dbSession, UserTokenNewValue newValue) {
    // no op
  }

  @Override
  public void addGroupPermission(DbSession dbSession, GroupPermissionNewValue newValue) {
    // no op
  }

  @Override
  public void deleteGroupPermission(DbSession dbSession, GroupPermissionNewValue newValue) {
    // no op
  }

  @Override
  public void addUserPermission(DbSession dbSession, UserPermissionNewValue newValue) {
    // no op
  }

  @Override
  public void deleteUserPermission(DbSession dbSession, UserPermissionNewValue newValue) {
    // no op
  }

  @Override
  public void addPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void updatePermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void deletePermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void addUserToPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void deleteUserFromPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void addGroupToPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void deleteGroupFromPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void addQualityGateEditor(DbSession dbSession, AbstractEditorNewValue newValue) {
    // no op
  }

  @Override
  public void deleteQualityGateEditor(DbSession dbSession, AbstractEditorNewValue newValue) {
    // no op
  }

  @Override
  public void addQualityProfileEditor(DbSession dbSession, AbstractEditorNewValue newValue) {
    // no op
  }

  @Override
  public void deleteQualityProfileEditor(DbSession dbSession, AbstractEditorNewValue newValue) {
    // no op
  }

  @Override
  public void addCharacteristicToPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void updateCharacteristicInPermissionTemplate(DbSession dbSession, PermissionTemplateNewValue newValue) {
    // no op
  }

  @Override
  public void addPlugin(DbSession dbSession, PluginNewValue newValue) {
    // no op
  }

  @Override
  public void updatePlugin(DbSession dbSession, PluginNewValue newValue) {
    // no op
  }

  @Override
  public void generateSecretKey(DbSession dbSession) {
    // no op
  }

  @Override
  public void setLicense(DbSession dbSession, boolean isSet, LicenseNewValue newValue) {
    // no op
  }

  @Override
  public void addWebhook(DbSession dbSession, WebhookNewValue newValue) {
    // no op
  }

  @Override
  public void updateWebhook(DbSession dbSession, WebhookNewValue newValue) {
    // no op
  }

  @Override
  public void deleteWebhook(DbSession dbSession, WebhookNewValue newValue) {
    // no op
  }

  @Override
  public void addDevOpsPlatformSetting(DbSession dbSession, DevOpsPlatformSettingNewValue newValue) {
    // no op
  }

  @Override
  public void updateDevOpsPlatformSetting(DbSession dbSession, DevOpsPlatformSettingNewValue newValue) {
    // no op
  }

  @Override
  public void deleteDevOpsPlatformSetting(DbSession dbSession, DevOpsPlatformSettingNewValue newValue) {
    // no op
  }

  @Override
  public void addPersonalAccessToken(DbSession dbSession, PersonalAccessTokenNewValue newValue) {
    // no op
  }

  @Override
  public void updatePersonalAccessToken(DbSession dbSession, PersonalAccessTokenNewValue newValue) {
    // no op
  }

  @Override
  public void deletePersonalAccessToken(DbSession dbSession, PersonalAccessTokenNewValue newValue) {
    // no op
  }

//  @Override
//  public boolean isTrackedProperty(String propertyKey) {
//    return false;
//  }

  @Override
  public void addComponent(DbSession dbSession, ComponentNewValue newValue) {
    // no op
  }

  @Override
  public void deleteComponent(DbSession dbSession, ComponentNewValue newValue) {
    // no op
  }

  @Override
  public void updateComponent(DbSession dbSession, ComponentNewValue newValue) {
    // no op
  }

  @Override
  public void updateComponentVisibility(DbSession session, ComponentNewValue componentNewValue) {
    // no op
  }

  @Override
  public void componentKeyUpdate(DbSession session, ComponentKeyNewValue componentKeyNewValue, String qualifier) {
    // no op
  }

  @Override
  public void componentKeyBranchUpdate(DbSession session, ComponentKeyNewValue componentKeyNewValue, String qualifier) {
    // no op
  }

  private void persist(DbSession dbSession, String organizationUuid, @Nullable String component, AuditOperation operation,
          NewValue paramNewValue) {
    getAuditCategory(component).ifPresent(category -> persist(dbSession, organizationUuid, category, operation, paramNewValue));
  }

  private void persist(DbSession dbSession, String organizationUuid, AuditCategory category, AuditOperation operation,
          @Nullable NewValue paramNewValue) {
    dao.insert(dbSession, buildAuditDto(organizationUuid, category, operation, paramNewValue));
  }

  private static Optional<AuditCategory> getAuditCategory(@Nullable String paramString) {
    return Optional.ofNullable(paramString).map(COMPONENT_QUALIFIERS::get);
  }

  private AuditDto buildAuditDto(String organizationUuid, AuditCategory category, AuditOperation operation, @Nullable NewValue newValue) {
    AuditDto auditDto = new AuditDto();
    auditDto.setOrganizationUuid(organizationUuid != null ? organizationUuid : "");
    if (userSession == null) {
      withSystemUser(auditDto);
    } else if (userSession.hasSession()) {
      withUserSession(auditDto);
    } else {
      withoutUserSession(newValue, auditDto);
    }
    auditDto.setCategory(category.name());
    auditDto.setOperation(operation.name());
    auditDto.setNewValue((newValue == null) ? "{}" : newValue.toString());
    return auditDto;
  }

  private void withUserSession(AuditDto paramAuditDto) {
    if (userSession.isLoggedIn()) {
      withRegularUser(paramAuditDto);
    } else if (userSession.isSystemAdministrator()) {
      withSystemUser(paramAuditDto);
    } else {
      withUnauthenticatedUser(paramAuditDto);
    }
  }

  private static void withoutUserSession(@CheckForNull NewValue paramNewValue, AuditDto paramAuditDto) {
    if (isNewValueAllowedWithoutUserSession(paramNewValue)) {
      withUserExctractedFromNewValue(paramAuditDto, paramNewValue);
    } else {
      withUnauthenticatedUser(paramAuditDto);
    }
  }

  private static boolean isNewValueAllowedWithoutUserSession(@CheckForNull NewValue newValue) {
    if (newValue instanceof UserNewValue) {
      return true;
    }

    if (newValue instanceof UserGroupNewValue userGroupNewValue) {
      return userGroupNewValue.getUserUuid() != null && userGroupNewValue.getUserLogin() != null;
    }

    return false;
  }

  private static void withUserExctractedFromNewValue(AuditDto paramAuditDto, @CheckForNull NewValue paramNewValue) {
    if (paramNewValue instanceof UserNewValue userValue) {
      withUserValue(paramAuditDto, userValue);
    } else if (paramNewValue instanceof UserGroupNewValue userGroupValue) {
      withUserGroupValue(paramAuditDto, userGroupValue);
    }
  }

  private static void withUserValue(AuditDto paramAuditDto, UserNewValue userValue) {
    paramAuditDto.setUserLogin(userValue.getUserLogin());
    paramAuditDto.setUserUuid(userValue.getUserUuid());
    paramAuditDto.setUserTriggered(true);
  }

  private static void withUserGroupValue(AuditDto auditDto, UserGroupNewValue userGroupValue) {
    auditDto.setUserLogin(userGroupValue.getUserLogin());
    auditDto.setUserUuid(userGroupValue.getUserUuid());
    auditDto.setUserTriggered(true);
  }

  private void withRegularUser(AuditDto auditDto) {
    auditDto.setUserLogin(this.userSession.getLogin());
    auditDto.setUserUuid(this.userSession.getUuid());
    auditDto.setUserTriggered(true);
  }

  private static void withSystemUser(AuditDto auditDto) {
    auditDto.setUserLogin("System");
    auditDto.setUserUuid("-");
    auditDto.setUserTriggered(false);
  }

  private static void withUnauthenticatedUser(AuditDto auditDto) {
    auditDto.setUserLogin("Unauthenticated user");
    auditDto.setUserUuid("-");
    auditDto.setUserTriggered(false);
  }
}
