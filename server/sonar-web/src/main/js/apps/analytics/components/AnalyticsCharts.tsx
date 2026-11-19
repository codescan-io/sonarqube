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

import { LinkStandalone, Text } from '@sonarsource/echoes-react';
import { orderBy } from 'lodash';
import * as React from 'react';
import { ClockIcon, FlagErrorIcon, FlagSuccessIcon, FlagWarningIcon } from '~design-system';
import { translate } from '../../../helpers/l10n';
import { getOrgIssuesUrl } from '../../../helpers/urls';
import { TaskStatuses } from '../../../types/tasks';
import { useIsReport } from '../report';
import {
  AnalysisStatus,
  AnalyticsFilterState,
  AnalyticsProject,
  ISSUE_TYPES,
  IssueMatrix,
  NO_TASK,
  RECENCY_BUCKETS,
  RecencyBucket,
  TaskFilter,
  countIssues,
  getRecencyBucket,
} from '../utils';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { ISSUE_TYPE_COLORS, MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import Donut from './charts/Donut';
import StackedBars from './charts/StackedBars';

export type ProjectDimension = 'project' | 'qualityGate' | 'taskStatus' | 'recency' | 'tag';

interface Props {
  filters: AnalyticsFilterState;
  loadingIssues: boolean;
  loadingTasks: boolean;
  matrices: Record<string, IssueMatrix>;
  now: Date;
  onChange: (changes: Partial<AnalyticsFilterState>) => void;
  organization: string;
  /** Projects matching every filter except the given one (all filters when omitted). */
  projectsIgnoring: (dimension?: ProjectDimension) => AnalyticsProject[];
  taskStatuses: Record<string, TaskStatuses>;
}

const TOP_PROJECTS = 10;

const TASK_STATUSES: Array<{ color: string; icon: React.ReactNode; status: TaskStatuses }> = [
  { color: STATUS_COLORS.success, icon: <FlagSuccessIcon />, status: TaskStatuses.Success },
  { color: STATUS_COLORS.danger, icon: <FlagErrorIcon />, status: TaskStatuses.Failed },
  { color: STATUS_COLORS.warning, icon: <FlagWarningIcon />, status: TaskStatuses.Canceled },
  { color: MAGNITUDE_COLOR, icon: <ClockIcon />, status: TaskStatuses.InProgress },
  { color: STATUS_COLORS.info, icon: <ClockIcon />, status: TaskStatuses.Pending },
];

export default function AnalyticsCharts(props: Readonly<Props>) {
  const isReport = useIsReport();
  const { filters, loadingIssues, loadingTasks, matrices, now, onChange, organization } = props;
  const { projectsIgnoring, taskStatuses } = props;

  const toggle = <K extends keyof AnalyticsFilterState>(key: K, value: AnalyticsFilterState[K]) =>
    onChange({
      [key]: filters[key] === value ? undefined : value,
    } as Partial<AnalyticsFilterState>);

  // Quality gate
  const gateProjects = projectsIgnoring('qualityGate');
  const countGate = (status: AnalysisStatus) =>
    gateProjects.filter((project) => project.status === status).length;
  const passed = countGate(AnalysisStatus.Passed);
  const failed = countGate(AnalysisStatus.Failed);

  // Top projects
  const visibleTypes = ISSUE_TYPES.filter((type) => !filters.type || filters.type === type);
  const topProjects = orderBy(
    projectsIgnoring('project')
      .map((project) => ({ project, total: countIssues(matrices[project.key], filters) }))
      .filter(({ total }) => total > 0),
    ['total'],
    ['desc'],
  ).slice(0, TOP_PROJECTS);

  const viewIssues = (
    <LinkStandalone
      to={getOrgIssuesUrl(
        {
          resolved: 'false',
          types: filters.type,
          severities: filters.severity,
          projects: filters.projectKey ?? undefined,
        },
        organization,
      )}
    >
      {translate('analytics.chart.view_issues')}
    </LinkStandalone>
  );

  return (
    <>
      {!isReport && (
        <Text as="p" className="sw-mb-3" isSubdued>
          {translate('analytics.chart.click_hint')}
        </Text>
      )}
      <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
        <ChartCard className="sw-col-span-4" title={translate('analytics.chart.quality_gate')}>
          <Donut
            centerLabel={translate('analytics.chart.pass_rate_label')}
            centerValue={
              passed + failed > 0 ? `${Math.round((passed / (passed + failed)) * 100)}%` : '-'
            }
            items={[
              { status: AnalysisStatus.Passed, color: STATUS_COLORS.success, value: passed },
              { status: AnalysisStatus.Failed, color: STATUS_COLORS.danger, value: failed },
              {
                status: AnalysisStatus.NotAvailable,
                color: STATUS_COLORS.neutral,
                value: countGate(AnalysisStatus.NotAvailable),
              },
            ].map(({ color, status, value }) => ({
              color,
              key: status,
              label: translate('analytics.status', status),
              value,
            }))}
            onSelect={(key) => toggle('qualityGate', key as AnalysisStatus)}
            selectedKey={filters.qualityGate}
          />
        </ChartCard>

        <ChartCard
          action={viewIssues}
          className="sw-col-span-8"
          description={translate('analytics.chart.top_projects.description')}
          loading={loadingIssues}
          title={translate('analytics.chart.top_projects')}
        >
          {topProjects.length > 0 ? (
            <StackedBars
              onSelect={(key) => onChange({ projectKey: filters.projectKey === key ? null : key })}
              rows={topProjects.map(({ project }) => ({
                key: project.key,
                label: project.name,
                values: Object.fromEntries(
                  visibleTypes.map((type) => [
                    type,
                    countIssues(matrices[project.key], { type, severity: filters.severity }),
                  ]),
                ),
              }))}
              selectedKey={filters.projectKey ?? undefined}
              series={visibleTypes.map((type) => ({
                color: ISSUE_TYPE_COLORS[type],
                key: type,
                label: translate('issue.type', type, 'plural'),
              }))}
            />
          ) : (
            <Text isSubdued>{translate('analytics.chart.no_issues')}</Text>
          )}
        </ChartCard>

        <ChartCard
          className="sw-col-span-6"
          loading={loadingTasks}
          title={translate('analytics.chart.background_tasks')}
        >
          <BarList
            items={[
              ...TASK_STATUSES.map(({ color, icon, status }) => ({
                color,
                icon,
                key: status as TaskFilter,
                label: translate('background_task.status', status),
              })),
              {
                color: STATUS_COLORS.neutral,
                icon: undefined,
                key: NO_TASK as TaskFilter,
                label: translate('analytics.chart.no_task'),
              },
            ].map((item) => ({
              ...item,
              value: projectsIgnoring('taskStatus').filter(
                ({ key }) => (taskStatuses[key] ?? NO_TASK) === item.key,
              ).length,
            }))}
            onSelect={(key) => toggle('taskStatus', key as TaskFilter)}
            selectedKey={filters.taskStatus}
          />
        </ChartCard>

        <ChartCard className="sw-col-span-6" title={translate('analytics.chart.analysis_age')}>
          <BarList
            items={RECENCY_BUCKETS.map((bucket) => ({
              color: MAGNITUDE_COLOR,
              key: bucket,
              label: translate('analytics.recency', bucket),
              value: projectsIgnoring('recency').filter(
                ({ analysisDate }) => getRecencyBucket(analysisDate, now) === bucket,
              ).length,
            }))}
            onSelect={(key) => toggle('recency', key as RecencyBucket)}
            selectedKey={filters.recency}
          />
        </ChartCard>
      </div>
    </>
  );
}
