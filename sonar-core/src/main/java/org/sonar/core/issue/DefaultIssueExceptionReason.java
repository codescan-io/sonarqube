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
package org.sonar.core.issue;

import java.io.Serializable;
import java.util.Date;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.core.util.Uuids;

public class DefaultIssueExceptionReason implements Serializable {

  private String issueKey;
  private String userUuid;
  private Date createdAt;
  private Date updatedAt;
  private String key;
  private String markdownText;
  private boolean isNew;

  public static DefaultIssueExceptionReason create(String issueKey, @Nullable String userUuid, String markdownText) {
    DefaultIssueExceptionReason exceptionReason = new DefaultIssueExceptionReason();
    exceptionReason.setIssueKey(issueKey);
    exceptionReason.setKey(Uuids.create());
    Date now = new Date();
    exceptionReason.setUserUuid(userUuid);
    exceptionReason.setMarkdownText(markdownText);
    exceptionReason.setCreatedAt(now).setUpdatedAt(now);
    exceptionReason.setNew(true);
    return exceptionReason;
  }

  public String markdownText() {
    return markdownText;
  }

  public DefaultIssueExceptionReason setMarkdownText(String s) {
    this.markdownText = s;
    return this;
  }

  public String issueKey() {
    return issueKey;
  }

  public DefaultIssueExceptionReason setIssueKey(String s) {
    this.issueKey = s;
    return this;
  }

  public String key() {
    return key;
  }

  public DefaultIssueExceptionReason setKey(String key) {
    this.key = key;
    return this;
  }

  /**
   * The user uuid who created the exception reason. Null if it was automatically generated during project scan.
   */
  @CheckForNull
  public String userUuid() {
    return userUuid;
  }

  public DefaultIssueExceptionReason setUserUuid(@Nullable String userUuid) {
    this.userUuid = userUuid;
    return this;
  }

  public Date createdAt() {
    return createdAt;
  }

  public DefaultIssueExceptionReason setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public Date updatedAt() {
    return updatedAt;
  }

  public DefaultIssueExceptionReason setUpdatedAt(@Nullable Date updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public boolean isNew() {
    return isNew;
  }

  public DefaultIssueExceptionReason setNew(boolean b) {
    isNew = b;
    return this;
  }

}
