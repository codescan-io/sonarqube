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
    underTest.addChange(change("2026-10-13", "FIXED", null));
    underTest.addChange(change("2026-10-13", "FIXED", "alice"));
    underTest.addChange(change("2026-10-14", "WONTFIX", "bob"));
    underTest.addChange(change("2026-10-01", "FIXED", null));

    assertThat(underTest.buckets().get(0).introduced()).isEqualTo(2);
    assertThat(underTest.buckets().get(1).fixed()).isEqualTo(2);
    assertThat(underTest.buckets().get(2).accepted()).isOne();
    assertThat(underTest.totals().introduced()).isEqualTo(2);
    assertThat(underTest.totals().resolved()).isEqualTo(3);
    assertThat(underTest.fixedByAnalysis()).isOne();
  }

  private static IssueResolutionChangeDto change(String day, String resolution, String user) {
    return new IssueResolutionChangeDto().setChangeData(diff(null, resolution)).setChangedAt(at(day)).setUserLogin(user);
  }

  private static String diff(String oldValue, String newValue) {
    return new FieldDiffs().setCreationDate(new Date()).setDiff("resolution", oldValue, newValue).setDiff("status", "OPEN", "CLOSED")
      .toEncodedString();
  }

  private static long at(String day) {
    return LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
  }
}
