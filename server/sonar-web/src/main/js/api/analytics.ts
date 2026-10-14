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

import { getJSON } from '~sonar-aligned/helpers/request';
import { TaskStatuses } from '../types/tasks';

export type AnalyticsInterval = 'day' | 'week';
export type AnalysisTarget = 'MAIN' | 'BRANCH' | 'PULL_REQUEST' | 'UNKNOWN';

export interface StatusCounts {
  canceled: number;
  failed: number;
  success: number;
  total: number;
}

/** Durations are Compute Engine processing times of successful analyses, in milliseconds. */
export interface AnalysisStats extends StatusCounts {
  avgExecutionTimeMs?: number;
  avgQueueTimeMs?: number;
  medianExecutionTimeMs?: number;
  medianQueueTimeMs?: number;
  p90ExecutionTimeMs?: number;
  projects: number;
}

export interface TimelineBucket extends StatusCounts {
  /** First day of the bucket, yyyy-MM-dd. */
  date: string;
  medianExecutionTimeMs?: number;
  p90ExecutionTimeMs?: number;
}

export interface OrganizationActivity {
  analyses: AnalysisStats;
  period: { from: string; interval: AnalyticsInterval; timeZone: string; to: string };
  previous: { analyses: AnalysisStats; from: string; to: string };
  targets: Array<StatusCounts & { projects: number; target: AnalysisTarget }>;
  taskTypes: Array<StatusCounts & { type: string }>;
  timeline: TimelineBucket[];
  totalProjects: number;
}

export interface OrganizationQueue {
  inProgress: number;
  oldestPendingAgeMs?: number;
  oldestPendingAt?: string;
  pending: number;
}

export function getOrganizationActivity(data: {
  from: string;
  interval?: AnalyticsInterval;
  organization: string;
  timeZone?: string;
  to?: string;
}): Promise<OrganizationActivity> {
  return getJSON('/api/analytics/activity', data);
}

export function getOrganizationQueue(organization: string): Promise<OrganizationQueue> {
  return getJSON('/api/analytics/queue', { organization });
}

export interface AnalyticsPaging {
  pageIndex: number;
  pageSize: number;
  total: number;
}

export interface AnalyticsTask {
  /** Branch name or pull request id; missing when the branch was deleted since. */
  branch?: string;
  errorMessage?: string;
  executedAt?: string;
  executionTimeMs?: number;
  id: string;
  projectKey: string;
  projectName?: string;
  /** For a queued task, how long it has been waiting so far. */
  queueTimeMs?: number;
  startedAt?: string;
  status: string;
  submittedAt: string;
  target: AnalysisTarget;
  type: string;
}

export interface AnalyticsProjectActivity {
  analyses: number;
  avgExecutionTimeMs?: number;
  canceled: number;
  failed: number;
  key: string;
  lastAnalysisAt?: string;
  name?: string;
  success: number;
}

export type AnalyticsTaskSort = 'submittedAt' | 'executionTime' | 'queueTime';

interface PeriodRequest {
  from: string;
  organization: string;
  p?: number;
  ps?: number;
  timeZone?: string;
  to?: string;
}

export function getOrganizationTasks(
  data: PeriodRequest & { s?: AnalyticsTaskSort; statuses?: string; type?: string },
): Promise<{ paging: AnalyticsPaging; tasks: AnalyticsTask[] }> {
  return getJSON('/api/analytics/tasks', data);
}

export function getOrganizationQueuedTasks(data: {
  organization: string;
  p?: number;
  ps?: number;
}): Promise<{ paging: AnalyticsPaging; tasks: AnalyticsTask[] }> {
  return getJSON('/api/analytics/queued_tasks', data);
}

export function getOrganizationProjectActivity(
  data: PeriodRequest & { analyzed?: boolean },
): Promise<{ paging: AnalyticsPaging; projects: AnalyticsProjectActivity[] }> {
  return getJSON('/api/analytics/projects', data);
}

/** Background task status of every project of the organization that had a task, in one call. */
export function getProjectTaskStatuses(
  organization: string,
): Promise<{ projects: Array<{ key: string; status: TaskStatuses }> }> {
  return getJSON('/api/analytics/project_task_statuses', { organization });
}

/** Unresolved issues on the main branch of every project of the organization, by type and severity. */
export function getProjectIssues(organization: string): Promise<{
  projects: Array<{ issues: Partial<Record<string, Partial<Record<string, number>>>>; key: string }>;
}> {
  return getJSON('/api/analytics/project_issues', { organization });
}

export interface QualityTrendPoint {
  blockerIssues: number;
  /** First day of the bucket, yyyy-MM-dd. */
  date: string;
  failed: number;
  openIssues: number;
  passed: number;
  projects: number;
  vulnerabilities: number;
}

export function getQualityTrend(data: {
  from: string;
  interval?: AnalyticsInterval;
  organization: string;
  timeZone?: string;
  to?: string;
}): Promise<{ period: OrganizationActivity['period']; timeline: QualityTrendPoint[] }> {
  return getJSON('/api/analytics/quality_trend', data);
}

export interface ExpiringException {
  expired: boolean;
  expiresAt: string;
  filePath?: string;
  issueKey: string;
  issueStatus: 'ACCEPTED' | 'FALSE_POSITIVE';
  projectKey: string;
  projectName?: string;
  rule: string;
  ruleName?: string;
}

export function getExpiringExceptions(data: {
  organization: string;
  p?: number;
  ps?: number;
  withinDays?: number;
}): Promise<{ exceptions: ExpiringException[]; paging: AnalyticsPaging }> {
  return getJSON('/api/analytics/expiring_exceptions', data);
}

export interface ResolutionCounts {
  accepted: number;
  falsePositive: number;
  fixed: number;
  introduced: number;
  removed: number;
  reopened: number;
}

export interface IssueResolutions {
  /** Fixed and removed counts before this day are incomplete: closed issues are deleted after retentionDays. */
  completeFrom: string;
  period: OrganizationActivity['period'];
  retentionDays: number;
  timeline: Array<ResolutionCounts & { date: string }>;
  totals: ResolutionCounts & { fixedByAnalysis: number; netChange: number; resolved: number };
}

export function getIssueResolutions(data: {
  from: string;
  interval?: AnalyticsInterval;
  organization: string;
  timeZone?: string;
  to?: string;
}): Promise<IssueResolutions> {
  return getJSON('/api/analytics/issue_resolutions', data);
}
