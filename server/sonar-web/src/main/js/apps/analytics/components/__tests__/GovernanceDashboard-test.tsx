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
import {
  getCustomRules,
  getExceptions,
  getIssueAudit,
  getIssueResolutions,
  getProjectIssues,
  getQualityTrend,
} from '../../../../api/analytics';
import { searchProjects } from '../../../../api/components';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import GovernanceDashboard from '../GovernanceDashboard';

jest.mock('../../../../api/analytics', () => ({
  ...jest.requireActual('../../../../api/analytics'),
  getCustomRules: jest.fn(),
  getExceptions: jest.fn(),
  getIssueAudit: jest.fn(),
  getIssueResolutions: jest.fn(),
  getProjectIssues: jest.fn(),
  getQualityTrend: jest.fn(),
  getScanComparison: jest.fn(),
}));
jest.mock('../../../../api/components', () => ({ searchProjects: jest.fn() }));
jest.mock('../../../../api/projectActivity', () => ({ getProjectActivity: jest.fn() }));

const period = { from: '2026-08-06', to: '2026-11-03', timeZone: 'UTC', interval: 'week' as const };
const paging = (total: number) => ({ pageIndex: 1, pageSize: 10, total });

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(searchProjects).mockResolvedValue({
    components: [],
    facets: [],
    organizations: [],
    paging: paging(0),
  });
  jest.mocked(getQualityTrend).mockResolvedValue({
    period,
    timeline: [
      {
        acceptedIssues: 10,
        blockerIssues: 4,
        bySeverity: { BLOCKER: 4, CRITICAL: 6, MAJOR: 50, MINOR: 30, INFO: 10 },
        byType: { BUG: 20, VULNERABILITY: 5, CODE_SMELL: 75, SECURITY_HOTSPOT: 8 },
        date: '2026-10-26',
        failed: 2,
        falsePositiveIssues: 3,
        openIssues: 100,
        passed: 8,
        projects: 10,
        remediationEffortMinutes: 600,
        vulnerabilities: 5,
      },
    ],
  });
  jest.mocked(getIssueResolutions).mockResolvedValue({
    completeFrom: '2026-10-04',
    period,
    retentionDays: 30,
    timeline: [
      {
        date: '2026-10-26',
        introduced: 5,
        fixed: 7,
        removed: 0,
        accepted: 1,
        falsePositive: 0,
        reopened: 0,
      },
    ],
    totals: {
      accepted: 1,
      falsePositive: 0,
      fixed: 7,
      fixedByAnalysis: 7,
      introduced: 5,
      netChange: -3,
      removed: 0,
      reopened: 0,
      resolved: 8,
    },
  });
  jest.mocked(getExceptions).mockResolvedValue({
    summary: {
      accepted: 10,
      expired: 1,
      expiringSoon: 2,
      falsePositive: 3,
      hotspotsToReview: 8,
      soonDays: 30,
      withoutExpiry: 6,
    },
    paging: paging(1),
    exceptions: [
      {
        comments: 2,
        expired: false,
        expiresAt: '2026-12-01T00:00:00+0000',
        filePath: 'classes/Payment.cls',
        issueKey: 'ISSUE-1',
        issueStatus: 'ACCEPTED',
        markedAt: '2026-09-01T00:00:00+0000',
        markedBy: { login: 'alice', name: 'Alice Martin' },
        projectKey: 'payments',
        projectName: 'Payments',
        rationale: {
          source: 'EXCEPTION_REASON',
          text: 'Validated by AppSec',
          user: { login: 'alice' },
        },
        rule: 'apex:ApexSOQLInjection',
        ruleName: 'SOQL injection',
        severity: 'CRITICAL',
        type: 'VULNERABILITY',
      },
    ],
  });
  jest.mocked(getProjectIssues).mockResolvedValue({
    projects: [
      {
        effortMinutes: 0,
        issues: {
          BUG: { BLOCKER: 2, MAJOR: 5 },
          VULNERABILITY: { BLOCKER: 1 },
          SECURITY_HOTSPOT: { CRITICAL: 4 },
        },
        key: 'payments',
      },
    ],
  });
  jest.mocked(getIssueAudit).mockResolvedValue({
    period,
    paging: paging(1),
    changes: [
      {
        action: 'REOPENED',
        date: '2026-10-30T10:00:00+0000',
        from: 'WONTFIX',
        id: 'C1',
        issueKey: 'ISSUE-2',
        projectKey: 'payments',
        projectName: 'Payments',
        rule: 'apex:ApexDoc',
        ruleName: 'Document public methods',
        user: { login: 'bob', name: 'Bob Lee' },
      },
    ],
    users: [{ login: 'bob', name: 'Bob Lee', total: 1, actions: { reopened: 1 } }],
  });
  jest.mocked(getCustomRules).mockResolvedValue({
    period,
    rules: [
      {
        closed: 1,
        dismissalRate: 0.5,
        introduced: 3,
        issues: {
          accepted: 4,
          falsePositive: 6,
          fixed: 2,
          open: 8,
          openBySeverity: { MAJOR: 8 },
          total: 20,
        },
        key: 'apex:no_hardcoded_ids',
        language: 'apex',
        name: 'No hard-coded IDs',
        template: 'apex:XPathTemplateRule',
        timeline: [{ closed: 1, date: '2026-10-26', introduced: 3 }],
      },
    ],
  });
});

it('shows policy and trends, the scan comparison and exception governance', async () => {
  renderComponent(<GovernanceDashboard organization="org" period={90} />);

  expect(
    within(
      await screen.findByRole('group', { name: 'analytics.governance.compliance.status' }),
    ).getByText('80%'),
  ).toBeInTheDocument();
  expect(screen.getByText('analytics.governance.scan.pick_project')).toBeInTheDocument();
  expect(await screen.findByText('Validated by AppSec')).toBeInTheDocument();
  expect(screen.getByText('Alice Martin (alice)')).toBeInTheDocument();
  // hotspots to review and expired exceptions
  expect(
    within(
      screen.getByRole('group', { name: 'analytics.governance.exceptions.awaiting_review' }),
    ).getByText('9'),
  ).toBeInTheDocument();
  expect(getExceptions).toHaveBeenCalledWith(
    expect.objectContaining({ statuses: 'ACCEPTED,FALSE_POSITIVE' }),
  );
});

it('keeps accepted issues apart from fixed ones', async () => {
  renderComponent(<GovernanceDashboard organization="org" period={90} />);

  const fixed = within(
    await screen.findByRole('group', { name: 'analytics.governance.compliance.fixed' }),
  );
  expect(fixed.getByText('7')).toBeInTheDocument();
  const dismissed = within(
    screen.getByRole('group', { name: 'analytics.governance.compliance.dismissed' }),
  );
  expect(dismissed.getByText('1')).toBeInTheDocument();
  expect(
    dismissed.getByText('analytics.governance.compliance.dismissed.footnote.1.0'),
  ).toBeInTheDocument();
  expect(
    within(
      screen.getByRole('group', { name: 'analytics.governance.compliance.introduced' }),
    ).getByText('analytics.quality.net_down.3'),
  ).toBeInTheDocument();
});

it('shows who changed findings and how the custom rules behave', async () => {
  renderComponent(<GovernanceDashboard organization="org" period={90} />);

  expect(await screen.findByText('Document public methods')).toBeInTheDocument();
  expect(screen.getAllByText('analytics.governance.audit.action.REOPENED')).toHaveLength(2);
  // in the table and in the legend of the trend chart
  expect(await screen.findAllByText('No hard-coded IDs')).toHaveLength(2);
  expect(screen.getByText('analytics.governance.rules.tune')).toBeInTheDocument();
  expect(screen.getByText('analytics.governance.rules.tuning_count.1')).toBeInTheDocument();
});
