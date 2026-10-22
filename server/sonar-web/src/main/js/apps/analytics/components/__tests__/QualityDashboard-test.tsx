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

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  getExpiringExceptions,
  getIssueResolutions,
  getQualityTrend,
  getSecurityRemediation,
} from '../../../../api/analytics';
import { getOrganizationHealth } from '../../../../api/codescan-analytics';
import { searchIssues } from '../../../../api/issues';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import QualityDashboard from '../QualityDashboard';

jest.mock('../../../../api/analytics', () => ({
  getExpiringExceptions: jest.fn(),
  getIssueResolutions: jest.fn(),
  getQualityTrend: jest.fn(),
  getSecurityRemediation: jest.fn(),
}));

const DAY = 24 * 60 * 60 * 1000;
jest.mock('../../../../api/issues', () => ({ searchIssues: jest.fn() }));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));
jest.mock('../../../../api/codescan-analytics', () => ({ getOrganizationHealth: jest.fn() }));

const issues = (facets: Record<string, Array<{ count: number; val: string }>>, extra = {}) => ({
  components: [],
  effortTotal: 0,
  facets: Object.entries(facets).map(([property, values]) => ({ property, values })),
  issues: [],
  languages: [],
  paging: { pageIndex: 1, pageSize: 1, total: 0 },
  ...extra,
});

const resolutions = {
  period: { from: '2026-07-17', to: '2026-10-14', timeZone: 'UTC', interval: 'week' as const },
  retentionDays: 30,
  completeFrom: '2026-09-14',
  totals: {
    introduced: 50,
    fixed: 80,
    removed: 2,
    accepted: 6,
    falsePositive: 3,
    reopened: 1,
    resolved: 91,
    fixedByAnalysis: 78,
    netChange: -40,
  },
  timeline: [
    {
      date: '2026-09-28',
      introduced: 20,
      fixed: 30,
      removed: 0,
      accepted: 2,
      falsePositive: 1,
      reopened: 0,
    },
    {
      date: '2026-10-05',
      introduced: 30,
      fixed: 50,
      removed: 2,
      accepted: 4,
      falsePositive: 2,
      reopened: 1,
    },
  ],
};

beforeEach(() => {
  jest.mocked(getIssueResolutions).mockResolvedValue(resolutions);
  jest.mocked(getOrganizationHealth).mockRejectedValue(new Error('not deployed'));
  jest.mocked(getQualityTrend).mockResolvedValue({
    period: { from: '2026-07-17', to: '2026-10-14', timeZone: 'UTC', interval: 'week' },
    timeline: [
      {
        date: '2026-09-28',
        projects: 10,
        passed: 6,
        failed: 4,
        openIssues: 1200,
        vulnerabilities: 30,
        blockerIssues: 8,
        remediationEffortMinutes: 6000,
      },
      {
        date: '2026-10-05',
        projects: 10,
        passed: 8,
        failed: 2,
        openIssues: 900,
        vulnerabilities: 20,
        blockerIssues: 5,
        remediationEffortMinutes: 4800,
      },
    ],
  });
  jest.mocked(getSecurityRemediation).mockResolvedValue({
    period: { from: '2026-07-17', to: '2026-10-14', timeZone: 'UTC', interval: 'week' },
    retentionDays: 30,
    completeFrom: '2026-09-14',
    totals: {
      fixed: 40,
      fixedWithinSla: 30,
      medianTimeToFixMs: 10 * DAY,
      open: 25,
      openPastSla: 4,
    },
    previous: {
      from: '2026-04-18',
      to: '2026-07-16',
      complete: true,
      fixed: 20,
      fixedWithinSla: 10,
      medianTimeToFixMs: 20 * DAY,
    },
    severities: [
      {
        severity: 'HIGH',
        slaDays: 30,
        fixed: 40,
        fixedWithinSla: 30,
        medianTimeToFixMs: 10 * DAY,
        open: 25,
        openPastSla: 4,
        oldestOpenAgeMs: 90 * DAY,
      },
    ],
    timeline: [{ date: '2026-09-28', fixed: 40, fixedWithinSla: 30, medianTimeToFixMs: 10 * DAY }],
  });
  jest.mocked(getExpiringExceptions).mockResolvedValue({
    paging: { pageIndex: 1, pageSize: 10, total: 1 },
    exceptions: [
      {
        issueKey: 'ISSUE-1',
        issueStatus: 'ACCEPTED',
        expiresAt: '2026-10-20T00:00:00+0000',
        expired: false,
        rule: 'apex:AvoidSoqlInLoops',
        ruleName: 'SOQL queries should not be in loops',
        projectKey: 'force-app',
        projectName: 'Force App',
        filePath: 'classes/AccountService.cls',
      },
    ],
  });
  jest.mocked(searchIssues).mockImplementation(((query: Record<string, string>) => {
    switch (query.facets) {
      case 'createdAt':
        return Promise.resolve(issues({ createdAt: [{ val: '2026-10-05', count: 42 }] }));
      case 'rules,languages':
        return Promise.resolve(
          issues(
            {
              rules: [{ val: 'apex:AvoidSoqlInLoops', count: 120 }],
              languages: [{ val: 'apex', count: 800 }],
            },
            {
              rules: [
                { key: 'apex:AvoidSoqlInLoops', name: 'SOQL queries should not be in loops' },
              ],
              languages: [{ key: 'apex', name: 'Apex' }],
            },
          ),
        );
      case 'issueStatuses':
        return Promise.resolve(
          issues({
            issueStatuses: [
              { val: 'OPEN', count: 900 },
              { val: 'ACCEPTED', count: 7 },
              { val: 'FALSE_POSITIVE', count: 3 },
              { val: 'FIXED', count: 120 },
            ],
          }),
        );
      default:
        // security issues by age: 2 high severity issues in every bucket
        return Promise.resolve(issues({ impactSeverities: [{ val: 'HIGH', count: 2 }] }));
    }
  }) as typeof searchIssues);
});

it('shows quality headline figures', async () => {
  renderComponent(<QualityDashboard organization="my-org" />);

  const passRate = within(
    await screen.findByRole('group', { name: 'analytics.quality.pass_rate' }),
  );
  expect(passRate.getByText('80%')).toBeInTheDocument();
  expect(passRate.getByText('+20 pts')).toBeInTheDocument();
  const open = within(screen.getByRole('button', { name: /^analytics\.quality\.open_issues/ }));
  expect(open.getByText('900')).toBeInTheDocument();
  expect(open.getByText('-25%')).toBeInTheDocument();
  // 3 buckets past the SLA x 2 high severity issues
  expect(
    within(screen.getByRole('button', { name: /^analytics\.quality\.past_sla/ })).getByText('6'),
  ).toBeInTheDocument();
  expect(
    within(
      screen.getByRole('button', { name: /^analytics\.quality\.expiring_exceptions/ }),
    ).getByText('analytics.quality.exceptions_footnote.7.3'),
  ).toBeInTheDocument();
});

it('shows rules, technologies, security ages and expiring exceptions', async () => {
  renderComponent(<QualityDashboard organization="my-org" />);

  expect(await screen.findAllByText('SOQL queries should not be in loops')).toHaveLength(2);
  expect(screen.getByText('Apex')).toBeInTheDocument();
  expect(
    screen.getByRole('link', { name: 'severity_impact.HIGH, analytics.quality.age.gt180: 2' }),
  ).toHaveAttribute('href', expect.stringContaining('impactSeverities=HIGH'));
  expect(screen.getByRole('link', { name: 'Force App' })).toHaveAttribute(
    'href',
    expect.stringContaining('open=ISSUE-1'),
  );
});

it('reloads for another period', async () => {
  const user = userEvent.setup();
  renderComponent(<QualityDashboard organization="my-org" />);
  await screen.findByRole('group', { name: 'analytics.quality.pass_rate' });

  await user.click(screen.getByRole('radio', { name: 'analytics.quality.period.30' }));

  expect(getQualityTrend).toHaveBeenLastCalledWith(expect.objectContaining({ interval: 'day' }));
});

it('shows an error when loading fails', async () => {
  jest.mocked(getQualityTrend).mockRejectedValue(new Error('boom'));
  renderComponent(<QualityDashboard organization="my-org" />);

  expect(await screen.findByText('analytics.load_error')).toBeInTheDocument();
});

it('shows the top root causes from debt analysis when there are some', async () => {
  jest.mocked(getOrganizationHealth).mockResolvedValue({
    rootCauses: [
      {
        title: 'SOQL inside loops',
        severity: 'HIGH',
        issueCount: 42,
        effortMinutes: 300,
        projectKey: 'force-app',
      },
    ],
  } as never);
  renderComponent(<QualityDashboard organization="my-org" />);

  expect(await screen.findByText('SOQL inside loops')).toBeInTheDocument();
  expect(screen.getByText('analytics.quality.hours.5')).toBeInTheDocument();
});

it('hides root causes when CodeScan services do not answer', async () => {
  renderComponent(<QualityDashboard organization="my-org" />);
  await screen.findByRole('group', { name: 'analytics.quality.pass_rate' });

  expect(screen.queryByText('analytics.quality.root_causes')).not.toBeInTheDocument();
});

it('shows how many issues were introduced and resolved, and how', async () => {
  renderComponent(<QualityDashboard organization="my-org" />);

  const resolved = within(
    await screen.findByRole('button', { name: /^analytics\.quality\.resolved/ }),
  );
  expect(resolved.getByText('91')).toBeInTheDocument();
  expect(resolved.getByText('analytics.quality.resolved_footnote.50')).toBeInTheDocument();
  expect(resolved.getByText('-40')).toBeInTheDocument();

  await userEvent
    .setup()
    .click(screen.getByRole('button', { name: /^analytics\.quality\.resolved/ }));
  expect(mockNavigate).toHaveBeenCalledWith(
    expect.objectContaining({
      search: expect.stringMatching(
        /issueStatuses=FIXED(,|%2C)ACCEPTED(,|%2C)FALSE_POSITIVE$/,
      ),
    }),
  );
  expect(screen.getByText('analytics.quality.net_down.40')).toBeInTheDocument();
  expect(
    screen.getByText('analytics.quality.fixed_by_analysis.78.80 · analytics.quality.reopened.1'),
  ).toBeInTheDocument();
  // the period starts before the closed issues retention
  expect(screen.getByText('analytics.quality.retention_note.2026-09-14.30')).toBeInTheDocument();
  expect(screen.getByText('issue.issue_status.FIXED')).toBeInTheDocument();
  expect(getIssueResolutions).toHaveBeenCalledWith(expect.objectContaining({ interval: 'week' }));
});

it('shows how fast security issues get fixed against their SLA', async () => {
  renderComponent(<QualityDashboard organization="my-org" />);

  const mttr = within(await screen.findByRole('group', { name: 'analytics.security.mttr' }));
  expect(mttr.getByText('analytics.brief.days.10')).toBeInTheDocument();
  // twice as fast as the previous period
  expect(mttr.getByText('-50%')).toBeInTheDocument();
  const compliance = within(screen.getByRole('group', { name: 'analytics.security.compliance' }));
  expect(compliance.getByText('75%')).toBeInTheDocument();
  expect(compliance.getByText('+25 pts')).toBeInTheDocument();
  expect(
    within(screen.getByRole('group', { name: 'analytics.security.open_past_sla' })).getByText('4'),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '25' })).toHaveAttribute(
    'href',
    expect.stringContaining('impactSoftwareQualities=SECURITY'),
  );
  // the period starts before the closed issues retention
  expect(screen.getByText(/^analytics\.security\.retention_note\.30/)).toBeInTheDocument();
});

it('loads more expiring exceptions on demand', async () => {
  const user = userEvent.setup();
  jest.mocked(getExpiringExceptions).mockImplementation(({ p = 1 }) =>
    Promise.resolve({
      paging: { pageIndex: p, pageSize: 10, total: 2 },
      exceptions: [
        {
          issueKey: `ISSUE-${p}`,
          issueStatus: 'ACCEPTED',
          expiresAt: '2026-10-20T00:00:00+0000',
          expired: false,
          rule: 'apex:Rule',
          projectKey: `project-${p}`,
          projectName: `Project ${p}`,
        },
      ],
    }),
  );
  renderComponent(<QualityDashboard organization="my-org" />);
  await screen.findByRole('link', { name: 'Project 1' });

  await user.click(screen.getByRole('button', { name: 'show_more' }));

  expect(await screen.findByRole('link', { name: 'Project 2' })).toBeInTheDocument();
  expect(getExpiringExceptions).toHaveBeenLastCalledWith(expect.objectContaining({ p: 2 }));
  expect(screen.queryByRole('button', { name: 'show_more' })).not.toBeInTheDocument();
});
