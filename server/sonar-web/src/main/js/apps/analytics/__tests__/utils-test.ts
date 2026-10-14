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

import { getProjectIssues, getProjectTaskStatuses } from '../../../api/analytics';
import { TaskStatuses } from '../../../types/tasks';
import { fetchBackgroundTaskStatuses, fetchIssueMatrices } from '../utils';

jest.mock('../../../api/analytics', () => ({
  getProjectIssues: jest.fn(),
  getProjectTaskStatuses: jest.fn(),
}));

it('builds every listed project matrix from a single call', async () => {
  jest.mocked(getProjectIssues).mockResolvedValue({
    projects: [
      { key: 'a', issues: { BUG: { MAJOR: 2 }, VULNERABILITY: { BLOCKER: 1 } } },
      { key: 'not-listed', issues: { BUG: { MAJOR: 5 } } },
    ],
  });

  const matrices = await fetchIssueMatrices('org', ['a', 'b']);

  expect(getProjectIssues).toHaveBeenCalledTimes(1);
  expect(getProjectIssues).toHaveBeenCalledWith('org');
  expect(matrices.a.BUG.MAJOR).toBe(2);
  expect(matrices.a.BUG.CRITICAL).toBe(0);
  expect(matrices.a.VULNERABILITY.BLOCKER).toBe(1);
  expect(matrices.a.CODE_SMELL.MINOR).toBe(0);
  expect(matrices.b.BUG.MAJOR).toBe(0);
  expect(matrices).not.toHaveProperty('not-listed');
});

it('reads every listed project background task status from a single call', async () => {
  jest.mocked(getProjectTaskStatuses).mockResolvedValue({
    projects: [
      { key: 'a', status: TaskStatuses.InProgress },
      { key: 'b', status: TaskStatuses.Failed },
      { key: 'not-listed', status: TaskStatuses.Success },
    ],
  });

  const statuses = await fetchBackgroundTaskStatuses('org', ['a', 'b', 'c']);

  expect(getProjectTaskStatuses).toHaveBeenCalledTimes(1);
  expect(getProjectTaskStatuses).toHaveBeenCalledWith('org');
  expect(statuses).toEqual({ a: TaskStatuses.InProgress, b: TaskStatuses.Failed });
});
