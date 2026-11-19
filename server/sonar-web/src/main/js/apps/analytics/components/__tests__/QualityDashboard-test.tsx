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
  getProjectIssues,
  getQualityTrend,
  getSecurityRemediation,
} from '../../../../api/analytics';
import { getOrganizationHealth } from '../../../../api/codescan-analytics';
import { searchIssues } from '../../../../api/issues';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import QualityDashboard from '../QualityDashboard';

jest.mock('../../../../api/analytics', () => ({
  getProjectIssues: jest.fn(),
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

beforeEach(() => {
  jest.mocked(getProjectIssues).mockResolvedValue({
    projects: [
      {
        key: 'force-app',
        effortMinutes: 4800,
        issues: {
          BUG: { BLOCKER: 2, MAJOR: 98 },
          VULNERABILITY: { CRITICAL: 3, MAJOR: 7 },
          CODE_SMELL: { MINOR: 690 },
          SECURITY_HOTSPOT: { MAJOR: 4 },
        },
      },
    ],
  });
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

it('shows the open findings as they are now', async () => {
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

  // the trend says 900 at the latest analyses; the issues now are 800
  const open = within(
    await screen.findByRole('button', { name: /^analytics\.quality\.open_issues/ }),
  );
  expect(open.getByText('800')).toBeInTheDocument();
  expect(open.getByText('-33%')).toBeInTheDocument();
  expect(
    within(screen.getByRole('button', { name: /^analytics\.quality\.vulnerabilities/ })).getByText(
      '10',
    ),
  ).toBeInTheDocument();
  // blocker bugs and critical vulnerabilities
  expect(
    within(screen.getByRole('button', { name: /^analytics\.risk\.high_severity/ })).getByText('5'),
  ).toBeInTheDocument();
  expect(
    within(screen.getByRole('group', { name: 'analytics.risk.hotspots' })).getByText('4'),
  ).toBeInTheDocument();
  expect(getProjectIssues).toHaveBeenCalledWith('my-org', true);
});

it('breaks findings down by severity, then by type, and the other way round', async () => {
  const user = userEvent.setup();
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

  // the first severity with findings is broken down until another one is picked
  expect(
    await screen.findByRole('heading', {
      name: 'analytics.governance.security.breakdown_of.severity.BLOCKER',
    }),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /^severity\.MAJOR/ }));
  expect(
    screen.getByRole('heading', {
      name: 'analytics.governance.security.breakdown_of.severity.MAJOR',
    }),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('radio', { name: 'analytics.governance.security.by_type' }));
  await user.click(screen.getByRole('button', { name: /^issue\.type\.SECURITY_HOTSPOT\.plural/ }));
  expect(
    screen.getByRole('heading', {
      name: 'analytics.governance.security.breakdown_of.issue.type.SECURITY_HOTSPOT.plural',
    }),
  ).toBeInTheDocument();
});

it('shows rules, technologies and security ages', async () => {
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

  expect(await screen.findByText('SOQL queries should not be in loops')).toBeInTheDocument();
  expect(screen.getByText('Apex')).toBeInTheDocument();
  expect(
    screen.getByRole('link', { name: 'severity_impact.HIGH, analytics.quality.age.gt180: 2' }),
  ).toHaveAttribute('href', expect.stringContaining('impactSeverities=HIGH'));
});

it('loads the period it is given', async () => {
  renderComponent(<QualityDashboard organization="my-org" period={90} />);
  await screen.findByRole('group', { name: 'analytics.risk.hotspots' });

  expect(getQualityTrend).toHaveBeenLastCalledWith(expect.objectContaining({ interval: 'week' }));
});

it('shows an error when loading fails', async () => {
  jest.mocked(getQualityTrend).mockRejectedValue(new Error('boom'));
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

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
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

  expect(await screen.findByText('SOQL inside loops')).toBeInTheDocument();
  expect(screen.getByText('analytics.quality.hours.5')).toBeInTheDocument();
});

it('hides root causes when CodeScan services do not answer', async () => {
  renderComponent(<QualityDashboard organization="my-org" period={30} />);
  await screen.findByRole('group', { name: 'analytics.risk.hotspots' });

  expect(screen.queryByText('analytics.quality.root_causes')).not.toBeInTheDocument();
});

it('shows how fast security issues get fixed against their SLA', async () => {
  renderComponent(<QualityDashboard organization="my-org" period={30} />);

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
