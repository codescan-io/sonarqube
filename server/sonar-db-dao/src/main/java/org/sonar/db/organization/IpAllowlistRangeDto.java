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
package org.sonar.db.organization;

import javax.annotation.CheckForNull;

public class IpAllowlistRangeDto {
  private String organizationUuid;
  private boolean appliesToAdmins;
  private String cidr;

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  public IpAllowlistRangeDto setOrganizationUuid(String organizationUuid) {
    this.organizationUuid = organizationUuid;
    return this;
  }

  public boolean isAppliesToAdmins() {
    return appliesToAdmins;
  }

  public IpAllowlistRangeDto setAppliesToAdmins(boolean appliesToAdmins) {
    this.appliesToAdmins = appliesToAdmins;
    return this;
  }

  /** Null when the allowlist is on but the organization has no range. */
  @CheckForNull
  public String getCidr() {
    return cidr;
  }

  public IpAllowlistRangeDto setCidr(String cidr) {
    this.cidr = cidr;
    return this;
  }
}
