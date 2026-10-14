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
  IconClock,
  IconProject,
  IconRefresh,
  IconWarning,
  Text,
} from '@sonarsource/echoes-react';
import { format as formatDate, parseISO } from 'date-fns';
import * as React from 'react';
import { FlagMessage, Spinner, ToggleButton } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  AnalysisTarget,
  OrganizationActivity,
  OrganizationQueue,
  getOrganizationActivity,
  getOrganizationQueue,
} from '../../../api/analytics';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  ACTIVITY_PERIODS,
  ActivityPeriod,
  DEFAULT_ACTIVITY_PERIOD,
  ActivityDrilldown as Drilldown,
  SLOW_QUEUE_MS,
  formatChange,
  formatDuration,
  getActivityRequest,
  getChange,
  getSuccessRate,
} from '../activity';
import ActivityDrilldown from './ActivityDrilldown';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import ColumnTimeline from './charts/ColumnTimeline';
import { toDelta } from './charts/Delta';
import LineTimeline from './charts/LineTimeline';
import PullRequestsPanel from './PullRequestsPanel';
import StatTile from './StatTile';

interface Props {
  organization: string;
}

const TARGETS: AnalysisTarget[] = ['MAIN', 'BRANCH', 'PULL_REQUEST', 'UNKNOWN'];

const STATUS_SERIES = [
  {
    color: STATUS_COLORS.success,
    key: 'success',
    label: translate('background_task.status.SUCCESS'),
  },
  { color: STATUS_COLORS.danger, key: 'failed', label: translate('background_task.status.FAILED') },
  {
    color: STATUS_COLORS.warning,
    key: 'canceled',
    label: translate('background_task.status.CANCELED'),
  },
];

const TIME_SERIES = [
  { color: MAGNITUDE_COLOR, key: 'median', label: translate('analytics.activity.median') },
  { color: MAGNITUDE_COLOR, dashed: true, key: 'p90', label: translate('analytics.activity.p90') },
];

/**
 * How much the organization analyses and how long it takes, over the last 7, 30 or 90 days,
 * compared with the period just before.
 */
export default function ActivityDashboard({ organization }: Readonly<Props>) {
  const [period, setPeriod] = React.useState<ActivityPeriod>(DEFAULT_ACTIVITY_PERIOD);
  const [activity, setActivity] = React.useState<OrganizationActivity>();
  const [queue, setQueue] = React.useState<OrganizationQueue>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);
  const [drilldown, setDrilldown] = React.useState<Drilldown>();
  const request = React.useMemo(() => getActivityRequest(period, new Date()), [period]);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    Promise.all([
      getOrganizationActivity({ organization, ...request }),
      getOrganizationQueue(organization),
    ]).then(
      ([activityResult, queueResult]) => {
        if (active) {
          setActivity(activityResult);
          setQueue(queueResult);
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
  }, [organization, request]);

  const toggleDrilldown = (value: Drilldown) =>
    setDrilldown((current) => (current === value ? undefined : value));

  return (
    <>
      <div className="sw-flex sw-items-center sw-gap-4 sw-mb-4">
        <ToggleButton
          label={translate('analytics.activity.period')}
          onChange={(value) => setPeriod(value as ActivityPeriod)}
          options={ACTIVITY_PERIODS.map((days) => ({
            label: translate('analytics.filter.date', String(days)),
            value: days,
          }))}
          value={period}
        />
        <Text isSubdued>{translate('analytics.activity.compared_hint')}</Text>
      </div>

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      {activity === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <Text as="p" className="sw-mb-3" isSubdued>
            {translate('analytics.activity.click_hint')}
          </Text>
          <ActivityTiles
            activity={activity}
            onSelect={toggleDrilldown}
            queue={queue}
            selected={drilldown}
          />
          {drilldown && (
            <ActivityDrilldown
              drilldown={drilldown}
              onClose={() => setDrilldown(undefined)}
              organization={organization}
              period={{ from: request.from, timeZone: request.timeZone, to: request.to }}
            />
          )}
          <ActivityCharts activity={activity} />
          <PullRequestsPanel days={period} organization={organization} />
        </div>
      )}
    </>
  );
}

interface TilesProps {
  activity: OrganizationActivity;
  onSelect: (drilldown: Drilldown) => void;
  queue?: OrganizationQueue;
  selected?: Drilldown;
}

function ActivityTiles({ activity, onSelect, queue, selected }: Readonly<TilesProps>) {
  const { analyses, previous, totalProjects } = activity;
  const drill = (drilldown: Drilldown) => ({
    onClick: () => onSelect(drilldown),
    selected: selected === drilldown,
  });
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const context = translate('analytics.activity.vs_previous_period');
  const percentDelta = (change: number | undefined, upIsGood: boolean) =>
    change === undefined ? undefined : toDelta(change, (r) => formatChange(r), upIsGood, context);
  const { timeline } = activity;

  const totalChange = getChange(analyses.total, previous.analyses.total);
  const successRate = getSuccessRate(analyses);
  const previousSuccessRate = getSuccessRate(previous.analyses);
  const medianChange = getChange(
    analyses.medianExecutionTimeMs,
    previous.analyses.medianExecutionTimeMs,
  );
  const projectsChange = getChange(analyses.projects, previous.analyses.projects);
  const slowQueue = (queue?.oldestPendingAgeMs ?? 0) > SLOW_QUEUE_MS;

  return (
    <div className="sw-grid sw-grid-cols-3 sw-gap-4 sw-mb-6">
      <StatTile
        color={MAGNITUDE_COLOR}
        delta={percentDelta(totalChange, true)}
        footnote={
          totalChange === undefined ? translate('analytics.activity.no_previous') : undefined
        }
        icon={<IconRefresh />}
        label={translate('analytics.activity.analyses')}
        trend={timeline.map(({ total }) => total)}
        {...drill(Drilldown.Analyses)}
      >
        {integer(analyses.total)}
      </StatTile>
      <StatTile
        color={MAGNITUDE_COLOR}
        delta={percentDelta(projectsChange, true)}
        footnote={translateWithParameters('analytics.summary.of_total', integer(totalProjects))}
        icon={<IconProject />}
        label={translate('analytics.activity.projects_analyzed')}
        {...drill(Drilldown.Projects)}
      >
        {integer(analyses.projects)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.success}
        delta={
          successRate === undefined || previousSuccessRate === undefined
            ? undefined
            : toDelta(
                successRate - previousSuccessRate,
                (r) => formatChange(r, ' pts'),
                true,
                context,
              )
        }
        icon={<IconCheckCircle />}
        label={translate('analytics.activity.success_rate')}
        trend={timeline.map((bucket) =>
          bucket.success + bucket.failed > 0
            ? (bucket.success / (bucket.success + bucket.failed)) * 100
            : undefined,
        )}
        {...drill(Drilldown.Failures)}
      >
        {successRate === undefined ? '-' : `${Math.round(successRate * 10) / 10}%`}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.info}
        delta={percentDelta(medianChange, false)}
        footnote={
          analyses.p90ExecutionTimeMs === undefined
            ? undefined
            : translateWithParameters(
                'analytics.activity.p90_value',
                formatDuration(analyses.p90ExecutionTimeMs),
              )
        }
        icon={<IconClock />}
        label={translate('analytics.activity.median_time')}
        trend={timeline.map(({ medianExecutionTimeMs }) => medianExecutionTimeMs)}
        {...drill(Drilldown.Slowest)}
      >
        {analyses.medianExecutionTimeMs === undefined
          ? '-'
          : formatDuration(analyses.medianExecutionTimeMs)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.info}
        footnote={
          analyses.avgQueueTimeMs === undefined
            ? undefined
            : translateWithParameters(
                'analytics.activity.average_value',
                formatDuration(analyses.avgQueueTimeMs),
              )
        }
        icon={<IconCalendar />}
        label={translate('analytics.activity.queue_time')}
        {...drill(Drilldown.LongestWaits)}
      >
        {analyses.medianQueueTimeMs === undefined
          ? '-'
          : formatDuration(analyses.medianQueueTimeMs)}
      </StatTile>
      <StatTile
        color={slowQueue ? STATUS_COLORS.warning : MAGNITUDE_COLOR}
        footnote={
          queue === undefined
            ? undefined
            : [
                translateWithParameters(
                  'analytics.activity.in_progress',
                  integer(queue.inProgress),
                ),
                queue.oldestPendingAgeMs === undefined
                  ? undefined
                  : translateWithParameters(
                      'analytics.activity.oldest_pending',
                      formatDuration(queue.oldestPendingAgeMs),
                    ),
              ]
                .filter(Boolean)
                .join(' · ')
        }
        icon={slowQueue ? <IconWarning /> : <IconClock />}
        label={translate('analytics.activity.queued_now')}
        {...drill(Drilldown.Queued)}
      >
        {queue === undefined ? '-' : integer(queue.pending)}
      </StatTile>
    </div>
  );
}

function ActivityCharts({ activity }: Readonly<{ activity: OrganizationActivity }>) {
  const weekly = activity.period.interval === 'week';
  const bucketLabel = (date: string) =>
    weekly
      ? translateWithParameters('analytics.activity.week_of', formatDate(parseISO(date), 'MMM d'))
      : formatDate(parseISO(date), 'MMM d');

  const timeline = activity.timeline.map((bucket) => ({
    key: bucket.date,
    label: bucketLabel(bucket.date),
    values: { success: bucket.success, failed: bucket.failed, canceled: bucket.canceled },
  }));
  const times = activity.timeline.map((bucket) => ({
    key: bucket.date,
    label: bucketLabel(bucket.date),
    values: { median: bucket.medianExecutionTimeMs, p90: bucket.p90ExecutionTimeMs },
  }));
  const hasTimes = activity.timeline.some(
    ({ medianExecutionTimeMs }) => medianExecutionTimeMs !== undefined,
  );

  return (
    <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
      <ChartCard
        className="sw-col-span-12"
        description={translate(
          weekly ? 'analytics.activity.timeline.weekly' : 'analytics.activity.timeline.daily',
        )}
        title={translate('analytics.activity.timeline')}
      >
        {activity.analyses.total > 0 ? (
          <ColumnTimeline columns={timeline} series={STATUS_SERIES} />
        ) : (
          <Text isSubdued>{translate('analytics.activity.no_analyses')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-12"
        description={translate('analytics.activity.time.description')}
        title={translate('analytics.activity.time')}
      >
        {hasTimes ? (
          <LineTimeline formatValue={formatDuration} points={times} series={TIME_SERIES} />
        ) : (
          <Text isSubdued>{translate('analytics.activity.no_analyses')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.activity.targets.description')}
        title={translate('analytics.activity.targets')}
      >
        <BarList
          items={TARGETS.map((target) => ({
            color: MAGNITUDE_COLOR,
            key: target,
            label: translate('analytics.activity.target', target),
            value: activity.targets.find((t) => t.target === target)?.total ?? 0,
          })).filter(({ key, value }) => key !== 'UNKNOWN' || value > 0)}
        />
        <PullRequestAdoption activity={activity} />
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.activity.task_types.description')}
        title={translate('analytics.activity.task_types')}
      >
        {activity.taskTypes.length > 0 ? (
          <BarList
            items={activity.taskTypes.map(({ total, type }) => ({
              color: MAGNITUDE_COLOR,
              key: type,
              label: translate('background_task.type', type),
              value: total,
            }))}
          />
        ) : (
          <Text isSubdued>{translate('analytics.activity.no_tasks')}</Text>
        )}
      </ChartCard>
    </div>
  );
}

/** How many projects already get their pull requests analyzed: the clearest sign of shift-left adoption. */
function PullRequestAdoption({ activity }: Readonly<{ activity: OrganizationActivity }>) {
  const withPullRequests = activity.targets.find((t) => t.target === 'PULL_REQUEST')?.projects ?? 0;
  if (activity.totalProjects === 0) {
    return null;
  }
  const share = Math.round((withPullRequests / activity.totalProjects) * 100);
  return (
    <Text as="p" className="sw-mt-4">
      {translateWithParameters(
        'analytics.activity.pr_adoption',
        formatMeasure(withPullRequests, MetricType.Integer),
        formatMeasure(activity.totalProjects, MetricType.Integer),
        share,
      )}
    </Text>
  );
}
