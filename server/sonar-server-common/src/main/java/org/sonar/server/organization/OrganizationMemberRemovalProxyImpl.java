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
package org.sonar.server.organization;

import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.server.organization.OrganizationMemberRemovalExtension.Organization;
import org.sonar.server.organization.OrganizationMemberRemovalExtension.User;
import org.springframework.beans.factory.annotation.Autowired;

public class OrganizationMemberRemovalProxyImpl implements OrganizationMemberRemovalProxy {

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationMemberRemovalProxyImpl.class);

  @Nullable
  private final OrganizationMemberRemovalExtension organizationMemberRemovalExtension;

  @Autowired(required = false)
  public OrganizationMemberRemovalProxyImpl(List<OrganizationMemberRemovalExtension> extensions) {
    this.organizationMemberRemovalExtension = extensions.isEmpty() ? null : extensions.get(0);
    LOG.info("OrganizationMemberRemovalProxyImpl initialized with {} extension(s)", extensions.size());
  }

  @Override
  public void onRemoveMember(Organization organization, User user) {
    if (organizationMemberRemovalExtension == null) {
      return;
    }
    organizationMemberRemovalExtension.onRemoveMember(organization, user);
  }

  @Override
  public void onRemoveUser(User user) {
    if (organizationMemberRemovalExtension == null) {
      return;
    }
    organizationMemberRemovalExtension.onRemoveUser(user);
  }
}
