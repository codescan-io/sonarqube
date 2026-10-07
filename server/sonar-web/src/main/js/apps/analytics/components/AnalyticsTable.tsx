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

import styled from '@emotion/styled';
import {
  Button,
  IconTriangleDown,
  IconTriangleUp,
  LinkHighlight,
  LinkStandalone,
  Text,
} from '@sonarsource/echoes-react';
import { orderBy } from 'lodash';
import * as React from 'react';
import {
  ContentCell,
  InputSearch,
  Link,
  NumericalCell,
  QualityGateIndicator,
  Table,
  TableRow,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { getComponentIssuesUrl } from '~sonar-aligned/helpers/urls';
import { MetricType } from '~sonar-aligned/types/metrics';
import DateTimeFormatter from '../../../components/intl/DateTimeFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getProjectUrl } from '../../../helpers/urls';
import { TaskStatuses } from '../../../types/tasks';
import TaskStatus from '../../background-tasks/components/TaskStatus';
import {
  AnalysisStatus,
  AnalyticsFilterState,
  AnalyticsProject,
  ISSUE_TYPES,
  IssueMatrix,
  countIssues,
} from '../utils';

interface Props {
  filters: AnalyticsFilterState;
  loading: boolean;
  matrices: Record<string, IssueMatrix>;
  projects: AnalyticsProject[];
  taskStatuses: Record<string, TaskStatuses>;
}

type SortKey = 'name' | 'status' | 'issues' | 'analysisDate';

const STATUS_INDICATOR = {
  [AnalysisStatus.Passed]: 'OK',
  [AnalysisStatus.Failed]: 'ERROR',
  [AnalysisStatus.NotAvailable]: 'NONE',
} as const;

const STATUS_ORDER = {
  [AnalysisStatus.Failed]: 0,
  [AnalysisStatus.Passed]: 1,
  [AnalysisStatus.NotAvailable]: 2,
};

// Project | Analysis Status | Issue Type | Background Tasks | Last Analysis | Total Issues
const COLUMN_WIDTHS = ['21%', '13%', '25%', '14%', '16%', '11%'];
const PAGE_SIZE = 10;
const NO_VALUE = '-';

export default function AnalyticsTable(props: Readonly<Props>) {
  const { filters, loading, matrices, projects, taskStatuses } = props;
  const [query, setQuery] = React.useState('');
  const [sort, setSort] = React.useState<{ asc: boolean; key: SortKey }>({
    key: 'issues',
    asc: false,
  });
  const [visible, setVisible] = React.useState(PAGE_SIZE);

  const issuesOf = (key: string) => countIssues(matrices[key], filters);

  const rows = React.useMemo(() => {
    const search = query.trim().toLowerCase();
    const matching = projects.filter(
      ({ key, name }) =>
        search === '' || name.toLowerCase().includes(search) || key.toLowerCase().includes(search),
    );
    const sortValue = {
      name: (p: AnalyticsProject) => p.name.toLowerCase(),
      status: (p: AnalyticsProject) => STATUS_ORDER[p.status],
      issues: (p: AnalyticsProject) => countIssues(matrices[p.key], filters),
      analysisDate: (p: AnalyticsProject) => p.analysisDate ?? '',
    }[sort.key];
    return orderBy(matching, [sortValue, 'name'], [sort.asc ? 'asc' : 'desc', 'asc']);
  }, [projects, query, sort, matrices, filters]);

  const changeSort = (key: SortKey) =>
    setSort((current) => ({
      key,
      asc: current.key === key ? !current.asc : key === 'name',
    }));

  const header = (key: SortKey, label: string) => (
    <SortButton onClick={() => changeSort(key)} type="button">
      {label}
      {sort.key === key && (sort.asc ? <IconTriangleUp /> : <IconTriangleDown />)}
    </SortButton>
  );

  const renderIssueTypes = (projectKey: string) => {
    const types = ISSUE_TYPES.filter(
      (type) =>
        (!filters.type || filters.type === type) &&
        countIssues(matrices[projectKey], { type, severity: filters.severity }) > 0,
    );
    if (types.length === 0) {
      return NO_VALUE;
    }
    return types.map((type) => (
      <span className="sw-flex sw-items-center sw-gap-1" key={type}>
        <Text isSubdued>{translate('issue.type', type, 'plural')}</Text>
        <LinkStandalone
          highlight={LinkHighlight.CurrentColor}
          to={getComponentIssuesUrl(projectKey, {
            resolved: 'false',
            types: type,
            severities: filters.severity,
          })}
        >
          {formatMeasure(
            countIssues(matrices[projectKey], { type, severity: filters.severity }),
            MetricType.Integer,
          )}
        </LinkStandalone>
      </span>
    ));
  };

  return (
    <section>
      <div className="sw-flex sw-items-center sw-justify-between sw-gap-4 sw-mb-3">
        <h3 className="sw-heading-sm">{translate('analytics.table.title')}</h3>
        <div className="sw-flex sw-items-center sw-gap-4">
          <Text isSubdued>
            {translateWithParameters('x_of_y_shown', Math.min(visible, rows.length), rows.length)}
          </Text>
          <InputSearch
            onChange={(value) => {
              setQuery(value);
              setVisible(PAGE_SIZE);
            }}
            placeholder={translate('analytics.table.search')}
            size="medium"
            value={query}
          />
        </div>
      </div>

      {rows.length === 0 ? (
        <Text as="p" className="sw-my-4" isSubdued>
          {translate('no_results')}
        </Text>
      ) : (
        <div className={loading ? 'sw-opacity-50' : undefined}>
          <Table
            columnCount={COLUMN_WIDTHS.length}
            columnWidths={COLUMN_WIDTHS}
            header={
              <TableRow>
                <ContentCell>{header('name', translate('analytics.table.project'))}</ContentCell>
                <ContentCell className="sw-whitespace-nowrap">
                  {header('status', translate('analytics.table.analysis_status'))}
                </ContentCell>
                <ContentCell className="sw-whitespace-nowrap">
                  {translate('analytics.table.issue_type')}
                </ContentCell>
                <ContentCell className="sw-whitespace-nowrap">
                  {translate('background_tasks.page')}
                </ContentCell>
                <NumericalCell className="sw-whitespace-nowrap">
                  {header('analysisDate', translate('analytics.table.last_analysis'))}
                </NumericalCell>
                <NumericalCell className="sw-whitespace-nowrap">
                  {header('issues', translate('analytics.table.total_issues'))}
                </NumericalCell>
              </TableRow>
            }
          >
            {rows.slice(0, visible).map((project) => (
              <TableRow key={project.key}>
                <ContentCell>
                  <Link to={getProjectUrl(project.key)}>{project.name}</Link>
                </ContentCell>
                <ContentCell className="sw-whitespace-nowrap">
                  <QualityGateIndicator
                    className="sw-mr-2"
                    size="sm"
                    status={STATUS_INDICATOR[project.status]}
                  />
                  {translate('analytics.status', project.status)}
                </ContentCell>
                <ContentCell className="sw-whitespace-nowrap sw-gap-4">
                  {renderIssueTypes(project.key)}
                </ContentCell>
                {taskStatuses[project.key] ? (
                  <TaskStatus status={taskStatuses[project.key]} />
                ) : (
                  <ContentCell>{NO_VALUE}</ContentCell>
                )}
                <NumericalCell className="sw-whitespace-nowrap">
                  {project.analysisDate ? (
                    <DateTimeFormatter date={project.analysisDate} short />
                  ) : (
                    NO_VALUE
                  )}
                </NumericalCell>
                <NumericalCell className="sw-typo-semibold">
                  {formatMeasure(issuesOf(project.key), MetricType.Integer)}
                </NumericalCell>
              </TableRow>
            ))}
          </Table>
        </div>
      )}

      {visible < rows.length && (
        <div className="sw-flex sw-justify-center sw-mt-4">
          <Button onClick={() => setVisible(visible + PAGE_SIZE)}>
            {translate('analytics.table.show_more')}
          </Button>
        </div>
      )}
    </section>
  );
}

const SortButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;
