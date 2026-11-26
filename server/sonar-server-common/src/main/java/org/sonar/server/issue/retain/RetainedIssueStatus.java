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
package org.sonar.server.issue.retain;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.FieldDiffs;

/**
 * Which issues the retained-issue listeners care about. "Fixed" is what the UI shows as Fixed: any CLOSED issue
 * (resolution FIXED or REMOVED) and RESOLVED/FIXED. Security hotspots are excluded.
 */
public final class RetainedIssueStatus {

  private static final String STATUS_FIELD = "status";
  private static final String RESOLUTION_FIELD = "resolution";

  private RetainedIssueStatus() {
    // static helpers only
  }

  public static boolean isFixed(DefaultIssue issue) {
    return issue.type() != RuleType.SECURITY_HOTSPOT && issue.issueStatus() == IssueStatus.FIXED;
  }

  /**
   * True when this save moves the issue into or out of Fixed. A new or copied issue counts as coming from outside
   * Fixed. Any other change (assignee, comment, Open to Accepted, Resolved Fixed to Closed) is ignored.
   */
  public static boolean isCandidate(DefaultIssue issue) {
    return isFixed(issue) != wasFixed(issue);
  }

  /** Whether the issue was Fixed before this save, read from the old values its current change keeps. */
  static boolean wasFixed(DefaultIssue issue) {
    if (issue.isNew() || issue.isCopied() || issue.type() == RuleType.SECURITY_HOTSPOT) {
      return false;
    }
    FieldDiffs change = issue.currentChange();
    String status = valueBefore(change, STATUS_FIELD, issue.status());
    String resolution = valueBefore(change, RESOLUTION_FIELD, issue.resolution());
    return IssueStatus.of(status, resolution) == IssueStatus.FIXED;
  }

  @CheckForNull
  private static String valueBefore(@Nullable FieldDiffs change, String field, @Nullable String current) {
    FieldDiffs.Diff<?> diff = change == null ? null : change.get(field);
    if (diff == null) {
      return current;
    }
    return diff.oldValue() == null ? null : diff.oldValue().toString();
  }
}
