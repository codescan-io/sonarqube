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
  IconError,
  IconSecurityFinding,
  IconWarning,
  LinkStandalone,
  Text,
} from '@sonarsource/echoes-react';
import { format, subDays } from 'date-fns';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ContentCell,
  FlagMessage,
  NumericalCell,
  Spinner,
  Table,
  TableRow,
  ToggleButton,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  ExpiringException,
  IssueResolutions,
  QualityTrendPoint,
  getExpiringExceptions,
  getIssueResolutions,
  getQualityTrend,
} from '../../../api/analytics';
import { OrganizationHealth, getOrganizationHealth } from '../../../api/codescan-analytics';
import { searchIssues } from '../../../api/issues';
import DateFormatter from '../../../components/intl/DateFormatter';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getComponentIssuesUrl, getOrgIssuesUrl } from '../../../helpers/urls';
import { formatChange, getChange } from '../activity';
import {
  AgeBucket,
  DEFAULT_QUALITY_PERIOD,
  IMPACT_SEVERITIES,
  QUALITY_PERIODS,
  QualityPeriod,
  SLA_SEVERITIES,
  getAgeBuckets,
} from '../quality';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, RESOLUTION_COLORS, STATUS_COLORS, tint } from './charts/colors';
import ColumnTimeline from './charts/ColumnTimeline';
import { toDelta } from './charts/Delta';
import LineTimeline from './charts/LineTimeline';
import StatTile from './StatTile';

interface Props {
  organization: string;
}

const OPEN = 'OPEN,CONFIRMED';
// The issues the "Issues Resolved" tile opens: everything acted on, not only fixed issues.
const RESOLVED_TILE_STATUSES = 'CONFIRMED,FALSE_POSITIVE,FIXED,ACCEPTED';
const TOP_RULES = 10;
const EXPIRING_WITHIN_DAYS = 30;
const EXPIRING_SHOWN = 10;

interface QualityData {
  ageMatrix: Array<{ bucket: AgeBucket; counts: Record<string, number> }>;
  expiring: { exceptions: ExpiringException[]; total: number };
  /** Issues of every branch by status, now. */
  issueStatuses: Record<string, number>;
  languages: Array<{ count: number; key: string; name: string }>;
  resolutions: IssueResolutions;
  rules: Array<{ count: number; key: string; name: string }>;
  trend: QualityTrendPoint[];
}

function facetOf(response: Awaited<ReturnType<typeof searchIssues>> | undefined, property: string) {
  if (!response) {
    throw new Error('Issue search failed');
  }
  return response.facets.find((facet) => facet.property === property)?.values ?? [];
}

async function fetchQualityData(organization: string, days: QualityPeriod): Promise<QualityData> {
  const now = new Date();
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const from = format(subDays(now, days - 1), 'yyyy-MM-dd');
  const buckets = getAgeBuckets(now);

  const interval = days > 31 ? 'week' : 'day';
  const [trend, resolutions, rulesAndLanguages, statuses, expiring, ...ages] = await Promise.all([
    getQualityTrend({ organization, from, timeZone, interval }),
    getIssueResolutions({ organization, from, timeZone, interval }),
    searchIssues({
      organization,
      issueStatuses: OPEN,
      facets: 'rules,languages',
      additionalFields: 'rules,languages',
      ps: 1,
    }),
    // every status, fixed ones included while the platform keeps them
    searchIssues({ organization, facets: 'issueStatuses', ps: 1 }),
    getExpiringExceptions({ organization, withinDays: EXPIRING_WITHIN_DAYS, ps: EXPIRING_SHOWN }),
    ...buckets.map((bucket) =>
      searchIssues({
        organization,
        issueStatuses: OPEN,
        impactSoftwareQualities: 'SECURITY',
        facets: 'impactSeverities',
        createdAfter: bucket.createdAfter,
        createdBefore: bucket.createdBefore,
        ps: 1,
      }),
    ),
  ]);

  const ruleNames = new Map(
    ((rulesAndLanguages?.rules ?? []) as Array<{ key: string; name: string }>).map((r) => [
      r.key,
      r.name,
    ]),
  );
  const languageNames = new Map(
    (rulesAndLanguages?.languages ?? []).map((l: { key: string; name: string }) => [l.key, l.name]),
  );

  return {
    trend: trend.timeline,
    resolutions,
    rules: facetOf(rulesAndLanguages, 'rules')
      .slice(0, TOP_RULES)
      .map(({ val, count }) => ({ key: val, count, name: ruleNames.get(val) ?? val })),
    languages: facetOf(rulesAndLanguages, 'languages')
      .filter(({ count }) => count > 0)
      .map(({ val, count }) => ({ key: val, count, name: languageNames.get(val) ?? val })),
    issueStatuses: Object.fromEntries(
      facetOf(statuses, 'issueStatuses').map(({ val, count }) => [val, count]),
    ),
    expiring: { exceptions: expiring.exceptions, total: expiring.paging.total },
    ageMatrix: buckets.map((bucket, i) => ({
      bucket,
      counts: Object.fromEntries(
        facetOf(ages[i], 'impactSeverities').map(({ val, count }) => [val, count]),
      ),
    })),
  };
}

/**
 * Is quality getting better, where is the security risk, and which accepted risks need a second look.
 */
export default function QualityDashboard({ organization }: Readonly<Props>) {
  const [period, setPeriod] = React.useState<QualityPeriod>(DEFAULT_QUALITY_PERIOD);
  const [data, setData] = React.useState<QualityData>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    fetchQualityData(organization, period).then(
      (result) => {
        if (active) {
          setData(result);
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
  }, [organization, period]);

  return (
    <>
      <div className="sw-flex sw-items-center sw-gap-4 sw-mb-4">
        <ToggleButton
          label={translate('analytics.activity.period')}
          onChange={(value) => setPeriod(value as QualityPeriod)}
          options={QUALITY_PERIODS.map((days) => ({
            label: translate('analytics.quality.period', String(days)),
            value: days,
          }))}
          value={period}
        />
        <Text isSubdued>{translate('analytics.quality.main_branch_hint')}</Text>
      </div>

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      {data === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <QualityTiles data={data} organization={organization} />
          <QualityCharts data={data} organization={organization} weekly={period > 31} />
          <RootCauses organization={organization} />
        </div>
      )}
    </>
  );
}

function passRate(point?: QualityTrendPoint) {
  return point && point.passed + point.failed > 0
    ? (point.passed / (point.passed + point.failed)) * 100
    : undefined;
}

function QualityTiles({
  data,
  organization,
}: Readonly<{ data: QualityData; organization: string }>) {
  const navigate = useNavigate();
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const first = data.trend[0];
  const last = data.trend[data.trend.length - 1];
  const rateNow = passRate(last);
  const rateThen = passRate(first);
  const issuesChange = getChange(last?.openIssues, first?.openIssues);
  const pastSla = data.ageMatrix
    .filter(({ bucket }) => bucket.pastSla)
    .reduce(
      (sum, { counts }) => sum + SLA_SEVERITIES.reduce((s, sev) => s + (counts[sev] ?? 0), 0),
      0,
    );
  const accepted = data.issueStatuses.ACCEPTED ?? 0;
  const falsePositives = data.issueStatuses.FALSE_POSITIVE ?? 0;
  const { totals } = data.resolutions;
  const sinceStart = translate('analytics.quality.since_period_start');

  return (
    <div className="sw-grid sw-grid-cols-5 sw-gap-4 sw-mb-6">
      <StatTile
        color={STATUS_COLORS.success}
        delta={toDelta(
          totals.netChange,
          (r) => formatChange(r, ''),
          false,
          translate('analytics.quality.net_change'),
        )}
        footnote={translateWithParameters(
          'analytics.quality.resolved_footnote',
          integer(totals.introduced),
        )}
        icon={<IconCheckCircle />}
        label={translate('analytics.quality.resolved')}
        trend={data.resolutions.timeline.map(
          (b) => b.fixed + b.removed + b.accepted + b.falsePositive,
        )}
        onClick={() =>
          navigate(getOrgIssuesUrl({ issueStatuses: RESOLVED_TILE_STATUSES }, organization))
        }
      >
        {integer(totals.resolved)}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.success}
        delta={
          rateNow === undefined || rateThen === undefined
            ? undefined
            : toDelta(rateNow - rateThen, (r) => formatChange(r, ' pts'), true, sinceStart)
        }
        icon={<IconCheckCircle />}
        label={translate('analytics.quality.pass_rate')}
        trend={data.trend.map(passRate)}
      >
        {rateNow === undefined ? '-' : `${Math.round(rateNow)}%`}
      </StatTile>
      <StatTile
        color={MAGNITUDE_COLOR}
        delta={
          issuesChange === undefined
            ? undefined
            : toDelta(issuesChange, (r) => formatChange(r), false, sinceStart)
        }
        icon={<IconError />}
        label={translate('analytics.quality.open_issues')}
        trend={data.trend.map(({ openIssues }) => openIssues)}
        onClick={() => navigate(getOrgIssuesUrl({ issueStatuses: OPEN }, organization))}
      >
        {last ? integer(last.openIssues) : '-'}
      </StatTile>
      <StatTile
        color={pastSla > 0 ? STATUS_COLORS.danger : STATUS_COLORS.success}
        footnote={translateWithParameters('analytics.quality.sla_rule', SLA_SEVERITIES.length)}
        icon={<IconSecurityFinding />}
        label={translate('analytics.quality.past_sla')}
        onClick={() =>
          navigate(
            getOrgIssuesUrl(
              {
                issueStatuses: OPEN,
                impactSoftwareQualities: 'SECURITY',
                impactSeverities: SLA_SEVERITIES.join(),
                createdBefore: getAgeBuckets(new Date())[1].createdBefore,
              },
              organization,
            ),
          )
        }
      >
        {integer(pastSla)}
      </StatTile>
      <StatTile
        color={data.expiring.total > 0 ? STATUS_COLORS.warning : MAGNITUDE_COLOR}
        footnote={translateWithParameters(
          'analytics.quality.exceptions_footnote',
          integer(accepted),
          integer(falsePositives),
        )}
        icon={<IconWarning />}
        label={translate('analytics.quality.expiring_exceptions')}
        onClick={() =>
          navigate(getOrgIssuesUrl({ issueStatuses: 'ACCEPTED,FALSE_POSITIVE' }, organization))
        }
      >
        {integer(data.expiring.total)}
      </StatTile>
    </div>
  );
}

function QualityCharts({
  data,
  organization,
  weekly,
}: Readonly<{ data: QualityData; organization: string; weekly: boolean }>) {
  const navigate = useNavigate();
  const label = (date: string) =>
    weekly
      ? translateWithParameters('analytics.activity.week_of', format(parseDate(date), 'MMM d'))
      : format(parseDate(date), 'MMM d');
  const points = data.trend.map((point) => ({ key: point.date, label: label(point.date) }));
  const hasHistory = data.trend.some(({ projects }) => projects > 0);

  return (
    <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.pass_rate.description')}
        title={translate('analytics.quality.pass_rate_trend')}
      >
        {hasHistory ? (
          <LineTimeline
            formatValue={(value) => `${Math.round(value)}%`}
            points={data.trend.map((point, i) => ({
              ...points[i],
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
          <Text isSubdued>{translate('analytics.quality.no_history')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.open_issues.description')}
        title={translate('analytics.quality.open_issues_trend')}
      >
        {hasHistory ? (
          <LineTimeline
            formatValue={(value) => formatMeasure(value, MetricType.ShortInteger)}
            points={data.trend.map((point, i) => ({
              ...points[i],
              values: { issues: point.openIssues },
            }))}
            series={[
              {
                color: MAGNITUDE_COLOR,
                key: 'issues',
                label: translate('analytics.quality.open_issues'),
              },
            ]}
          />
        ) : (
          <Text isSubdued>{translate('analytics.quality.no_history')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.risk.description')}
        title={translate('analytics.quality.risk_trend')}
      >
        {hasHistory ? (
          <LineTimeline
            formatValue={(value) => formatMeasure(value, MetricType.ShortInteger)}
            points={data.trend.map((point, i) => ({
              ...points[i],
              values: { vulnerabilities: point.vulnerabilities, blockers: point.blockerIssues },
            }))}
            series={[
              {
                color: STATUS_COLORS.danger,
                key: 'vulnerabilities',
                label: translate('analytics.quality.vulnerabilities'),
              },
              {
                color: STATUS_COLORS.danger,
                dashed: true,
                key: 'blockers',
                label: translate('analytics.quality.blocker_issues'),
              },
            ]}
          />
        ) : (
          <Text isSubdued>{translate('analytics.quality.no_history')}</Text>
        )}
      </ChartCard>

      <ResolutionCharts data={data} label={label} organization={organization} />

      <ChartCard
        className="sw-col-span-12"
        description={translateWithParameters(
          'analytics.quality.security_age.description',
          SLA_SEVERITIES.length,
        )}
        title={translate('analytics.quality.security_age')}
      >
        <SecurityAgeTable matrix={data.ageMatrix} organization={organization} />
      </ChartCard>

      <ChartCard
        className="sw-col-span-7"
        description={translate('analytics.quality.top_rules.description')}
        title={translate('analytics.quality.top_rules')}
      >
        {data.rules.length > 0 ? (
          <BarList
            items={data.rules.map(({ count, key, name }) => ({
              color: MAGNITUDE_COLOR,
              key,
              label: name,
              value: count,
            }))}
            onSelect={(rule) =>
              navigate(getOrgIssuesUrl({ issueStatuses: OPEN, rules: rule }, organization))
            }
          />
        ) : (
          <Text isSubdued>{translate('analytics.chart.no_issues')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-5"
        description={translate('analytics.quality.languages.description')}
        title={translate('analytics.quality.languages')}
      >
        {data.languages.length > 0 ? (
          <BarList
            items={data.languages.map(({ count, key, name }) => ({
              color: MAGNITUDE_COLOR,
              key,
              label: name,
              value: count,
            }))}
            onSelect={(language) =>
              navigate(getOrgIssuesUrl({ issueStatuses: OPEN, languages: language }, organization))
            }
          />
        ) : (
          <Text isSubdued>{translate('analytics.chart.no_issues')}</Text>
        )}
      </ChartCard>

      <ChartCard
        className="sw-col-span-12"
        description={translateWithParameters(
          'analytics.quality.expiring.description',
          EXPIRING_WITHIN_DAYS,
        )}
        title={translate('analytics.quality.expiring_exceptions')}
      >
        <ExpiringTable expiring={data.expiring} />
      </ChartCard>
    </div>
  );
}

const AGE_COLUMNS = ['24%', '19%', '19%', '19%', '19%'];

function SecurityAgeTable({
  matrix,
  organization,
}: Readonly<{ matrix: QualityData['ageMatrix']; organization: string }>) {
  return (
    <Table
      columnCount={AGE_COLUMNS.length}
      columnWidths={AGE_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.quality.severity')}</ContentCell>
          {matrix.map(({ bucket }) => (
            <NumericalCell key={bucket.key}>
              {translate('analytics.quality.age', bucket.key)}
            </NumericalCell>
          ))}
        </TableRow>
      }
    >
      {IMPACT_SEVERITIES.map((severity) => (
        <TableRow key={severity}>
          <ContentCell>{translate('severity_impact', severity)}</ContentCell>
          {matrix.map(({ bucket, counts }) => {
            const count = counts[severity] ?? 0;
            const breach = bucket.pastSla && SLA_SEVERITIES.includes(severity) && count > 0;
            return (
              <NumericalCell
                key={bucket.key}
                style={breach ? { backgroundColor: tint(STATUS_COLORS.danger, 12) } : undefined}
              >
                {count > 0 ? (
                  <LinkStandalone
                    aria-label={`${translate('severity_impact', severity)}, ${translate('analytics.quality.age', bucket.key)}: ${count}`}
                    to={getOrgIssuesUrl(
                      {
                        issueStatuses: OPEN,
                        impactSoftwareQualities: 'SECURITY',
                        impactSeverities: severity,
                        createdAfter: bucket.createdAfter,
                        createdBefore: bucket.createdBefore,
                      },
                      organization,
                    )}
                  >
                    {formatMeasure(count, MetricType.Integer)}
                  </LinkStandalone>
                ) : (
                  '0'
                )}
              </NumericalCell>
            );
          })}
        </TableRow>
      ))}
    </Table>
  );
}

const EXPIRING_COLUMNS = ['26%', '34%', '20%', '20%'];

function ExpiringTable({ expiring }: Readonly<{ expiring: QualityData['expiring'] }>) {
  if (expiring.exceptions.length === 0) {
    return <Text isSubdued>{translate('analytics.quality.no_expiring')}</Text>;
  }
  return (
    <>
      <Table
        columnCount={EXPIRING_COLUMNS.length}
        columnWidths={EXPIRING_COLUMNS}
        header={
          <TableRow>
            <ContentCell>{translate('analytics.table.project')}</ContentCell>
            <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
            <ContentCell>{translate('analytics.quality.exception')}</ContentCell>
            <NumericalCell>{translate('analytics.quality.expires')}</NumericalCell>
          </TableRow>
        }
      >
        {expiring.exceptions.map((exception) => (
          <TableRow key={exception.issueKey}>
            <ContentCell>
              <span className="sw-flex sw-flex-col sw-min-w-0">
                <LinkStandalone
                  to={getComponentIssuesUrl(exception.projectKey, {
                    issues: exception.issueKey,
                    open: exception.issueKey,
                  })}
                >
                  {exception.projectName ?? exception.projectKey}
                </LinkStandalone>
                {exception.filePath && (
                  <span className="sw-truncate" title={exception.filePath}>
                    <Text isSubdued>{exception.filePath}</Text>
                  </span>
                )}
              </span>
            </ContentCell>
            <ContentCell>{exception.ruleName ?? exception.rule}</ContentCell>
            <ContentCell>{translate('issue.issue_status', exception.issueStatus)}</ContentCell>
            <NumericalCell className="sw-whitespace-nowrap">
              {exception.expired ? (
                <Text>{translate('analytics.quality.expired')}</Text>
              ) : (
                <DateFormatter date={exception.expiresAt} long={false} />
              )}
            </NumericalCell>
          </TableRow>
        ))}
      </Table>
      {expiring.total > expiring.exceptions.length && (
        <Text as="p" className="sw-mt-3" isSubdued>
          {translateWithParameters('x_of_y_shown', expiring.exceptions.length, expiring.total)}
        </Text>
      )}
    </>
  );
}

/**
 * The biggest root causes found by AI debt analysis, across projects. Optional: hidden when no project has one,
 * or when CodeScan's own services do not answer.
 */
function RootCauses({ organization }: Readonly<{ organization: string }>) {
  const [causes, setCauses] = React.useState<OrganizationHealth['rootCauses']>([]);

  React.useEffect(() => {
    let active = true;
    getOrganizationHealth(organization).then(
      (health) => active && setCauses(health.rootCauses),
      () => active && setCauses([]),
    );
    return () => {
      active = false;
    };
  }, [organization]);

  if (causes.length === 0) {
    return null;
  }
  return (
    <ChartCard
      className="sw-mb-8"
      description={translate('analytics.quality.root_causes.description')}
      title={translate('analytics.quality.root_causes')}
    >
      <Table
        columnCount={ROOT_CAUSE_COLUMNS.length}
        columnWidths={ROOT_CAUSE_COLUMNS}
        header={
          <TableRow>
            <ContentCell>{translate('analytics.quality.root_cause')}</ContentCell>
            <ContentCell>{translate('analytics.table.project')}</ContentCell>
            <NumericalCell>{translate('analytics.quality.issues')}</NumericalCell>
            <NumericalCell>{translate('analytics.quality.effort')}</NumericalCell>
          </TableRow>
        }
      >
        {causes.map((cause) => (
          <TableRow key={`${cause.projectKey}-${cause.title}`}>
            <ContentCell>
              <span className="sw-flex sw-flex-col">
                <Text className="sw-typo-semibold">{cause.title}</Text>
                <Text isSubdued>
                  {translate('analytics.quality.severity')}: {cause.severity}
                </Text>
              </span>
            </ContentCell>
            <ContentCell>
              <LinkStandalone to={getComponentIssuesUrl(cause.projectKey, { issueStatuses: OPEN })}>
                {cause.projectKey}
              </LinkStandalone>
            </ContentCell>
            <NumericalCell>{formatMeasure(cause.issueCount, MetricType.Integer)}</NumericalCell>
            <NumericalCell>
              {translateWithParameters(
                'analytics.quality.hours',
                Math.round(cause.effortMinutes / 60),
              )}
            </NumericalCell>
          </TableRow>
        ))}
      </Table>
    </ChartCard>
  );
}

const ROOT_CAUSE_COLUMNS = ['46%', '26%', '14%', '14%'];

const RESOLUTION_SERIES = [
  { key: 'fixed', color: RESOLUTION_COLORS.fixed },
  { key: 'accepted', color: RESOLUTION_COLORS.accepted },
  { key: 'falsePositive', color: RESOLUTION_COLORS.falsePositive },
  { key: 'removed', color: RESOLUTION_COLORS.removed },
] as const;

const STATUS_ORDER = ['OPEN', 'CONFIRMED', 'ACCEPTED', 'FALSE_POSITIVE', 'FIXED'];

/**
 * Introduced versus resolved issues on main branches, how they got resolved, and every issue by status now.
 */
function ResolutionCharts({
  data,
  label,
  organization,
}: Readonly<{ data: QualityData; label: (date: string) => string; organization: string }>) {
  const navigate = useNavigate();
  const { resolutions } = data;
  const { totals } = resolutions;
  // yyyy-MM-dd strings compare in date order
  const incomplete = resolutions.period.from < resolutions.completeFrom;
  const series = RESOLUTION_SERIES.map(({ color, key }) => ({
    color,
    key,
    label: translate('analytics.quality.outcome', key),
  }));
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);

  return (
    <>
      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.introduced_resolved.description')}
        title={translate('analytics.quality.introduced_resolved')}
      >
        <LineTimeline
          formatValue={(value) => formatMeasure(value, MetricType.ShortInteger)}
          points={resolutions.timeline.map((bucket) => ({
            key: bucket.date,
            label: label(bucket.date),
            values: {
              resolved: bucket.fixed + bucket.removed + bucket.accepted + bucket.falsePositive,
              introduced: bucket.introduced,
            },
          }))}
          series={[
            {
              color: STATUS_COLORS.success,
              key: 'resolved',
              label: translate('analytics.quality.resolved_series'),
            },
            {
              color: STATUS_COLORS.danger,
              dashed: true,
              key: 'introduced',
              label: translate('analytics.quality.introduced_series'),
            },
          ]}
        />
        <Text as="p" className="sw-mt-3">
          {translateWithParameters(
            totals.netChange <= 0 ? 'analytics.quality.net_down' : 'analytics.quality.net_up',
            integer(Math.abs(totals.netChange)),
          )}
        </Text>
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.how_resolved.description')}
        title={translate('analytics.quality.how_resolved')}
      >
        {totals.resolved > 0 ? (
          <div className="sw-flex sw-flex-col sw-gap-4">
            <ColumnTimeline
              columns={resolutions.timeline.map((bucket) => ({
                key: bucket.date,
                label: label(bucket.date),
                values: {
                  fixed: bucket.fixed,
                  accepted: bucket.accepted,
                  falsePositive: bucket.falsePositive,
                  removed: bucket.removed,
                },
              }))}
              series={series}
            />
            <BarList
              items={series.map(({ color, key, label: name }) => ({
                color,
                key,
                label: name,
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
        ) : (
          <Text isSubdued>{translate('analytics.quality.no_resolutions')}</Text>
        )}
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

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.quality.by_status.description')}
        title={translate('analytics.quality.by_status')}
      >
        <BarList
          items={STATUS_ORDER.map((status) => ({
            color: status === 'FIXED' ? RESOLUTION_COLORS.fixed : MAGNITUDE_COLOR,
            key: status,
            label: translate('issue.issue_status', status),
            value: data.issueStatuses[status] ?? 0,
          }))}
          onSelect={(status) => navigate(getOrgIssuesUrl({ issueStatuses: status }, organization))}
        />
      </ChartCard>
    </>
  );
}
