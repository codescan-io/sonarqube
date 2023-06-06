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
package org.sonar.server.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.web.UserRole;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.entity.EntityDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.OrganizationPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;

import static java.lang.String.format;
import static org.sonar.api.resources.Qualifiers.APP;
import static org.sonar.server.user.UserSession.IdentityProvider.SONARQUBE;

public abstract class AbstractUserSession implements UserSession {
  private static final Set<String> PUBLIC_PERMISSIONS = Set.of(UserRole.USER, UserRole.CODEVIEWER);
  private static final String INSUFFICIENT_PRIVILEGES_MESSAGE = "Insufficient privileges";
  private static final String AUTHENTICATION_IS_REQUIRED_MESSAGE = "Authentication is required";

  protected static Identity computeIdentity(UserDto userDto) {
    IdentityProvider identityProvider = IdentityProvider.getFromKey(userDto.getExternalIdentityProvider());
    ExternalIdentity externalIdentity = identityProvider == SONARQUBE ? null : externalIdentityOf(userDto);
    return new Identity(identityProvider, externalIdentity);
  }

  private static ExternalIdentity externalIdentityOf(UserDto userDto) {
    String externalId = userDto.getExternalId();
    String externalLogin = userDto.getExternalLogin();
    return new ExternalIdentity(externalId, externalLogin);
  }

  protected static final class Identity {
    private final IdentityProvider identityProvider;
    private final ExternalIdentity externalIdentity;

    private Identity(IdentityProvider identityProvider, @Nullable ExternalIdentity externalIdentity) {
      this.identityProvider = identityProvider;
      this.externalIdentity = externalIdentity;
    }

    public IdentityProvider getIdentityProvider() {
      return identityProvider;
    }

    @CheckForNull
    public ExternalIdentity getExternalIdentity() {
      return externalIdentity;
    }
  }

  @Override
  @CheckForNull
  public Long getLastSonarlintConnectionDate() {
    return null;
  }

  @Override
  public final boolean hasPermission(OrganizationPermission permission, OrganizationDto organization) {
    return hasPermission(permission, organization.getUuid());
  }

  @Override
  public final boolean hasPermission(OrganizationPermission permission, String organizationUuid) {
    return isRoot() || hasPermissionImpl(permission, organizationUuid);
  }

  protected boolean hasPermissionImpl(OrganizationPermission permission, String organizationUuid) {
    return false;
  }

  @Override
  public boolean hasComponentPermission(String permission, ComponentDto component) {
    Optional<String> projectUuid1 = componentUuidToEntityUuid(component.uuid());

    return isRoot() || projectUuid1
      .map(projectUuid -> hasEntityUuidPermission(permission, projectUuid))
      .orElse(false);
  }

  @Override
  public final boolean hasEntityPermission(String permission, EntityDto entity) {
    return isRoot() || hasEntityUuidPermission(permission, entity.getAuthUuid());
  }

  @Override
  public final boolean hasEntityPermission(String permission, String entityUuid) {
    return isRoot() || hasEntityUuidPermission(permission, entityUuid);
  }

  @Override
  public final boolean hasChildProjectsPermission(String permission, ComponentDto component) {
    return isRoot() || componentUuidToEntityUuid(component.uuid())
      .map(applicationUuid -> hasChildProjectsPermission(permission, applicationUuid)).orElse(false);
  }

  @Override
  public final boolean hasChildProjectsPermission(String permission, EntityDto application) {
    return isRoot() || hasChildProjectsPermission(permission, application.getUuid());
  }

  @Override
  public final boolean hasPortfolioChildProjectsPermission(String permission, ComponentDto portfolio) {
    return isRoot() || hasPortfolioChildProjectsPermission(permission, portfolio.uuid());
  }

  @Override
  public boolean hasComponentUuidPermission(String permission, String componentUuid) {
    Optional<String> entityUuid = componentUuidToEntityUuid(componentUuid);
    return isRoot() || entityUuid
      .map(s -> hasEntityUuidPermission(permission, s))
      .orElse(false);
  }

  protected abstract Optional<String> componentUuidToEntityUuid(String componentUuid);

  protected abstract boolean hasEntityUuidPermission(String permission, String entityUuid);

  protected abstract boolean hasChildProjectsPermission(String permission, String applicationUuid);

  protected abstract boolean hasPortfolioChildProjectsPermission(String permission, String portfolioUuid);

  @Override
  public final List<ComponentDto> keepAuthorizedComponents(String permission, Collection<ComponentDto> components) {
    if (isRoot()) {
      return new ArrayList<>(components);
    }
    return doKeepAuthorizedComponents(permission, components);
  }

  @Override
  public  <T extends EntityDto>  List<T> keepAuthorizedEntities(String permission, Collection<T> projects) {
    if (isRoot()) {
      return new ArrayList<>(projects);
    }
    return doKeepAuthorizedEntities(permission, projects);
  }

  /**
   * Naive implementation, to be overridden if needed
   */
  protected  <T extends EntityDto> List<T> doKeepAuthorizedEntities(String permission, Collection<T> entities) {
    boolean allowPublicComponent = PUBLIC_PERMISSIONS.contains(permission);
    return entities.stream()
      .filter(c -> (allowPublicComponent && !c.isPrivate()) || hasEntityPermission(permission, c.getUuid()))
      .toList();
  }

  /**
   * Naive implementation, to be overridden if needed
   */
  protected List<ComponentDto> doKeepAuthorizedComponents(String permission, Collection<ComponentDto> components) {
    boolean allowPublicComponent = PUBLIC_PERMISSIONS.contains(permission);
    return components.stream()
      .filter(c -> (allowPublicComponent && !c.isPrivate()) || hasComponentPermission(permission, c))
      .collect(MoreCollectors.toList());
  }

  @Override
  public final UserSession checkLoggedIn() {
    if (!isLoggedIn()) {
      throw new UnauthorizedException(AUTHENTICATION_IS_REQUIRED_MESSAGE);
    }
    return this;
  }

  @Override
  public final UserSession checkPermission(OrganizationPermission permission, OrganizationDto organization) {
    return checkPermission(permission, organization.getUuid());
  }

  @Override
  public final UserSession checkPermission(OrganizationPermission permission, String organizationUuid) {
    if (!hasPermission(permission, organizationUuid)) {
      throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
    }
    return this;
  }

  @Override
  public final UserSession checkComponentPermission(String projectPermission, ComponentDto component) {
    if (!hasComponentPermission(projectPermission, component)) {
      throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
    }
    return this;
  }

  @Override
  public UserSession checkEntityPermission(String projectPermission, EntityDto entity) {
    if (hasEntityPermission(projectPermission, entity)) {
      return this;
    }

    throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
  }

  @Override
  public UserSession checkChildProjectsPermission(String projectPermission, ComponentDto component) {
    if (!APP.equals(component.qualifier()) || hasChildProjectsPermission(projectPermission, component)) {
      return this;
    }

    throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
  }

  @Override
  public UserSession checkChildProjectsPermission(String projectPermission, EntityDto application) {
    if (!APP.equals(application.getQualifier()) || hasChildProjectsPermission(projectPermission, application)) {
      return this;
    }

    throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
  }

  @Override
  public final UserSession checkComponentUuidPermission(String permission, String componentUuid) {
    if (!hasComponentUuidPermission(permission, componentUuid)) {
      throw new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
    }
    return this;
  }

  public static ForbiddenException insufficientPrivilegesException() {
    return new ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE);
  }

  @Override
  public final UserSession checkIsSystemAdministrator() {
    if (!isSystemAdministrator()) {
      throw insufficientPrivilegesException();
    }
    return this;
  }

  @Override
  public void checkMembership(OrganizationDto organization) {
    if (!hasMembership(organization)) {
      throw new ForbiddenException(format("You're not member of organization '%s'", organization.getKey()));
    }
  }

  @Override
  public final boolean hasMembership(OrganizationDto organizationDto) {
    return isRoot() || hasMembershipImpl(organizationDto);
  }

  @Override
  public boolean isRoot() {
    // Implement in subclasses if required.
    throw new RuntimeException("Not implemented");
  }

  protected boolean hasMembershipImpl(OrganizationDto organizationDto) {
    // Implement in subclasses if required.
    throw new RuntimeException("Not implemented");
  }
}
