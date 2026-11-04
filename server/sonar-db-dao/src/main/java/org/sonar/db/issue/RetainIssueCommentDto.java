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

/**
 * A row of 'cs_retain_issue_comments': a comment of a retained issue, copied from 'issue_changes' before the issue
 * was deleted.
 */
public class RetainIssueCommentDto {

  private String uuid;
  private String issueKey;
  private String changeData;
  private String userUuid;
  private Long issueChangeCreationDate;

  /** The uuid of the 'issue_changes' row it was copied from. */
  public String getUuid() {
    return uuid;
  }

  public String getIssueKey() {
    return issueKey;
  }

  /** The comment text, in markdown. */
  @CheckForNull
  public String getChangeData() {
    return changeData;
  }

  @CheckForNull
  public String getUserUuid() {
    return userUuid;
  }

  @CheckForNull
  public Long getIssueChangeCreationDate() {
    return issueChangeCreationDate;
  }
}
