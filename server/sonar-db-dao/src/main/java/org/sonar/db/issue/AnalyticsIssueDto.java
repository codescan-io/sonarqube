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

/** An issue as the analytics reports list it: what it is, where it is and where it stands. */
public class AnalyticsIssueDto {
  // set by reflection by MyBatis
  private String issueKey;
  private String ruleUuid;
  private Integer issueType;
  private String severity;
  private String status;
  private String resolution;
  private String ruleRepository;
  private String ruleKey;
  private String ruleName;
  private String projectKey;
  private String projectName;
  private String filePath;
  private Integer line;
  private Long creationDate;
  private Long closeDate;
  private Long resolutionExpiresAt;

  public String getIssueKey() {
    return issueKey;
  }

  public String getRuleUuid() {
    return ruleUuid;
  }

  /** The {@code RuleType} database constant: 1 code smell, 2 bug, 3 vulnerability, 4 security hotspot. */
  @CheckForNull
  public Integer getIssueType() {
    return issueType;
  }

  @CheckForNull
  public String getSeverity() {
    return severity;
  }

  @CheckForNull
  public String getStatus() {
    return status;
  }

  @CheckForNull
  public String getResolution() {
    return resolution;
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
  public Integer getLine() {
    return line;
  }

  @CheckForNull
  public Long getCreationDate() {
    return creationDate;
  }

  @CheckForNull
  public Long getCloseDate() {
    return closeDate;
  }

  /** When the exception of an accepted or false-positive issue expires and the issue reopens. */
  @CheckForNull
  public Long getResolutionExpiresAt() {
    return resolutionExpiresAt;
  }
}
