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

import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { getPullRequestAnalytics } from '../../../../api/codescan-analytics';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import PullRequestsPanel from '../PullRequestsPanel';

jest.mock('../../../../api/codescan-analytics', () => ({ getPullRequestAnalytics: jest.fn() }));

const project = (key: string, type: string, analyzed: number) => ({
  projectKey: key,
  projectName: `Project ${key}`,
  type,
  pullRequestsReceived: analyzed + 1,
  pullRequestsAnalyzed: analyzed,
  analyses: analyzed * 2,
  failedAnalyses: 1,
  failedJobs: 1,
  lastAnalysisAt: Date.UTC(2026, 9, 12),
});

beforeEach(() => {
  jest.mocked(getPullRequestAnalytics).mockImplementation(({ type, page = 1 }) =>
    Promise.resolve({
      days: 30,
      byType: [
        {
          type: 'github',
          projects: 10,
          projectsWithPullRequests: 6,
          pullRequestsReceived: 40,
          pullRequestsAnalyzed: 35,
          analyses: 80,
          failedAnalyses: 2,
          failedJobs: 1,
        },
        {
          type: 'metadata_api',
          projects: 4,
          projectsWithPullRequests: 0,
          pullRequestsReceived: 0,
          pullRequestsAnalyzed: 0,
          analyses: 0,
          failedAnalyses: 0,
          failedJobs: 0,
        },
        {
          type: 'ci_azure',
          projects: 3,
          projectsWithPullRequests: 2,
          pullRequestsReceived: 0,
          pullRequestsAnalyzed: 9,
          analyses: 12,
          failedAnalyses: 0,
          failedJobs: 0,
        },
      ],
      total: type ? 1 : 2,
      projects:
        type === 'ci_azure'
          ? [project('azure-app', 'ci_azure', 9)]
          : [page === 1 ? project('force-app', 'github', 20) : project('billing', 'github', 15)],
    }),
  );
});

it('shows pull requests by project type and by project', async () => {
  renderComponent(<PullRequestsPanel days={30} organization="my-org" />);

  await screen.findByRole('link', { name: 'Project force-app' });
  const byType = within(screen.getAllByRole('table')[0]);
  expect(byType.getByRole('button', { name: 'analytics.pr.type.github' })).toBeInTheDocument();
  expect(byType.getByText('analytics.pr.x_of_y.6.10')).toBeInTheDocument();
  expect(byType.getByText('35')).toBeInTheDocument();
  expect(byType.getByText('3')).toBeInTheDocument();
  expect(screen.getByText('analytics.pr.salesforce_note')).toBeInTheDocument();

  const link = screen.getByRole('link', { name: 'Project force-app' });
  expect(link).toHaveAttribute('href', '/project/branches?id=force-app');
  expect(getPullRequestAnalytics).toHaveBeenCalledWith({
    organizationKey: 'my-org',
    days: 30,
    type: undefined,
    page: 1,
    pageSize: 20,
  });
});

it('filters projects by type, and pages through them', async () => {
  const user = userEvent.setup();
  renderComponent(<PullRequestsPanel days={30} organization="my-org" />);
  await screen.findByRole('link', { name: 'Project force-app' });

  await user.click(screen.getByRole('button', { name: 'analytics.table.show_more' }));
  expect(await screen.findByRole('link', { name: 'Project billing' })).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'analytics.pr.type.ci_azure' }));
  expect(await screen.findByRole('link', { name: 'Project azure-app' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'analytics.pr.type.ci_azure' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
  expect(
    screen.getByText('analytics.pr.by_project.filtered.analytics.pr.type.ci_azure'),
  ).toBeInTheDocument();
});

it('stays hidden when CodeScan services do not answer', async () => {
  jest.mocked(getPullRequestAnalytics).mockRejectedValue(new Error('not deployed'));
  const { container } = renderComponent(<PullRequestsPanel days={30} organization="my-org" />);

  await act(async () => {
    await getPullRequestAnalytics({ organizationKey: 'my-org', days: 30 }).catch(() => undefined);
  });
  expect(container).toBeEmptyDOMElement();
});
