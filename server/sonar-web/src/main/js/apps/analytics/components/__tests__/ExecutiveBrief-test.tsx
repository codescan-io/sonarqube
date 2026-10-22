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
  getIssueResolutions,
  getOrganizationActivity,
  getProjectIssues,
  getProjectQualityGates,
  getProjectTaskStatuses,
  getQualityTrend,
  getSecurityRemediation,
} from '../../../../api/analytics';
import { getAiValue, getOrganizationHealth } from '../../../../api/codescan-analytics';
import { searchProjects } from '../../../../api/components';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import ExecutiveBrief from '../ExecutiveBrief';

jest.mock('../../../../api/analytics', () => ({
  getIssueResolutions: jest.fn(),
  getOrganizationActivity: jest.fn(),
  getProjectIssues: jest.fn(),
  getProjectQualityGates: jest.fn(),
  getProjectTaskStatuses: jest.fn(),
  getQualityTrend: jest.fn(),
  getSecurityRemediation: jest.fn(),
}));
jest.mock('../../../../api/codescan-analytics', () => ({
  getAiValue: jest.fn(),
  getOrganizationHealth: jest.fn(),
}));
jest.mock('../../../../api/components', () => ({ searchProjects: jest.fn() }));

const DAY = 24 * 60 * 60 * 1000;
const recent = new Date(Date.now() - DAY).toISOString();
const period = { from: '2026-09-15', to: '2026-10-14', timeZone: 'UTC', interval: 'day' as const };
const stats = (success: number, failed: number) => ({
  canceled: 0,
  failed,
  projects: 2,
  success,
  total: success + failed,
});

beforeEach(() => {
  jest.mocked(getQualityTrend).mockResolvedValue({
    period,
    timeline: [
      {
        date: '2026-09-15',
        projects: 2,
        passed: 1,
        failed: 1,
        openIssues: 100,
        vulnerabilities: 10,
        blockerIssues: 1,
        remediationEffortMinutes: 6000,
      },
      {
        date: '2026-10-14',
        projects: 2,
        passed: 2,
        failed: 0,
        openIssues: 80,
        vulnerabilities: 5,
        blockerIssues: 0,
        remediationEffortMinutes: 4800,
      },
    ],
  });
  jest.mocked(getIssueResolutions).mockResolvedValue({
    completeFrom: '2026-09-14',
    period,
    retentionDays: 30,
    timeline: [],
    totals: {
      accepted: 0,
      falsePositive: 0,
      fixed: 40,
      fixedByAnalysis: 40,
      introduced: 20,
      netChange: -20,
      removed: 0,
      reopened: 0,
      resolved: 40,
    },
  });
  jest.mocked(getSecurityRemediation).mockResolvedValue({
    completeFrom: '2026-09-14',
    period,
    previous: {
      complete: true,
      fixed: 4,
      fixedWithinSla: 2,
      from: '2026-08-16',
      medianTimeToFixMs: 20 * DAY,
      to: '2026-09-14',
    },
    retentionDays: 30,
    severities: [],
    timeline: [],
    totals: { fixed: 5, fixedWithinSla: 4, medianTimeToFixMs: 10 * DAY, open: 6, openPastSla: 2 },
  });
  jest.mocked(getOrganizationActivity).mockResolvedValue({
    analyses: stats(95, 5),
    period,
    previous: { analyses: stats(80, 20), from: '2026-08-16', to: '2026-09-14' },
    targets: [],
    taskTypes: [],
    timeline: [],
    totalProjects: 2,
  } as never);
  jest.mocked(getAiValue).mockResolvedValue({
    codefixJobs: {},
    credits: [],
    days: 30,
    fixJobs: {
      effortSavedMinutes: 1200,
      failed: 0,
      inProgress: 0,
      issuesResolved: 9,
      issuesSelected: 9,
      issuesWithoutKnownEffort: 0,
      merged: 1,
      pullRequests: 1,
      succeeded: 2,
      total: 2,
    },
    totalCredits: 4,
  });
  jest.mocked(getOrganizationHealth).mockResolvedValue({
    lowest: [],
    movers: [],
    projectsScored: 0,
    qualityGateChanges: [
      { date: 1, projectKey: 'web', projectName: 'Web Portal', status: 'Failed' },
    ],
    rootCauses: [],
    trend: [],
  });
  jest.mocked(searchProjects).mockResolvedValue({
    components: [
      { key: 'api', name: 'Payments API', tags: ['payments'], analysisDate: recent },
      { key: 'web', name: 'Web Portal', tags: ['web'], analysisDate: recent },
    ],
    facets: [],
    organizations: [],
    paging: { pageIndex: 1, pageSize: 500, total: 2 },
  } as never);
  jest.mocked(getProjectQualityGates).mockResolvedValue({
    projects: [
      { key: 'api', status: 'OK' },
      { key: 'web', status: 'ERROR' },
    ],
  });
  jest.mocked(getProjectIssues).mockResolvedValue({
    projects: [{ key: 'web', effortMinutes: 600, issues: { VULNERABILITY: { CRITICAL: 2 } } }],
  });
  jest.mocked(getProjectTaskStatuses).mockResolvedValue({ projects: [] });
});

it('grades the organization and writes what changed', async () => {
  renderComponent(<ExecutiveBrief organization="acme" organizationName="Acme" />);

  expect(await screen.findByText('analytics.health.title')).toBeInTheDocument();
  // gates 100, security 67, velocity 100, reliability 95, freshness 100
  expect(
    screen.getByRole('img', { name: 'analytics.health.grade_label.A.91' }),
  ).toBeInTheDocument();
  expect(screen.getByText('analytics.brief.insight.past_sla.2.6')).toBeInTheDocument();
  expect(
    screen.getByText('analytics.brief.insight.gate_failures.1.Web Portal'),
  ).toBeInTheDocument();
  expect(
    screen.getByText(
      'analytics.brief.insight.mttr_faster.analytics.brief.days.10.analytics.brief.days.20',
    ),
  ).toBeInTheDocument();

  const ai = within(screen.getByRole('group', { name: 'analytics.roi.ai_saved' }));
  expect(ai.getByText('20 h')).toBeInTheDocument();
  expect(ai.getByText('analytics.roi.ai_footnote.9')).toBeInTheDocument();
});

it('lists where to act and the leading and lagging teams', async () => {
  renderComponent(<ExecutiveBrief organization="acme" organizationName="Acme" />);

  // failing quality gate and 2 critical vulnerabilities
  expect(await screen.findByText('Web Portal')).toBeInTheDocument();
  expect(
    await screen.findByText(
      'analytics.focus.reason.quality_gate_failed · analytics.focus.reason.vulnerabilities.2',
    ),
  ).toBeInTheDocument();
  expect(screen.getByText('analytics.brief.teams.leading')).toBeInTheDocument();
  expect(screen.getByText('payments')).toBeInTheDocument();
});

it('still writes the brief when CodeScan services do not answer', async () => {
  jest.mocked(getAiValue).mockRejectedValue(new Error('not deployed'));
  jest.mocked(getOrganizationHealth).mockRejectedValue(new Error('not deployed'));
  renderComponent(<ExecutiveBrief organization="acme" organizationName="Acme" />);

  expect(await screen.findByText('analytics.brief.insight.past_sla.2.6')).toBeInTheDocument();
  expect(screen.getByText('analytics.roi.ai_unavailable')).toBeInTheDocument();
  expect(screen.queryByText(/gate_failures/)).not.toBeInTheDocument();
});

it('copies the brief as text and prints it', async () => {
  const user = userEvent.setup();
  const writeText = jest.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
  const print = jest.spyOn(window, 'print').mockImplementation(() => undefined);
  renderComponent(<ExecutiveBrief organization="acme" organizationName="Acme" />);
  await screen.findByText('analytics.health.title');

  await user.click(screen.getByRole('button', { name: 'analytics.brief.copy' }));
  expect(writeText).toHaveBeenCalledWith(
    expect.stringContaining('analytics.brief.text.title.Acme'),
  );
  expect(await screen.findByRole('button', { name: 'analytics.brief.copied' })).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'analytics.brief.pdf' }));
  expect(print).toHaveBeenCalled();
});

it('reloads for another period', async () => {
  const user = userEvent.setup();
  renderComponent(<ExecutiveBrief organization="acme" organizationName="Acme" />);
  await screen.findByText('analytics.health.title');

  await user.click(screen.getByRole('radio', { name: 'analytics.quality.period.90' }));

  expect(getQualityTrend).toHaveBeenLastCalledWith(expect.objectContaining({ interval: 'week' }));
  expect(getAiValue).toHaveBeenLastCalledWith('acme', 90);
});
