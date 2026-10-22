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
import { AnalysisStatus, AnalyticsProject } from '../../utils';
import TeamLeaderboard from '../TeamLeaderboard';

const now = new Date('2026-10-14T12:00:00Z');
const project = (
  key: string,
  tags: string[],
  status = AnalysisStatus.Passed,
): AnalyticsProject => ({
  analysisDate: '2026-10-13T00:00:00+0000',
  key,
  name: key,
  status,
  tags,
});

function renderLeaderboard(projects: AnalyticsProject[], onSelect = jest.fn()) {
  renderComponent(
    <TeamLeaderboard
      effortMinutes={{ a: 120 }}
      loading={false}
      matrices={{}}
      now={now}
      onSelect={onSelect}
      projects={projects}
      taskStatuses={{}}
    />,
  );
  return onSelect;
}

it('compares teams and scopes the page to the one clicked', async () => {
  const user = userEvent.setup();
  const onSelect = renderLeaderboard([
    project('a', ['payments']),
    project('b', ['web'], AnalysisStatus.Failed),
  ]);

  const rows = screen.getAllByRole('row');
  expect(rows[1]).toHaveTextContent('payments');
  expect(rows[1]).toHaveTextContent('100%');
  expect(rows[1]).toHaveTextContent('2 h');
  expect(rows[2]).toHaveTextContent('web');

  await user.click(screen.getByRole('button', { name: 'web' }));
  expect(onSelect).toHaveBeenCalledWith('web');
});

it('invites to tag projects when none is', () => {
  renderLeaderboard([project('a', [])]);

  expect(screen.getByText('analytics.teams.no_tags')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});
