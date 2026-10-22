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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
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
 * <p>
 * Each issue counts once, for its net change over the period: resolved when it was open at the start and resolved at the
 * end, reopened when it was resolved at the start and open at the end. An issue resolved then reopened within the period, or
 * resolved more than once, does not inflate the counts.
 */
class ResolutionAggregator {

  enum Outcome {
    FIXED, REMOVED, ACCEPTED, FALSE_POSITIVE, REOPENED
  }

  private final AnalyticsPeriod period;
  private final List<Counts> buckets = new ArrayList<>();
  private final Counts totals = new Counts();
  private int fixedByAnalysis;
  private final Map<String, IssueTrack> issues = new HashMap<>();
  private boolean counted;

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
    FieldDiffs.Diff<?> diff = resolutionDiff(change.getChangeData());
    long changedAt = change.getChangedAt();
    if (diff == null || changedAt < period.startMs() || changedAt >= period.endMs()) {
      return;
    }
    IssueTrack track = issues.computeIfAbsent(change.getIssueKey(), k -> new IssueTrack());
    if (changedAt < track.firstAt) {
      track.firstAt = changedAt;
      track.resolvedAtStart = !isEmpty(valueOf(diff.oldValue()));
    }
    if (changedAt >= track.lastAt) {
      track.lastAt = changedAt;
      track.resolutionAtEnd = valueOf(diff.newValue());
      track.lastUserLogin = change.getUserLogin();
    }
    counted = false;
  }

  /** Turns the changes of every issue into its net outcome, once every change has been added. */
  private void count() {
    if (counted) {
      return;
    }
    buckets.forEach(Counts::clearResolutions);
    totals.clearResolutions();
    fixedByAnalysis = 0;
    for (IssueTrack track : issues.values()) {
      Outcome outcome = netOutcome(track);
      Counts bucket = bucketOf(track.lastAt);
      if (outcome == null || bucket == null) {
        continue;
      }
      bucket.add(outcome);
      totals.add(outcome);
      if (outcome == Outcome.FIXED && track.lastUserLogin == null) {
        fixedByAnalysis++;
      }
    }
    counted = true;
  }

  @CheckForNull
  private static Outcome netOutcome(IssueTrack track) {
    boolean resolvedAtEnd = !isEmpty(track.resolutionAtEnd);
    if (track.resolvedAtStart == resolvedAtEnd) {
      return null;
    }
    return resolvedAtEnd ? resolutionOutcome(track.resolutionAtEnd) : Outcome.REOPENED;
  }

  @CheckForNull
  private static FieldDiffs.Diff<?> resolutionDiff(String changeData) {
    return FieldDiffs.parse(changeData).diffs().get("resolution");
  }

  @CheckForNull
  private static String valueOf(@Nullable Object value) {
    return value == null ? null : value.toString();
  }

  /** What one change did to an issue's resolution, regardless of the issue's other changes. */
  @CheckForNull
  static Outcome outcomeOf(String changeData) {
    FieldDiffs.Diff<?> diff = resolutionDiff(changeData);
    if (diff == null) {
      return null;
    }
    String newValue = valueOf(diff.newValue());
    if (isEmpty(newValue)) {
      return isEmpty(valueOf(diff.oldValue())) ? null : Outcome.REOPENED;
    }
    return resolutionOutcome(newValue);
  }

  @CheckForNull
  private static Outcome resolutionOutcome(String resolution) {
    return switch (resolution) {
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
    count();
    return buckets;
  }

  Counts totals() {
    count();
    return totals;
  }

  /** Fixed issues that the analysis closed because they were gone from the code, rather than a person marking them fixed. */
  int fixedByAnalysis() {
    count();
    return fixedByAnalysis;
  }

  /** The resolution changes of one issue within the period: how it started and how it ended. */
  private static class IssueTrack {
    private long firstAt = Long.MAX_VALUE;
    private boolean resolvedAtStart;
    private long lastAt = Long.MIN_VALUE;
    private String resolutionAtEnd;
    private String lastUserLogin;
  }

  static class Counts {
    private int introduced;
    private int fixed;
    private int removed;
    private int accepted;
    private int falsePositive;
    private int reopened;

    private void clearResolutions() {
      fixed = 0;
      removed = 0;
      accepted = 0;
      falsePositive = 0;
      reopened = 0;
    }

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
