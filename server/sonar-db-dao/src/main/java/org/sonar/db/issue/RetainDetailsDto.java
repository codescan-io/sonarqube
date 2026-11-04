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
import org.sonar.db.component.BranchType;

/**
 * A row of 'cs_retain_details': the branch or pull request a retained issue was on, kept by value because the
 * 'project_branches' row is deleted with the branch.
 */
public class RetainDetailsDto {

  private String branchUuid;
  private String projectUuid;
  private String branchKey;
  private BranchType branchType;
  private String targetBranchKey;

  public String getBranchUuid() {
    return branchUuid;
  }

  public RetainDetailsDto setBranchUuid(String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  public String getProjectUuid() {
    return projectUuid;
  }

  public RetainDetailsDto setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  /** The branch name, or the pull request key. */
  public String getBranchKey() {
    return branchKey;
  }

  public RetainDetailsDto setBranchKey(String branchKey) {
    this.branchKey = branchKey;
    return this;
  }

  public BranchType getBranchType() {
    return branchType;
  }

  public RetainDetailsDto setBranchType(BranchType branchType) {
    this.branchType = branchType;
    return this;
  }

  /** The target branch of a pull request; null for a branch. */
  @CheckForNull
  public String getTargetBranchKey() {
    return targetBranchKey;
  }

  public RetainDetailsDto setTargetBranchKey(@Nullable String targetBranchKey) {
    this.targetBranchKey = targetBranchKey;
    return this;
  }
}
