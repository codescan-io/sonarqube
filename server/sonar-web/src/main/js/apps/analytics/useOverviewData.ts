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

import * as React from 'react';
import { TaskStatuses } from '../../types/tasks';
import {
  AnalyticsProject,
  IssueMatrix,
  fetchBackgroundTaskStatuses,
  fetchIssueMatrices,
  fetchOrganizationProjects,
} from './utils';

export interface OverviewData {
  /** Estimated minutes to fix the open issues of each project. */
  effortMinutes: Record<string, number>;
  error: boolean;
  loadingIssues: boolean;
  loadingProjects: boolean;
  loadingTasks: boolean;
  matrices: Record<string, IssueMatrix>;
  projects: AnalyticsProject[];
  taskStatuses: Record<string, TaskStatuses>;
  totalProjects: number;
}

/**
 * Every project of the organization with its quality gate, open issues, remediation effort and background task
 * status. Projects come first, so the page can render; issues and tasks follow, each in one request.
 */
export function useOverviewData(organization: string): OverviewData {
  const [projects, setProjects] = React.useState<AnalyticsProject[]>([]);
  const [totalProjects, setTotalProjects] = React.useState(0);
  const [loadingProjects, setLoadingProjects] = React.useState(true);
  const [matrices, setMatrices] = React.useState<Record<string, IssueMatrix>>({});
  const [effortMinutes, setEffortMinutes] = React.useState<Record<string, number>>({});
  const [loadingIssues, setLoadingIssues] = React.useState(false);
  const [taskStatuses, setTaskStatuses] = React.useState<Record<string, TaskStatuses>>({});
  const [loadingTasks, setLoadingTasks] = React.useState(false);
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoadingProjects(true);
    setError(false);
    fetchOrganizationProjects(organization).then(
      (result) => {
        if (active) {
          setProjects(result.projects);
          setTotalProjects(result.total);
          setLoadingProjects(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoadingProjects(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [organization]);

  // Issues and background tasks are loaded once for every project; all filtering after
  // that happens in the browser, so clicking around the charts is instant.
  React.useEffect(() => {
    if (projects.length === 0) {
      return undefined;
    }
    let active = true;
    const keys = projects.map((p) => p.key);
    setLoadingIssues(true);
    setLoadingTasks(true);
    fetchIssueMatrices(organization, keys).then(
      (result) => {
        if (active) {
          setMatrices(result.matrices);
          setEffortMinutes(result.effortMinutes);
          setLoadingIssues(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoadingIssues(false);
        }
      },
    );
    fetchBackgroundTaskStatuses(organization, keys).then(
      (statuses) => {
        if (active) {
          setTaskStatuses(statuses);
          setLoadingTasks(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoadingTasks(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [organization, projects]);

  return {
    effortMinutes,
    error,
    loadingIssues,
    loadingProjects,
    loadingTasks,
    matrices,
    projects,
    taskStatuses,
    totalProjects,
  };
}
