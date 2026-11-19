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

import { QualityTrendPoint, TypeSeverityCounts, getProjectIssues } from '../../api/analytics';
import { countOf, sumTypeSeverity } from './governance';
import { SEVERITIES } from './utils';

/** The periods the page offers. One period applies to every tab, so switching tabs never changes it. */
export const ANALYTICS_PERIODS = [7, 30, 90, 180] as const;
export type AnalyticsPeriod = (typeof ANALYTICS_PERIODS)[number];
export const DEFAULT_ANALYTICS_PERIOD: AnalyticsPeriod = 30;

export function parsePeriod(value: string | null): AnalyticsPeriod {
  return ANALYTICS_PERIODS.find((days) => String(days) === value) ?? DEFAULT_ANALYTICS_PERIOD;
}

/** Unresolved issues of every main branch as they are now, security hotspots to review included. */
export interface CurrentIssues {
  effortMinutes: number;
  matrix: TypeSeverityCounts;
}

export async function fetchCurrentIssues(organization: string): Promise<CurrentIssues> {
  const { projects } = await getProjectIssues(organization, true);
  return {
    effortMinutes: projects.reduce((sum, { effortMinutes }) => sum + effortMinutes, 0),
    matrix: sumTypeSeverity(projects),
  };
}

const HOTSPOT = 'SECURITY_HOTSPOT';

/** Issues, hotspots left out, the way the trend counts them. */
function issueCount(matrix: TypeSeverityCounts, type?: string, severity?: string) {
  const all = countOf(matrix, type, severity);
  return type === undefined ? all - countOf(matrix, HOTSPOT, severity) : all;
}

/**
 * The trend with its last point set to the issues as they are now.
 *
 * The trend reads the measures each analysis saved, so it misses what happened since a project's latest analysis:
 * an issue accepted yesterday still counts as open until the project is analyzed again. Its last point is today, so
 * it takes the live counts, and every headline number then matches the issues page.
 */
export function withCurrent(
  trend: QualityTrendPoint[],
  current?: CurrentIssues,
  exceptions?: { accepted: number; falsePositive: number },
): QualityTrendPoint[] {
  const last = trend[trend.length - 1];
  if (last === undefined || (current === undefined && exceptions === undefined)) {
    return trend;
  }
  const point = { ...last };
  if (current) {
    const { matrix } = current;
    point.openIssues = issueCount(matrix);
    point.vulnerabilities = issueCount(matrix, 'VULNERABILITY');
    point.blockerIssues = issueCount(matrix, undefined, 'BLOCKER');
    point.remediationEffortMinutes = current.effortMinutes;
    point.bySeverity = Object.fromEntries(
      SEVERITIES.map((severity) => [severity, issueCount(matrix, undefined, severity)]),
    );
    point.byType = Object.fromEntries(
      ['BUG', 'VULNERABILITY', 'CODE_SMELL', HOTSPOT].map((type) => [type, countOf(matrix, type)]),
    );
  }
  if (exceptions) {
    point.acceptedIssues = exceptions.accepted;
    point.falsePositiveIssues = exceptions.falsePositive;
  }
  return [...trend.slice(0, -1), point];
}

/**
 * The first point of the period, when it can be compared with the last one. Projects analyzed for the first time
 * during the period have nothing before: comparing with an empty start would show their whole backlog as new.
 */
export function periodStart(trend: QualityTrendPoint[]): QualityTrendPoint | undefined {
  const first = trend[0];
  return first !== undefined && first.projects > 0 ? first : undefined;
}

/**
 * The trend from the first point with an analysis on. A period longer than the organization's history would otherwise
 * draw mostly a flat line at zero.
 */
export function fromFirstAnalysis(trend: QualityTrendPoint[]): QualityTrendPoint[] {
  const start = trend.findIndex(({ projects }) => projects > 0);
  // keep two points at least, so a line can still be drawn
  return start <= 0 ? trend : trend.slice(Math.min(start, Math.max(trend.length - 2, 0)));
}

/** Buckets from the first one with any count on, for the same reason. */
export function fromFirstActivity<T extends { date: string }>(
  buckets: T[],
  hasActivity: (bucket: T) => boolean,
): T[] {
  const start = buckets.findIndex(hasActivity);
  return start <= 0 ? buckets : buckets.slice(Math.min(start, Math.max(buckets.length - 2, 0)));
}
