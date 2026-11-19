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
 * CodeScan: an issue key paired with the status it is moving away from and the component it sits on. A bulk
 * transition needs all three — the key to write the changelog against, the previous status to record in it, and
 * the component so the caller can recompute what the change affects.
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

  public IssueKeyStatusDto setStatus(String status) {
    this.status = status;
    return this;
  }

  public String getComponentUuid() {
    return componentUuid;
  }

  public IssueKeyStatusDto setComponentUuid(String componentUuid) {
    this.componentUuid = componentUuid;
    return this;
  }
}
