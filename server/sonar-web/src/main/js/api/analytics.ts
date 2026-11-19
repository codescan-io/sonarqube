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

/**
 * Unresolved issues on the main branch of every project of the organization, by type and severity, with the
 * estimated time to fix them.
 */
export function getProjectIssues(
  organization: string,
  includeHotspots = false,
): Promise<{
  projects: Array<{
    effortMinutes: number;
    issues: Partial<Record<string, Partial<Record<string, number>>>>;
    key: string;
  }>;
}> {
  return getJSON('/api/analytics/project_issues', {
    organization,
    ...(includeHotspots ? { includeHotspots: true } : {}),
  });
}

/** Quality gate status of every project of the organization that has one, in one call. */
export function getProjectQualityGates(
  organization: string,
): Promise<{ projects: Array<{ key: string; status: 'OK' | 'ERROR' }> }> {
  return getJSON('/api/analytics/project_quality_gates', { organization });
}

export interface RemediationFixes {
  fixed: number;
  fixedWithinSla: number;
  /** Missing when nothing was fixed. */
  medianTimeToFixMs?: number;
}

export interface SecurityRemediation {
  /** Fixes before this day are incomplete: closed issues are deleted after retentionDays. */
  completeFrom: string;
  period: OrganizationActivity['period'];
  previous: RemediationFixes & { complete: boolean; from: string; to: string };
  retentionDays: number;
  severities: Array<
    RemediationFixes & {
      oldestOpenAgeMs?: number;
      open: number;
      openPastSla: number;
      severity: string;
      slaDays: number;
    }
  >;
  timeline: Array<RemediationFixes & { date: string }>;
  totals: RemediationFixes & { averageTimeToFixMs?: number; open: number; openPastSla: number };
}

export function getSecurityRemediation(data: {
  from: string;
  interval?: AnalyticsInterval;
  organization: string;
  timeZone?: string;
  to?: string;
}): Promise<SecurityRemediation> {
  return getJSON('/api/analytics/security_remediation', data);
}

export interface QualityTrendPoint {
  // acceptedIssues, bySeverity, byType and falsePositiveIssues are missing from servers older than the governance reports.
  acceptedIssues?: number;
  blockerIssues: number;
  /** Open issues by severity, BLOCKER to INFO. */
  bySeverity?: Record<string, number>;
  /** Open issues by type, security hotspots to review included. */
  byType?: Record<string, number>;
  /** First day of the bucket, yyyy-MM-dd. */
  date: string;
  failed: number;
  falsePositiveIssues?: number;
  openIssues: number;
  passed: number;
  projects: number;
  /** Estimated time to fix every open issue. */
  remediationEffortMinutes: number;
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

/** Counts by type, then severity. */
export type TypeSeverityCounts = Partial<Record<string, Partial<Record<string, number>>>>;

export interface AnalyticsUser {
  active?: boolean;
  login: string;
  name?: string;
}

export interface AnalyticsIssue {
  closedAt?: string;
  createdAt?: string;
  filePath?: string;
  issueKey: string;
  line?: number;
  projectKey: string;
  projectName?: string;
  resolution?: string;
  rule: string;
  ruleName?: string;
  severity?: string;
  status?: string;
  type?: string;
}

export interface GovernedException extends AnalyticsIssue {
  comments: number;
  expired: boolean;
  expiresAt?: string;
  issueStatus: 'ACCEPTED' | 'FALSE_POSITIVE';
  markedAt?: string;
  markedBy?: AnalyticsUser;
  rationale?: {
    date?: string;
    source: 'EXCEPTION_REASON' | 'COMMENT';
    text?: string;
    user?: AnalyticsUser;
  };
}

export interface ExceptionSummary {
  accepted: number;
  expired: number;
  expiringSoon: number;
  falsePositive: number;
  hotspotsToReview: number;
  soonDays: number;
  withoutExpiry: number;
}

export function getExceptions(data: {
  organization: string;
  p?: number;
  project?: string;
  ps?: number;
  soonDays?: number;
  statuses?: string;
}): Promise<{
  exceptions: GovernedException[];
  paging: AnalyticsPaging;
  summary: ExceptionSummary;
}> {
  return getJSON('/api/analytics/exceptions', data);
}

export type AuditCategory = 'STATUS' | 'SEVERITY' | 'COMMENT' | 'EXCEPTION_REASON';

export type AuditAction =
  | 'ACCEPTED'
  | 'FALSE_POSITIVE'
  | 'FIXED'
  | 'REMOVED'
  | 'SAFE'
  | 'ACKNOWLEDGED'
  | 'REOPENED'
  | 'STATUS_CHANGED'
  | 'SEVERITY_CHANGED'
  | 'COMMENTED'
  | 'EXCEPTION_REASON'
  | 'OTHER';

export interface AuditChange {
  action: AuditAction;
  date: string;
  filePath?: string;
  from?: string;
  id: string;
  issueKey: string;
  projectKey: string;
  projectName?: string;
  rule: string;
  ruleName?: string;
  severity?: string;
  text?: string;
  to?: string;
  type?: string;
  /** Missing when the analysis made the change. */
  user?: AnalyticsUser;
}

export interface AuditUserActivity {
  /** Counts by lower-case action. */
  actions: Partial<Record<string, number>>;
  /** Missing for the changes made by the analysis. */
  login?: string;
  name?: string;
  total: number;
}

export interface AuditRequest {
  categories?: string;
  from: string;
  includeAnalysisChanges?: boolean;
  organization: string;
  p?: number;
  project?: string;
  ps?: number;
  timeZone?: string;
  to?: string;
  user?: string;
}

export function getIssueAudit(data: AuditRequest): Promise<{
  changes: AuditChange[];
  paging: AnalyticsPaging;
  period: { from: string; timeZone: string; to: string };
  users: AuditUserActivity[];
}> {
  return getJSON('/api/analytics/issue_audit', data);
}

/** Issues counted by the scan comparison; the issues themselves are listed page by page. */
export interface ScanIssueCounts {
  byType: TypeSeverityCounts;
  open: number;
  total: number;
}

export interface ScanChange {
  date: string;
  filePath?: string;
  from?: string;
  issueKey: string;
  outcome?: 'ACCEPTED' | 'FALSE_POSITIVE' | 'REOPENED';
  rule: string;
  ruleName?: string;
  severity?: string;
  to?: string;
  type?: string;
  user?: AnalyticsUser;
}

export interface ScanAnalysis {
  date: string;
  key: string;
  projectVersion?: string;
}

export interface ScanComparison {
  analysis: ScanAnalysis;
  base: ScanAnalysis;
  exceptionChanges: { accepted: number; falsePositive: number; reopened: number };
  measures: Array<{
    analysis?: number | string;
    base?: number | string;
    change?: number;
    metric: string;
  }>;
  newIssues: ScanIssueCounts;
  project: { key: string; name: string };
  remediated: ScanIssueCounts & { complete: boolean; retentionDays: number };
  severityChanges: { lowered: number; raised: number; total: number };
}

export interface ScanComparisonRequest {
  analysis?: string;
  baseAnalysis: string;
  organization: string;
  project: string;
}

export function getScanComparison(data: ScanComparisonRequest): Promise<ScanComparison> {
  return getJSON('/api/analytics/scan_comparison', data);
}

export enum ScanList {
  New = 'new',
  Remediated = 'remediated',
  SeverityChanges = 'severity_changes',
  ExceptionChanges = 'exception_changes',
}

/** One page of the new or remediated issues of a scan comparison. */
export function getScanComparisonIssues(
  data: ScanComparisonRequest & {
    list: ScanList.New | ScanList.Remediated;
    p?: number;
    ps?: number;
  },
): Promise<{ issues: AnalyticsIssue[]; paging: AnalyticsPaging }> {
  return getJSON('/api/analytics/scan_comparison', data);
}

/** One page of the severity or exception changes of a scan comparison. */
export function getScanComparisonChanges(
  data: ScanComparisonRequest & {
    list: ScanList.SeverityChanges | ScanList.ExceptionChanges;
    p?: number;
    ps?: number;
  },
): Promise<{ changes: ScanChange[]; paging: AnalyticsPaging }> {
  return getJSON('/api/analytics/scan_comparison', data);
}

export interface CustomRuleStats {
  closed: number;
  createdAt?: string;
  /** Share of the rule's issues accepted or marked as false positive; missing without issues. */
  dismissalRate?: number;
  introduced: number;
  issues: {
    accepted: number;
    falsePositive: number;
    fixed: number;
    open: number;
    openBySeverity: Partial<Record<string, number>>;
    total: number;
  };
  key: string;
  language?: string;
  name?: string;
  severity?: string;
  status?: string;
  template: string;
  timeline: Array<{ closed: number; date: string; introduced: number }>;
  type?: string;
}

export function getCustomRules(data: {
  from: string;
  interval?: AnalyticsInterval;
  organization: string;
  timeZone?: string;
  to?: string;
}): Promise<{ period: OrganizationActivity['period']; rules: CustomRuleStats[] }> {
  return getJSON('/api/analytics/custom_rules', data);
}
