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
import org.junit.jupiter.api.Test;
import org.sonar.db.measure.MainBranchMeasureHistoryDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.QualityTrendAggregator.Point;

import static org.assertj.core.api.Assertions.assertThat;

class QualityTrendAggregatorTest {

  // three daily buckets: 2026-10-12, 13 and 14
  private final QualityTrendAggregator underTest = new QualityTrendAggregator(
    new AnalyticsPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"), ZoneOffset.UTC, Interval.DAY));

  @Test
  void projects_analyzed_before_the_period_count_from_the_first_bucket() {
    underTest.add(gate("p1", "2026-09-01", "OK"));
    underTest.add(number("p1", "2026-09-01", "violations", 10));

    assertThat(underTest.points()).hasSize(3)
      .allSatisfy(point -> assertThat(point).isEqualTo(new Point(1, 1, 0, 10, 0, 0)));
  }

  @Test
  void each_bucket_keeps_the_latest_state_of_every_project() {
    underTest.add(gate("p1", "2026-10-01", "OK"));
    underTest.add(number("p1", "2026-10-01", "violations", 10));
    underTest.add(gate("p2", "2026-10-12", "ERROR"));
    underTest.add(number("p2", "2026-10-12", "vulnerabilities", 3));
    underTest.add(gate("p1", "2026-10-13", "ERROR"));
    underTest.add(number("p1", "2026-10-13", "violations", 4));
    underTest.add(number("p2", "2026-10-14", "blocker_violations", 2));

    assertThat(underTest.points()).containsExactly(
      new Point(2, 1, 1, 10, 3, 0),
      new Point(2, 0, 2, 4, 3, 0),
      new Point(2, 0, 2, 4, 3, 2));
  }

  @Test
  void no_analysis_means_empty_points() {
    assertThat(underTest.points()).containsOnly(new Point(0, 0, 0, 0, 0, 0)).hasSize(3);
  }

  private static MainBranchMeasureHistoryDto gate(String project, String day, String status) {
    return new MainBranchMeasureHistoryDto().setProjectUuid(project).setAnalysisDate(at(day)).setMetricKey("alert_status").setTextValue(status);
  }

  private static MainBranchMeasureHistoryDto number(String project, String day, String metric, double value) {
    return new MainBranchMeasureHistoryDto().setProjectUuid(project).setAnalysisDate(at(day)).setMetricKey(metric).setValue(value);
  }

  private static long at(String day) {
    return LocalDate.parse(day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
  }
}
