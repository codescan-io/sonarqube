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

import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;


public class ProliferationChangeQuery {

  private List<String> keys;
  private String branchUuid;
  private String changeAuthor;
  private long now;
  private long markedAt;
  private Collection<String> statuses;

  private String resolution;
  private String issueStatus;
  private String comment;
  private String changeType;

  private String uuidPrefix;
  private String keePrefix;

  /** The issues to write a row for. Set per partition by the DAO, not by the caller. */
  public List<String> getKeys() {
    return keys;
  }

  public ProliferationChangeQuery setKeys(List<String> keys) {
    this.keys = keys;
    return this;
  }

  /** Written to {@code issue_changes.project_uuid}, which holds the branch the issue belongs to. */
  public String getBranchUuid() {
    return branchUuid;
  }

  public ProliferationChangeQuery setBranchUuid(String branchUuid) {
    this.branchUuid = branchUuid;
    return this;
  }

  @CheckForNull
  public String getChangeAuthor() {
    return changeAuthor;
  }

  public ProliferationChangeQuery setChangeAuthor(@Nullable String changeAuthor) {
    this.changeAuthor = changeAuthor;
    return this;
  }

  /** The technical timestamp: {@code created_at} and {@code updated_at}. */
  public long getNow() {
    return now;
  }

  public ProliferationChangeQuery setNow(long now) {
    this.now = now;
    return this;
  }

  /**
   * When the user made the marking this row was copied from. Written to {@code issue_change_creation_date}, the
   * date the activity feed renders, so a copy is dated with the marking rather than with the run that copied it.
   */
  public long getMarkedAt() {
    return markedAt;
  }

  public ProliferationChangeQuery setMarkedAt(long markedAt) {
    this.markedAt = markedAt;
    return this;
  }

  /** The statuses an issue must be in to be written, re-checked in SQL so a concurrent change cannot slip past. */
  public Collection<String> getStatuses() {
    return statuses;
  }

  public ProliferationChangeQuery setStatuses(Collection<String> statuses) {
    this.statuses = statuses;
    return this;
  }

  /** Diff rows only. */
  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public ProliferationChangeQuery setResolution(String resolution) {
    this.resolution = resolution;
    return this;
  }

  /** Diff rows only: the issue status the change moves to, as the changelog spells it. */
  @CheckForNull
  public String getIssueStatus() {
    return issueStatus;
  }

  public ProliferationChangeQuery setIssueStatus(String issueStatus) {
    this.issueStatus = issueStatus;
    return this;
  }

  /** Comment rows only: the markdown written verbatim to every target. */
  @CheckForNull
  public String getComment() {
    return comment;
  }

  public ProliferationChangeQuery setComment(String comment) {
    this.comment = comment;
    return this;
  }

  /** Comment rows only, so one statement writes both plain comments and Exception reasons. */
  @CheckForNull
  public String getChangeType() {
    return changeType;
  }

  public ProliferationChangeQuery setChangeType(String changeType) {
    this.changeType = changeType;
    return this;
  }

  /** Completed with a row number by the SQL, because no uuid function exists on every supported dialect. */
  public String getUuidPrefix() {
    return uuidPrefix;
  }

  public ProliferationChangeQuery setUuidPrefix(String uuidPrefix) {
    this.uuidPrefix = uuidPrefix;
    return this;
  }

  /** As {@link #getUuidPrefix()}, for the {@code kee} column a comment row also carries. */
  @CheckForNull
  public String getKeePrefix() {
    return keePrefix;
  }

  public ProliferationChangeQuery setKeePrefix(@Nullable String keePrefix) {
    this.keePrefix = keePrefix;
    return this;
  }

  /**
   * This query against another set of keys. The original is left untouched, so one shared query can be copied per
   * statement and per partition without any of them seeing another's payload.
   */
  public ProliferationChangeQuery copyFor(List<String> keys) {
    return new ProliferationChangeQuery()
      .setKeys(keys)
      .setBranchUuid(branchUuid)
      .setChangeAuthor(changeAuthor)
      .setNow(now)
      .setMarkedAt(markedAt)
      .setStatuses(statuses)
      .setResolution(resolution)
      .setIssueStatus(issueStatus)
      .setComment(comment)
      .setChangeType(changeType)
      .setUuidPrefix(uuidPrefix)
      .setKeePrefix(keePrefix);
  }

  /** As {@link #copyFor}, giving the partition its own row-key prefixes so no two statements generate the same. */
  public ProliferationChangeQuery forPartition(List<String> partition, String uuidPrefix, @Nullable String keePrefix) {
    return copyFor(partition)
      .setUuidPrefix(uuidPrefix)
      .setKeePrefix(keePrefix);
  }
}
