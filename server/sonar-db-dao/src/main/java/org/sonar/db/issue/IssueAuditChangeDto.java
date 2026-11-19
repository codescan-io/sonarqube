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

/** One changelog entry, comment or exception reason of an issue, with the issue it belongs to. */
public class IssueAuditChangeDto {
  // set by reflection by MyBatis
  private String uuid;
  private String issueKey;
  private String changeType;
  private String changeData;
  private long changedAt;
  private String userUuid;
  private Integer issueType;
  private String severity;
  private String ruleRepository;
  private String ruleKey;
  private String ruleName;
  private String projectKey;
  private String projectName;
  private String filePath;

  public String getUuid() {
    return uuid;
  }

  public String getIssueKey() {
    return issueKey;
  }

  /** {@link IssueChangeDto#TYPE_FIELD_CHANGE}, {@link IssueChangeDto#TYPE_COMMENT} or {@link IssueChangeDto#TYPE_EXCEPTION_REASON} */
  public String getChangeType() {
    return changeType;
  }

  /** Encoded {@code FieldDiffs} for a field change, the markdown text otherwise. */
  @CheckForNull
  public String getChangeData() {
    return changeData;
  }

  public long getChangedAt() {
    return changedAt;
  }

  /** Null when the analysis made the change. */
  @CheckForNull
  public String getUserUuid() {
    return userUuid;
  }

  @CheckForNull
  public Integer getIssueType() {
    return issueType;
  }

  /** Current severity of the issue. */
  @CheckForNull
  public String getSeverity() {
    return severity;
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
}
