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
import { FlagMessage, Spinner } from '~design-system';
import { translate } from '../../../helpers/l10n';
import { TaskStatuses } from '../../../types/tasks';
import {
  AnalyticsFilterState,
  AnalyticsProject,
  DEFAULT_FILTERS,
  IssueMatrix,
  fetchBackgroundTaskStatuses,
  fetchIssueMatrices,
  fetchOrganizationProjects,
  filterProjects,
} from '../utils';
import AnalyticsCharts, { ProjectDimension } from './AnalyticsCharts';
import AnalyticsFilters from './AnalyticsFilters';
import AnalyticsSummary from './AnalyticsSummary';
import AnalyticsTable from './AnalyticsTable';

interface Props {
  organization: string;
}

export default function AnalyticsDashboard({ organization }: Readonly<Props>) {
  const [now] = React.useState(() => new Date());
  const [projects, setProjects] = React.useState<AnalyticsProject[]>([]);
  const [totalProjects, setTotalProjects] = React.useState(0);
  const [loadingProjects, setLoadingProjects] = React.useState(true);
  const [matrices, setMatrices] = React.useState<Record<string, IssueMatrix>>({});
  const [loadingIssues, setLoadingIssues] = React.useState(false);
  const [taskStatuses, setTaskStatuses] = React.useState<Record<string, TaskStatuses>>({});
  const [loadingTasks, setLoadingTasks] = React.useState(false);
  const [error, setError] = React.useState(false);
  const [filters, setFilters] = React.useState<AnalyticsFilterState>(DEFAULT_FILTERS);

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
          setMatrices(result);
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
    fetchBackgroundTaskStatuses(keys).then(
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

  const changeFilters = React.useCallback(
    (changes: Partial<AnalyticsFilterState>) =>
      setFilters((current) => ({ ...current, ...changes })),
    [],
  );
  const resetFilters = React.useCallback(() => setFilters(DEFAULT_FILTERS), []);

  const projectsIgnoring = React.useCallback(
    (dimension?: ProjectDimension) =>
      filterProjects(projects, filters, { now, taskStatuses }, dimension),
    [projects, filters, now, taskStatuses],
  );
  const selectedProjects = React.useMemo(() => projectsIgnoring(), [projectsIgnoring]);

  if (loadingProjects) {
    return <Spinner />;
  }

  return (
    <>
      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      <AnalyticsFilters
        filters={filters}
        onChange={changeFilters}
        onReset={resetFilters}
        projects={projects}
      />

      <AnalyticsSummary
        filters={filters}
        loading={loadingIssues}
        matrices={matrices}
        now={now}
        onToggleQualityGate={(status) =>
          changeFilters({ qualityGate: filters.qualityGate === status ? undefined : status })
        }
        projects={selectedProjects}
        projectsForQualityGate={projectsIgnoring('qualityGate')}
        totalProjects={totalProjects}
      />

      <AnalyticsCharts
        filters={filters}
        loadingIssues={loadingIssues}
        loadingTasks={loadingTasks}
        matrices={matrices}
        now={now}
        onChange={changeFilters}
        organization={organization}
        projectsIgnoring={projectsIgnoring}
        taskStatuses={taskStatuses}
      />

      <AnalyticsTable
        filters={filters}
        loading={loadingIssues}
        matrices={matrices}
        projects={selectedProjects}
        taskStatuses={taskStatuses}
      />
    </>
  );
}
