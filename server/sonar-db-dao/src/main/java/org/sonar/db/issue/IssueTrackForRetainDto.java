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

/**
 * A row of 'cs_issue_track_for_retain': a retained issue as it was when it became Fixed. The rule, the assignee and
 * the user who marked it are referenced by uuid and read from 'rules' and 'users', which outlive the issue.
 */
public class IssueTrackForRetainDto {

  private String uuid;
  private String branchUuid;
  private String issueKey;
  private IssueStatus retainedStatus;
  private String ruleUuid;
  private String componentKey;
  private String message;
  private String severity;
  private Integer issueType;
  private String tags;
  private String assigneeUuid;
  private String statusMarkedByUuid;
  private Long issueCreationDate;
  private long fixedAt;

  public String getUuid() {
    return uuid;
  }

  public IssueTrackForRetainDto setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getBranchUuid() {
    return branchUuid;
  }

  public IssueTrackForRetainDto setBranchUuid(String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  public String getIssueKey() {
    return issueKey;
  }

  public IssueTrackForRetainDto setIssueKey(String issueKey) {
    this.issueKey = issueKey;
    return this;
  }

  public IssueStatus getRetainedStatus() {
    return retainedStatus;
  }

  public IssueTrackForRetainDto setRetainedStatus(IssueStatus retainedStatus) {
    this.retainedStatus = retainedStatus;
    return this;
  }

  public String getRuleUuid() {
    return ruleUuid;
  }

  public IssueTrackForRetainDto setRuleUuid(String ruleUuid) {
    this.ruleUuid = ruleUuid;
    return this;
  }

  public String getComponentKey() {
    return componentKey;
  }

  public IssueTrackForRetainDto setComponentKey(String componentKey) {
    this.componentKey = componentKey;
    return this;
  }

  @CheckForNull
  public String getMessage() {
    return message;
  }

  public IssueTrackForRetainDto setMessage(@Nullable String message) {
    this.message = message;
    return this;
  }

  @CheckForNull
  public String getSeverity() {
    return severity;
  }

  public IssueTrackForRetainDto setSeverity(@Nullable String severity) {
    this.severity = severity;
    return this;
  }

  /** The rule type's database value, as in 'issues.issue_type'. */
  @CheckForNull
  public Integer getIssueType() {
    return issueType;
  }

  public IssueTrackForRetainDto setIssueType(@Nullable Integer issueType) {
    this.issueType = issueType;
    return this;
  }

  @CheckForNull
  public String getTags() {
    return tags;
  }

  public IssueTrackForRetainDto setTags(@Nullable String tags) {
    this.tags = tags;
    return this;
  }

  @CheckForNull
  public String getAssigneeUuid() {
    return assigneeUuid;
  }

  public IssueTrackForRetainDto setAssigneeUuid(@Nullable String assigneeUuid) {
    this.assigneeUuid = assigneeUuid;
    return this;
  }

  /** The user who marked the issue Fixed; null when an analysis closed it. */
  @CheckForNull
  public String getStatusMarkedByUuid() {
    return statusMarkedByUuid;
  }

  public IssueTrackForRetainDto setStatusMarkedByUuid(@Nullable String statusMarkedByUuid) {
    this.statusMarkedByUuid = statusMarkedByUuid;
    return this;
  }

  @CheckForNull
  public Long getIssueCreationDate() {
    return issueCreationDate;
  }

  public IssueTrackForRetainDto setIssueCreationDate(@Nullable Long issueCreationDate) {
    this.issueCreationDate = issueCreationDate;
    return this;
  }

  /**
   * When the issue became Fixed. Not the issue's close date, which is empty for an issue a user resolved and is
   * cleared when the issue reopens.
   */
  public long getFixedAt() {
    return fixedAt;
  }

  public IssueTrackForRetainDto setFixedAt(long fixedAt) {
    this.fixedAt = fixedAt;
    return this;
  }
}
