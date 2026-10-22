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

import {
  getProjectIssues,
  getProjectQualityGates,
  getProjectTaskStatuses,
} from '../../../api/analytics';
import { searchProjects } from '../../../api/components';
import { TaskStatuses } from '../../../types/tasks';
import {
  AnalysisStatus,
  DEFAULT_FILTERS,
  UNTAGGED,
  fetchBackgroundTaskStatuses,
  fetchIssueMatrices,
  fetchOrganizationProjects,
  filterProjects,
} from '../utils';

jest.mock('../../../api/analytics', () => ({
  getProjectIssues: jest.fn(),
  getProjectQualityGates: jest.fn(),
  getProjectTaskStatuses: jest.fn(),
}));

jest.mock('../../../api/components', () => ({ searchProjects: jest.fn() }));

it('builds every listed project matrix from a single call', async () => {
  jest.mocked(getProjectIssues).mockResolvedValue({
    projects: [
      { key: 'a', effortMinutes: 45, issues: { BUG: { MAJOR: 2 }, VULNERABILITY: { BLOCKER: 1 } } },
      { key: 'not-listed', effortMinutes: 10, issues: { BUG: { MAJOR: 5 } } },
    ],
  });

  const { effortMinutes, matrices } = await fetchIssueMatrices('org', ['a', 'b']);

  expect(getProjectIssues).toHaveBeenCalledTimes(1);
  expect(getProjectIssues).toHaveBeenCalledWith('org');
  expect(matrices.a.BUG.MAJOR).toBe(2);
  expect(matrices.a.BUG.CRITICAL).toBe(0);
  expect(matrices.a.VULNERABILITY.BLOCKER).toBe(1);
  expect(matrices.a.CODE_SMELL.MINOR).toBe(0);
  expect(matrices.b.BUG.MAJOR).toBe(0);
  expect(matrices).not.toHaveProperty('not-listed');
  expect(effortMinutes).toEqual({ a: 45 });
});

it('reads projects with their tags, and every quality gate in one call', async () => {
  jest.mocked(getProjectQualityGates).mockResolvedValue({
    projects: [
      { key: 'a', status: 'OK' },
      { key: 'b', status: 'ERROR' },
    ],
  });
  jest.mocked(searchProjects).mockResolvedValue({
    components: [
      { key: 'a', name: 'A', tags: ['payments'] },
      { key: 'b', name: 'B', tags: [] },
      { key: 'c', name: 'C', tags: [] },
    ],
    facets: [],
    organizations: [],
    paging: { pageIndex: 1, pageSize: 500, total: 3 },
  } as unknown as Awaited<ReturnType<typeof searchProjects>>);

  const { projects, total } = await fetchOrganizationProjects('org');

  expect(getProjectQualityGates).toHaveBeenCalledTimes(1);
  expect(searchProjects).toHaveBeenCalledTimes(1);
  expect(total).toBe(3);
  expect(projects.map(({ key, status, tags }) => ({ key, status, tags }))).toEqual([
    { key: 'a', status: AnalysisStatus.Passed, tags: ['payments'] },
    { key: 'b', status: AnalysisStatus.Failed, tags: [] },
    { key: 'c', status: AnalysisStatus.NotAvailable, tags: [] },
  ]);
});

it('filters projects by team tag, or by having none', () => {
  const projects = [
    { key: 'a', name: 'A', status: AnalysisStatus.Passed, tags: ['payments', 'web'] },
    { key: 'b', name: 'B', status: AnalysisStatus.Passed, tags: ['web'] },
    { key: 'c', name: 'C', status: AnalysisStatus.Passed, tags: [] },
  ];
  const context = { now: new Date(), taskStatuses: {} };
  const keys = (tag: string) =>
    filterProjects(projects, { ...DEFAULT_FILTERS, tag }, context).map(({ key }) => key);

  expect(keys('web')).toEqual(['a', 'b']);
  expect(keys('payments')).toEqual(['a']);
  expect(keys(UNTAGGED)).toEqual(['c']);
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
