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
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.sonar.db.measure.MainBranchMeasureHistoryDto;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.analytics.ws.QualityTrendAggregator.Point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class QualityTrendAggregatorTest {

  // three daily buckets: 2026-10-12, 13 and 14
  private final QualityTrendAggregator underTest = new QualityTrendAggregator(
    new AnalyticsPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"), ZoneOffset.UTC, Interval.DAY));

  @Test
  void projects_analyzed_before_the_period_count_from_the_first_bucket() {
    underTest.add(gate("p1", "2026-09-01", "OK"));
    underTest.add(number("p1", "2026-09-01", "violations", 10));

    assertThat(underTest.points()).hasSize(3)
      .allSatisfy(point -> assertThat(headline(point)).isEqualTo(tuple(1, 1, 0, 10L, 0L, 0L, 0L)));
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

    assertThat(underTest.points()).extracting(QualityTrendAggregatorTest::headline).containsExactly(
      tuple(2, 1, 1, 10L, 3L, 0L, 0L),
      tuple(2, 0, 2, 4L, 3L, 0L, 0L),
      tuple(2, 0, 2, 4L, 3L, 2L, 0L));
  }

  @Test
  void counts_open_issues_by_severity_and_type_and_exceptions() {
    underTest.add(number("p1", "2026-10-01", "critical_violations", 4));
    underTest.add(number("p1", "2026-10-01", "info_violations", 1));
    underTest.add(number("p1", "2026-10-01", "bugs", 2));
    underTest.add(number("p1", "2026-10-01", "security_hotspots", 5));
    underTest.add(number("p1", "2026-10-01", "accepted_issues", 3));
    underTest.add(number("p2", "2026-10-13", "critical_violations", 1));
    underTest.add(number("p2", "2026-10-13", "code_smells", 7));
    underTest.add(number("p2", "2026-10-13", "false_positive_issues", 2));

    Point first = underTest.points().get(0);
    Point last = underTest.points().get(2);
    assertThat(first.bySeverity()).containsExactly(0L, 4L, 0L, 0L, 1L);
    assertThat(first.byType()).containsExactly(2L, 0L, 0L, 5L);
    assertThat(last.bySeverity()).containsExactly(0L, 5L, 0L, 0L, 1L);
    assertThat(last.byType()).containsExactly(2L, 0L, 7L, 5L);
    assertThat(last.acceptedIssues()).isEqualTo(3);
    assertThat(last.falsePositiveIssues()).isEqualTo(2);
  }

  @Test
  void remediation_effort_adds_up_every_kind_of_issue_of_every_project() {
    underTest.add(number("p1", "2026-10-01", "sqale_index", 120));
    underTest.add(number("p1", "2026-10-01", "reliability_remediation_effort", 30));
    underTest.add(number("p1", "2026-10-01", "security_remediation_effort", 15));
    underTest.add(number("p2", "2026-10-12", "sqale_index", 60));
    underTest.add(number("p1", "2026-10-13", "sqale_index", 20));

    assertThat(underTest.points()).extracting(Point::remediationEffortMinutes).containsExactly(225L, 125L, 125L);
  }

  @Test
  void no_analysis_means_empty_points() {
    assertThat(underTest.points()).extracting(QualityTrendAggregatorTest::headline).containsOnly(tuple(0, 0, 0, 0L, 0L, 0L, 0L)).hasSize(3);
  }

  private static Tuple headline(Point point) {
    return tuple(point.projects(), point.passed(), point.failed(), point.openIssues(), point.vulnerabilities(), point.blockerIssues(),
      point.remediationEffortMinutes());
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
