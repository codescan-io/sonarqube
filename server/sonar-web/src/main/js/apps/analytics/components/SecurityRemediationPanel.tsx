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
  IconCheckCircle,
  IconClock,
  IconSecurityFinding,
  IconWarning,
  LinkStandalone,
  Text,
} from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import { ContentCell, NumericalCell, Table, TableRow } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { SecurityRemediation } from '../../../api/analytics';
import DateFormatter from '../../../components/intl/DateFormatter';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getOrgIssuesUrl } from '../../../helpers/urls';
import { formatChange, getChange } from '../activity';
import { formatDays } from '../brief';
import { share } from '../health';
import ChartCard from './charts/ChartCard';
import { STATUS_COLORS, tint } from './charts/colors';
import { toDelta } from './charts/Delta';
import LineTimeline from './charts/LineTimeline';
import StatTile from './StatTile';

interface Props {
  data: SecurityRemediation;
  organization: string;
  weekly: boolean;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const COLUMNS = ['16%', '10%', '10%', '16%', '14%', '10%', '12%', '12%'];

/**
 * How fast security issues get fixed, against a remediation SLA per severity: the time to fix and the share fixed in
 * time over the period, compared with the period before, and the open issues already past their SLA.
 */
export default function SecurityRemediationPanel({ data, organization, weekly }: Readonly<Props>) {
  const { previous, totals } = data;
  const compliance = share(totals.fixedWithinSla, totals.fixed);
  const previousCompliance = previous.complete
    ? share(previous.fixedWithinSla, previous.fixed)
    : undefined;
  const mttrChange = previous.complete
    ? getChange(totals.medianTimeToFixMs, previous.medianTimeToFixMs)
    : undefined;
  const vsPrevious = translate('analytics.activity.vs_previous_period');
  const incomplete = data.completeFrom > data.period.from;
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const label = (date: string) =>
    weekly
      ? translateWithParameters('analytics.activity.week_of', format(parseDate(date), 'MMM d'))
      : format(parseDate(date), 'MMM d');

  return (
    <section aria-labelledby="analytics-security-remediation" className="sw-mb-8">
      <div className="sw-mb-4">
        <h3 className="sw-heading-md" id="analytics-security-remediation">
          {translate('analytics.security.title')}
        </h3>
        <Text as="p" isSubdued>
          {translate('analytics.security.description')}
        </Text>
      </div>

      <div className="sw-grid sw-grid-cols-4 sw-gap-4 sw-mb-6">
        <StatTile
          color={STATUS_COLORS.info}
          delta={
            mttrChange === undefined
              ? undefined
              : toDelta(mttrChange, (r) => formatChange(r), false, vsPrevious)
          }
          footnote={
            totals.averageTimeToFixMs === undefined
              ? undefined
              : translateWithParameters(
                  'analytics.security.mttr_average',
                  formatDays(totals.averageTimeToFixMs),
                )
          }
          icon={<IconClock />}
          label={translate('analytics.security.mttr')}
          trend={data.timeline.map(({ medianTimeToFixMs }) =>
            medianTimeToFixMs === undefined ? undefined : medianTimeToFixMs / DAY_MS,
          )}
        >
          {totals.medianTimeToFixMs === undefined ? '-' : formatDays(totals.medianTimeToFixMs)}
        </StatTile>
        <StatTile
          color={
            compliance === undefined || compliance >= 90
              ? STATUS_COLORS.success
              : STATUS_COLORS.warning
          }
          delta={
            compliance === undefined || previousCompliance === undefined
              ? undefined
              : toDelta(
                  compliance - previousCompliance,
                  (r) => formatChange(r, ' pts'),
                  true,
                  vsPrevious,
                )
          }
          footnote={translateWithParameters(
            'analytics.security.compliance_footnote',
            integer(totals.fixedWithinSla),
            integer(totals.fixed),
          )}
          icon={<IconCheckCircle />}
          label={translate('analytics.security.compliance')}
          trend={data.timeline.map(({ fixed, fixedWithinSla }) => share(fixedWithinSla, fixed))}
        >
          {compliance === undefined ? '-' : `${Math.round(compliance)}%`}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.success}
          delta={
            previous.complete && previous.fixed > 0
              ? toDelta(
                  getChange(totals.fixed, previous.fixed) ?? 0,
                  (r) => formatChange(r),
                  true,
                  vsPrevious,
                )
              : undefined
          }
          icon={<IconSecurityFinding />}
          label={translate('analytics.security.fixed')}
          trend={data.timeline.map(({ fixed }) => fixed)}
        >
          {integer(totals.fixed)}
        </StatTile>
        <StatTile
          color={totals.openPastSla > 0 ? STATUS_COLORS.danger : STATUS_COLORS.success}
          footnote={translateWithParameters(
            'analytics.security.open_footnote',
            integer(totals.open),
          )}
          icon={<IconWarning />}
          label={translate('analytics.security.open_past_sla')}
        >
          {integer(totals.openPastSla)}
        </StatTile>
      </div>

      <div className="sw-grid sw-grid-cols-12 sw-gap-6">
        <ChartCard
          className="sw-col-span-7"
          description={translate('analytics.security.by_severity.description')}
          title={translate('analytics.security.by_severity')}
        >
          <Table
            columnCount={COLUMNS.length}
            columnWidths={COLUMNS}
            header={
              <TableRow>
                <ContentCell>{translate('analytics.quality.severity')}</ContentCell>
                <NumericalCell>{translate('analytics.security.sla')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.fixed_short')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.median')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.within_sla')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.open')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.past_sla')}</NumericalCell>
                <NumericalCell>{translate('analytics.security.oldest')}</NumericalCell>
              </TableRow>
            }
          >
            {data.severities.map((severity) => {
              const within = share(severity.fixedWithinSla, severity.fixed);
              return (
                <TableRow key={severity.severity}>
                  <ContentCell>{translate('severity_impact', severity.severity)}</ContentCell>
                  <NumericalCell>
                    {translateWithParameters('analytics.brief.days', severity.slaDays)}
                  </NumericalCell>
                  <NumericalCell>{integer(severity.fixed)}</NumericalCell>
                  <NumericalCell>
                    {severity.medianTimeToFixMs === undefined
                      ? '-'
                      : formatDays(severity.medianTimeToFixMs)}
                  </NumericalCell>
                  <NumericalCell>
                    {within === undefined ? '-' : `${Math.round(within)}%`}
                  </NumericalCell>
                  <NumericalCell>
                    {severity.open > 0 ? (
                      <LinkStandalone
                        to={getOrgIssuesUrl(
                          {
                            issueStatuses: 'OPEN,CONFIRMED',
                            impactSoftwareQualities: 'SECURITY',
                            impactSeverities: severity.severity,
                          },
                          organization,
                        )}
                      >
                        {integer(severity.open)}
                      </LinkStandalone>
                    ) : (
                      '0'
                    )}
                  </NumericalCell>
                  <NumericalCell
                    style={
                      severity.openPastSla > 0
                        ? { backgroundColor: tint(STATUS_COLORS.danger, 12) }
                        : undefined
                    }
                  >
                    {integer(severity.openPastSla)}
                  </NumericalCell>
                  <NumericalCell>
                    {severity.oldestOpenAgeMs === undefined
                      ? '-'
                      : formatDays(severity.oldestOpenAgeMs)}
                  </NumericalCell>
                </TableRow>
              );
            })}
          </Table>
        </ChartCard>

        <ChartCard
          className="sw-col-span-5"
          description={translate('analytics.security.trend.description')}
          title={translate('analytics.security.trend')}
        >
          {totals.fixed > 0 ? (
            <LineTimeline
              formatValue={(days) =>
                translateWithParameters('analytics.brief.days', Math.round(days))
              }
              points={data.timeline.map((bucket) => ({
                key: bucket.date,
                label: label(bucket.date),
                values: {
                  median:
                    bucket.medianTimeToFixMs === undefined
                      ? undefined
                      : bucket.medianTimeToFixMs / DAY_MS,
                },
              }))}
              series={[
                {
                  color: STATUS_COLORS.info,
                  key: 'median',
                  label: translate('analytics.security.median'),
                },
              ]}
            />
          ) : (
            <Text isSubdued>{translate('analytics.security.no_fixes')}</Text>
          )}
        </ChartCard>
      </div>

      {incomplete && (
        <Text as="p" className="sw-mt-3" isSubdued>
          {translateWithParameters('analytics.security.retention_note', data.retentionDays)}{' '}
          <DateFormatter date={data.completeFrom} long={false} />
        </Text>
      )}
    </section>
  );
}
