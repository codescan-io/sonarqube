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
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsPeriodTest {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  @Test
  void bounds_are_read_in_the_time_zone_and_end_is_exclusive() {
    AnalyticsPeriod period = new AnalyticsPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), PARIS, Interval.DAY);

    assertThat(period.startMs()).isEqualTo(LocalDate.parse("2026-10-01").atStartOfDay(PARIS).toInstant().toEpochMilli());
    assertThat(period.endMs()).isEqualTo(LocalDate.parse("2026-10-03").atStartOfDay(PARIS).toInstant().toEpochMilli());
    assertThat(period.lengthInDays()).isEqualTo(2);
  }

  @Test
  void previous_period_has_the_same_length_and_ends_the_day_before() {
    AnalyticsPeriod previous = new AnalyticsPeriod(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-14"), PARIS, Interval.DAY).previous();

    assertThat(previous.from()).isEqualTo("2026-10-01");
    assertThat(previous.to()).isEqualTo("2026-10-07");
  }

  @Test
  void daily_buckets() {
    AnalyticsPeriod period = new AnalyticsPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), PARIS, Interval.DAY);

    assertThat(period.bucketStarts()).extracting(LocalDate::toString).containsExactly("2026-10-01", "2026-10-02", "2026-10-03");
    assertThat(period.bucketIndex(LocalDate.parse("2026-10-03").atTime(23, 59).atZone(PARIS).toInstant().toEpochMilli())).isEqualTo(2);
  }

  @Test
  void weekly_buckets_start_on_monday_except_the_first_one() {
    // 2026-10-01 is a Thursday
    AnalyticsPeriod period = new AnalyticsPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-14"), PARIS, Interval.WEEK);

    assertThat(period.bucketStarts()).extracting(LocalDate::toString).containsExactly("2026-10-01", "2026-10-05", "2026-10-12");
    assertThat(period.bucketIndex(atNoon("2026-10-04"))).isZero();
    assertThat(period.bucketIndex(atNoon("2026-10-05"))).isOne();
    assertThat(period.bucketIndex(atNoon("2026-10-14"))).isEqualTo(2);
  }

  @Test
  void fail_when_from_is_after_to() {
    LocalDate from = LocalDate.parse("2026-10-02");
    LocalDate to = LocalDate.parse("2026-10-01");
    assertThatThrownBy(() -> new AnalyticsPeriod(from, to, PARIS, Interval.DAY))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'from' must be before or equal to 'to'");
  }

  @Test
  void fail_when_period_is_longer_than_retention() {
    LocalDate to = LocalDate.parse("2026-10-12");
    LocalDate from = to.minusDays(AnalyticsPeriod.MAX_DAYS);
    assertThat(new AnalyticsPeriod(from.plusDays(1), to, PARIS, Interval.DAY).lengthInDays()).isEqualTo(AnalyticsPeriod.MAX_DAYS);
    assertThatThrownBy(() -> new AnalyticsPeriod(from, to, PARIS, Interval.DAY))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("The period cannot be longer than 180 days");
  }

  private static long atNoon(String day) {
    return LocalDate.parse(day).atTime(12, 0).atZone(PARIS).toInstant().toEpochMilli();
  }
}
