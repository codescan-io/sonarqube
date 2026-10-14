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
import { Button, Text } from '@sonarsource/echoes-react';
import * as React from 'react';
import { ContentCell, Link, NumericalCell, Table, TableRow, themeColor } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { queryToSearchString } from '~sonar-aligned/helpers/urls';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  PullRequestAnalytics,
  PullRequestProjectStats,
  getPullRequestAnalytics,
} from '../../../api/codescan-analytics';
import DateTimeFormatter from '../../../components/intl/DateTimeFormatter';
import { hasMessage, translate, translateWithParameters } from '../../../helpers/l10n';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR } from './charts/colors';
import InlineBar from './charts/InlineBar';

interface Props {
  days: number;
  organization: string;
}

const PAGE_SIZE = 20;
const SALESFORCE = 'metadata_api';

export function projectTypeLabel(type: string) {
  return hasMessage('analytics.pr.type', type) ? translate('analytics.pr.type', type) : type;
}

/** The project's pull request list, where each pull request links to its analysis. */
function pullRequestsUrl(projectKey: string) {
  return { pathname: '/project/branches', search: queryToSearchString({ id: projectKey }) };
}

/**
 * How many pull requests each kind of project gets analyzed: Salesforce orgs, repositories CodeScan pulls from
 * GitHub, GitLab or Bitbucket, and projects scanned from the customer's own CI. Built on CodeScan's own data,
 * so it stays hidden when CodeScan's services do not answer.
 */
export default function PullRequestsPanel({ days, organization }: Readonly<Props>) {
  const [type, setType] = React.useState<string>();
  const [data, setData] = React.useState<PullRequestAnalytics>();
  const [rows, setRows] = React.useState<PullRequestProjectStats[]>([]);
  const [page, setPage] = React.useState(1);
  const [loading, setLoading] = React.useState(true);
  const [unavailable, setUnavailable] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    getPullRequestAnalytics({
      organizationKey: organization,
      days,
      type,
      page: 1,
      pageSize: PAGE_SIZE,
    }).then(
      (result) => {
        if (active) {
          setData(result);
          setRows(result.projects);
          setPage(1);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setUnavailable(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [organization, days, type]);

  const loadMore = () => {
    setLoading(true);
    getPullRequestAnalytics({
      organizationKey: organization,
      days,
      type,
      page: page + 1,
      pageSize: PAGE_SIZE,
    }).then(
      (result) => {
        setRows((current) => [...current, ...result.projects]);
        setPage(page + 1);
        setLoading(false);
      },
      () => setLoading(false),
    );
  };

  if (unavailable || data === undefined) {
    return null;
  }

  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const maxByType = Math.max(...data.byType.map((t) => t.pullRequestsAnalyzed), 1);
  const maxByProject = Math.max(...rows.map((p) => p.pullRequestsAnalyzed), 1);

  return (
    <div className="sw-flex sw-flex-col sw-gap-6 sw-mb-8">
      <ChartCard
        description={translate('analytics.pr.by_type.description')}
        loading={loading}
        title={translate('analytics.pr.by_type')}
      >
        <Table
          columnCount={TYPE_COLUMNS.length}
          columnWidths={TYPE_COLUMNS}
          header={
            <TableRow>
              <ContentCell>{translate('analytics.pr.project_type')}</ContentCell>
              <NumericalCell>{translate('analytics.pr.projects_with_prs')}</NumericalCell>
              <NumericalCell>{translate('analytics.pr.received')}</NumericalCell>
              <NumericalCell>{translate('analytics.pr.analyzed')}</NumericalCell>
              <NumericalCell>{translate('analytics.pr.analyses')}</NumericalCell>
              <NumericalCell>{translate('analytics.pr.failed')}</NumericalCell>
            </TableRow>
          }
        >
          {data.byType.map((stats) => {
            const selected = type === stats.type;
            return (
              <TableRow key={stats.type}>
                <ContentCell>
                  <TypeButton
                    aria-pressed={selected}
                    className={selected ? 'selected' : undefined}
                    onClick={() => setType(selected ? undefined : stats.type)}
                    type="button"
                  >
                    {projectTypeLabel(stats.type)}
                  </TypeButton>
                </ContentCell>
                <NumericalCell>
                  {translateWithParameters(
                    'analytics.pr.x_of_y',
                    integer(stats.projectsWithPullRequests),
                    integer(stats.projects),
                  )}
                </NumericalCell>
                <NumericalCell>
                  {stats.type.startsWith('ci') ? '-' : integer(stats.pullRequestsReceived)}
                </NumericalCell>
                <NumericalCell>
                  <InlineBar
                    color={MAGNITUDE_COLOR}
                    max={maxByType}
                    value={stats.pullRequestsAnalyzed}
                  />
                </NumericalCell>
                <NumericalCell>{integer(stats.analyses)}</NumericalCell>
                <NumericalCell>{integer(stats.failedAnalyses + stats.failedJobs)}</NumericalCell>
              </TableRow>
            );
          })}
        </Table>
        {data.byType.some((stats) => stats.type === SALESFORCE) && (
          <Text as="p" className="sw-mt-3" isSubdued>
            {translate('analytics.pr.salesforce_note')}
          </Text>
        )}
      </ChartCard>

      <ChartCard
        description={
          type
            ? translateWithParameters('analytics.pr.by_project.filtered', projectTypeLabel(type))
            : translate('analytics.pr.by_project.description')
        }
        loading={loading}
        title={translate('analytics.pr.by_project')}
      >
        {rows.length === 0 ? (
          <Text isSubdued>{translate('analytics.pr.none')}</Text>
        ) : (
          <>
            <Table
              columnCount={PROJECT_COLUMNS.length}
              columnWidths={PROJECT_COLUMNS}
              header={
                <TableRow>
                  <ContentCell>{translate('analytics.table.project')}</ContentCell>
                  <ContentCell>{translate('analytics.pr.project_type')}</ContentCell>
                  <NumericalCell>{translate('analytics.pr.received')}</NumericalCell>
                  <NumericalCell>{translate('analytics.pr.analyzed')}</NumericalCell>
                  <NumericalCell>{translate('analytics.pr.analyses')}</NumericalCell>
                  <NumericalCell>{translate('analytics.pr.failed')}</NumericalCell>
                  <NumericalCell>{translate('analytics.pr.last_analysis')}</NumericalCell>
                </TableRow>
              }
            >
              {rows.map((project) => (
                <TableRow key={project.projectKey}>
                  <ContentCell>
                    <Link to={pullRequestsUrl(project.projectKey)}>
                      {project.projectName ?? project.projectKey}
                    </Link>
                  </ContentCell>
                  <ContentCell>{projectTypeLabel(project.type)}</ContentCell>
                  <NumericalCell>
                    {project.type.startsWith('ci') ? '-' : integer(project.pullRequestsReceived)}
                  </NumericalCell>
                  <NumericalCell>
                    <InlineBar
                      color={MAGNITUDE_COLOR}
                      max={maxByProject}
                      value={project.pullRequestsAnalyzed}
                    />
                  </NumericalCell>
                  <NumericalCell>{integer(project.analyses)}</NumericalCell>
                  <NumericalCell>
                    {integer(project.failedAnalyses + project.failedJobs)}
                  </NumericalCell>
                  <NumericalCell className="sw-whitespace-nowrap">
                    {project.lastAnalysisAt ? (
                      <DateTimeFormatter date={project.lastAnalysisAt} short />
                    ) : (
                      '-'
                    )}
                  </NumericalCell>
                </TableRow>
              ))}
            </Table>
            <div className="sw-flex sw-items-center sw-justify-center sw-gap-4 sw-mt-4">
              <Text isSubdued>
                {translateWithParameters('x_of_y_shown', rows.length, data.total)}
              </Text>
              {rows.length < data.total && (
                <Button isDisabled={loading} onClick={loadMore}>
                  {translate('analytics.table.show_more')}
                </Button>
              )}
            </div>
          </>
        )}
      </ChartCard>
    </div>
  );
}

const TYPE_COLUMNS = ['28%', '16%', '14%', '14%', '14%', '14%'];
const PROJECT_COLUMNS = ['28%', '16%', '10%', '10%', '10%', '10%', '16%'];

const TypeButton = styled.button`
  padding: 2px 6px;
  border: none;
  border-radius: 4px;
  background: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover,
  &.selected {
    background-color: ${themeColor('dropdownMenuHover')};
    font-weight: 600;
  }
`;
