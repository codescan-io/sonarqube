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
package org.sonar.db.ce;

/** The status of one task of a project, with what is needed to tell which of its tasks is the latest. */
public class AnalyticsProjectTaskStatusDto {
  // set by reflection by MyBatis
  private String projectKey;
  private String status;
  private long createdAt;
  private String uuid;

  public String getProjectKey() {
    return projectKey;
  }

  public String getStatus() {
    return status;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public String getUuid() {
    return uuid;
  }
}
