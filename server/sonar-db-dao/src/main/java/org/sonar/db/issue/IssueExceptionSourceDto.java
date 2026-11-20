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

/** CodeScan: the source a transfer paired an issue with, and the resolution and expiry that source carries now. */
public class IssueExceptionSourceDto {

  private String issueKey;
  private String sourceKey;
  private String resolution;
  private Long expiresAt;

  public String getIssueKey() {
    return issueKey;
  }

  public IssueExceptionSourceDto setIssueKey(String issueKey) {
    this.issueKey = issueKey;
    return this;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public IssueExceptionSourceDto setSourceKey(String sourceKey) {
    this.sourceKey = sourceKey;
    return this;
  }

  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public IssueExceptionSourceDto setResolution(@Nullable String resolution) {
    this.resolution = resolution;
    return this;
  }

  @CheckForNull
  public Long getExpiresAt() {
    return expiresAt;
  }

  public IssueExceptionSourceDto setExpiresAt(@Nullable Long expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }
}
