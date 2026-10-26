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

/**
 * CodeScan: an issue key paired with the status it is moving away from. A bulk transition needs both — the key to
 * write the changelog against, and the previous status to record in it.
 */
public class IssueKeyStatusDto {

  private String kee;
  private String status;
  private String componentUuid;

  public String getKee() {
    return kee;
  }

  public IssueKeyStatusDto setKee(String kee) {
    this.kee = kee;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public String getComponentUuid() {
    return componentUuid;
  }

  public IssueKeyStatusDto setComponentUuid(String componentUuid) {
    this.componentUuid = componentUuid;
    return this;
  }

  public IssueKeyStatusDto setStatus(String status) {
    this.status = status;
    return this;
  }
}
