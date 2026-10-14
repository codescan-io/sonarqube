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
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.db.measure.MainBranchMeasureHistoryDto;

import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.CoreMetrics.BLOCKER_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.VULNERABILITIES_KEY;

/**
 * Replays the main branch analyses of an organization, oldest first, and records at the end of every bucket the state
 * each project was in at that time: its latest quality gate status and issue counts.
 */
class QualityTrendAggregator implements ResultHandler<MainBranchMeasureHistoryDto> {

  static final List<String> METRIC_KEYS = List.of(ALERT_STATUS_KEY, VIOLATIONS_KEY, VULNERABILITIES_KEY, BLOCKER_VIOLATIONS_KEY);

  /** How far before the period analyses are read, so projects analyzed rarely still have a state at its start. */
  static final long LOOKBACK_MS = 365L * 24 * 60 * 60 * 1000;

  private final List<Long> bucketEnds;
  private final List<Point> points = new ArrayList<>();
  private final Map<String, ProjectState> states = new HashMap<>();

  QualityTrendAggregator(AnalyticsPeriod period) {
    this.bucketEnds = period.bucketEndsMs();
  }

  @Override
  public void handleResult(ResultContext<? extends MainBranchMeasureHistoryDto> context) {
    add(context.getResultObject());
  }

  /** Measures must come in analysis date order. */
  void add(MainBranchMeasureHistoryDto measure) {
    while (points.size() < bucketEnds.size() && measure.getAnalysisDate() >= bucketEnds.get(points.size())) {
      points.add(snapshot());
    }
    if (points.size() == bucketEnds.size()) {
      return;
    }
    ProjectState state = states.computeIfAbsent(measure.getProjectUuid(), k -> new ProjectState());
    Double value = measure.getValue();
    switch (measure.getMetricKey()) {
      case ALERT_STATUS_KEY -> state.qualityGate = measure.getTextValue();
      case VIOLATIONS_KEY -> state.issues = value == null ? 0 : value.longValue();
      case VULNERABILITIES_KEY -> state.vulnerabilities = value == null ? 0 : value.longValue();
      case BLOCKER_VIOLATIONS_KEY -> state.blockers = value == null ? 0 : value.longValue();
      default -> {
        // not read
      }
    }
  }

  /** One point per bucket, once every measure has been added. */
  List<Point> points() {
    while (points.size() < bucketEnds.size()) {
      points.add(snapshot());
    }
    return points;
  }

  private Point snapshot() {
    int passed = 0;
    int failed = 0;
    long issues = 0;
    long vulnerabilities = 0;
    long blockers = 0;
    for (ProjectState state : states.values()) {
      if ("OK".equals(state.qualityGate)) {
        passed++;
      } else if ("ERROR".equals(state.qualityGate)) {
        failed++;
      }
      issues += state.issues;
      vulnerabilities += state.vulnerabilities;
      blockers += state.blockers;
    }
    return new Point(states.size(), passed, failed, issues, vulnerabilities, blockers);
  }

  record Point(int projects, int passed, int failed, long openIssues, long vulnerabilities, long blockerIssues) {
  }

  private static class ProjectState {
    private String qualityGate;
    private long issues;
    private long vulnerabilities;
    private long blockers;
  }
}
