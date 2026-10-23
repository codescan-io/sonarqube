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

import { QualityTrendPoint, SecurityRemediation } from '../../../api/analytics';
import { BriefData, buildInsights, computeOrganizationHealth, formatDays } from '../brief';
import { AnalysisStatus } from '../utils';

const DAY = 24 * 60 * 60 * 1000;
const now = new Date('2026-10-14T12:00:00Z');

const point = (overrides: Partial<QualityTrendPoint>): QualityTrendPoint => ({
  blockerIssues: 0,
  date: '2026-10-01',
  failed: 0,
  openIssues: 0,
  passed: 0,
  projects: 10,
  remediationEffortMinutes: 0,
  vulnerabilities: 0,
  ...overrides,
});

const security = (
  overrides: Partial<SecurityRemediation['totals']> = {},
  previous: Partial<SecurityRemediation['previous']> = {},
): SecurityRemediation => ({
  completeFrom: '2026-09-14',
  period: { from: '2026-09-15', to: '2026-10-14', timeZone: 'UTC', interval: 'day' },
  previous: {
    complete: true,
    fixed: 10,
    fixedWithinSla: 5,
    from: '2026-08-16',
    medianTimeToFixMs: 20 * DAY,
    to: '2026-09-14',
    ...previous,
  },
  retentionDays: 30,
  severities: [],
  timeline: [],
  totals: {
    fixed: 10,
    fixedWithinSla: 8,
    medianTimeToFixMs: 10 * DAY,
    open: 20,
    openPastSla: 0,
    ...overrides,
  },
});

const data: BriefData = {
  trend: [
    point({
      passed: 6,
      failed: 4,
      openIssues: 1000,
      vulnerabilities: 50,
      remediationEffortMinutes: 60000,
    }),
    point({
      passed: 8,
      failed: 2,
      openIssues: 800,
      vulnerabilities: 40,
      remediationEffortMinutes: 48000,
    }),
  ],
  security: security({ openPastSla: 3 }),
  resolutions: {
    completeFrom: '2026-09-14',
    period: { from: '2026-09-15', to: '2026-10-14', timeZone: 'UTC', interval: 'day' },
    retentionDays: 30,
    timeline: [],
    totals: {
      accepted: 0,
      falsePositive: 0,
      fixed: 300,
      fixedByAnalysis: 300,
      introduced: 100,
      netChange: -200,
      removed: 0,
      reopened: 0,
      resolved: 300,
    },
  },
  ai: {
    codefixJobs: {},
    credits: [],
    days: 30,
    fixJobs: {
      effortSavedMinutes: 600,
      failed: 0,
      inProgress: 0,
      issuesResolved: 12,
      issuesSelected: 12,
      issuesWithoutKnownEffort: 0,
      merged: 2,
      pullRequests: 2,
      succeeded: 3,
      total: 3,
    },
    totalCredits: 10,
  },
  qualityGateChanges: [
    { date: 1, projectKey: 'a', projectName: 'Alpha', status: 'Failed' },
    { date: 2, projectKey: 'b', status: 'Red (was Green)' },
    { date: 3, projectKey: 'c', projectName: 'Gamma', status: 'Passed' },
  ],
};

describe('computeOrganizationHealth', () => {
  it('weighs quality gates, security SLA, velocity, reliability and freshness', () => {
    const health = computeOrganizationHealth(
      data,
      [
        {
          key: 'a',
          name: 'a',
          status: AnalysisStatus.Passed,
          tags: [],
          analysisDate: '2026-10-13T00:00:00+0000',
        },
        {
          key: 'b',
          name: 'b',
          status: AnalysisStatus.Passed,
          tags: [],
          analysisDate: '2026-01-01T00:00:00+0000',
        },
      ],
      now,
    );

    expect(health.components.map(({ key, value }) => [key, value])).toEqual([
      ['qualityGates', 80],
      ['security', 85],
      ['velocity', 100],
      ['reliability', undefined],
      ['freshness', 50],
    ]);
    // (80 x 30 + 85 x 25 + 100 x 15 + 50 x 15) / 85
    expect(health.score).toBe(80);
    expect(health.grade).toBe('B');
  });
});

describe('buildInsights', () => {
  it('writes the significant moves of the period, most significant first', () => {
    const insights = buildInsights(data);

    // weights: backlog 100, past SLA 53, time to fix 50, gate failures 50, pass rate 40, vulnerabilities 30, AI 30
    expect(insights.map(({ key }) => key)).toEqual([
      'backlog',
      'past_sla',
      'mttr',
      'gate_failures',
      'pass_rate',
      'vulnerabilities',
    ]);
    expect(insights.find(({ key }) => key === 'gate_failures')).toMatchObject({
      tone: 'bad',
      text: 'analytics.brief.insight.gate_failures.2.Alpha, b',
    });
    expect(insights.find(({ key }) => key === 'pass_rate')).toMatchObject({
      tone: 'good',
      text: 'analytics.brief.insight.pass_rate_up.20.80',
    });
  });

  it('reports the engineering time AI saved', () => {
    const insights = buildInsights(data, 20);

    expect(insights.find(({ key }) => key === 'ai')).toMatchObject({
      tone: 'good',
      text: 'analytics.brief.insight.ai.12.10 h',
    });
  });

  it('keeps the moves under the limit, and leaves out what did not move', () => {
    const flat: BriefData = {
      trend: [point({ passed: 5, failed: 5 }), point({ passed: 5, failed: 5 })],
    };

    expect(buildInsights(flat)).toEqual([]);
    expect(buildInsights(data, 2)).toHaveLength(2);
  });

  it('does not compare the time to fix with an incomplete previous period', () => {
    const insights = buildInsights({ ...data, security: security({}, { complete: false }) }, 20);

    expect(insights.map(({ key }) => key)).not.toContain('mttr');
    expect(insights.map(({ key }) => key)).toContain('within_sla');
  });

  it('reports a growing remediation backlog in engineering time', () => {
    const insights = buildInsights({
      trend: [
        point({ remediationEffortMinutes: 6000 }),
        point({ remediationEffortMinutes: 12000 }),
      ],
    });

    expect(insights).toEqual([
      expect.objectContaining({
        key: 'remediation',
        tone: 'bad',
        text: 'analytics.brief.insight.remediation_up.100 h',
      }),
    ]);
  });
});

it('formats durations in days', () => {
  expect(formatDays(DAY / 2)).toBe('analytics.brief.less_than_a_day');
  expect(formatDays(DAY)).toBe('analytics.brief.day.1');
  expect(formatDays(12.4 * DAY)).toBe('analytics.brief.days.12');
});
