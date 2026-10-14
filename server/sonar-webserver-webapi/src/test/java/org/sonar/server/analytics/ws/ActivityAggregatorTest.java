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
import org.sonar.db.ce.CeActivityAnalyticsDto;
import org.sonar.db.ce.CeActivityDto.Status;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.server.analytics.ws.ActivityAggregator.Durations;
import org.sonar.server.analytics.ws.ActivityAggregator.Target;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityAggregatorTest {

  // current period is 2026-10-08..2026-10-14, previous one 2026-10-01..2026-10-07
  private final ActivityAggregator underTest = new ActivityAggregator(
    new AnalyticsPeriod(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-14"), ZoneOffset.UTC, Interval.DAY));

  @Test
  void scroll_covers_previous_and_current_periods() {
    assertThat(underTest.scrollFromMs()).isEqualTo(dayMs("2026-10-01"));
    assertThat(underTest.scrollToMs()).isEqualTo(dayMs("2026-10-15"));
  }

  @Test
  void counts_analyses_by_status_and_distinct_projects() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1000L));
    underTest.add(analysis("p1", "2026-10-09", Status.FAILED, 50L));
    underTest.add(analysis("p2", "2026-10-09", Status.CANCELED, null));

    ActivityAggregator.Stats stats = underTest.currentStats();
    assertThat(stats.total()).isEqualTo(3);
    assertThat(stats.success()).isOne();
    assertThat(stats.failed()).isOne();
    assertThat(stats.canceled()).isOne();
    assertThat(stats.projects()).isEqualTo(2);
  }

  @Test
  void execution_times_only_use_successful_analyses() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1000L));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 3000L));
    underTest.add(analysis("p1", "2026-10-08", Status.FAILED, 99_000L));

    Durations times = underTest.currentStats().executionTimes();
    assertThat(times.size()).isEqualTo(2);
    assertThat(times.average()).isEqualTo(2000L);
  }

  @Test
  void queue_time_is_start_minus_submission() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1000L).setStartedAt(dayMs("2026-10-08") + 401));
    underTest.add(analysis("p1", "2026-10-08", Status.CANCELED, null).setStartedAt(null));

    Durations queue = underTest.currentStats().queueTimes();
    assertThat(queue.size()).isOne();
    assertThat(queue.average()).isEqualTo(400L);
  }

  @Test
  void tasks_before_the_current_period_go_to_the_previous_one() {
    underTest.add(analysis("p1", "2026-10-07", Status.SUCCESS, 1000L));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1000L));

    assertThat(underTest.previousStats().total()).isOne();
    assertThat(underTest.currentStats().total()).isOne();
    assertThat(underTest.taskTypes().get(CeTaskTypes.REPORT).total()).isOne();
  }

  @Test
  void every_task_type_is_counted_but_only_reports_are_analyses() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1000L));
    underTest.add(analysis("p1", "2026-10-08", Status.FAILED, null).setTaskType("ISSUE_SYNC"));

    assertThat(underTest.taskTypes()).containsOnlyKeys(CeTaskTypes.REPORT, "ISSUE_SYNC");
    assertThat(underTest.taskTypes().get("ISSUE_SYNC").failed()).isOne();
    assertThat(underTest.currentStats().total()).isOne();
    assertThat(underTest.buckets().get(0).total()).isOne();
  }

  @Test
  void analyses_are_split_by_target() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1L).setIsMain(true).setBranchType("BRANCH"));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1L).setIsMain(false).setBranchType("BRANCH"));
    underTest.add(analysis("p1", "2026-10-08", Status.FAILED, 1L).setIsMain(false).setBranchType("PULL_REQUEST"));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 1L));

    assertThat(underTest.targets()).containsOnlyKeys(Target.MAIN, Target.BRANCH, Target.PULL_REQUEST, Target.UNKNOWN);
    assertThat(underTest.targets().get(Target.PULL_REQUEST).failed()).isOne();
  }

  @Test
  void timeline_has_one_bucket_per_day_with_percentiles() {
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 100L));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 200L));
    underTest.add(analysis("p1", "2026-10-08", Status.SUCCESS, 900L));
    underTest.add(analysis("p1", "2026-10-14", Status.FAILED, null));

    assertThat(underTest.buckets()).hasSize(7);
    assertThat(underTest.buckets().get(0).executionTimes().percentile(0.5)).isEqualTo(200L);
    assertThat(underTest.buckets().get(0).executionTimes().percentile(0.9)).isEqualTo(900L);
    assertThat(underTest.buckets().get(6).failed()).isOne();
    assertThat(underTest.buckets().get(3).total()).isZero();
  }

  @Test
  void durations_without_values_have_no_statistics() {
    Durations durations = new Durations();

    assertThat(durations.average()).isNull();
    assertThat(durations.percentile(0.5)).isNull();
  }

  @Test
  void durations_grow_past_their_initial_capacity() {
    Durations durations = new Durations();
    for (long i = 100; i >= 1; i--) {
      durations.add(i);
    }

    assertThat(durations.size()).isEqualTo(100);
    assertThat(durations.percentile(0.5)).isEqualTo(50L);
    assertThat(durations.percentile(0.9)).isEqualTo(90L);
    assertThat(durations.percentile(1)).isEqualTo(100L);
  }

  private static CeActivityAnalyticsDto analysis(String project, String day, Status status, Long executionTimeMs) {
    return new CeActivityAnalyticsDto()
      .setEntityUuid(project)
      .setTaskType(CeTaskTypes.REPORT)
      .setStatus(status)
      .setSubmittedAt(dayMs(day) + 1)
      .setExecutionTimeMs(executionTimeMs);
  }

  private static long dayMs(String day) {
    return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
