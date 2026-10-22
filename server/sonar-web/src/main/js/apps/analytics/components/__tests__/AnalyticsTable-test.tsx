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

import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { AnalysisStatus, AnalyticsProject, DEFAULT_FILTERS } from '../../utils';
import AnalyticsTable from '../AnalyticsTable';

const projects: AnalyticsProject[] = Array.from({ length: 30 }, (_, i) => ({
  key: `project-${String(i + 1).padStart(2, '0')}`,
  name: `Project ${String(i + 1).padStart(2, '0')}`,
  status: AnalysisStatus.Passed,
  tags: [],
}));

function renderTable() {
  renderComponent(
    <AnalyticsTable
      filters={DEFAULT_FILTERS}
      loading={false}
      matrices={{}}
      onOpenScorecard={jest.fn()}
      projects={projects}
      taskStatuses={{}}
    />,
  );
}

const shownProjects = () =>
  screen.getAllByRole('link', { name: /^Project \d+$/ }).map((link) => link.textContent);

it('shows one page of projects at a time', async () => {
  const user = userEvent.setup();
  renderTable();
  await user.click(screen.getByRole('button', { name: 'analytics.table.project' }));

  expect(shownProjects()).toHaveLength(10);
  expect(shownProjects()[0]).toBe('Project 01');
  expect(screen.getByText('analytics.pagination.range.1.10.30')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'analytics.pagination.page.3' }));
  expect(shownProjects()[0]).toBe('Project 21');
  expect(screen.getByRole('button', { name: 'analytics.pagination.page.3' })).toHaveAttribute(
    'aria-current',
    'page',
  );

  await user.click(screen.getByRole('button', { name: 'analytics.pagination.previous' }));
  expect(shownProjects()[0]).toBe('Project 11');

  await user.click(screen.getByRole('radio', { name: '25' }));
  expect(shownProjects()).toHaveLength(25);
  expect(shownProjects()[0]).toBe('Project 01');
});

it('goes back to the first page when searching', async () => {
  const user = userEvent.setup();
  renderTable();
  await user.click(screen.getByRole('button', { name: 'analytics.pagination.page.2' }));

  await user.type(screen.getByRole('searchbox'), 'Project 0');

  expect(shownProjects()).toHaveLength(9);
  expect(
    screen.queryByRole('navigation', { name: 'analytics.pagination.label' }),
  ).not.toBeInTheDocument();
});
