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

/** An accepted or false-positive issue whose exception expires at a known date. */
public class ExpiringExceptionDto {
  // set by reflection by MyBatis
  private String issueKey;
  private String resolution;
  private long expiresAt;
  private String ruleRepository;
  private String ruleKey;
  private String ruleName;
  private String projectKey;
  private String projectName;
  private String filePath;
  private String assigneeUuid;

  public String getIssueKey() {
    return issueKey;
  }

  /** {@code WONTFIX} (accepted) or {@code FALSE-POSITIVE} */
  public String getResolution() {
    return resolution;
  }

  public long getExpiresAt() {
    return expiresAt;
  }

  public String getRuleRepository() {
    return ruleRepository;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  @CheckForNull
  public String getRuleName() {
    return ruleName;
  }

  public String getProjectKey() {
    return projectKey;
  }

  @CheckForNull
  public String getProjectName() {
    return projectName;
  }

  @CheckForNull
  public String getFilePath() {
    return filePath;
  }

  @CheckForNull
  public String getAssigneeUuid() {
    return assigneeUuid;
  }
}
