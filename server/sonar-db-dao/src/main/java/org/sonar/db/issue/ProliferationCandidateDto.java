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

/**
 * CodeScan: what matching a proliferated marking needs of a candidate issue - its key, the branch it is on (its
 * project_uuid) and the line and message the signature compares. The full issue is loaded only for the matches.
 */
public class ProliferationCandidateDto {
  private String issueKey;
  private String branchUuid;
  private Integer line;
  private String message;

  public String getIssueKey() {
    return issueKey;
  }

  public ProliferationCandidateDto setIssueKey(String issueKey) {
    this.issueKey = issueKey;
    return this;
  }

  public String getBranchUuid() {
    return branchUuid;
  }

  public ProliferationCandidateDto setBranchUuid(String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  @CheckForNull
  public Integer getLine() {
    return line;
  }

  public ProliferationCandidateDto setLine(@Nullable Integer line) {
    this.line = line;
    return this;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  public ProliferationCandidateDto setMessage(@Nullable String message) {
    this.message = message;
    return this;
  }
}
