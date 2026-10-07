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
  IconCalendar,
  IconCheckCircle,
  IconError,
  IconProject,
  IconWarning,
} from '@sonarsource/echoes-react';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  AnalysisStatus,
  AnalyticsFilterState,
  AnalyticsProject,
  IssueMatrix,
  RecencyBucket,
  countIssues,
  getRecencyBucket,
} from '../utils';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import StatTile from './StatTile';

interface Props {
  filters: AnalyticsFilterState;
  loading: boolean;
  matrices: Record<string, IssueMatrix>;
  now: Date;
  onToggleQualityGate: (status: AnalysisStatus) => void;
  projects: AnalyticsProject[];
  /** The selected projects, ignoring the quality gate filter, for the Passed/Failed tiles. */
  projectsForQualityGate: AnalyticsProject[];
  totalProjects: number;
}

export default function AnalyticsSummary(props: Readonly<Props>) {
  const { filters, loading, matrices, now, onToggleQualityGate, projects } = props;
  const { projectsForQualityGate, totalProjects } = props;

  const format = (value: number) => formatMeasure(value, MetricType.Integer);
  const countStatus = (status: AnalysisStatus) =>
    projectsForQualityGate.filter((project) => project.status === status).length;
  const openIssues = projects.reduce(
    (sum, { key }) => sum + countIssues(matrices[key], filters),
    0,
  );
  const analyzedRecently = projects.filter(({ analysisDate }) =>
    [RecencyBucket.Week, RecencyBucket.Month].includes(getRecencyBucket(analysisDate, now)),
  ).length;

  return (
    <div className="sw-grid sw-grid-cols-5 sw-gap-4 sw-mb-6">
      <StatTile
        color={MAGNITUDE_COLOR}
        footnote={
          projects.length === totalProjects
            ? undefined
            : translateWithParameters('analytics.summary.of_total', format(totalProjects))
        }
        icon={<IconProject />}
        label={translate('analytics.summary.total_projects')}
      >
        {format(projects.length)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.success}
        icon={<IconCheckCircle />}
        label={translate('analytics.summary.passed')}
        onClick={() => onToggleQualityGate(AnalysisStatus.Passed)}
        selected={filters.qualityGate === AnalysisStatus.Passed}
      >
        {format(countStatus(AnalysisStatus.Passed))}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.danger}
        icon={<IconError />}
        label={translate('analytics.summary.failed')}
        onClick={() => onToggleQualityGate(AnalysisStatus.Failed)}
        selected={filters.qualityGate === AnalysisStatus.Failed}
      >
        {format(countStatus(AnalysisStatus.Failed))}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.warning}
        icon={<IconWarning />}
        label={translate('analytics.summary.total_issues')}
        loading={loading}
      >
        {format(openIssues)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.info}
        icon={<IconCalendar />}
        label={translate('analytics.summary.analyzed_recently')}
      >
        {format(analyzedRecently)}
      </StatTile>
    </div>
  );
}
