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
import { getQualityTrend } from '../../../../api/analytics';
import { getAiValue, getIdeAdoption } from '../../../../api/codescan-analytics';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import ValueDashboard from '../ValueDashboard';

jest.mock('../../../../api/analytics', () => ({ getQualityTrend: jest.fn() }));

jest.mock('../../../../api/codescan-analytics', () => ({
  getAiValue: jest.fn(),
  getIdeAdoption: jest.fn(),
}));

beforeEach(() => {
  jest.mocked(getAiValue).mockResolvedValue({
    days: 90,
    fixJobs: {
      total: 7,
      succeeded: 4,
      failed: 2,
      inProgress: 1,
      issuesSelected: 49,
      issuesResolved: 30,
      pullRequests: 4,
      merged: 3,
      effortSavedMinutes: 600,
      issuesWithoutKnownEffort: 0,
    },
    codefixJobs: {},
    credits: [
      { addon: 'ai-codefix', calls: 10, credits: 12.5 },
      { addon: 'ai-custom-rules', calls: 4, credits: 2 },
    ],
    totalCredits: 14.5,
  });
  jest.mocked(getQualityTrend).mockResolvedValue({
    period: { from: '2026-07-17', to: '2026-10-14', timeZone: 'UTC', interval: 'week' },
    timeline: [
      {
        date: '2026-09-28',
        projects: 2,
        passed: 1,
        failed: 1,
        openIssues: 10,
        vulnerabilities: 1,
        blockerIssues: 0,
        remediationEffortMinutes: 12000,
      },
      {
        date: '2026-10-05',
        projects: 2,
        passed: 2,
        failed: 0,
        openIssues: 8,
        vulnerabilities: 0,
        blockerIssues: 0,
        remediationEffortMinutes: 9000,
      },
    ],
  });
  jest.mocked(getIdeAdoption).mockResolvedValue({
    days: 90,
    activeUsers: 12,
    members: 40,
    byIde: [{ ideType: 'VSCode', users: 9, sessions: 120, lines: 50000 }],
  });
});

function tile(label: string) {
  return within(screen.getByRole('group', { name: label }));
}

it('shows what the AI add-ons and IDE plugins delivered', async () => {
  renderComponent(<ValueDashboard organization="my-org" />);

  await screen.findByRole('group', { name: 'analytics.value.issues_resolved' });
  expect(tile('analytics.value.issues_resolved').getByText('30')).toBeInTheDocument();
  expect(
    tile('analytics.value.issues_resolved').getByText('analytics.value.fix_jobs_footnote.4.7'),
  ).toBeInTheDocument();
  expect(tile('analytics.value.merged').getByText('3')).toBeInTheDocument();
  expect(
    tile('analytics.value.credits').getByText('analytics.value.calls_footnote.14'),
  ).toBeInTheDocument();
  expect(
    tile('analytics.value.ide_users').getByText('analytics.value.ide_footnote.40.30'),
  ).toBeInTheDocument();
  expect(screen.getByText('analytics.value.addon.ai-codefix')).toBeInTheDocument();
  expect(screen.getByText('VSCode')).toBeInTheDocument();
});

it('reloads for another period', async () => {
  const user = userEvent.setup();
  renderComponent(<ValueDashboard organization="my-org" />);
  await screen.findByRole('group', { name: 'analytics.value.issues_resolved' });

  await user.click(screen.getByRole('radio', { name: 'analytics.quality.period.180' }));

  expect(getAiValue).toHaveBeenLastCalledWith('my-org', 180);
  expect(getIdeAdoption).toHaveBeenLastCalledWith('my-org', 180);
});

it('says when usage data cannot be loaded', async () => {
  jest.mocked(getAiValue).mockRejectedValue(new Error('boom'));
  renderComponent(<ValueDashboard organization="my-org" />);

  expect(await screen.findByText('analytics.value.load_error')).toBeInTheDocument();
});

it('shows the remediation backlog and the engineering time AI saved', async () => {
  renderComponent(<ValueDashboard organization="my-org" />);

  const backlog = within(await screen.findByRole('group', { name: 'analytics.roi.backlog' }));
  expect(await backlog.findByText('150 h')).toBeInTheDocument();
  expect(backlog.getByText('analytics.roi.backlog_footnote.8')).toBeInTheDocument();
  expect(backlog.getByText('-25%')).toBeInTheDocument();
  expect(tile('analytics.roi.debt_paid_down').getByText('50 h')).toBeInTheDocument();
  const ai = within(await screen.findByRole('group', { name: 'analytics.roi.ai_saved' }));
  expect(await ai.findByText('10 h')).toBeInTheDocument();
  expect(ai.getByText('analytics.roi.ai_footnote.30')).toBeInTheDocument();
  expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
});

it('still prices the backlog when AI usage cannot be loaded', async () => {
  jest.mocked(getAiValue).mockRejectedValue(new Error('boom'));
  renderComponent(<ValueDashboard organization="my-org" />);

  const backlog = within(await screen.findByRole('group', { name: 'analytics.roi.backlog' }));
  expect(await backlog.findByText('150 h')).toBeInTheDocument();
  expect(await screen.findByText('analytics.roi.ai_unavailable')).toBeInTheDocument();
});
