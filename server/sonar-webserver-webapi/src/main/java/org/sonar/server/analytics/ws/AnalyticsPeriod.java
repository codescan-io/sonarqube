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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A range of calendar days, both ends included, read in one time zone and split into day or week buckets.
 */
record AnalyticsPeriod(LocalDate from, LocalDate to, ZoneId zone, Interval interval) {

  /** CE activity older than this is purged, so a longer period would only show empty buckets. */
  static final int MAX_DAYS = 180;

  enum Interval {
    DAY, WEEK
  }

  AnalyticsPeriod {
    checkArgument(!from.isAfter(to), "'from' must be before or equal to 'to'");
    checkArgument(lengthInDays(from, to) <= MAX_DAYS, "The period cannot be longer than %s days", MAX_DAYS);
  }

  long startMs() {
    return from.atStartOfDay(zone).toInstant().toEpochMilli();
  }

  /** Exclusive: the start of the day after {@link #to}. */
  long endMs() {
    return to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
  }

  long lengthInDays() {
    return lengthInDays(from, to);
  }

  /** The period of the same length that ends the day before this one starts. */
  AnalyticsPeriod previous() {
    long days = lengthInDays();
    return new AnalyticsPeriod(from.minusDays(days), from.minusDays(1), zone, interval);
  }

  /**
   * Start day of every bucket. Weeks start on Monday; the first bucket starts on {@link #from} even when that is mid-week.
   */
  List<LocalDate> bucketStarts() {
    List<LocalDate> starts = new ArrayList<>();
    LocalDate day = from;
    while (!day.isAfter(to)) {
      starts.add(day);
      day = interval == Interval.DAY ? day.plusDays(1) : day.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }
    return starts;
  }

  /** Exclusive end of every bucket, in the order of {@link #bucketStarts()}: the start of the next one, then {@link #endMs()}. */
  List<Long> bucketEndsMs() {
    List<LocalDate> starts = bucketStarts();
    List<Long> ends = new ArrayList<>();
    for (int i = 1; i < starts.size(); i++) {
      ends.add(starts.get(i).atStartOfDay(zone).toInstant().toEpochMilli());
    }
    ends.add(endMs());
    return ends;
  }

  /** Index in {@link #bucketStarts()} of the bucket holding the given time, which must be inside the period. */
  int bucketIndex(long timeMs) {
    LocalDate day = java.time.Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate();
    if (interval == Interval.DAY) {
      return (int) ChronoUnit.DAYS.between(from, day);
    }
    LocalDate firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate dayMonday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    return (int) ChronoUnit.WEEKS.between(firstMonday, dayMonday);
  }

  private static long lengthInDays(LocalDate from, LocalDate to) {
    return ChronoUnit.DAYS.between(from, to) + 1;
  }
}
