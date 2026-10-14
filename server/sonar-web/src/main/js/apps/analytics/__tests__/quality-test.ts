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
import {
  FocusReason,
  ReadinessReason,
  getAgeBuckets,
  getFocusList,
  getReadiness,
} from '../quality';
import { AnalysisStatus, AnalyticsProject, IssueMatrix } from '../utils';

const NOW = new Date(2026, 9, 14, 12, 0);

it('splits open issues into age buckets around the SLA', () => {
  expect(getAgeBuckets(NOW)).toEqual([
    { key: 'lt30', createdAfter: '2026-09-14', pastSla: false },
    { key: '30to90', createdAfter: '2026-07-16', createdBefore: '2026-09-14', pastSla: true },
    { key: '90to180', createdAfter: '2026-04-17', createdBefore: '2026-07-16', pastSla: true },
    { key: 'gt180', createdBefore: '2026-04-17', pastSla: true },
  ]);
});

describe('getReadiness', () => {
  const ok = {
    analysisDate: '2026-10-13T10:00:00+0000',
    criticalVulnerabilities: 0,
    lastTaskStatus: TaskStatuses.Success,
    now: NOW,
    qualityGate: 'OK',
  };

  it('is ready when everything is green', () => {
    expect(getReadiness(ok)).toEqual({ ready: true, reasons: [] });
  });

  it('lists every reason it is not ready', () => {
    expect(
      getReadiness({
        ...ok,
        analysisDate: '2026-08-01T10:00:00+0000',
        criticalVulnerabilities: 2,
        lastTaskStatus: TaskStatuses.Failed,
        qualityGate: 'ERROR',
      }).reasons,
    ).toEqual([
      ReadinessReason.QualityGateFailed,
      ReadinessReason.CriticalVulnerabilities,
      ReadinessReason.LastAnalysisFailed,
      ReadinessReason.Stale,
    ]);
    expect(getReadiness({ ...ok, qualityGate: undefined }).reasons).toEqual([
      ReadinessReason.NoQualityGate,
    ]);
  });

  it('is never ready without an analysis', () => {
    expect(getReadiness({ ...ok, analysisDate: undefined })).toEqual({
      ready: false,
      reasons: [ReadinessReason.NeverAnalyzed],
    });
  });
});

describe('getFocusList', () => {
  const project = (key: string, overrides: Partial<AnalyticsProject> = {}): AnalyticsProject => ({
    key,
    name: key,
    analysisDate: '2026-10-13T10:00:00+0000',
    status: AnalysisStatus.Passed,
    ...overrides,
  });
  const matrix = (vulnerabilities: number, blockers: number): IssueMatrix => {
    const empty = () => ({ BLOCKER: 0, CRITICAL: 0, MAJOR: 0, MINOR: 0, INFO: 0 });
    return {
      BUG: { ...empty(), BLOCKER: blockers },
      CODE_SMELL: empty(),
      VULNERABILITY: { ...empty(), CRITICAL: vulnerabilities },
    };
  };

  it('ranks risky projects first and leaves healthy ones out', () => {
    const items = getFocusList(
      [
        project('healthy'),
        project('stale', { analysisDate: '2026-08-01T10:00:00+0000' }),
        project('failing', { status: AnalysisStatus.Failed }),
        project('vulnerable'),
        project('broken'),
      ],
      { vulnerable: matrix(3, 1), failing: matrix(0, 0), healthy: matrix(0, 0) },
      { broken: TaskStatuses.Failed },
      NOW,
    );

    expect(items.map(({ project: p }) => p.key)).toEqual([
      'failing',
      'vulnerable',
      'broken',
      'stale',
    ]);
    expect(items[1].reasons).toEqual([
      { reason: FocusReason.Vulnerabilities, count: 3 },
      { reason: FocusReason.Blockers, count: 1 },
    ]);
  });

  it('caps the list', () => {
    const projects = Array.from({ length: 15 }, (_, i) =>
      project(`p${i}`, { status: AnalysisStatus.Failed }),
    );
    expect(getFocusList(projects, {}, {}, NOW, 10)).toHaveLength(10);
  });
});
