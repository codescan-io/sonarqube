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
import { useOverviewData } from '../useOverviewData';
import { AnalyticsFilterState, AnalyticsProject, DEFAULT_FILTERS, filterProjects } from '../utils';
import AnalyticsCharts, { ProjectDimension } from './AnalyticsCharts';
import AnalyticsFilters from './AnalyticsFilters';
import AnalyticsSummary from './AnalyticsSummary';
import AnalyticsTable from './AnalyticsTable';
import FocusList from './FocusList';
import ProjectScorecard from './ProjectScorecard';
import TeamLeaderboard from './TeamLeaderboard';

interface Props {
  organization: string;
}

export default function AnalyticsDashboard({ organization }: Readonly<Props>) {
  const [now] = React.useState(() => new Date());
  const {
    effortMinutes,
    error,
    loadingIssues,
    loadingProjects,
    loadingTasks,
    matrices,
    projects,
    taskStatuses,
    totalProjects,
  } = useOverviewData(organization);
  const [filters, setFilters] = React.useState<AnalyticsFilterState>(DEFAULT_FILTERS);
  const [scorecard, setScorecard] = React.useState<AnalyticsProject>();

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

      <TeamLeaderboard
        effortMinutes={effortMinutes}
        loading={loadingIssues || loadingTasks}
        matrices={matrices}
        now={now}
        onSelect={(tag) => changeFilters({ tag: filters.tag === tag ? undefined : tag })}
        projects={projectsIgnoring('tag')}
        selectedTag={filters.tag}
        taskStatuses={taskStatuses}
      />

      <FocusList
        loading={loadingIssues || loadingTasks}
        matrices={matrices}
        now={now}
        onOpen={setScorecard}
        projects={selectedProjects}
        taskStatuses={taskStatuses}
      />

      <AnalyticsTable
        filters={filters}
        loading={loadingIssues}
        matrices={matrices}
        onOpenScorecard={setScorecard}
        projects={selectedProjects}
        taskStatuses={taskStatuses}
      />

      {scorecard && (
        <ProjectScorecard
          analysisDate={scorecard.analysisDate}
          name={scorecard.name}
          onClose={() => setScorecard(undefined)}
          projectKey={scorecard.key}
        />
      )}
    </>
  );
}
