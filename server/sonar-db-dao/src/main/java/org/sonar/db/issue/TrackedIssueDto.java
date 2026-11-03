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
import org.sonar.api.issue.IssueStatus;
import org.sonar.db.component.BranchType;

/**
 * A row of 'cs_tracked_issue': one issue, as it looked when it reached a terminal resolution.
 *
 * <p>Every branch, component and rule reference is a value rather than a foreign key, because the row has to stay
 * readable after the branch, the component and the 'issues' row are gone.
 */
public class TrackedIssueDto {

  /** Which of the two paths captured the row. */
  public enum CaptureSource {
    /** A user transition through the web API, including a bulk change. */
    USER,
    /** The compute engine closing the issue during an analysis. */
    ANALYSIS
  }

  private String uuid;
  private String organizationUuid;
  private String projectUuid;
  private String branchUuid;
  private String branchKey;
  private BranchType branchType;
  private String baseBranchKey;
  private String issueKey;
  private String ruleUuid;
  private String ruleKey;
  private String componentKey;
  private Integer line;
  private String message;
  private String severity;
  private String issueType;
  private String cleanCodeAttribute;
  private String impacts;
  private String tags;
  private String authorLogin;
  private String assigneeUuid;
  private Integer effort;
  private IssueStatus issueStatus;
  private String status;
  private String resolution;
  private Long issueCreationDate;
  private Long issueCloseDate;
  private long capturedAt;
  private CaptureSource capturedBy;
  private String capturedByUuid;

  public String getUuid() {
    return uuid;
  }

  public TrackedIssueDto setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  public TrackedIssueDto setOrganizationUuid(String organizationUuid) {
    this.organizationUuid = organizationUuid;
    return this;
  }

  public String getProjectUuid() {
    return projectUuid;
  }

  public TrackedIssueDto setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  /** Null once the branch or pull request has been deleted. {@link #getBranchKey()} stays populated. */
  @CheckForNull
  public String getBranchUuid() {
    return branchUuid;
  }

  public TrackedIssueDto setBranchUuid(@Nullable String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  public String getBranchKey() {
    return branchKey;
  }

  public TrackedIssueDto setBranchKey(String branchKey) {
    this.branchKey = branchKey;
    return this;
  }

  public BranchType getBranchType() {
    return branchType;
  }

  public TrackedIssueDto setBranchType(BranchType branchType) {
    this.branchType = branchType;
    return this;
  }

  /** The target branch of a pull request, so an export can resolve a deleted pull request to the branch it targeted. */
  @CheckForNull
  public String getBaseBranchKey() {
    return baseBranchKey;
  }

  public TrackedIssueDto setBaseBranchKey(@Nullable String baseBranchKey) {
    this.baseBranchKey = baseBranchKey;
    return this;
  }

  public String getIssueKey() {
    return issueKey;
  }

  public TrackedIssueDto setIssueKey(String issueKey) {
    this.issueKey = issueKey;
    return this;
  }

  @CheckForNull
  public String getRuleUuid() {
    return ruleUuid;
  }

  public TrackedIssueDto setRuleUuid(@Nullable String ruleUuid) {
    this.ruleUuid = ruleUuid;
    return this;
  }

  @CheckForNull
  public String getRuleKey() {
    return ruleKey;
  }

  public TrackedIssueDto setRuleKey(@Nullable String ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  @CheckForNull
  public String getComponentKey() {
    return componentKey;
  }

  public TrackedIssueDto setComponentKey(@Nullable String componentKey) {
    this.componentKey = componentKey;
    return this;
  }

  @CheckForNull
  public Integer getLine() {
    return line;
  }

  public TrackedIssueDto setLine(@Nullable Integer line) {
    this.line = line;
    return this;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  public TrackedIssueDto setMessage(@Nullable String message) {
    this.message = message;
    return this;
  }

  @CheckForNull
  public String getSeverity() {
    return severity;
  }

  public TrackedIssueDto setSeverity(@Nullable String severity) {
    this.severity = severity;
    return this;
  }

  @CheckForNull
  public String getIssueType() {
    return issueType;
  }

  public TrackedIssueDto setIssueType(@Nullable String issueType) {
    this.issueType = issueType;
    return this;
  }

  @CheckForNull
  public String getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public TrackedIssueDto setCleanCodeAttribute(@Nullable String cleanCodeAttribute) {
    this.cleanCodeAttribute = cleanCodeAttribute;
    return this;
  }

  /** Software quality impacts, serialized the same way the issue search exposes them. */
  @CheckForNull
  public String getImpacts() {
    return impacts;
  }

  public TrackedIssueDto setImpacts(@Nullable String impacts) {
    this.impacts = impacts;
    return this;
  }

  @CheckForNull
  public String getTags() {
    return tags;
  }

  public TrackedIssueDto setTags(@Nullable String tags) {
    this.tags = tags;
    return this;
  }

  @CheckForNull
  public String getAuthorLogin() {
    return authorLogin;
  }

  public TrackedIssueDto setAuthorLogin(@Nullable String authorLogin) {
    this.authorLogin = authorLogin;
    return this;
  }

  @CheckForNull
  public String getAssigneeUuid() {
    return assigneeUuid;
  }

  public TrackedIssueDto setAssigneeUuid(@Nullable String assigneeUuid) {
    this.assigneeUuid = assigneeUuid;
    return this;
  }

  @CheckForNull
  public Integer getEffort() {
    return effort;
  }

  public TrackedIssueDto setEffort(@Nullable Integer effort) {
    this.effort = effort;
    return this;
  }

  public IssueStatus getIssueStatus() {
    return issueStatus;
  }

  public TrackedIssueDto setIssueStatus(IssueStatus issueStatus) {
    this.issueStatus = issueStatus;
    return this;
  }

  @CheckForNull
  public String getStatus() {
    return status;
  }

  public TrackedIssueDto setStatus(@Nullable String status) {
    this.status = status;
    return this;
  }

  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public TrackedIssueDto setResolution(@Nullable String resolution) {
    this.resolution = resolution;
    return this;
  }

  @CheckForNull
  public Long getIssueCreationDate() {
    return issueCreationDate;
  }

  public TrackedIssueDto setIssueCreationDate(@Nullable Long issueCreationDate) {
    this.issueCreationDate = issueCreationDate;
    return this;
  }

  /** Null for a status a user set without closing the issue, such as a resolution on a still-open issue. */
  @CheckForNull
  public Long getIssueCloseDate() {
    return issueCloseDate;
  }

  public TrackedIssueDto setIssueCloseDate(@Nullable Long issueCloseDate) {
    this.issueCloseDate = issueCloseDate;
    return this;
  }

  public long getCapturedAt() {
    return capturedAt;
  }

  public TrackedIssueDto setCapturedAt(long capturedAt) {
    this.capturedAt = capturedAt;
    return this;
  }

  public CaptureSource getCapturedBy() {
    return capturedBy;
  }

  public TrackedIssueDto setCapturedBy(CaptureSource capturedBy) {
    this.capturedBy = capturedBy;
    return this;
  }

  /** The user who made the transition; null when the compute engine captured the row. */
  @CheckForNull
  public String getCapturedByUuid() {
    return capturedByUuid;
  }

  public TrackedIssueDto setCapturedByUuid(@Nullable String capturedByUuid) {
    this.capturedByUuid = capturedByUuid;
    return this;
  }
}
