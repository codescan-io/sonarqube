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
package org.sonar.server.platform.web;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.organization.IpAllowlistBlockedException;
import org.sonar.server.organization.OrganizationIpAccess;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.UserSession;

/**
 * Gives a blocked member {@link IpAllowlistBlockedException} instead of a generic "Insufficient privileges". The user
 * session decides who is blocked; root, exempt administrators and the analysis job's own login never are.
 * <p>
 * Both checks throw {@link org.sonar.server.organization.IpAllowlistUnavailableException} (HTTP 503), passed on from
 * the user session, when the allowlist cannot be read.
 */
@ServerSide
public class IpAllowlistRequestCheck {

  private final DbClient dbClient;
  private final UserSession userSession;
  private final OrganizationIpAccess ipAccess;

  public IpAllowlistRequestCheck(DbClient dbClient, UserSession userSession, OrganizationIpAccess ipAccess) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.ipAccess = ipAccess;
  }

  /**
   * False on the URLs the session filter skips, such as project badges. Asking the session there would answer 401.
   */
  public boolean hasUserSession() {
    return !(userSession instanceof ThreadLocalUserSession threadLocalSession) || threadLocalSession.hasSession();
  }

  /**
   * Organizations whose allowlist blocks this request. Read once per request by the user session, so asking first
   * costs nothing and lets a caller that no organization blocks skip every lookup.
   */
  public Set<String> blockedOrganizationUuids() {
    return userSession.getIpBlockedOrganizationUuids();
  }

  /**
   * @throws IpAllowlistBlockedException when the organization's allowlist blocks the request and the caller is a member
   */
  public void checkOrganizationKey(String organizationKey) {
    if (userSession.isBlockedByIpAllowlistForKey(organizationKey)) {
      refuseMember(dbSession -> dbClient.organizationDao().selectByKey(dbSession, organizationKey));
    }
  }

  /**
   * @throws IpAllowlistBlockedException when the organization's allowlist blocks the request and the caller is a member
   */
  public void checkOrganizationUuid(String organizationUuid) {
    if (userSession.isBlockedByIpAllowlist(organizationUuid)) {
      refuseMember(dbSession -> dbClient.organizationDao().selectByUuid(dbSession, organizationUuid));
    }
  }

  private void refuseMember(Function<DbSession, Optional<OrganizationDto>> blockedOrganization) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      blockedOrganization.apply(dbSession)
        // nobody may use an archived organization, so the allowlist is not the reason for the refusal
        .filter(organization -> !organization.isArchived())
        .filter(organization -> isListedMember(dbSession, organization))
        .ifPresent(this::refuse);
    }
  }

  /**
   * Only a member is told. Anyone else gets the answer they would get from any address, so the reply does not reveal
   * whether the organization has an allowlist; the user session withholds the organization's data from them anyway.
   * Reads the members table directly because the session's membership already counts a blocked member as a non-member.
   */
  private boolean isListedMember(DbSession dbSession, OrganizationDto organization) {
    if (!userSession.isLoggedIn()) {
      return false;
    }
    String userUuid = userSession.getUuid();
    return userUuid != null && dbClient.organizationMemberDao().select(dbSession, organization.getUuid(), userUuid).isPresent();
  }

  private void refuse(OrganizationDto organization) {
    String clientIp = userSession.getClientIp();
    ipAccess.logBlocked(organization.getKey(), clientIp, userSession.getLogin());
    throw new IpAllowlistBlockedException(organization.getKey(), clientIp);
  }
}
