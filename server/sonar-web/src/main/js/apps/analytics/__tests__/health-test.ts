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

import { TaskStatuses } from '../../../types/tasks';
import { pageNumbers } from '../components/Pagination';
import { computeHealth, gradeOf } from '../health';
import { formatHours } from '../roi';
import { computeTeams } from '../teams';
import { AnalysisStatus, AnalyticsProject, IssueMatrix, UNTAGGED } from '../utils';

describe('computeHealth', () => {
  it('averages the components by weight', () => {
    expect(
      computeHealth([
        { key: 'a', value: 100, weight: 30 },
        { key: 'b', value: 50, weight: 10 },
      ]),
    ).toMatchObject({ score: 88, grade: 'A' });
  });

  it('gives the weight of a component without data to the others', () => {
    expect(
      computeHealth([
        { key: 'a', value: 60, weight: 30 },
        { key: 'b', value: undefined, weight: 70 },
      ]),
    ).toMatchObject({ score: 60, grade: 'C' });
  });

  it('has no score without any data', () => {
    expect(computeHealth([{ key: 'a', weight: 1 }])).toEqual({
      components: [{ key: 'a', weight: 1 }],
    });
  });

  it.each([
    [85, 'A'],
    [84, 'B'],
    [70, 'B'],
    [55, 'C'],
    [40, 'D'],
    [39, 'E'],
  ])('grades %s as %s', (score, grade) => {
    expect(gradeOf(score)).toBe(grade);
  });
});

describe('computeTeams', () => {
  const now = new Date('2026-10-14T12:00:00Z');
  const empty = () => ({ BLOCKER: 0, CRITICAL: 0, MAJOR: 0, MINOR: 0, INFO: 0 });
  const matrix = (
    bugs: number,
    vulnerabilities: number,
    criticalVulnerabilities = 0,
  ): IssueMatrix => ({
    BUG: { ...empty(), MAJOR: bugs },
    CODE_SMELL: empty(),
    VULNERABILITY: { ...empty(), MINOR: vulnerabilities, CRITICAL: criticalVulnerabilities },
  });
  const project = (
    key: string,
    tags: string[],
    status: AnalysisStatus,
    analysisDate?: string,
  ): AnalyticsProject => ({
    analysisDate,
    key,
    name: key,
    status,
    tags,
  });

  it('rolls projects up by tag, healthiest first, with untagged projects in their own team', () => {
    const teams = computeTeams({
      now,
      projects: [
        project('pay-api', ['payments'], AnalysisStatus.Passed, '2026-10-13T00:00:00+0000'),
        project('pay-web', ['payments', 'web'], AnalysisStatus.Failed, '2026-10-12T00:00:00+0000'),
        project('portal', ['web'], AnalysisStatus.Failed, '2026-06-01T00:00:00+0000'),
        project('legacy', [], AnalysisStatus.NotAvailable),
      ],
      matrices: {
        'pay-api': matrix(2, 1),
        'pay-web': matrix(3, 0),
        portal: matrix(1, 2, 1),
      },
      effortMinutes: { 'pay-api': 60, 'pay-web': 30, portal: 90 },
      taskStatuses: { portal: TaskStatuses.Failed },
    });

    // payments 80; untagged 67, its missing quality gate weighing nothing; web 30
    expect(teams.map(({ tag }) => tag)).toEqual(['payments', UNTAGGED, 'web']);
    expect(teams.map(({ health }) => health.score)).toEqual([80, 67, 30]);
    expect(teams[0]).toMatchObject({
      projects: 2,
      passed: 1,
      failed: 1,
      passRate: 50,
      openIssues: 6,
      vulnerabilities: 1,
      atRisk: 0,
      remediationMinutes: 90,
      stale: 0,
      failedAnalyses: 0,
    });
    expect(teams[2]).toMatchObject({
      projects: 2,
      passRate: 0,
      vulnerabilities: 3,
      atRisk: 1,
      remediationMinutes: 120,
      stale: 1,
      failedAnalyses: 1,
    });
    // never analyzed: stale, and no quality gate to weigh
    expect(teams[1]).toMatchObject({ projects: 1, stale: 1, passRate: undefined });
  });
});

describe('roi', () => {
  it('formats effort in hours', () => {
    expect(formatHours(0)).toBe('0 h');
    expect(formatHours(10)).toBe('<1 h');
    expect(formatHours(90 * 60)).toBe('90 h');
  });
});

describe('pageNumbers', () => {
  it('shows every page when there are few', () => {
    expect(pageNumbers(2, 3)).toEqual([1, 2, 3]);
  });

  it('shows the first, last and neighbouring pages, with gaps', () => {
    expect(pageNumbers(5, 10)).toEqual([1, undefined, 4, 5, 6, undefined, 10]);
    expect(pageNumbers(1, 10)).toEqual([1, 2, undefined, 10]);
    expect(pageNumbers(10, 10)).toEqual([1, undefined, 9, 10]);
  });
});
