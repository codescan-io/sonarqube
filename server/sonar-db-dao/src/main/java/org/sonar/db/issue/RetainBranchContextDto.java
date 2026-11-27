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

import com.google.protobuf.InvalidProtocolBufferException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.db.component.BranchType;
import org.sonar.db.protobuf.DbProjectBranches;

/**
 * What recording a retained issue needs to know about its branch, read in one query: the live branch, whether its
 * organization retains issues, and the 'cs_retain_details' row if one was already written.
 */
public class RetainBranchContextDto {

  private static final Logger LOG = LoggerFactory.getLogger(RetainBranchContextDto.class);

  private String branchUuid;
  private String projectUuid;
  private String branchKey;
  private BranchType branchType;
  private byte[] pullRequestBinary;
  private String targetBranchKey;
  private String targetBranchUuid;
  private boolean retainEnabled;
  private String retainedBranchUuid;
  private String retainedBranchKey;
  private String retainedBranchName;
  private String retainedTargetBranchKey;
  private String retainedTargetBranchUuid;

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

  /** A pull request's data, which holds its source branch; null for a branch. */
  public RetainBranchContextDto setPullRequestBinary(@Nullable byte[] pullRequestBinary) {
    this.pullRequestBinary = pullRequestBinary;
    return this;
  }

  /**
   * The branch's name: its key for a branch, its source branch for a pull request. A branch's key is its name, but a
   * pull request's key is its id, so the source branch is read from its data. Without readable data the key is used,
   * as {@code api/project_pull_requests/list} does; the name must never fail the analysis that retains the issues.
   */
  public String getBranchName() {
    if (branchType != BranchType.PULL_REQUEST || pullRequestBinary == null) {
      return branchKey;
    }
    try {
      String branch = DbProjectBranches.PullRequestData.parseFrom(pullRequestBinary).getBranch();
      return branch.isEmpty() ? branchKey : branch;
    } catch (InvalidProtocolBufferException | RuntimeException e) {
      LOG.warn("Cannot read the data of pull request {}; its key is used as its name", branchUuid, e);
      return branchKey;
    }
  }

  /**
   * The name of a pull request's target. A target deleted before it had a retained row leaves no name in the tables, so
   * the name the pull request was analysed against is read from its data; an unreadable name stays unknown.
   */
  @CheckForNull
  public String getTargetBranchKey() {
    if (targetBranchKey != null || branchType != BranchType.PULL_REQUEST || pullRequestBinary == null) {
      return targetBranchKey;
    }
    try {
      String target = DbProjectBranches.PullRequestData.parseFrom(pullRequestBinary).getTarget();
      return target.isEmpty() ? null : target;
    } catch (InvalidProtocolBufferException | RuntimeException e) {
      LOG.warn("Cannot read the target of pull request {}", branchUuid, e);
      return null;
    }
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
  public String getRetainedBranchName() {
    return retainedBranchName;
  }

  public RetainBranchContextDto setRetainedBranchName(@Nullable String retainedBranchName) {
    this.retainedBranchName = retainedBranchName;
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

  /** The uuid of a pull request's target branch, kept even when that branch is deleted; null for a branch. */
  @CheckForNull
  public String getTargetBranchUuid() {
    return targetBranchUuid;
  }

  public RetainBranchContextDto setTargetBranchUuid(@Nullable String targetBranchUuid) {
    this.targetBranchUuid = targetBranchUuid;
    return this;
  }

  @CheckForNull
  public String getRetainedTargetBranchUuid() {
    return retainedTargetBranchUuid;
  }

  public RetainBranchContextDto setRetainedTargetBranchUuid(@Nullable String retainedTargetBranchUuid) {
    this.retainedTargetBranchUuid = retainedTargetBranchUuid;
    return this;
  }
}
