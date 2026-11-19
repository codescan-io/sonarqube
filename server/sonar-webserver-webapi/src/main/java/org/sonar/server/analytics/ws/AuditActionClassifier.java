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
package org.sonar.server.analytics.ws;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.issue.IssueChangeDto;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.sonar.api.issue.Issue.RESOLUTION_ACKNOWLEDGED;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_REMOVED;
import static org.sonar.api.issue.Issue.RESOLUTION_SAFE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;

/** Reads what a changelog entry, comment or exception reason did to an issue, as the audit trail names it. */
class AuditActionClassifier {

  enum Action {
    ACCEPTED, FALSE_POSITIVE, FIXED, REMOVED, SAFE, ACKNOWLEDGED, REOPENED, STATUS_CHANGED, SEVERITY_CHANGED, COMMENTED,
    EXCEPTION_REASON, OTHER
  }

  /** @param from the value before the change, for a field change */
  record Classified(Action action, @Nullable String from, @Nullable String to, @Nullable String text) {
  }

  private AuditActionClassifier() {
    // static only
  }

  static Classified classify(String changeType, @Nullable String changeData) {
    if (IssueChangeDto.TYPE_COMMENT.equals(changeType)) {
      return new Classified(Action.COMMENTED, null, null, changeData);
    }
    if (IssueChangeDto.TYPE_EXCEPTION_REASON.equals(changeType)) {
      return new Classified(Action.EXCEPTION_REASON, null, null, changeData);
    }
    FieldDiffs diffs = FieldDiffs.parse(changeData);
    FieldDiffs.Diff<?> resolution = diffs.get("resolution");
    if (resolution != null) {
      String from = valueOf(resolution.oldValue());
      String to = valueOf(resolution.newValue());
      if (isEmpty(to)) {
        return isEmpty(from) ? other(diffs) : new Classified(Action.REOPENED, from, null, null);
      }
      return new Classified(resolutionAction(to), from, to, null);
    }
    FieldDiffs.Diff<?> status = diffs.get("status");
    if (status != null) {
      return new Classified(Action.STATUS_CHANGED, valueOf(status.oldValue()), valueOf(status.newValue()), null);
    }
    FieldDiffs.Diff<?> severity = diffs.get("severity");
    if (severity != null) {
      return new Classified(Action.SEVERITY_CHANGED, valueOf(severity.oldValue()), valueOf(severity.newValue()), null);
    }
    return other(diffs);
  }

  private static Classified other(FieldDiffs diffs) {
    return new Classified(Action.OTHER, null, null, String.join(",", diffs.diffs().keySet()));
  }

  private static Action resolutionAction(String resolution) {
    return switch (resolution) {
      case RESOLUTION_WONT_FIX -> Action.ACCEPTED;
      case RESOLUTION_FALSE_POSITIVE -> Action.FALSE_POSITIVE;
      case RESOLUTION_FIXED -> Action.FIXED;
      case RESOLUTION_REMOVED -> Action.REMOVED;
      case RESOLUTION_SAFE -> Action.SAFE;
      case RESOLUTION_ACKNOWLEDGED -> Action.ACKNOWLEDGED;
      default -> Action.STATUS_CHANGED;
    };
  }

  @CheckForNull
  private static String valueOf(@Nullable Object value) {
    return value == null ? null : value.toString();
  }
}
