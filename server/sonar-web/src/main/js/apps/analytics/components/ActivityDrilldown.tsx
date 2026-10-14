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
  Button,
  ButtonIcon,
  ButtonVariety,
  IconX,
  LinkStandalone,
  Text,
  TextSize,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import {
  Card,
  ContentCell,
  FlagMessage,
  Link,
  NumericalCell,
  Spinner,
  Table,
  TableRow,
  ToggleButton,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  AnalyticsPaging,
  AnalyticsProjectActivity,
  AnalyticsTask,
  getOrganizationProjectActivity,
  getOrganizationQueuedTasks,
  getOrganizationTasks,
} from '../../../api/analytics';
import DateTimeFormatter from '../../../components/intl/DateTimeFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  getBranchUrl,
  getComponentBackgroundTaskUrl,
  getProjectUrl,
  getPullRequestUrl,
} from '../../../helpers/urls';
import TaskStatus from '../../background-tasks/components/TaskStatus';
import {
  ActivityDrilldown as Drilldown,
  DRILLDOWN_TASK_QUERIES,
  formatDuration,
} from '../activity';

interface Props {
  drilldown: Drilldown;
  onClose: () => void;
  organization: string;
  period: { from: string; timeZone?: string; to: string };
}

const PAGE_SIZE = 20;
const NO_VALUE = '-';

type Page =
  | { kind: 'tasks'; paging: AnalyticsPaging; rows: AnalyticsTask[] }
  | { kind: 'projects'; paging: AnalyticsPaging; rows: AnalyticsProjectActivity[] };

/**
 * The tasks or projects behind a headline tile, loaded a page at a time.
 */
export default function ActivityDrilldown(props: Readonly<Props>) {
  const { drilldown, onClose, organization, period } = props;
  const [analyzed, setAnalyzed] = React.useState(true);
  const [page, setPage] = React.useState<Page>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  const fetchPage = React.useCallback(
    (p: number): Promise<Page> => {
      const paging = { organization, p, ps: PAGE_SIZE };
      if (drilldown === Drilldown.Projects) {
        return getOrganizationProjectActivity({ ...paging, ...period, analyzed }).then((r) => ({
          kind: 'projects',
          paging: r.paging,
          rows: r.projects,
        }));
      }
      const tasks =
        drilldown === Drilldown.Queued
          ? getOrganizationQueuedTasks(paging)
          : getOrganizationTasks({ ...paging, ...period, ...DRILLDOWN_TASK_QUERIES[drilldown] });
      return tasks.then((r) => ({ kind: 'tasks', paging: r.paging, rows: r.tasks }));
    },
    [analyzed, drilldown, organization, period],
  );

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    fetchPage(1).then(
      (result) => {
        if (active) {
          setPage(result);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [fetchPage]);

  const loadMore = () => {
    if (!page) {
      return;
    }
    setLoading(true);
    fetchPage(page.paging.pageIndex + 1).then(
      (next) => {
        setPage(
          (current) =>
            current &&
            ({
              ...next,
              rows: [...current.rows, ...next.rows],
            } as Page),
        );
        setLoading(false);
      },
      () => {
        setError(true);
        setLoading(false);
      },
    );
  };

  const title = translate('analytics.activity.drilldown', drilldown);

  return (
    <Card aria-label={title} className="sw-mb-6" role="region">
      <div className="sw-flex sw-items-start sw-justify-between sw-gap-4 sw-mb-4">
        <div>
          <h3 className="sw-heading-sm">{title}</h3>
          <Text as="p" className="sw-mt-1" isSubdued>
            {translate(
              'analytics.activity.drilldown',
              drilldown === Drilldown.Projects && !analyzed ? 'not_analyzed' : drilldown,
              'description',
            )}
          </Text>
        </div>
        <ButtonIcon
          Icon={IconX}
          ariaLabel={translate('analytics.activity.drilldown.close')}
          onClick={onClose}
          variety={ButtonVariety.DefaultGhost}
        />
      </div>

      {drilldown === Drilldown.Projects && (
        <div className="sw-mb-4">
          <ToggleButton
            onChange={(value) => setAnalyzed(value === true)}
            options={[
              { label: translate('analytics.activity.drilldown.analyzed'), value: true },
              { label: translate('analytics.activity.drilldown.not_analyzed'), value: false },
            ]}
            value={analyzed}
          />
        </div>
      )}

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      {page === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          {page.rows.length === 0 && (
            <Text as="p" isSubdued>
              {translate('no_results')}
            </Text>
          )}
          {page.rows.length > 0 && page.kind === 'tasks' && (
            <TaskTable showErrors={drilldown === Drilldown.Failures} tasks={page.rows} />
          )}
          {page.rows.length > 0 && page.kind === 'projects' && (
            <ProjectTable analyzed={analyzed} projects={page.rows} />
          )}
          {page.rows.length > 0 && (
            <div className="sw-flex sw-items-center sw-justify-center sw-gap-4 sw-mt-4">
              <Text isSubdued>
                {translateWithParameters('x_of_y_shown', page.rows.length, page.paging.total)}
              </Text>
              {page.rows.length < page.paging.total && (
                <Button isDisabled={loading} onClick={loadMore}>
                  {translate('analytics.table.show_more')}
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

function projectLink(task: AnalyticsTask) {
  if (task.branch && task.target === 'BRANCH') {
    return getBranchUrl(task.projectKey, task.branch);
  }
  if (task.branch && task.target === 'PULL_REQUEST') {
    return getPullRequestUrl(task.projectKey, task.branch);
  }
  return getProjectUrl(task.projectKey);
}

const TASK_COLUMNS = ['36%', '14%', '18%', '11%', '11%', '10%'];

function TaskTable({
  showErrors,
  tasks,
}: Readonly<{ showErrors: boolean; tasks: AnalyticsTask[] }>) {
  return (
    <Table
      columnCount={TASK_COLUMNS.length}
      columnWidths={TASK_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.table.project')}</ContentCell>
          <ContentCell>{translate('analytics.activity.drilldown.status')}</ContentCell>
          <ContentCell>{translate('analytics.activity.drilldown.submitted')}</ContentCell>
          <NumericalCell>{translate('analytics.activity.drilldown.analysis_time')}</NumericalCell>
          <NumericalCell>{translate('analytics.activity.drilldown.queue_wait')}</NumericalCell>
          <ContentCell>
            <span className="sw-sr-only">{translate('analytics.activity.drilldown.details')}</span>
          </ContentCell>
        </TableRow>
      }
    >
      {tasks.map((task) => (
        <TableRow key={task.id}>
          <ContentCell>
            <span className="sw-flex sw-flex-col sw-min-w-0">
              <Link to={projectLink(task)}>{task.projectName ?? task.projectKey}</Link>
              <Text isSubdued size={TextSize.Small}>
                {task.target === 'MAIN' || !task.branch
                  ? translate('analytics.activity.target', task.target)
                  : `${translate('analytics.activity.target', task.target)}: ${task.branch}`}
              </Text>
              {showErrors && task.errorMessage && (
                <span className="sw-truncate" title={task.errorMessage}>
                  <Text isSubdued size={TextSize.Small}>
                    {task.errorMessage}
                  </Text>
                </span>
              )}
            </span>
          </ContentCell>
          <TaskStatus status={task.status} />
          <ContentCell className="sw-whitespace-nowrap">
            <DateTimeFormatter date={task.submittedAt} short />
          </ContentCell>
          <NumericalCell>
            {task.executionTimeMs === undefined ? NO_VALUE : formatDuration(task.executionTimeMs)}
          </NumericalCell>
          <NumericalCell>
            {task.queueTimeMs === undefined ? NO_VALUE : formatDuration(task.queueTimeMs)}
          </NumericalCell>
          <ContentCell>
            <LinkStandalone
              to={getComponentBackgroundTaskUrl(task.projectKey, task.status, task.type)}
            >
              {translate('analytics.activity.drilldown.details')}
            </LinkStandalone>
          </ContentCell>
        </TableRow>
      ))}
    </Table>
  );
}

const ANALYZED_COLUMNS = ['40%', '14%', '14%', '14%', '18%'];
const NOT_ANALYZED_COLUMNS = ['70%', '30%'];

function ProjectTable({
  analyzed,
  projects,
}: Readonly<{ analyzed: boolean; projects: AnalyticsProjectActivity[] }>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const lastAnalysis = (project: AnalyticsProjectActivity) =>
    project.lastAnalysisAt ? (
      <DateTimeFormatter date={project.lastAnalysisAt} short />
    ) : (
      translate('analytics.activity.drilldown.no_recent_analysis')
    );
  const name = (project: AnalyticsProjectActivity) => (
    <Link to={getProjectUrl(project.key)}>{project.name ?? project.key}</Link>
  );

  if (!analyzed) {
    return (
      <Table
        columnCount={NOT_ANALYZED_COLUMNS.length}
        columnWidths={NOT_ANALYZED_COLUMNS}
        header={
          <TableRow>
            <ContentCell>{translate('analytics.table.project')}</ContentCell>
            <NumericalCell>{translate('analytics.table.last_analysis')}</NumericalCell>
          </TableRow>
        }
      >
        {projects.map((project) => (
          <TableRow key={project.key}>
            <ContentCell>{name(project)}</ContentCell>
            <NumericalCell className="sw-whitespace-nowrap">{lastAnalysis(project)}</NumericalCell>
          </TableRow>
        ))}
      </Table>
    );
  }

  return (
    <Table
      columnCount={ANALYZED_COLUMNS.length}
      columnWidths={ANALYZED_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.table.project')}</ContentCell>
          <NumericalCell>{translate('analytics.activity.analyses')}</NumericalCell>
          <NumericalCell>{translate('background_task.status.FAILED')}</NumericalCell>
          <NumericalCell>{translate('analytics.activity.drilldown.avg_time')}</NumericalCell>
          <NumericalCell>{translate('analytics.table.last_analysis')}</NumericalCell>
        </TableRow>
      }
    >
      {projects.map((project) => (
        <TableRow key={project.key}>
          <ContentCell>{name(project)}</ContentCell>
          <NumericalCell className="sw-typo-semibold">{integer(project.analyses)}</NumericalCell>
          <NumericalCell>
            {project.failed > 0 ? (
              <LinkStandalone to={getComponentBackgroundTaskUrl(project.key, 'FAILED', 'REPORT')}>
                {integer(project.failed)}
              </LinkStandalone>
            ) : (
              integer(project.failed)
            )}
          </NumericalCell>
          <NumericalCell>
            {project.avgExecutionTimeMs === undefined
              ? NO_VALUE
              : formatDuration(project.avgExecutionTimeMs)}
          </NumericalCell>
          <NumericalCell className="sw-whitespace-nowrap">{lastAnalysis(project)}</NumericalCell>
        </TableRow>
      ))}
    </Table>
  );
}
