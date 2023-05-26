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
package org.sonar.db.entity;

import org.sonar.db.project.ProjectDto;

import java.util.Objects;

/**
 * Represents a project, an application, a portfolio or a sub-portfolio.
 * Entities are stored either in the projects or portfolios tables.
 */
public class EntityDto {
  protected String kee;
  protected String uuid;
  protected String name;
  protected String qualifier;
  protected boolean isPrivate;
  protected String organizationUuid;

  // This field should be null for anything that is not subportfolio
  protected String authUuid;

  public String getAuthUuid() {
    if ("SVW".equals(qualifier)) {
      return authUuid;
    }
    return uuid;
  }

  public String getKey() {
    return kee;
  }

  public String getKee() {
    return kee;
  }

  public String getUuid() {
    return uuid;
  }

  /**
   * Can be TRK, APP, VW or SVW
   */
  public String getQualifier() {
    return qualifier;
  }

  public String getName() {
    return name;
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EntityDto entityDto)) {
      return false;
    }
    return Objects.equals(uuid, entityDto.uuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid);
  }
}
