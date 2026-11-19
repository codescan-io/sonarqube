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
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.rule.Severity;
import org.sonar.db.measure.MainBranchMeasureHistoryDto;

import static org.sonar.api.measures.CoreMetrics.ACCEPTED_ISSUES_KEY;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.CoreMetrics.BLOCKER_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.BUGS_KEY;
import static org.sonar.api.measures.CoreMetrics.CODE_SMELLS_KEY;
import static org.sonar.api.measures.CoreMetrics.CRITICAL_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.FALSE_POSITIVE_ISSUES_KEY;
import static org.sonar.api.measures.CoreMetrics.INFO_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.MAJOR_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.MINOR_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.RELIABILITY_REMEDIATION_EFFORT_KEY;
import static org.sonar.api.measures.CoreMetrics.SECURITY_HOTSPOTS_KEY;
import static org.sonar.api.measures.CoreMetrics.SECURITY_REMEDIATION_EFFORT_KEY;
import static org.sonar.api.measures.CoreMetrics.TECHNICAL_DEBT_KEY;
import static org.sonar.api.measures.CoreMetrics.VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.VULNERABILITIES_KEY;

/**
 * Replays the main branch analyses of an organization, oldest first, and records at the end of every bucket the state
 * each project was in at that time: its latest quality gate status, issue counts and remediation effort.
 */
class QualityTrendAggregator implements ResultHandler<MainBranchMeasureHistoryDto> {

  /** Unresolved issues by severity, from blocker to info, as {@link Point#bySeverity()} lists them. */
  static final List<String> SEVERITY_METRIC_KEYS = List.of(BLOCKER_VIOLATIONS_KEY, CRITICAL_VIOLATIONS_KEY, MAJOR_VIOLATIONS_KEY,
    MINOR_VIOLATIONS_KEY, INFO_VIOLATIONS_KEY);
  static final List<String> SEVERITIES = List.of(Severity.BLOCKER, Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO);
  /** Unresolved issues and hotspots to review by type, as {@link Point#byType()} lists them. */
  static final List<String> TYPE_METRIC_KEYS = List.of(BUGS_KEY, VULNERABILITIES_KEY, CODE_SMELLS_KEY, SECURITY_HOTSPOTS_KEY);
  static final List<String> TYPES = List.of("BUG", "VULNERABILITY", "CODE_SMELL", "SECURITY_HOTSPOT");

  static final List<String> METRIC_KEYS = Stream.of(
    List.of(ALERT_STATUS_KEY, VIOLATIONS_KEY, TECHNICAL_DEBT_KEY, RELIABILITY_REMEDIATION_EFFORT_KEY, SECURITY_REMEDIATION_EFFORT_KEY,
      ACCEPTED_ISSUES_KEY, FALSE_POSITIVE_ISSUES_KEY),
    SEVERITY_METRIC_KEYS, TYPE_METRIC_KEYS).flatMap(List::stream).distinct().toList();

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
      case ACCEPTED_ISSUES_KEY -> state.accepted = value == null ? 0 : value.longValue();
      case FALSE_POSITIVE_ISSUES_KEY -> state.falsePositives = value == null ? 0 : value.longValue();
      case TECHNICAL_DEBT_KEY -> state.maintainabilityEffort = value == null ? 0 : value.longValue();
      case RELIABILITY_REMEDIATION_EFFORT_KEY -> state.reliabilityEffort = value == null ? 0 : value.longValue();
      case SECURITY_REMEDIATION_EFFORT_KEY -> state.securityEffort = value == null ? 0 : value.longValue();
      default -> {
        int severity = SEVERITY_METRIC_KEYS.indexOf(measure.getMetricKey());
        int type = TYPE_METRIC_KEYS.indexOf(measure.getMetricKey());
        long count = value == null ? 0 : value.longValue();
        if (severity >= 0) {
          state.bySeverity[severity] = count;
        }
        if (type >= 0) {
          state.byType[type] = count;
        }
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
    long accepted = 0;
    long falsePositives = 0;
    long[] bySeverity = new long[SEVERITY_METRIC_KEYS.size()];
    long[] byType = new long[TYPE_METRIC_KEYS.size()];
    long effort = 0;
    for (ProjectState state : states.values()) {
      if ("OK".equals(state.qualityGate)) {
        passed++;
      } else if ("ERROR".equals(state.qualityGate)) {
        failed++;
      }
      issues += state.issues;
      accepted += state.accepted;
      falsePositives += state.falsePositives;
      for (int i = 0; i < bySeverity.length; i++) {
        bySeverity[i] += state.bySeverity[i];
      }
      for (int i = 0; i < byType.length; i++) {
        byType[i] += state.byType[i];
      }
      effort += state.maintainabilityEffort + state.reliabilityEffort + state.securityEffort;
    }
    return new Point(states.size(), passed, failed, issues, effort, accepted, falsePositives, LongStream.of(bySeverity).boxed().toList(),
      LongStream.of(byType).boxed().toList());
  }

  /**
   * @param remediationEffortMinutes time to fix every open issue, as the analysis estimates it
   * @param bySeverity open issues by severity, in the order of {@link #SEVERITIES}
   * @param byType open issues and hotspots to review by type, in the order of {@link #TYPES}
   */
  record Point(int projects, int passed, int failed, long openIssues, long remediationEffortMinutes, long acceptedIssues,
    long falsePositiveIssues, List<Long> bySeverity, List<Long> byType) {

    long vulnerabilities() {
      return byType.get(TYPES.indexOf("VULNERABILITY"));
    }

    long blockerIssues() {
      return bySeverity.get(0);
    }
  }

  private static class ProjectState {
    private String qualityGate;
    private long issues;
    private long accepted;
    private long falsePositives;
    private final long[] bySeverity = new long[SEVERITY_METRIC_KEYS.size()];
    private final long[] byType = new long[TYPE_METRIC_KEYS.size()];
    private long maintainabilityEffort;
    private long reliabilityEffort;
    private long securityEffort;
  }
}
