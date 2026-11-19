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

/** Number of issues of one type, severity and resolution, optionally within a group such as a rule. */
public class IssueGroupCountDto {
  // set by reflection by MyBatis
  private String groupKey;
  private Integer issueType;
  private String severity;
  private String resolution;
  private long count;

  @CheckForNull
  public String getGroupKey() {
    return groupKey;
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

  /** Null for unresolved issues. */
  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public long getCount() {
    return count;
  }
}
