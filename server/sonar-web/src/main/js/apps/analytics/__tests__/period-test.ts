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

import { QualityTrendPoint } from '../../../api/analytics';
import {
  DEFAULT_ANALYTICS_PERIOD,
  fromFirstActivity,
  fromFirstAnalysis,
  parsePeriod,
  periodStart,
  withCurrent,
} from '../period';

const point = (date: string, projects: number, openIssues = 0): QualityTrendPoint => ({
  acceptedIssues: 0,
  blockerIssues: 0,
  date,
  failed: 0,
  falsePositiveIssues: 0,
  openIssues,
  passed: projects,
  projects,
  remediationEffortMinutes: 0,
  vulnerabilities: 0,
});

it('reads the period from the URL', () => {
  expect(parsePeriod('90')).toBe(90);
  expect(parsePeriod('45')).toBe(DEFAULT_ANALYTICS_PERIOD);
  expect(parsePeriod(null)).toBe(DEFAULT_ANALYTICS_PERIOD);
});

it('sets the last point of the trend to the issues as they are now', () => {
  const trend = [point('2026-10-01', 2, 100), point('2026-10-08', 2, 120)];
  const current = withCurrent(
    trend,
    {
      effortMinutes: 90,
      matrix: {
        BUG: { BLOCKER: 1, MAJOR: 4 },
        VULNERABILITY: { CRITICAL: 3 },
        SECURITY_HOTSPOT: { MAJOR: 5 },
      },
    },
    { accepted: 7, falsePositive: 2 },
  );

  expect(current[0]).toBe(trend[0]);
  expect(current[1]).toEqual(
    expect.objectContaining({
      acceptedIssues: 7,
      blockerIssues: 1,
      falsePositiveIssues: 2,
      // hotspots are not issues
      openIssues: 8,
      remediationEffortMinutes: 90,
      vulnerabilities: 3,
      bySeverity: { BLOCKER: 1, CRITICAL: 3, MAJOR: 4, MINOR: 0, INFO: 0 },
      byType: { BUG: 5, VULNERABILITY: 3, CODE_SMELL: 0, SECURITY_HOTSPOT: 5 },
    }),
  );
  expect(withCurrent(trend)).toBe(trend);
  expect(withCurrent([], { effortMinutes: 0, matrix: {} })).toEqual([]);
});

it('only compares with a period start that had analyses', () => {
  expect(periodStart([point('2026-10-01', 2), point('2026-10-08', 3)])?.date).toBe('2026-10-01');
  // projects analyzed for the first time during the period
  expect(periodStart([point('2026-10-01', 0), point('2026-10-08', 3)])).toBeUndefined();
  expect(periodStart([])).toBeUndefined();
});

it('starts charts at the first analysis, keeping two points at least', () => {
  const trend = [point('a', 0), point('b', 0), point('c', 1), point('d', 2)];
  expect(fromFirstAnalysis(trend).map(({ date }) => date)).toEqual(['c', 'd']);
  expect(
    fromFirstAnalysis([point('a', 0), point('b', 0), point('c', 1)]).map(({ date }) => date),
  ).toEqual(['b', 'c']);
  expect(fromFirstAnalysis([point('a', 0), point('b', 0)])).toHaveLength(2);
  expect(
    fromFirstActivity(
      [
        { date: 'a', count: 0 },
        { date: 'b', count: 0 },
        { date: 'c', count: 4 },
        { date: 'd', count: 0 },
      ],
      ({ count }) => count > 0,
    ).map(({ date }) => date),
  ).toEqual(['c', 'd']);
});
