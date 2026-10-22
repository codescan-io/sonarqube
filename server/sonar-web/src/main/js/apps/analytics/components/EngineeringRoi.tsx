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

import { IconClock, IconRocket, IconSparkle, Text } from '@sonarsource/echoes-react';
import { format, subDays } from 'date-fns';
import * as React from 'react';
import { QualityTrendPoint, getQualityTrend } from '../../../api/analytics';
import { AiValue } from '../../../api/codescan-analytics';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { formatChange, getChange } from '../activity';
import { formatHours, toHours } from '../roi';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import { toDelta } from './charts/Delta';
import LineTimeline from './charts/LineTimeline';
import StatTile from './StatTile';

interface Props {
  /** Missing when CodeScan's own services do not answer: the backlog part still shows. */
  ai?: AiValue;
  aiLoading: boolean;
  days: number;
  organization: string;
}

/**
 * Engineering time: the remediation backlog of the main branches, how it moved over the period, and the engineering
 * time AI fixes saved.
 */
export default function EngineeringRoi({ ai, aiLoading, days, organization }: Readonly<Props>) {
  const [trend, setTrend] = React.useState<QualityTrendPoint[]>();

  React.useEffect(() => {
    let active = true;
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    getQualityTrend({
      organization,
      from: format(subDays(new Date(), days - 1), 'yyyy-MM-dd'),
      timeZone,
      interval: days > 31 ? 'week' : 'day',
    }).then(
      (result) => active && setTrend(result.timeline),
      () => active && setTrend([]),
    );
    return () => {
      active = false;
    };
  }, [organization, days]);

  const first = trend?.[0];
  const last = trend?.[trend.length - 1];
  const backlog = last?.remediationEffortMinutes;
  const change =
    first && last ? last.remediationEffortMinutes - first.remediationEffortMinutes : undefined;
  const changePercent =
    first && last
      ? getChange(last.remediationEffortMinutes, first.remediationEffortMinutes)
      : undefined;
  const saved = ai?.fixJobs.effortSavedMinutes ?? 0;
  const sinceStart = translate('analytics.quality.since_period_start');

  return (
    <section aria-labelledby="analytics-roi" className="sw-mb-8">
      <div className="sw-mb-4">
        <h3 className="sw-heading-md" id="analytics-roi">
          {translate('analytics.roi.title')}
        </h3>
        <Text as="p" isSubdued>
          {translate('analytics.roi.description')}
        </Text>
      </div>

      <div className="sw-grid sw-grid-cols-3 sw-gap-4 sw-mb-6">
        <StatTile
          color={MAGNITUDE_COLOR}
          delta={
            changePercent === undefined
              ? undefined
              : toDelta(changePercent, (r) => formatChange(r), false, sinceStart)
          }
          footnote={
            last === undefined
              ? undefined
              : translateWithParameters(
                  'analytics.roi.backlog_footnote',
                  new Intl.NumberFormat().format(last.openIssues),
                )
          }
          icon={<IconClock />}
          label={translate('analytics.roi.backlog')}
          loading={trend === undefined}
          trend={trend?.map(({ remediationEffortMinutes }) => toHours(remediationEffortMinutes))}
        >
          {backlog === undefined ? '-' : formatHours(backlog)}
        </StatTile>
        <StatTile
          color={change !== undefined && change > 0 ? STATUS_COLORS.danger : STATUS_COLORS.success}
          footnote={change === undefined ? undefined : sinceStart}
          icon={<IconRocket />}
          label={translate(
            change !== undefined && change > 0
              ? 'analytics.roi.debt_added'
              : 'analytics.roi.debt_paid_down',
          )}
          loading={trend === undefined}
        >
          {change === undefined ? '-' : formatHours(Math.abs(change))}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.success}
          footnote={
            ai
              ? translateWithParameters(
                  'analytics.roi.ai_footnote',
                  new Intl.NumberFormat().format(ai.fixJobs.issuesResolved),
                )
              : translate('analytics.roi.ai_unavailable')
          }
          icon={<IconSparkle />}
          label={translate('analytics.roi.ai_saved')}
          loading={aiLoading && ai === undefined}
        >
          {ai ? formatHours(saved) : '-'}
        </StatTile>
      </div>

      <ChartCard
        description={translate('analytics.roi.trend.description')}
        loading={trend === undefined}
        title={translate('analytics.roi.trend')}
      >
        {trend && trend.some(({ projects }) => projects > 0) ? (
          <LineTimeline
            formatValue={(hours) => formatHours(hours * 60)}
            points={trend.map((point) => ({
              key: point.date,
              label: format(parseDate(point.date), 'MMM d'),
              values: {
                backlog: point.projects > 0 ? toHours(point.remediationEffortMinutes) : undefined,
              },
            }))}
            series={[
              { color: MAGNITUDE_COLOR, key: 'backlog', label: translate('analytics.roi.backlog') },
            ]}
          />
        ) : (
          <Text isSubdued>{translate('analytics.quality.no_history')}</Text>
        )}
      </ChartCard>
    </section>
  );
}
