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
 * What recording a retained issue needs to know about its branch, read in one query: the live branch, whether its
 * organization retains issues, and the 'cs_retain_details' row if one was already written.
 */
public class RetainBranchContextDto {

  private String branchUuid;
  private String projectUuid;
  private String branchKey;
  private BranchType branchType;
  private String targetBranchKey;
  private boolean retainEnabled;
  private String retainedBranchUuid;
  private String retainedBranchKey;
  private String retainedTargetBranchKey;

  public String getBranchUuid() {
    return branchUuid;
  }

  public RetainBranchContextDto setBranchUuid(String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  public String getProjectUuid() {
    return projectUuid;
  }

  public RetainBranchContextDto setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  public String getBranchKey() {
    return branchKey;
  }

  public RetainBranchContextDto setBranchKey(String branchKey) {
    this.branchKey = branchKey;
    return this;
  }

  public BranchType getBranchType() {
    return branchType;
  }

  public RetainBranchContextDto setBranchType(BranchType branchType) {
    this.branchType = branchType;
    return this;
  }

  @CheckForNull
  public String getTargetBranchKey() {
    return targetBranchKey;
  }

  public RetainBranchContextDto setTargetBranchKey(@Nullable String targetBranchKey) {
    this.targetBranchKey = targetBranchKey;
    return this;
  }

  /** The organization's "Retain Fixed Issues" toggle. */
  public boolean isRetainEnabled() {
    return retainEnabled;
  }

  public RetainBranchContextDto setRetainEnabled(boolean retainEnabled) {
    this.retainEnabled = retainEnabled;
    return this;
  }

  /** Null until the first issue of the branch is retained. */
  @CheckForNull
  public String getRetainedBranchUuid() {
    return retainedBranchUuid;
  }

  public RetainBranchContextDto setRetainedBranchUuid(@Nullable String retainedBranchUuid) {
    this.retainedBranchUuid = retainedBranchUuid;
    return this;
  }

  @CheckForNull
  public String getRetainedBranchKey() {
    return retainedBranchKey;
  }

  public RetainBranchContextDto setRetainedBranchKey(@Nullable String retainedBranchKey) {
    this.retainedBranchKey = retainedBranchKey;
    return this;
  }

  @CheckForNull
  public String getRetainedTargetBranchKey() {
    return retainedTargetBranchKey;
  }

  public RetainBranchContextDto setRetainedTargetBranchKey(@Nullable String retainedTargetBranchKey) {
    this.retainedTargetBranchKey = retainedTargetBranchKey;
    return this;
  }

  RetainDetailsDto toRetainDetails() {
    return new RetainDetailsDto()
      .setBranchUuid(branchUuid)
      .setProjectUuid(projectUuid)
      .setBranchKey(branchKey)
      .setBranchType(branchType)
      .setTargetBranchKey(targetBranchKey);
  }
}
