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

import static java.util.Objects.requireNonNull;

import org.sonar.api.ExtensionPoint;
import org.sonar.api.server.ServerSide;

/**
 * The billing plugin must implement this interface to react when a member is removed from an organization.
 */
@ServerSide
@ExtensionPoint
public interface OrganizationMemberRemovalExtension {

  void onRemoveMember(Organization organization, User user);

  void onRemoveUser(User user);

  class Organization {
    private final String key;
    private final String uuid;
    private final String name;

    public Organization(String key, String uuid, String name) {
      this.key = requireNonNull(key, "Organization key cannot be null");
      this.uuid = requireNonNull(uuid, "Organization uuid cannot be null");
      this.name = requireNonNull(name, "Organization name cannot be null");
    }

    public String getKey() {
      return key;
    }

    public String getUuid() {
      return uuid;
    }

    public String getName() {
      return name;
    }
  }

  class User {
    private final String uuid;
    private final String login;
    private final String email;

    public User(String uuid, String login, String email) {
      this.uuid = requireNonNull(uuid, "User uuid cannot be null");
      this.login = requireNonNull(login, "User login cannot be null");
      this.email = email;
    }

    public String getUuid() {
      return uuid;
    }

    public String getLogin() {
      return login;
    }

    public String getEmail() {
      return email;
    }
  }
}
