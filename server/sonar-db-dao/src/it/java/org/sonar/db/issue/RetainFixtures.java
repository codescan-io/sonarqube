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

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbTester;

/**
 * Rows of the live tables a retained issue is copied from, inserted directly so the organization toggle can be set, and
 * readers of the retained tables.
 */
public final class RetainFixtures {

  private RetainFixtures() {
  }

  /** The test database keeps 'organizations' between tests, so each call creates its own organization. */
  public static void project(DbTester db, boolean retainEnabled, String projectUuid) {
    String organizationUuid = Uuids.createFast();
    db.executeInsert("organizations",
      "uuid", organizationUuid,
      "kee", "key-" + organizationUuid,
      "name", "name-" + organizationUuid,
      "default_quality_gate_uuid", "qg-1",
      "new_project_private", true,
      "subscription", "FREE",
      "created_at", 1L,
      "updated_at", 1L,
      "retain_issue_history_enabled", retainEnabled);
    db.executeInsert("projects",
      "uuid", projectUuid,
      "organization_uuid", organizationUuid,
      "kee", "key-" + projectUuid,
      "qualifier", "TRK",
      "private", true,
      "updated_at", 1L,
      "creation_method", "LOCAL_API");
  }

  public static void branch(DbTester db, String branchUuid, String projectUuid, String key, String type, @Nullable String mergeBranchUuid) {
    db.executeInsert("project_branches",
      "uuid", branchUuid,
      "project_uuid", projectUuid,
      "kee", key,
      "branch_type", type,
      "merge_branch_uuid", mergeBranchUuid,
      "created_at", 1L,
      "updated_at", 1L,
      "need_issue_sync", false,
      "is_main", "main".equals(key));
  }

  public static void file(DbTester db, String fileUuid, String branchUuid, String key) {
    file(db, fileUuid, branchUuid, key, true);
  }

  public static void file(DbTester db, String fileUuid, String branchUuid, String key, boolean enabled) {
    db.executeInsert("components",
      "uuid", fileUuid,
      "kee", key,
      "branch_uuid", branchUuid,
      "uuid_path", ".",
      "private", true,
      "enabled", enabled,
      "scope", "FIL",
      "qualifier", "FIL");
  }

  public static void issue(DbTester db, String issueKey, String branchUuid, String fileUuid, int issueType) {
    issue(db, issueKey, branchUuid, fileUuid, issueType, "OPEN", null, null);
  }

  /** An issue the analysis closed at {@code closeDate}. */
  public static void closedIssue(DbTester db, String issueKey, String branchUuid, String fileUuid, int issueType, long closeDate) {
    issue(db, issueKey, branchUuid, fileUuid, issueType, "CLOSED", "FIXED", closeDate);
  }

  /** An issue a user resolved as fixed: it has no close date. */
  public static void resolvedFixedIssue(DbTester db, String issueKey, String branchUuid, String fileUuid, int issueType) {
    issue(db, issueKey, branchUuid, fileUuid, issueType, "RESOLVED", "FIXED", null);
  }

  public static void issue(DbTester db, String issueKey, String branchUuid, String fileUuid, int issueType, String status,
    @Nullable String resolution, @Nullable Long closeDate) {
    db.executeInsert("issues",
      "kee", issueKey,
      "rule_uuid", "rule-1",
      "severity", "MAJOR",
      "manual_severity", false,
      "message", "message of " + issueKey,
      "assignee", "user-7",
      "issue_creation_date", 1_000L,
      "tags", "design,apex",
      "component_uuid", fileUuid,
      "project_uuid", branchUuid,
      "issue_type", issueType,
      "status", status,
      "resolution", resolution,
      "issue_close_date", closeDate);
  }

  /** A retained branch row written as is, for tests that need one without its 'project_branches' row. */
  public static void retainedBranch(DbTester db, String branchUuid, String projectUuid, String key, String type) {
    db.executeInsert("cs_retain_details",
      "branch_uuid", branchUuid,
      "project_uuid", projectUuid,
      "branch_key", key,
      "branch_type", type);
  }

  /** A retained row written as is, for tests that need one without the issue it is copied from. */
  public static void retainedIssue(DbTester db, String branchUuid, String issueKey, String retainedStatus, long statusChangedAt) {
    db.executeInsert("cs_issue_track_for_retain",
      "branch_uuid", branchUuid,
      "issue_kee", issueKey,
      "retained_status", retainedStatus,
      "rule_uuid", "rule-1",
      "component_key", "project-1:src/Foo.cls",
      "status_changed_at", statusChangedAt);
  }

  /** Every retained issue row, keyed by upper-case column name, in the order the issues were retained. */
  public static List<Map<String, Object>> retainedIssues(DbTester db) {
    return db.select("select * from cs_issue_track_for_retain order by status_changed_at, issue_kee");
  }

  public static List<String> retainedIssueKeys(DbTester db) {
    return retainedIssues(db).stream().map(row -> (String) row.get("ISSUE_KEE")).toList();
  }

  public static List<Map<String, Object>> retainedBranches(DbTester db) {
    return db.select("select * from cs_retain_details order by branch_uuid");
  }

  public static List<String> retainedCommentUuids(DbTester db) {
    return db.select("select uuid from cs_retain_issue_comments order by uuid").stream().map(row -> (String) row.get("UUID")).toList();
  }
}
