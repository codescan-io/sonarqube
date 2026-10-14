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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.issue.IssueResolutionChangeDto;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_REMOVED;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;

/**
 * Counts, per bucket of a period, the issues introduced on the organization's main branches and how issues got resolved:
 * fixed (gone from the code), removed (rule removed or file deleted), accepted, marked as false positive, or reopened.
 */
class ResolutionAggregator {

  enum Outcome {
    FIXED, REMOVED, ACCEPTED, FALSE_POSITIVE, REOPENED
  }

  private final AnalyticsPeriod period;
  private final List<Counts> buckets = new ArrayList<>();
  private final Counts totals = new Counts();
  private int fixedByAnalysis;

  ResolutionAggregator(AnalyticsPeriod period) {
    this.period = period;
    period.bucketStarts().forEach(start -> buckets.add(new Counts()));
  }

  void addCreation(long createdAt) {
    Counts bucket = bucketOf(createdAt);
    if (bucket != null) {
      bucket.introduced++;
      totals.introduced++;
    }
  }

  void addChange(IssueResolutionChangeDto change) {
    Outcome outcome = outcomeOf(change.getChangeData());
    Counts bucket = bucketOf(change.getChangedAt());
    if (outcome == null || bucket == null) {
      return;
    }
    bucket.add(outcome);
    totals.add(outcome);
    if (outcome == Outcome.FIXED && change.getUserLogin() == null) {
      fixedByAnalysis++;
    }
  }

  @CheckForNull
  static Outcome outcomeOf(String changeData) {
    FieldDiffs.Diff<?> diff = FieldDiffs.parse(changeData).diffs().get("resolution");
    if (diff == null) {
      return null;
    }
    String newValue = diff.newValue() == null ? null : diff.newValue().toString();
    if (isEmpty(newValue)) {
      return diff.oldValue() == null || isEmpty(diff.oldValue().toString()) ? null : Outcome.REOPENED;
    }
    return switch (newValue) {
      case RESOLUTION_FIXED -> Outcome.FIXED;
      case RESOLUTION_REMOVED -> Outcome.REMOVED;
      case RESOLUTION_WONT_FIX -> Outcome.ACCEPTED;
      case RESOLUTION_FALSE_POSITIVE -> Outcome.FALSE_POSITIVE;
      default -> null;
    };
  }

  @CheckForNull
  private Counts bucketOf(long time) {
    if (time < period.startMs() || time >= period.endMs()) {
      return null;
    }
    int index = period.bucketIndex(time);
    return index >= 0 && index < buckets.size() ? buckets.get(index) : null;
  }

  List<Counts> buckets() {
    return buckets;
  }

  Counts totals() {
    return totals;
  }

  /** Fixed issues that the analysis closed because they were gone from the code, rather than a person marking them fixed. */
  int fixedByAnalysis() {
    return fixedByAnalysis;
  }

  static class Counts {
    private int introduced;
    private int fixed;
    private int removed;
    private int accepted;
    private int falsePositive;
    private int reopened;

    private void add(Outcome outcome) {
      switch (outcome) {
        case FIXED -> fixed++;
        case REMOVED -> removed++;
        case ACCEPTED -> accepted++;
        case FALSE_POSITIVE -> falsePositive++;
        case REOPENED -> reopened++;
      }
    }

    int introduced() {
      return introduced;
    }

    int fixed() {
      return fixed;
    }

    int removed() {
      return removed;
    }

    int accepted() {
      return accepted;
    }

    int falsePositive() {
      return falsePositive;
    }

    int reopened() {
      return reopened;
    }

    int resolved() {
      return fixed + removed + accepted + falsePositive;
    }
  }
}
