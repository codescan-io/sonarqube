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
package org.sonar.db.issue;

import javax.annotation.CheckForNull;

/** When an issue with a security impact was created and, once fixed, closed, with the severity of that impact. */
public class SecurityIssueDatesDto {
  // set by reflection by MyBatis
  private String severity;
  private long createdAt;
  private Long closedAt;

  public String getSeverity() {
    return severity;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  @CheckForNull
  public Long getClosedAt() {
    return closedAt;
  }

  public SecurityIssueDatesDto setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public SecurityIssueDatesDto setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public SecurityIssueDatesDto setClosedAt(Long closedAt) {
    this.closedAt = closedAt;
    return this;
  }
}
