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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sonar.db.issue.SecurityIssueDatesDto;
import org.sonar.server.analytics.ws.ActivityAggregator.Durations;

/**
 * How fast security issues get fixed against their remediation SLA: the fixes of a period and of the period before it, and the
 * issues still open.
 */
class SecurityRemediationAggregator {

  static final long DAY_MS = 24L * 60 * 60 * 1000;

  /** Days to fix an issue of each security impact severity, most severe first. */
  static final Map<String, Integer> DEFAULT_SLA_DAYS = defaultSlaDays();

  /** Severities that are not in the SLA get the most lenient one. */
  private static final int UNKNOWN_SEVERITY_SLA_DAYS = 365;

  private final AnalyticsPeriod current;
  private final AnalyticsPeriod previous;
  private final long now;
  private final Map<String, Severity> severities = new LinkedHashMap<>();
  private final Fixes totals = new Fixes();
  private final Fixes previousTotals = new Fixes();
  private final List<Fixes> buckets = new ArrayList<>();

  SecurityRemediationAggregator(AnalyticsPeriod current, Map<String, Integer> slaDays, long now) {
    this.current = current;
    this.previous = current.previous();
    this.now = now;
    slaDays.forEach((severity, days) -> severities.put(severity, new Severity(days)));
    current.bucketStarts().forEach(start -> buckets.add(new Fixes()));
  }

  private static Map<String, Integer> defaultSlaDays() {
    Map<String, Integer> days = new LinkedHashMap<>();
    days.put("BLOCKER", 30);
    days.put("HIGH", 30);
    days.put("MEDIUM", 90);
    days.put("LOW", 180);
    days.put("INFO", 365);
    return days;
  }

  /** Fixes are read from the start of the previous period, to compare with it. */
  long scrollFromMs() {
    return previous.startMs();
  }

  long scrollToMs() {
    return current.endMs();
  }

  void addFixed(SecurityIssueDatesDto issue) {
    Long closedAt = issue.getClosedAt();
    if (closedAt == null || closedAt < previous.startMs() || closedAt >= current.endMs()) {
      return;
    }
    Severity severity = severity(issue.getSeverity());
    long timeToFix = Math.max(0, closedAt - issue.getCreatedAt());
    boolean withinSla = timeToFix <= severity.slaDays * DAY_MS;
    if (closedAt >= current.startMs()) {
      totals.add(timeToFix, withinSla);
      severity.fixes.add(timeToFix, withinSla);
      buckets.get(current.bucketIndex(closedAt)).add(timeToFix, withinSla);
    } else {
      previousTotals.add(timeToFix, withinSla);
    }
  }

  void addOpen(SecurityIssueDatesDto issue) {
    Severity severity = severity(issue.getSeverity());
    long age = Math.max(0, now - issue.getCreatedAt());
    severity.open++;
    totals.open++;
    if (age > severity.slaDays * DAY_MS) {
      severity.openPastSla++;
      totals.openPastSla++;
    }
    severity.oldestOpenAgeMs = Math.max(severity.oldestOpenAgeMs, age);
  }

  private Severity severity(String name) {
    return severities.computeIfAbsent(name, k -> new Severity(UNKNOWN_SEVERITY_SLA_DAYS));
  }

  AnalyticsPeriod current() {
    return current;
  }

  AnalyticsPeriod previous() {
    return previous;
  }

  Fixes totals() {
    return totals;
  }

  Fixes previousTotals() {
    return previousTotals;
  }

  Map<String, Severity> severities() {
    return severities;
  }

  List<Fixes> buckets() {
    return buckets;
  }

  static class Fixes {
    private final Durations timesToFix = new Durations();
    private int withinSla;
    private int open;
    private int openPastSla;

    private void add(long timeToFix, boolean inSla) {
      timesToFix.add(timeToFix);
      if (inSla) {
        withinSla++;
      }
    }

    int fixed() {
      return timesToFix.size();
    }

    int fixedWithinSla() {
      return withinSla;
    }

    Durations timesToFix() {
      return timesToFix;
    }

    int open() {
      return open;
    }

    int openPastSla() {
      return openPastSla;
    }
  }

  static class Severity {
    private final int slaDays;
    private final Fixes fixes = new Fixes();
    private int open;
    private int openPastSla;
    private long oldestOpenAgeMs;

    private Severity(int slaDays) {
      this.slaDays = slaDays;
    }

    int slaDays() {
      return slaDays;
    }

    Fixes fixes() {
      return fixes;
    }

    int open() {
      return open;
    }

    int openPastSla() {
      return openPastSla;
    }

    long oldestOpenAgeMs() {
      return oldestOpenAgeMs;
    }
  }
}
