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

import { IconBug, IconCheckCircle, IconWarning, Text } from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import { FlagMessage, Spinner } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  IssueResolutions,
  QualityTrendPoint,
  getExceptions,
  getIssueResolutions,
  getQualityTrend,
} from '../../../api/analytics';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { formatChange } from '../activity';
import { CsvTable, exportFileName } from '../csv';
import { fromFirstActivity, fromFirstAnalysis, periodStart, withCurrent } from '../period';
import { periodRequest, useLoaded } from '../useLoaded';
import AuditTrailPanel from './AuditTrailPanel';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, RESOLUTION_COLORS, STATUS_COLORS } from './charts/colors';
import { toDelta } from './charts/Delta';
import LineTimeline from './charts/LineTimeline';
import CustomRulesPanel from './CustomRulesPanel';
import ExceptionGovernancePanel from './ExceptionGovernancePanel';
import ExportCsvButton from './ExportCsvButton';
import ScanComparisonPanel, { ScanSelection } from './ScanComparisonPanel';
import StatTile from './StatTile';

interface Props {
  onScanChange?: (scan: ScanSelection) => void;
  organization: string;
  period: number;
  scan?: ScanSelection;
}

/**
 * Is the organization meeting its policy and getting better, what changed between two scans, which risks were accepted
 * and by whom, and which custom rules need tuning.
 */
export default function GovernanceDashboard(props: Readonly<Props>) {
  return (
    <>
      <ComplianceReport {...props} />
      <AdministrationReport organization={props.organization} period={props.period} />
    </>
  );
}

function passRate(point?: QualityTrendPoint) {
  return point && point.passed + point.failed > 0
    ? (point.passed / (point.passed + point.failed)) * 100
    : undefined;
}

const fixed = (counts: { fixed: number; removed: number }) => counts.fixed + counts.removed;
const dismissed = (counts: { accepted: number; falsePositive: number }) =>
  counts.accepted + counts.falsePositive;

/** Policy and trends, the comparison of two scans, and the exceptions: what compliance reviews. */
export function ComplianceReport({ onScanChange, organization, period, scan }: Readonly<Props>) {
  const { data, error, loading } = useLoaded(async () => {
    const request = { organization, ...periodRequest(period) };
    const [trend, resolutions, exceptions] = await Promise.all([
      getQualityTrend(request),
      getIssueResolutions(request),
      getExceptions({ organization, ps: 1 }),
    ]);
    return { trend: withCurrent(trend.timeline, undefined, exceptions.summary), resolutions };
  }, [organization, period]);

  return (
    <>
      <h3 className="sw-heading-md sw-mb-4">
        {translate('analytics.governance.compliance.trends')}
      </h3>
      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}
      {data === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <PolicyTiles resolutions={data.resolutions} trend={data.trend} />
          <TrendCharts organization={organization} period={period} {...data} />
        </div>
      )}

      <h3 className="sw-heading-md sw-mt-8 sw-mb-2">{translate('analytics.governance.scan')}</h3>
      <Text as="p" className="sw-mb-4" isSubdued>
        {translate('analytics.governance.scan.description')}
      </Text>
      <ScanComparisonPanel
        onSelectionChange={onScanChange}
        organization={organization}
        selection={scan}
      />

      <h3 className="sw-heading-md sw-mt-8 sw-mb-4">
        {translate('analytics.governance.exceptions.heading')}
      </h3>
      <ExceptionGovernancePanel organization={organization} />
    </>
  );
}

/** Who changed each finding and why, and how the custom rules behave: what administrators review. */
export function AdministrationReport({
  organization,
  period,
}: Readonly<{ organization: string; period: number }>) {
  return (
    <>
      <h3 className="sw-heading-md sw-mt-8 sw-mb-4">
        {translate('analytics.governance.audit.heading')}
      </h3>
      <AuditTrailPanel organization={organization} period={period} />
      <h3 className="sw-heading-md sw-mt-8 sw-mb-4">
        {translate('analytics.governance.rules.heading')}
      </h3>
      <CustomRulesPanel organization={organization} period={period} />
    </>
  );
}

function PolicyTiles({
  resolutions,
  trend,
}: Readonly<{ resolutions: IssueResolutions; trend: QualityTrendPoint[] }>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const first = periodStart(trend);
  const last = trend[trend.length - 1];
  const rateNow = passRate(last);
  const rateThen = passRate(first);
  const { totals } = resolutions;

  return (
    <div className="sw-grid sw-grid-cols-4 sw-gap-4 sw-mb-6">
      <StatTile
        color={STATUS_COLORS.success}
        delta={
          rateNow === undefined || rateThen === undefined
            ? undefined
            : toDelta(
                rateNow - rateThen,
                (r) => formatChange(r, ' pts'),
                true,
                translate('analytics.quality.since_period_start'),
              )
        }
        icon={<IconCheckCircle />}
        label={translate('analytics.governance.compliance.status')}
        trend={fromFirstAnalysis(trend).map(passRate)}
      >
        {rateNow === undefined ? '-' : `${Math.round(rateNow)}%`}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.success}
        footnote={translate('analytics.governance.compliance.fixed.footnote')}
        icon={<IconCheckCircle />}
        label={translate('analytics.governance.compliance.fixed')}
      >
        {integer(fixed(totals))}
      </StatTile>
      <StatTile
        color={MAGNITUDE_COLOR}
        footnote={translateWithParameters(
          totals.netChange <= 0 ? 'analytics.quality.net_down' : 'analytics.quality.net_up',
          integer(Math.abs(totals.netChange)),
        )}
        icon={<IconBug />}
        label={translate('analytics.governance.compliance.introduced')}
      >
        {integer(totals.introduced)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.warning}
        footnote={translateWithParameters(
          'analytics.governance.compliance.dismissed.footnote',
          integer(totals.accepted),
          integer(totals.falsePositive),
        )}
        icon={<IconWarning />}
        label={translate('analytics.governance.compliance.dismissed')}
      >
        {integer(dismissed(totals))}
      </StatTile>
    </div>
  );
}

function trendTable(trend: QualityTrendPoint[], resolutions: IssueResolutions): CsvTable {
  const byDate = new Map(resolutions.timeline.map((bucket) => [bucket.date, bucket]));
  return {
    header: [
      'Period start',
      'Projects',
      'Quality gate passed',
      'Quality gate failed',
      'Pass rate %',
      'Open issues',
      'Blocker issues',
      'Vulnerabilities',
      'Accepted',
      'False positives',
      'Introduced',
      'Fixed',
      'Removed',
      'Accepted in period',
      'Marked false positive in period',
      'Reopened',
    ],
    rows: trend.map((point) => {
      const bucket = byDate.get(point.date);
      const rate = passRate(point);
      return [
        point.date,
        point.projects,
        point.passed,
        point.failed,
        rate === undefined ? undefined : rate.toFixed(1),
        point.openIssues,
        point.blockerIssues,
        point.vulnerabilities,
        point.acceptedIssues,
        point.falsePositiveIssues,
        bucket?.introduced,
        bucket?.fixed,
        bucket?.removed,
        bucket?.accepted,
        bucket?.falsePositive,
        bucket?.reopened,
      ];
    }),
  };
}

function TrendCharts({
  organization,
  period,
  resolutions,
  trend: fullTrend,
}: Readonly<{
  organization: string;
  period: number;
  resolutions: IssueResolutions;
  trend: QualityTrendPoint[];
}>) {
  const weekly = period > 31;
  const label = (date: string) =>
    weekly
      ? translateWithParameters('analytics.activity.week_of', format(parseDate(date), 'MMM d'))
      : format(parseDate(date), 'MMM d');
  const trend = fromFirstAnalysis(fullTrend);
  const hasHistory = trend.some(({ projects }) => projects > 0);
  const noHistory = <Text isSubdued>{translate('analytics.quality.no_history')}</Text>;
  const formatValue = (value: number) => formatMeasure(value, MetricType.ShortInteger);
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const { totals } = resolutions;
  // yyyy-MM-dd strings compare in date order
  const incomplete = resolutions.period.from < resolutions.completeFrom;
  const buckets = fromFirstActivity(
    resolutions.timeline,
    (bucket) => bucket.introduced + fixed(bucket) + dismissed(bucket) > 0,
  );
  const exportTrend = (
    <ExportCsvButton
      fileName={exportFileName('compliance-trends', organization)}
      getTable={() => trendTable(fullTrend, resolutions)}
    />
  );

  return (
    <div className="sw-grid sw-grid-cols-12 sw-gap-6">
      <ChartCard
        action={exportTrend}
        className="sw-col-span-12"
        description={translate('analytics.quality.pass_rate.description')}
        title={translate('analytics.quality.pass_rate_trend')}
      >
        {hasHistory ? (
          <LineTimeline
            formatValue={(value) => `${Math.round(value)}%`}
            points={trend.map((point) => ({
              key: point.date,
              label: label(point.date),
              values: { rate: passRate(point) },
            }))}
            series={[
              {
                color: STATUS_COLORS.success,
                key: 'rate',
                label: translate('analytics.quality.pass_rate'),
              },
            ]}
          />
        ) : (
          noHistory
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-12"
        description={translate('analytics.governance.compliance.progress.description')}
        title={translate('analytics.governance.compliance.progress')}
      >
        <div className="sw-grid sw-grid-cols-12 sw-gap-6">
          <div className="sw-col-span-8">
            <LineTimeline
              formatValue={formatValue}
              points={buckets.map((bucket) => ({
                key: bucket.date,
                label: label(bucket.date),
                values: {
                  introduced: bucket.introduced,
                  fixed: fixed(bucket),
                  dismissed: dismissed(bucket),
                },
              }))}
              series={[
                {
                  color: STATUS_COLORS.danger,
                  dashed: true,
                  key: 'introduced',
                  label: translate('analytics.quality.introduced_series'),
                },
                {
                  color: STATUS_COLORS.success,
                  key: 'fixed',
                  label: translate('analytics.governance.compliance.fixed_series'),
                },
                {
                  color: RESOLUTION_COLORS.accepted,
                  key: 'dismissed',
                  label: translate('analytics.governance.compliance.dismissed_series'),
                },
              ]}
            />
          </div>
          <div className="sw-col-span-4 sw-flex sw-flex-col sw-gap-4">
            <Text className="sw-typo-semibold">{translate('analytics.quality.how_resolved')}</Text>
            <BarList
              items={(['fixed', 'removed', 'accepted', 'falsePositive'] as const).map((key) => ({
                color: RESOLUTION_COLORS[key],
                key,
                label: translate('analytics.quality.outcome', key),
                value: totals[key],
              }))}
            />
            <Text isSubdued>
              {translateWithParameters(
                'analytics.quality.fixed_by_analysis',
                integer(totals.fixedByAnalysis),
                integer(totals.fixed),
              )}
              {totals.reopened > 0 &&
                ` · ${translateWithParameters('analytics.quality.reopened', integer(totals.reopened))}`}
            </Text>
          </div>
        </div>
        {incomplete && (
          <FlagMessage className="sw-mt-4" variant="info">
            {translateWithParameters(
              'analytics.quality.retention_note',
              resolutions.completeFrom,
              resolutions.retentionDays,
            )}
          </FlagMessage>
        )}
      </ChartCard>
    </div>
  );
}
