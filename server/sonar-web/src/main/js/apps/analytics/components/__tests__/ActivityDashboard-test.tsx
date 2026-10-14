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
  getOrganizationActivity,
  getOrganizationProjectActivity,
  getOrganizationQueue,
  getOrganizationQueuedTasks,
  getOrganizationTasks,
} from '../../../../api/analytics';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import ActivityDashboard from '../ActivityDashboard';

jest.mock('../../../../api/codescan-analytics', () => ({
  getPullRequestAnalytics: jest.fn().mockRejectedValue(new Error('not deployed')),
}));

jest.mock('../../../../api/analytics', () => ({
  getOrganizationActivity: jest.fn(),
  getOrganizationProjectActivity: jest.fn(),
  getOrganizationQueue: jest.fn(),
  getOrganizationQueuedTasks: jest.fn(),
  getOrganizationTasks: jest.fn(),
}));

const task = (id: string, overrides = {}) => ({
  id,
  type: 'REPORT',
  status: 'FAILED',
  projectKey: `project-${id}`,
  projectName: `Project ${id}`,
  target: 'MAIN' as const,
  submittedAt: '2026-10-14T09:41:07+0000',
  executionTimeMs: 102_000,
  queueTimeMs: 2_000,
  ...overrides,
});

const analyses = {
  total: 40,
  success: 36,
  failed: 4,
  canceled: 0,
  projects: 12,
  avgExecutionTimeMs: 52_000,
  medianExecutionTimeMs: 48_000,
  p90ExecutionTimeMs: 182_000,
  avgQueueTimeMs: 4_000,
  medianQueueTimeMs: 900,
};

const activity = {
  period: { from: '2026-10-13', to: '2026-10-14', timeZone: 'UTC', interval: 'day' as const },
  totalProjects: 20,
  analyses,
  previous: {
    from: '2026-10-11',
    to: '2026-10-12',
    analyses: { ...analyses, total: 32, medianExecutionTimeMs: 40_000 },
  },
  taskTypes: [{ type: 'REPORT', total: 40, success: 36, failed: 4, canceled: 0 }],
  targets: [
    { target: 'MAIN' as const, total: 30, success: 28, failed: 2, canceled: 0, projects: 12 },
    { target: 'PULL_REQUEST' as const, total: 10, success: 8, failed: 2, canceled: 0, projects: 5 },
  ],
  timeline: [
    { date: '2026-10-13', total: 0, success: 0, failed: 0, canceled: 0 },
    {
      date: '2026-10-14',
      total: 40,
      success: 36,
      failed: 4,
      canceled: 0,
      medianExecutionTimeMs: 48_000,
      p90ExecutionTimeMs: 182_000,
    },
  ],
};

beforeEach(() => {
  jest.mocked(getOrganizationActivity).mockResolvedValue(activity);
  jest.mocked(getOrganizationQueue).mockResolvedValue({
    pending: 3,
    inProgress: 1,
    oldestPendingAgeMs: 20 * 60 * 1000,
  });
  jest.mocked(getOrganizationTasks).mockResolvedValue({
    paging: { pageIndex: 1, pageSize: 20, total: 1 },
    tasks: [
      task('1', {
        branch: 'feature/login',
        target: 'BRANCH',
        errorMessage: 'Analysis report is corrupted',
      }),
    ],
  });
  jest.mocked(getOrganizationQueuedTasks).mockResolvedValue({
    paging: { pageIndex: 1, pageSize: 20, total: 1 },
    tasks: [task('q', { status: 'PENDING', executionTimeMs: undefined, queueTimeMs: 74_000 })],
  });
  jest.mocked(getOrganizationProjectActivity).mockImplementation(({ analyzed }) =>
    Promise.resolve({
      paging: { pageIndex: 1, pageSize: 20, total: 1 },
      projects: [
        analyzed === false
          ? { key: 'stale', name: 'Stale', analyses: 0, success: 0, failed: 0, canceled: 0 }
          : {
              key: 'busy',
              name: 'Busy',
              analyses: 58,
              success: 55,
              failed: 3,
              canceled: 0,
              avgExecutionTimeMs: 61_200,
              lastAnalysisAt: '2026-10-14T09:41:07+0000',
            },
      ],
    }),
  );
});

it('shows headline figures compared with the previous period', async () => {
  renderComponent(<ActivityDashboard organization="my-org" />);

  expect(await screen.findByText('analytics.activity.analyses')).toBeInTheDocument();
  expect(getOrganizationActivity).toHaveBeenCalledWith(
    expect.objectContaining({ organization: 'my-org', interval: 'day' }),
  );
  expectTile('analytics.activity.analyses', '40', { delta: '+25%' });
  expectTile('analytics.activity.projects_analyzed', '12', {
    delta: '0%',
    footnote: 'analytics.summary.of_total.20',
  });
  expectTile('analytics.activity.success_rate', '90%', { delta: '0 pts' });
  expectTile('analytics.activity.median_time', '48s', {
    delta: '+20%',
    footnote: 'analytics.activity.p90_value.3m 02s',
  });
  expectTile('analytics.activity.queue_time', '900ms', {
    footnote: 'analytics.activity.average_value.4s',
  });
  expectTile('analytics.activity.queued_now', '3', {
    footnote: 'analytics.activity.in_progress.1 · analytics.activity.oldest_pending.20m 00s',
  });
});

it('shows the breakdowns by target and task type', async () => {
  renderComponent(<ActivityDashboard organization="my-org" />);

  expect(await screen.findByText('analytics.activity.target.MAIN')).toBeInTheDocument();
  expect(screen.getByText('analytics.activity.target.PULL_REQUEST')).toBeInTheDocument();
  expect(screen.queryByText('analytics.activity.target.UNKNOWN')).not.toBeInTheDocument();
  expect(screen.getByText('background_task.type.REPORT')).toBeInTheDocument();
  expect(screen.getByLabelText(/Oct 14: 40/)).toBeInTheDocument();
});

it('reloads with weekly buckets for a 90 day period', async () => {
  const user = userEvent.setup();
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');

  await user.click(screen.getByRole('radio', { name: 'analytics.filter.date.90' }));

  expect(getOrganizationActivity).toHaveBeenLastCalledWith(
    expect.objectContaining({ interval: 'week' }),
  );
});

it('says when nothing ran', async () => {
  jest.mocked(getOrganizationActivity).mockResolvedValue({
    ...activity,
    analyses: {
      ...analyses,
      total: 0,
      success: 0,
      failed: 0,
      projects: 0,
      medianExecutionTimeMs: undefined,
    },
    taskTypes: [],
    targets: [],
    timeline: [{ date: '2026-10-14', total: 0, success: 0, failed: 0, canceled: 0 }],
  });
  renderComponent(<ActivityDashboard organization="my-org" />);

  expect(await screen.findAllByText('analytics.activity.no_analyses')).toHaveLength(2);
  expect(screen.getByText('analytics.activity.no_tasks')).toBeInTheDocument();
});

it('shows an error when loading fails', async () => {
  jest.mocked(getOrganizationActivity).mockRejectedValue(new Error('boom'));
  renderComponent(<ActivityDashboard organization="my-org" />);

  expect(await screen.findByText('analytics.load_error')).toBeInTheDocument();
});

function tileButton(label: string) {
  return screen.getByRole('button', { name: new RegExp(`^${label.replace(/\./g, '\\.')}`) });
}

function expectTile(
  label: string,
  value: string,
  { delta, footnote }: { delta?: string; footnote?: string },
) {
  const tile = within(tileButton(label));
  expect(tile.getByText(value)).toBeInTheDocument();
  if (delta) {
    expect(tile.getByText(delta)).toBeInTheDocument();
  }
  if (footnote) {
    expect(tile.getByText(footnote)).toBeInTheDocument();
  }
}

it('lists the failed analyses behind the success rate', async () => {
  const user = userEvent.setup();
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');

  await user.click(tileButton('analytics.activity.success_rate'));

  expect(getOrganizationTasks).toHaveBeenCalledWith(
    expect.objectContaining({ organization: 'my-org', p: 1, s: 'submittedAt', statuses: 'FAILED' }),
  );
  const panel = within(
    await screen.findByRole('region', { name: 'analytics.activity.drilldown.failures' }),
  );
  expect(panel.getByRole('link', { name: 'Project 1' })).toBeInTheDocument();
  expect(panel.getByText('analytics.activity.target.BRANCH: feature/login')).toBeInTheDocument();
  expect(panel.getByText('Analysis report is corrupted')).toBeInTheDocument();
  expect(panel.getByText('1m 42s')).toBeInTheDocument();
  expect(tileButton('analytics.activity.success_rate')).toHaveAttribute('aria-pressed', 'true');

  await user.click(tileButton('analytics.activity.success_rate'));
  expect(
    screen.queryByRole('region', { name: 'analytics.activity.drilldown.failures' }),
  ).not.toBeInTheDocument();
});

it.each([
  ['analytics.activity.analyses', { s: 'submittedAt' }],
  ['analytics.activity.median_time', { s: 'executionTime', statuses: 'SUCCESS' }],
  ['analytics.activity.queue_time', { s: 'queueTime' }],
])('sorts and filters the tasks behind %s', async (label, query) => {
  const user = userEvent.setup();
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');

  await user.click(tileButton(label));

  expect(getOrganizationTasks).toHaveBeenLastCalledWith(expect.objectContaining(query));
});

it('lists the tasks waiting in the queue now', async () => {
  const user = userEvent.setup();
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');

  await user.click(tileButton('analytics.activity.queued_now'));

  const panel = within(
    await screen.findByRole('region', { name: 'analytics.activity.drilldown.queued' }),
  );
  expect(getOrganizationQueuedTasks).toHaveBeenCalledWith({ organization: 'my-org', p: 1, ps: 20 });
  expect(panel.getByText('1m 14s')).toBeInTheDocument();
});

it('lists analyzed projects and the ones that were not', async () => {
  const user = userEvent.setup();
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');

  await user.click(tileButton('analytics.activity.projects_analyzed'));

  const panel = within(
    await screen.findByRole('region', { name: 'analytics.activity.drilldown.projects' }),
  );
  expect(await panel.findByRole('link', { name: 'Busy' })).toBeInTheDocument();
  expect(panel.getByText('58')).toBeInTheDocument();
  expect(panel.getByRole('link', { name: '3' })).toBeInTheDocument();

  await user.click(panel.getByRole('radio', { name: 'analytics.activity.drilldown.not_analyzed' }));

  expect(getOrganizationProjectActivity).toHaveBeenLastCalledWith(
    expect.objectContaining({ analyzed: false }),
  );
  expect(await panel.findByRole('link', { name: 'Stale' })).toBeInTheDocument();
  expect(panel.getByText('analytics.activity.drilldown.no_recent_analysis')).toBeInTheDocument();
});

it('loads more rows on demand', async () => {
  const user = userEvent.setup();
  jest
    .mocked(getOrganizationTasks)
    .mockResolvedValueOnce({ paging: { pageIndex: 1, pageSize: 20, total: 2 }, tasks: [task('1')] })
    .mockResolvedValueOnce({
      paging: { pageIndex: 2, pageSize: 20, total: 2 },
      tasks: [task('2')],
    });
  renderComponent(<ActivityDashboard organization="my-org" />);
  await screen.findByText('analytics.activity.analyses');
  await user.click(tileButton('analytics.activity.analyses'));
  const panel = within(
    await screen.findByRole('region', { name: 'analytics.activity.drilldown.analyses' }),
  );
  await panel.findByRole('link', { name: 'Project 1' });

  await user.click(panel.getByRole('button', { name: 'analytics.table.show_more' }));

  expect(getOrganizationTasks).toHaveBeenLastCalledWith(expect.objectContaining({ p: 2 }));
  expect(await panel.findByRole('link', { name: 'Project 2' })).toBeInTheDocument();
  expect(panel.getByRole('link', { name: 'Project 1' })).toBeInTheDocument();
  expect(
    panel.queryByRole('button', { name: 'analytics.table.show_more' }),
  ).not.toBeInTheDocument();
});
