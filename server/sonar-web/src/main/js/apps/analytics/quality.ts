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

import { differenceInCalendarDays, format, subDays } from 'date-fns';
import { parseDate } from '../../helpers/dates';
import { TaskStatuses } from '../../types/tasks';
import { AnalysisStatus, AnalyticsProject, IssueMatrix } from './utils';

export const QUALITY_PERIODS = [30, 90, 180] as const;
export type QualityPeriod = (typeof QUALITY_PERIODS)[number];
export const DEFAULT_QUALITY_PERIOD: QualityPeriod = 90;

export const IMPACT_SEVERITIES = ['BLOCKER', 'HIGH', 'MEDIUM', 'LOW', 'INFO'] as const;
export type ImpactSeverity = (typeof IMPACT_SEVERITIES)[number];

/** Open security issues of these severities are expected to be fixed within {@link SLA_DAYS}. */
export const SLA_SEVERITIES: ImpactSeverity[] = ['BLOCKER', 'HIGH'];
export const SLA_DAYS = 30;

/** A project not analyzed for this long is called out as stale. */
export const STALE_DAYS = 30;

export interface AgeBucket {
  /** Created on or after this day, yyyy-MM-dd. */
  createdAfter?: string;
  /** Created before this day, yyyy-MM-dd. */
  createdBefore?: string;
  key: string;
  /** Issues of this bucket are past the SLA. */
  pastSla: boolean;
}

/** Open issues by age: under 30 days, 30 to 90, 90 to 180, over 180. */
export function getAgeBuckets(now: Date): AgeBucket[] {
  const day = (days: number) => format(subDays(now, days), 'yyyy-MM-dd');
  return [
    { key: 'lt30', createdAfter: day(SLA_DAYS), pastSla: false },
    { key: '30to90', createdAfter: day(90), createdBefore: day(SLA_DAYS), pastSla: true },
    { key: '90to180', createdAfter: day(180), createdBefore: day(90), pastSla: true },
    { key: 'gt180', createdBefore: day(180), pastSla: true },
  ];
}

export enum ReadinessReason {
  QualityGateFailed = 'quality_gate_failed',
  NoQualityGate = 'no_quality_gate',
  CriticalVulnerabilities = 'critical_vulnerabilities',
  LastAnalysisFailed = 'last_analysis_failed',
  Stale = 'stale',
  NeverAnalyzed = 'never_analyzed',
}

export interface ReadinessInput {
  analysisDate?: string;
  criticalVulnerabilities: number;
  lastTaskStatus?: string;
  now: Date;
  qualityGate?: string;
}

/**
 * Whether a project's main branch looks safe to release, and every reason it does not:
 * a failing or missing quality gate, open blocker or high security issues, a failed latest analysis,
 * or results too old to trust.
 */
export function getReadiness({
  analysisDate,
  criticalVulnerabilities,
  lastTaskStatus,
  now,
  qualityGate,
}: ReadinessInput) {
  const reasons: ReadinessReason[] = [];
  if (!analysisDate) {
    reasons.push(ReadinessReason.NeverAnalyzed);
    return { ready: false, reasons };
  }
  if (qualityGate === 'ERROR') {
    reasons.push(ReadinessReason.QualityGateFailed);
  } else if (qualityGate !== 'OK') {
    reasons.push(ReadinessReason.NoQualityGate);
  }
  if (criticalVulnerabilities > 0) {
    reasons.push(ReadinessReason.CriticalVulnerabilities);
  }
  if (lastTaskStatus === TaskStatuses.Failed) {
    reasons.push(ReadinessReason.LastAnalysisFailed);
  }
  if (differenceInCalendarDays(now, parseDate(analysisDate)) > STALE_DAYS) {
    reasons.push(ReadinessReason.Stale);
  }
  return { ready: reasons.length === 0, reasons };
}

export enum FocusReason {
  QualityGateFailed = 'quality_gate_failed',
  Vulnerabilities = 'vulnerabilities',
  Blockers = 'blockers',
  LastAnalysisFailed = 'last_analysis_failed',
  Stale = 'stale',
}

export interface FocusItem {
  project: AnalyticsProject;
  reasons: Array<{ count?: number; reason: FocusReason }>;
  score: number;
}

// How much each signal weighs in the ranking; counts are capped so one huge project can't hide the others.
const FOCUS_WEIGHTS = {
  qualityGate: 30,
  perVulnerability: 6,
  maxVulnerabilities: 40,
  perBlocker: 2,
  maxBlockers: 20,
  failedAnalysis: 15,
  stale: 10,
};

/**
 * Projects that most need attention, riskiest first: failing quality gate, blocker or critical vulnerabilities,
 * blocker issues, a failed latest analysis, or no recent analysis. Projects with none of these are left out.
 */
export function getFocusList(
  projects: AnalyticsProject[],
  matrices: Record<string, IssueMatrix>,
  taskStatuses: Record<string, TaskStatuses>,
  now: Date,
  limit = 10,
): FocusItem[] {
  const items = projects.map((project) => {
    const matrix = matrices[project.key];
    const vulnerabilities = matrix
      ? matrix.VULNERABILITY.BLOCKER + matrix.VULNERABILITY.CRITICAL
      : 0;
    const blockers = matrix ? matrix.BUG.BLOCKER + matrix.CODE_SMELL.BLOCKER : 0;
    const stale =
      project.analysisDate !== undefined &&
      differenceInCalendarDays(now, parseDate(project.analysisDate)) > STALE_DAYS;

    const reasons: FocusItem['reasons'] = [];
    let score = 0;
    if (project.status === AnalysisStatus.Failed) {
      reasons.push({ reason: FocusReason.QualityGateFailed });
      score += FOCUS_WEIGHTS.qualityGate;
    }
    if (vulnerabilities > 0) {
      reasons.push({ reason: FocusReason.Vulnerabilities, count: vulnerabilities });
      score += Math.min(
        vulnerabilities * FOCUS_WEIGHTS.perVulnerability,
        FOCUS_WEIGHTS.maxVulnerabilities,
      );
    }
    if (blockers > 0) {
      reasons.push({ reason: FocusReason.Blockers, count: blockers });
      score += Math.min(blockers * FOCUS_WEIGHTS.perBlocker, FOCUS_WEIGHTS.maxBlockers);
    }
    if (taskStatuses[project.key] === TaskStatuses.Failed) {
      reasons.push({ reason: FocusReason.LastAnalysisFailed });
      score += FOCUS_WEIGHTS.failedAnalysis;
    }
    if (stale) {
      reasons.push({ reason: FocusReason.Stale });
      score += FOCUS_WEIGHTS.stale;
    }
    return { project, reasons, score };
  });
  return items
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score || a.project.name.localeCompare(b.project.name))
    .slice(0, limit);
}
