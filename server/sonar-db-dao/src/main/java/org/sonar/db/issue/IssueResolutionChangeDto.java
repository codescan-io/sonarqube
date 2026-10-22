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
import javax.annotation.Nullable;

/** A changelog entry that touched an issue's resolution. */
public class IssueResolutionChangeDto {
  // set by reflection by MyBatis
  private String issueKey;
  private String changeData;
  private long changedAt;
  private String userLogin;

  public String getIssueKey() {
    return issueKey;
  }

  public IssueResolutionChangeDto setIssueKey(String issueKey) {
    this.issueKey = issueKey;
    return this;
  }

  /** Encoded {@code FieldDiffs} */
  public String getChangeData() {
    return changeData;
  }

  public long getChangedAt() {
    return changedAt;
  }

  /** Null when the analysis made the change, for example closing an issue that is gone from the code. */
  @CheckForNull
  public String getUserLogin() {
    return userLogin;
  }

  public IssueResolutionChangeDto setChangeData(String changeData) {
    this.changeData = changeData;
    return this;
  }

  public IssueResolutionChangeDto setChangedAt(long changedAt) {
    this.changedAt = changedAt;
    return this;
  }

  public IssueResolutionChangeDto setUserLogin(@Nullable String userLogin) {
    this.userLogin = userLogin;
    return this;
  }
}
