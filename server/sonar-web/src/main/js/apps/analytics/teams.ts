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

import { differenceInCalendarDays } from 'date-fns';
import { parseDate } from '../../helpers/dates';
import { TaskStatuses } from '../../types/tasks';
import { Health, computeHealth, share } from './health';
import { STALE_DAYS } from './quality';
import { AnalysisStatus, AnalyticsProject, IssueMatrix, UNTAGGED } from './utils';

/** The projects of one tag, rolled up. */
export interface Team {
  /** Projects with open blocker or critical vulnerabilities. */
  atRisk: number;
  failed: number;
  /** Projects that ran into a failed latest analysis. */
  failedAnalyses: number;
  health: Health;
  openIssues: number;
  /** Quality gate pass rate among the projects with a status. */
  passRate?: number;
  passed: number;
  projects: number;
  remediationMinutes: number;
  /** Projects with no analysis in the last {@link STALE_DAYS} days. */
  stale: number;
  /** A project tag, or {@link UNTAGGED}. */
  tag: string;
  vulnerabilities: number;
}

export interface TeamInput {
  effortMinutes: Record<string, number>;
  matrices: Record<string, IssueMatrix>;
  now: Date;
  projects: AnalyticsProject[];
  taskStatuses: Record<string, TaskStatuses>;
}

// What a team health is made of: quality gates first, then security exposure, analysis freshness and reliability.
export const TEAM_HEALTH_WEIGHTS = {
  qualityGates: 40,
  security: 25,
  freshness: 20,
  reliability: 15,
};

/**
 * One row per project tag, healthiest first. A project with several tags counts in each of them; projects without a
 * tag form their own group, so the totals always cover every project.
 */
export function computeTeams({
  effortMinutes,
  matrices,
  now,
  projects,
  taskStatuses,
}: TeamInput): Team[] {
  const teams = new Map<string, Omit<Team, 'health' | 'passRate'>>();
  for (const project of projects) {
    const matrix = matrices[project.key];
    const vulnerabilities = matrix ? sum(matrix.VULNERABILITY) : 0;
    const severeVulnerabilities = matrix
      ? matrix.VULNERABILITY.BLOCKER + matrix.VULNERABILITY.CRITICAL
      : 0;
    const stale =
      project.analysisDate === undefined ||
      differenceInCalendarDays(now, parseDate(project.analysisDate)) > STALE_DAYS;
    for (const tag of project.tags.length > 0 ? project.tags : [UNTAGGED]) {
      const team = teams.get(tag) ?? {
        atRisk: 0,
        failed: 0,
        failedAnalyses: 0,
        openIssues: 0,
        passed: 0,
        projects: 0,
        remediationMinutes: 0,
        stale: 0,
        tag,
        vulnerabilities: 0,
      };
      team.projects++;
      team.passed += project.status === AnalysisStatus.Passed ? 1 : 0;
      team.failed += project.status === AnalysisStatus.Failed ? 1 : 0;
      team.openIssues += matrix ? sum(matrix.BUG) + sum(matrix.CODE_SMELL) + vulnerabilities : 0;
      team.vulnerabilities += vulnerabilities;
      team.atRisk += severeVulnerabilities > 0 ? 1 : 0;
      team.remediationMinutes += effortMinutes[project.key] ?? 0;
      team.failedAnalyses += taskStatuses[project.key] === TaskStatuses.Failed ? 1 : 0;
      team.stale += stale ? 1 : 0;
      teams.set(tag, team);
    }
  }

  return [...teams.values()]
    .map((team) => {
      const passRate = share(team.passed, team.passed + team.failed);
      const health = computeHealth([
        { key: 'qualityGates', value: passRate, weight: TEAM_HEALTH_WEIGHTS.qualityGates },
        {
          key: 'security',
          value: 100 - (share(team.atRisk, team.projects) ?? 0),
          weight: TEAM_HEALTH_WEIGHTS.security,
        },
        {
          key: 'freshness',
          value: 100 - (share(team.stale, team.projects) ?? 0),
          weight: TEAM_HEALTH_WEIGHTS.freshness,
        },
        {
          key: 'reliability',
          value: 100 - (share(team.failedAnalyses, team.projects) ?? 0),
          weight: TEAM_HEALTH_WEIGHTS.reliability,
        },
      ]);
      return { ...team, health, passRate };
    })
    .sort(
      (a, b) =>
        (b.health.score ?? -1) - (a.health.score ?? -1) ||
        b.projects - a.projects ||
        a.tag.localeCompare(b.tag),
    );
}

/** Whether any project carries a tag: without tags, there is nothing to compare. */
export function hasTeams(projects: AnalyticsProject[]) {
  return projects.some((project) => project.tags.length > 0);
}

function sum(counts: Record<string, number>) {
  return Object.values(counts).reduce((total, count) => total + count, 0);
}
