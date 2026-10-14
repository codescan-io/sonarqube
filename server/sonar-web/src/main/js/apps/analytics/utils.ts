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

import { differenceInCalendarDays, endOfDay, startOfDay, subDays } from 'date-fns';
import { chunk, range } from 'lodash';
import { MetricKey } from '~sonar-aligned/types/metrics';
import { getProjectIssues, getProjectTaskStatuses } from '../../api/analytics';
import { searchProjects } from '../../api/components';
import { getMeasuresForProjects } from '../../api/measures';
import { parseDate } from '../../helpers/dates';
import { TaskStatuses } from '../../types/tasks';
import { Organization } from '../../types/types';

/**
 * Analytics follows the same audience as the organization Administration tab:
 * organization admins and Super Admins, who both get the organization 'admin' action.
 */
export function canViewAnalytics(organization?: Organization) {
  return organization?.actions?.admin === true;
}

export const ISSUE_TYPES = ['BUG', 'CODE_SMELL', 'VULNERABILITY'] as const;
export type AnalyticsIssueType = (typeof ISSUE_TYPES)[number];

export const SEVERITIES = ['BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'INFO'] as const;
export type AnalyticsSeverity = (typeof SEVERITIES)[number];

export enum AnalysisStatus {
  Passed = 'passed',
  Failed = 'failed',
  NotAvailable = 'not_available',
}

export interface AnalyticsProject {
  analysisDate?: string;
  key: string;
  name: string;
  status: AnalysisStatus;
}

export type IssueCounts = Record<AnalyticsIssueType, number>;
export type SeverityCounts = Record<AnalyticsSeverity, number>;
/** Open issues of one project, by type then severity. */
export type IssueMatrix = Record<AnalyticsIssueType, SeverityCounts>;

export interface IssueFilters {
  severity?: AnalyticsSeverity;
  type?: AnalyticsIssueType;
}

export enum DatePreset {
  AnyTime = 'any',
  Last7Days = '7',
  Last30Days = '30',
  Last90Days = '90',
  Custom = 'custom',
}

export interface DateRange {
  from?: Date;
  to?: Date;
}

// api/components/search_projects accepts up to 500 projects per page.
const PROJECTS_PAGE_SIZE = 500;
// api/measures/search is capped at 100 projects.
const PROJECTS_BATCH_SIZE = 100;

export async function fetchOrganizationProjects(organization: string) {
  const fetchPage = (page: number) =>
    searchProjects({ organization, f: 'analysisDate', p: page, ps: PROJECTS_PAGE_SIZE });

  const firstPage = await fetchPage(1);
  const total = firstPage.paging.total;
  const otherPages = await Promise.all(
    range(2, Math.ceil(total / PROJECTS_PAGE_SIZE) + 1).map(fetchPage),
  );
  const projects: AnalyticsProject[] = [firstPage, ...otherPages]
    .flatMap(({ components }) => components)
    .map(({ analysisDate, key, name }) => ({
      analysisDate,
      key,
      name,
      status: AnalysisStatus.NotAvailable,
    }));

  const statuses = await fetchQualityGateStatuses(projects.map((p) => p.key));
  return {
    projects: projects.map((p) => ({
      ...p,
      status: statuses[p.key] ?? AnalysisStatus.NotAvailable,
    })),
    total,
  };
}

async function fetchQualityGateStatuses(projectKeys: string[]) {
  const statuses: Record<string, AnalysisStatus> = {};
  const batches = await Promise.all(
    chunk(projectKeys, PROJECTS_BATCH_SIZE).map((keys) =>
      getMeasuresForProjects(keys, [MetricKey.alert_status]),
    ),
  );
  batches.flat().forEach(({ component, value }) => {
    if (value === 'OK') {
      statuses[component] = AnalysisStatus.Passed;
    } else if (value === 'ERROR') {
      statuses[component] = AnalysisStatus.Failed;
    }
  });
  return statuses;
}

function emptyMatrix(): IssueMatrix {
  const severities = () => ({ BLOCKER: 0, CRITICAL: 0, MAJOR: 0, MINOR: 0, INFO: 0 });
  return { BUG: severities(), CODE_SMELL: severities(), VULNERABILITY: severities() };
}

/**
 * Open issues of every project, split by type and severity, from one server call. The type and
 * severity filters are then applied in the browser without asking the server again.
 */
export async function fetchIssueMatrices(organization: string, projectKeys: string[]) {
  const matrices: Record<string, IssueMatrix> = {};
  projectKeys.forEach((key) => {
    matrices[key] = emptyMatrix();
  });

  const { projects } = await getProjectIssues(organization);
  projects.forEach(({ issues, key }) => {
    const matrix = matrices[key];
    if (!matrix) {
      return;
    }
    ISSUE_TYPES.forEach((type) => {
      SEVERITIES.forEach((severity) => {
        matrix[type][severity] = issues[type]?.[severity] ?? 0;
      });
    });
  });
  return matrices;
}

/** Open issues in a matrix that match the type and severity filters. */
export function countIssues(matrix: IssueMatrix | undefined, filters: IssueFilters) {
  if (!matrix) {
    return 0;
  }
  return ISSUE_TYPES.filter((type) => !filters.type || filters.type === type).reduce(
    (sum, type) =>
      sum +
      SEVERITIES.filter((severity) => !filters.severity || filters.severity === severity).reduce(
        (typeSum, severity) => typeSum + matrix[type][severity],
        0,
      ),
    0,
  );
}

/**
 * The most recent background task state of each project, from one server call: a queued task
 * (in progress first, then pending) wins over the last executed one. Projects with no task at
 * all are left out.
 */
export async function fetchBackgroundTaskStatuses(organization: string, projectKeys: string[]) {
  const listed = new Set(projectKeys);
  const { projects } = await getProjectTaskStatuses(organization);
  const statuses: Record<string, TaskStatuses> = {};
  projects
    .filter(({ key }) => listed.has(key))
    .forEach(({ key, status }) => {
      statuses[key] = status;
    });
  return statuses;
}

export function getDateBounds(preset: DatePreset, range: DateRange, now: Date): DateRange {
  if (preset === DatePreset.Custom) {
    return range;
  }
  if (preset === DatePreset.AnyTime) {
    return {};
  }
  return { from: startOfDay(subDays(now, Number(preset) - 1)) };
}

export enum RecencyBucket {
  Week = 'week',
  Month = 'month',
  Quarter = 'quarter',
  Older = 'older',
  Never = 'never',
}

export const RECENCY_BUCKETS = [
  RecencyBucket.Week,
  RecencyBucket.Month,
  RecencyBucket.Quarter,
  RecencyBucket.Older,
  RecencyBucket.Never,
];

export function getRecencyBucket(analysisDate: string | undefined, now: Date) {
  if (!analysisDate) {
    return RecencyBucket.Never;
  }
  const age = differenceInCalendarDays(now, parseDate(analysisDate));
  if (age <= 7) {
    return RecencyBucket.Week;
  }
  if (age <= 30) {
    return RecencyBucket.Month;
  }
  if (age <= 90) {
    return RecencyBucket.Quarter;
  }
  return RecencyBucket.Older;
}

export const NO_TASK = 'NONE';
export type TaskFilter = TaskStatuses | typeof NO_TASK;

export interface AnalyticsFilterState {
  datePreset: DatePreset;
  dateRange: DateRange;
  projectKey: string | null;
  qualityGate?: AnalysisStatus;
  recency?: RecencyBucket;
  severity?: AnalyticsSeverity;
  taskStatus?: TaskFilter;
  type?: AnalyticsIssueType;
}

export const DEFAULT_FILTERS: AnalyticsFilterState = {
  datePreset: DatePreset.AnyTime,
  dateRange: {},
  projectKey: null,
};

/** Filters that pick projects; type and severity only change which issues are counted. */
type ProjectDimension = 'project' | 'date' | 'qualityGate' | 'taskStatus' | 'recency';

/**
 * Projects matching every project filter except the ignored one. A chart passes its own dimension
 * so it keeps showing all of its categories, with the selected one highlighted.
 */
export function filterProjects(
  projects: AnalyticsProject[],
  filters: AnalyticsFilterState,
  context: { now: Date; taskStatuses: Record<string, TaskStatuses> },
  ignore?: ProjectDimension,
) {
  const bounds = getDateBounds(filters.datePreset, filters.dateRange, context.now);
  const from = bounds.from ? startOfDay(bounds.from) : undefined;
  const to = bounds.to ? endOfDay(bounds.to) : undefined;

  return projects.filter((project) => {
    if (ignore !== 'project' && filters.projectKey !== null && project.key !== filters.projectKey) {
      return false;
    }
    if (ignore !== 'qualityGate' && filters.qualityGate && project.status !== filters.qualityGate) {
      return false;
    }
    if (
      ignore !== 'taskStatus' &&
      filters.taskStatus &&
      (context.taskStatuses[project.key] ?? NO_TASK) !== filters.taskStatus
    ) {
      return false;
    }
    if (
      ignore !== 'recency' &&
      filters.recency &&
      getRecencyBucket(project.analysisDate, context.now) !== filters.recency
    ) {
      return false;
    }
    if (ignore !== 'date' && (from !== undefined || to !== undefined)) {
      if (!project.analysisDate) {
        return false;
      }
      const analysisDate = parseDate(project.analysisDate);
      if ((from !== undefined && analysisDate < from) || (to !== undefined && analysisDate > to)) {
        return false;
      }
    }
    return true;
  });
}
