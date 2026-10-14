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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.db.ce.CeActivityAnalyticsDto;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeTaskTypes;

/**
 * Aggregates the CE activity of one organization, streamed over the previous and the current period,
 * into the figures returned by {@code api/analytics/activity}.
 * <p>
 * "Analyses" are {@link CeTaskTypes#REPORT} tasks. Every task type is counted in {@link #taskTypes()}.
 * Execution times only use successful analyses, as failed ones stop part way.
 */
class ActivityAggregator implements ResultHandler<CeActivityAnalyticsDto> {

  enum Target {
    MAIN, BRANCH, PULL_REQUEST, UNKNOWN;

    static Target of(@Nullable Boolean isMain, @Nullable String branchType) {
      if (Boolean.TRUE.equals(isMain)) {
        return MAIN;
      }
      if ("PULL_REQUEST".equals(branchType)) {
        return PULL_REQUEST;
      }
      if ("BRANCH".equals(branchType)) {
        return BRANCH;
      }
      // the branch was deleted since the task ran
      return UNKNOWN;
    }
  }

  private final AnalyticsPeriod current;
  private final AnalyticsPeriod previous;
  private final Stats currentStats = new Stats();
  private final Stats previousStats = new Stats();
  private final Map<String, StatusCounts> taskTypes = new TreeMap<>();
  private final Map<Target, Stats> targets = new TreeMap<>();
  private final List<Stats> buckets = new ArrayList<>();

  ActivityAggregator(AnalyticsPeriod current) {
    this.current = current;
    this.previous = current.previous();
    current.bucketStarts().forEach(start -> buckets.add(new Stats()));
  }

  /** Where to start streaming CE activity so both periods are covered. */
  long scrollFromMs() {
    return previous.startMs();
  }

  long scrollToMs() {
    return current.endMs();
  }

  @Override
  public void handleResult(ResultContext<? extends CeActivityAnalyticsDto> context) {
    add(context.getResultObject());
  }

  void add(CeActivityAnalyticsDto task) {
    long submittedAt = task.getSubmittedAt();
    boolean inCurrent = submittedAt >= current.startMs() && submittedAt < current.endMs();
    boolean inPrevious = submittedAt >= previous.startMs() && submittedAt < previous.endMs();
    if (inCurrent) {
      taskTypes.computeIfAbsent(task.getTaskType(), k -> new StatusCounts()).add(task.getStatus());
    }
    if (!CeTaskTypes.REPORT.equals(task.getTaskType())) {
      return;
    }
    if (inCurrent) {
      currentStats.add(task);
      targets.computeIfAbsent(Target.of(task.getIsMain(), task.getBranchType()), k -> new Stats()).add(task);
      int bucket = current.bucketIndex(submittedAt);
      if (bucket >= 0 && bucket < buckets.size()) {
        buckets.get(bucket).add(task);
      }
    } else if (inPrevious) {
      previousStats.add(task);
    }
  }

  Stats currentStats() {
    return currentStats;
  }

  Stats previousStats() {
    return previousStats;
  }

  Map<String, StatusCounts> taskTypes() {
    return taskTypes;
  }

  /** Analyses of the current period by target, with the projects they cover. */
  Map<Target, Stats> targets() {
    return targets;
  }

  /** One entry per bucket of the current period, in the order of {@link AnalyticsPeriod#bucketStarts()}. */
  List<Stats> buckets() {
    return buckets;
  }

  AnalyticsPeriod current() {
    return current;
  }

  AnalyticsPeriod previous() {
    return previous;
  }

  static class StatusCounts {
    private int total;
    private int success;
    private int failed;
    private int canceled;

    void add(CeActivityDto.Status status) {
      total++;
      switch (status) {
        case SUCCESS -> success++;
        case FAILED -> failed++;
        case CANCELED -> canceled++;
      }
    }

    int total() {
      return total;
    }

    int success() {
      return success;
    }

    int failed() {
      return failed;
    }

    int canceled() {
      return canceled;
    }
  }

  static class Stats extends StatusCounts {
    private final Set<String> projects = new HashSet<>();
    private final Durations executionTimes = new Durations();
    private final Durations queueTimes = new Durations();

    void add(CeActivityAnalyticsDto task) {
      add(task.getStatus());
      projects.add(task.getEntityUuid());
      Long executionTimeMs = task.getExecutionTimeMs();
      if (task.getStatus() == CeActivityDto.Status.SUCCESS && executionTimeMs != null) {
        executionTimes.add(executionTimeMs);
      }
      Long startedAt = task.getStartedAt();
      if (startedAt != null) {
        queueTimes.add(Math.max(0, startedAt - task.getSubmittedAt()));
      }
    }

    int projects() {
      return projects.size();
    }

    Durations executionTimes() {
      return executionTimes;
    }

    Durations queueTimes() {
      return queueTimes;
    }
  }

  /** Durations in milliseconds, kept as a growable primitive array so large periods stay cheap. */
  static class Durations {
    private long[] values = new long[16];
    private int size;
    private long sum;
    private boolean sorted = true;

    void add(long value) {
      if (size == values.length) {
        values = Arrays.copyOf(values, size * 2);
      }
      values[size++] = value;
      sum += value;
      sorted = false;
    }

    int size() {
      return size;
    }

    @CheckForNull
    Long average() {
      return size == 0 ? null : Math.round((double) sum / size);
    }

    /** Nearest-rank percentile, {@code p} in (0, 1]. */
    @CheckForNull
    Long percentile(double p) {
      if (size == 0) {
        return null;
      }
      if (!sorted) {
        Arrays.sort(values, 0, size);
        sorted = true;
      }
      int rank = (int) Math.ceil(p * size);
      return values[Math.max(0, Math.min(size, rank) - 1)];
    }
  }
}
