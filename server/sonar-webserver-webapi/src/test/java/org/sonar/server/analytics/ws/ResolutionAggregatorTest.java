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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.issue.IssueResolutionChangeDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.ResolutionAggregator.Outcome;

import static org.assertj.core.api.Assertions.assertThat;

class ResolutionAggregatorTest {

  private final ResolutionAggregator underTest = new ResolutionAggregator(
    new AnalyticsPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"), ZoneOffset.UTC, Interval.DAY));

  @Test
  void classify_resolution_changes() {
    assertThat(ResolutionAggregator.outcomeOf(diff("", "FIXED"))).isEqualTo(Outcome.FIXED);
    assertThat(ResolutionAggregator.outcomeOf(diff(null, "REMOVED"))).isEqualTo(Outcome.REMOVED);
    assertThat(ResolutionAggregator.outcomeOf(diff(null, "WONTFIX"))).isEqualTo(Outcome.ACCEPTED);
    assertThat(ResolutionAggregator.outcomeOf(diff(null, "FALSE-POSITIVE"))).isEqualTo(Outcome.FALSE_POSITIVE);
    assertThat(ResolutionAggregator.outcomeOf(diff("FIXED", null))).isEqualTo(Outcome.REOPENED);
    assertThat(ResolutionAggregator.outcomeOf(new FieldDiffs().setDiff("status", "OPEN", "CONFIRMED").toEncodedString())).isNull();
  }

  @Test
  void count_by_bucket_and_in_total() {
    underTest.addCreation(at("2026-10-12"));
    underTest.addCreation(at("2026-10-12"));
    underTest.addCreation(at("2026-10-01"));
    underTest.addChange(change("issue-1", "2026-10-13", null, "FIXED", null));
    underTest.addChange(change("issue-2", "2026-10-13", null, "FIXED", "alice"));
    underTest.addChange(change("issue-3", "2026-10-14", null, "WONTFIX", "bob"));
    underTest.addChange(change("issue-4", "2026-10-01", null, "FIXED", null));

    assertThat(underTest.buckets().get(0).introduced()).isEqualTo(2);
    assertThat(underTest.buckets().get(1).fixed()).isEqualTo(2);
    assertThat(underTest.buckets().get(2).accepted()).isOne();
    assertThat(underTest.totals().introduced()).isEqualTo(2);
    assertThat(underTest.totals().resolved()).isEqualTo(3);
    assertThat(underTest.fixedByAnalysis()).isOne();
  }

  @Test
  void an_issue_resolved_then_reopened_in_the_period_is_neither_resolved_nor_reopened() {
    underTest.addChange(change("issue-1", "2026-10-12", "", "WONTFIX", "alice"));
    underTest.addChange(change("issue-1", "2026-10-13", "WONTFIX", "", "alice"));

    assertThat(underTest.totals().resolved()).isZero();
    assertThat(underTest.totals().reopened()).isZero();
  }

  @Test
  void an_issue_resolved_several_times_counts_once_with_its_last_resolution() {
    // added in reverse order: the scroll is not sorted by date
    underTest.addChange(change("issue-1", "2026-10-14", "", "FALSE-POSITIVE", "bob"));
    underTest.addChange(change("issue-1", "2026-10-13", "WONTFIX", "", "bob"));
    underTest.addChange(change("issue-1", "2026-10-12", "", "WONTFIX", "alice"));

    assertThat(underTest.totals().resolved()).isOne();
    assertThat(underTest.totals().falsePositive()).isOne();
    assertThat(underTest.totals().accepted()).isZero();
    assertThat(underTest.totals().reopened()).isZero();
    assertThat(underTest.buckets().get(2).falsePositive()).isOne();
  }

  @Test
  void an_issue_resolved_before_the_period_and_still_open_at_its_end_is_reopened() {
    underTest.addChange(change("issue-1", "2026-10-13", "FALSE-POSITIVE", "", "alice"));
    // reopened, then resolved again: open neither at the start nor at the end
    underTest.addChange(change("issue-2", "2026-10-12", "WONTFIX", "", "alice"));
    underTest.addChange(change("issue-2", "2026-10-14", "", "WONTFIX", "alice"));

    assertThat(underTest.totals().reopened()).isOne();
    assertThat(underTest.buckets().get(1).reopened()).isOne();
    assertThat(underTest.totals().resolved()).isZero();
  }

  private static IssueResolutionChangeDto change(String issueKey, String day, String from, String to, String user) {
    return new IssueResolutionChangeDto().setIssueKey(issueKey).setChangeData(diff(from, to)).setChangedAt(at(day)).setUserLogin(user);
  }

  private static String diff(String oldValue, String newValue) {
    return new FieldDiffs().setCreationDate(new Date()).setDiff("resolution", oldValue, newValue).setDiff("status", "OPEN", "CLOSED")
      .toEncodedString();
  }

  private static long at(String day) {
    return LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
  }
}
