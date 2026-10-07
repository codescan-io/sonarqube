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

import {
  IconBug,
  IconCodeSmell,
  IconVulnerability,
  LinkStandalone,
  Text,
} from '@sonarsource/echoes-react';
import { orderBy } from 'lodash';
import * as React from 'react';
import { ClockIcon, FlagErrorIcon, FlagSuccessIcon, FlagWarningIcon } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { translate } from '../../../helpers/l10n';
import { getOrgIssuesUrl } from '../../../helpers/urls';
import { TaskStatuses } from '../../../types/tasks';
import {
  AnalysisStatus,
  AnalyticsFilterState,
  AnalyticsIssueType,
  AnalyticsProject,
  AnalyticsSeverity,
  ISSUE_TYPES,
  IssueMatrix,
  NO_TASK,
  RECENCY_BUCKETS,
  RecencyBucket,
  SEVERITIES,
  TaskFilter,
  countIssues,
  getRecencyBucket,
} from '../utils';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { ISSUE_TYPE_COLORS, MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import Donut from './charts/Donut';
import Heatmap from './charts/Heatmap';
import StackedBars from './charts/StackedBars';

export type ProjectDimension = 'project' | 'qualityGate' | 'taskStatus' | 'recency';

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

const ISSUE_TYPE_ICONS: Record<AnalyticsIssueType, React.ReactNode> = {
  BUG: <IconBug />,
  CODE_SMELL: <IconCodeSmell />,
  VULNERABILITY: <IconVulnerability />,
};

const TASK_STATUSES: Array<{ color: string; icon: React.ReactNode; status: TaskStatuses }> = [
  { color: STATUS_COLORS.success, icon: <FlagSuccessIcon />, status: TaskStatuses.Success },
  { color: STATUS_COLORS.danger, icon: <FlagErrorIcon />, status: TaskStatuses.Failed },
  { color: STATUS_COLORS.warning, icon: <FlagWarningIcon />, status: TaskStatuses.Canceled },
  { color: MAGNITUDE_COLOR, icon: <ClockIcon />, status: TaskStatuses.InProgress },
  { color: STATUS_COLORS.info, icon: <ClockIcon />, status: TaskStatuses.Pending },
];

export default function AnalyticsCharts(props: Readonly<Props>) {
  const { filters, loadingIssues, loadingTasks, matrices, now, onChange, organization } = props;
  const { projectsIgnoring, taskStatuses } = props;

  const selection = projectsIgnoring();
  const sumIssues = (
    projects: AnalyticsProject[],
    type?: AnalyticsIssueType,
    severity?: AnalyticsSeverity,
  ) => projects.reduce((sum, { key }) => sum + countIssues(matrices[key], { type, severity }), 0);
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

  // Type x severity
  const matrixValues = Object.fromEntries(
    ISSUE_TYPES.map((type) => [
      type,
      Object.fromEntries(
        SEVERITIES.map((severity) => [severity, sumIssues(selection, type, severity)]),
      ),
    ]),
  );
  const pairs = ISSUE_TYPES.flatMap((type) =>
    SEVERITIES.map((severity) => ({ type, severity, count: matrixValues[type][severity] })),
  );
  const topPair = orderBy(pairs, ['count'], ['desc']).find(({ count }) => count > 0);
  const pairTotal = pairs.reduce((sum, { count }) => sum + count, 0);
  const highSeverityShare =
    pairTotal > 0
      ? Math.round(
          (pairs
            .filter(({ severity }) => severity === 'BLOCKER' || severity === 'CRITICAL')
            .reduce((sum, { count }) => sum + count, 0) /
            pairTotal) *
            100,
        )
      : 0;

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
      <Text as="p" className="sw-mb-3" isSubdued>
        {translate('analytics.chart.click_hint')}
      </Text>
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
          className="sw-col-span-4"
          loading={loadingIssues}
          title={translate('analytics.chart.issues_by_type')}
        >
          <BarList
            items={ISSUE_TYPES.map((type) => ({
              color: ISSUE_TYPE_COLORS[type],
              icon: ISSUE_TYPE_ICONS[type],
              key: type,
              label: translate('issue.type', type, 'plural'),
              value: sumIssues(selection, type, filters.severity),
            }))}
            onSelect={(key) => toggle('type', key as AnalyticsIssueType)}
            selectedKey={filters.type}
          />
        </ChartCard>

        <ChartCard
          className="sw-col-span-4"
          loading={loadingIssues}
          title={translate('analytics.chart.issues_by_severity')}
        >
          <BarList
            items={SEVERITIES.map((severity) => ({
              color: MAGNITUDE_COLOR,
              key: severity,
              label: translate('severity', severity),
              value: sumIssues(selection, filters.type, severity),
            }))}
            onSelect={(key) => toggle('severity', key as AnalyticsSeverity)}
            selectedKey={filters.severity}
          />
        </ChartCard>

        <ChartCard
          className="sw-col-span-7"
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
          className="sw-col-span-5"
          description={translate('analytics.chart.type_severity.description')}
          loading={loadingIssues}
          title={translate('analytics.chart.type_severity')}
        >
          <Heatmap
            columns={SEVERITIES.map((severity) => ({
              key: severity,
              label: translate('severity', severity),
            }))}
            onSelect={(type, severity) => {
              const same = filters.type === type && filters.severity === severity;
              onChange({
                type: same ? undefined : (type as AnalyticsIssueType),
                severity: same ? undefined : (severity as AnalyticsSeverity),
              });
            }}
            rows={ISSUE_TYPES.map((type) => ({
              key: type,
              label: translate('issue.type', type, 'plural'),
            }))}
            selected={{ row: filters.type, column: filters.severity }}
            values={matrixValues}
          />
          {topPair && (
            <ul className="sw-flex sw-flex-col sw-gap-3 sw-mt-6">
              <Insight
                label={translate('analytics.chart.insight.top_pair')}
                value={`${translate('issue.type', topPair.type, 'plural')} · ${translate(
                  'severity',
                  topPair.severity,
                )} (${formatMeasure(topPair.count, MetricType.Integer)})`}
              />
              <Insight
                label={translate('analytics.chart.insight.high_severity')}
                value={`${highSeverityShare}%`}
              />
            </ul>
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

function Insight({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <li className="sw-flex sw-items-center sw-justify-between sw-gap-4">
      <Text isSubdued>{label}</Text>
      <Text className="sw-typo-semibold">{value}</Text>
    </li>
  );
}
