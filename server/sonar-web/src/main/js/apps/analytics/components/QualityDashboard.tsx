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
  IconError,
  IconSecurityFinding,
  IconVulnerability,
  LinkStandalone,
  Text,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { ContentCell, FlagMessage, NumericalCell, Spinner, Table, TableRow } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  QualityTrendPoint,
  SecurityRemediation,
  TypeSeverityCounts,
  getQualityTrend,
  getSecurityRemediation,
} from '../../../api/analytics';
import { OrganizationHealth, getOrganizationHealth } from '../../../api/codescan-analytics';
import { searchIssues } from '../../../api/issues';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getComponentIssuesUrl, getOrgIssuesUrl } from '../../../helpers/urls';
import { formatChange, getChange } from '../activity';
import { CsvTable, exportFileName } from '../csv';
import { countOf } from '../governance';
import { fetchCurrentIssues, periodStart, withCurrent } from '../period';
import { AgeBucket, IMPACT_SEVERITIES, SLA_SEVERITIES, getAgeBuckets } from '../quality';
import { periodRequest } from '../useLoaded';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { HOTSPOT_COLOR, MAGNITUDE_COLOR, STATUS_COLORS, tint } from './charts/colors';
import { toDelta } from './charts/Delta';
import ExportCsvButton from './ExportCsvButton';
import FindingsBreakdown from './FindingsBreakdown';
import SecurityRemediationPanel from './SecurityRemediationPanel';
import StatTile from './StatTile';

interface Props {
  organization: string;
  period: number;
}

const OPEN = 'OPEN,CONFIRMED';
const TOP_RULES = 10;

interface RiskData {
  ageMatrix: Array<{ bucket: AgeBucket; counts: Record<string, number> }>;
  languages: Array<{ count: number; key: string; name: string }>;
  /** Unresolved issues of main branches now, security hotspots to review included. */
  matrix: TypeSeverityCounts;
  rules: Array<{ count: number; key: string; name: string }>;
  security: SecurityRemediation;
  /** Main branches over the period, the last point being now. */
  trend: QualityTrendPoint[];
}

function facetOf(response: Awaited<ReturnType<typeof searchIssues>> | undefined, property: string) {
  if (!response) {
    throw new Error('Issue search failed');
  }
  return response.facets.find((facet) => facet.property === property)?.values ?? [];
}

async function fetchRiskData(organization: string, days: number): Promise<RiskData> {
  const request = { organization, ...periodRequest(days) };
  const buckets = getAgeBuckets(new Date());

  const [trend, current, security, rulesAndLanguages, ...ages] = await Promise.all([
    getQualityTrend(request),
    fetchCurrentIssues(organization),
    getSecurityRemediation(request),
    searchIssues({
      organization,
      issueStatuses: OPEN,
      facets: 'rules,languages',
      additionalFields: 'rules,languages',
      ps: 1,
    }),
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
    trend: withCurrent(trend.timeline, current),
    matrix: current.matrix,
    security,
    rules: facetOf(rulesAndLanguages, 'rules')
      .slice(0, TOP_RULES)
      .map(({ val, count }) => ({ key: val, count, name: ruleNames.get(val) ?? val })),
    languages: facetOf(rulesAndLanguages, 'languages')
      .filter(({ count }) => count > 0)
      .map(({ val, count }) => ({ key: val, count, name: languageNames.get(val) ?? val })),
    ageMatrix: buckets.map((bucket, i) => ({
      bucket,
      counts: Object.fromEntries(
        facetOf(ages[i], 'impactSeverities').map(({ val, count }) => [val, count]),
      ),
    })),
  };
}

/**
 * Where the risk is: open findings by severity and type with their trends, how fast security issues get fixed against
 * their SLA, how old the open ones are, and which rules and technologies raise the most issues.
 */
export default function QualityDashboard({ organization, period }: Readonly<Props>) {
  const [data, setData] = React.useState<RiskData>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    fetchRiskData(organization, period).then(
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
      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      {data === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <RiskTiles data={data} organization={organization} />
          <FindingsBreakdown
            matrix={data.matrix}
            organization={organization}
            trend={data.trend}
            weekly={period > 31}
          />
          <SecurityRemediationPanel
            data={data.security}
            organization={organization}
            weekly={period > 31}
          />
          <RootCauseCharts data={data} organization={organization} />
          <RootCauses organization={organization} />
        </div>
      )}
    </>
  );
}

function RiskTiles({ data, organization }: Readonly<{ data: RiskData; organization: string }>) {
  const navigate = useNavigate();
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const first = periodStart(data.trend);
  const last = data.trend[data.trend.length - 1];
  const sinceStart = translate('analytics.quality.since_period_start');
  const change = (now?: number, then?: number) => {
    const value = getChange(now, then);
    return value === undefined
      ? undefined
      : toDelta(value, (r) => formatChange(r), false, sinceStart);
  };
  const highSeverity =
    countOf(data.matrix, undefined, 'BLOCKER') +
    countOf(data.matrix, undefined, 'CRITICAL') -
    countOf(data.matrix, 'SECURITY_HOTSPOT', 'BLOCKER') -
    countOf(data.matrix, 'SECURITY_HOTSPOT', 'CRITICAL');
  const hotspots = countOf(data.matrix, 'SECURITY_HOTSPOT');

  return (
    <div className="sw-grid sw-grid-cols-4 sw-gap-4 sw-mb-6">
      <StatTile
        color={MAGNITUDE_COLOR}
        delta={change(last?.openIssues, first?.openIssues)}
        icon={<IconBug />}
        label={translate('analytics.quality.open_issues')}
        onClick={() => navigate(getOrgIssuesUrl({ issueStatuses: OPEN }, organization))}
        trend={data.trend.map(({ openIssues }) => openIssues)}
      >
        {last ? integer(last.openIssues) : '-'}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.danger}
        delta={change(last?.vulnerabilities, first?.vulnerabilities)}
        icon={<IconVulnerability />}
        label={translate('analytics.quality.vulnerabilities')}
        onClick={() =>
          navigate(getOrgIssuesUrl({ issueStatuses: OPEN, types: 'VULNERABILITY' }, organization))
        }
        trend={data.trend.map(({ vulnerabilities }) => vulnerabilities)}
      >
        {last ? integer(last.vulnerabilities) : '-'}
      </StatTile>
      <StatTile
        color={highSeverity > 0 ? STATUS_COLORS.danger : STATUS_COLORS.success}
        footnote={translate('analytics.risk.high_severity.footnote')}
        icon={<IconError />}
        label={translate('analytics.risk.high_severity')}
        onClick={() =>
          navigate(
            getOrgIssuesUrl({ issueStatuses: OPEN, severities: 'BLOCKER,CRITICAL' }, organization),
          )
        }
      >
        {integer(highSeverity)}
      </StatTile>
      <StatTile
        color={HOTSPOT_COLOR}
        footnote={translate('analytics.risk.hotspots.footnote')}
        icon={<IconSecurityFinding />}
        label={translate('analytics.risk.hotspots')}
      >
        {integer(hotspots)}
      </StatTile>
    </div>
  );
}

function ageTable(matrix: RiskData['ageMatrix']): CsvTable {
  return {
    header: [
      translate('analytics.quality.severity'),
      ...matrix.map(({ bucket }) => translate('analytics.quality.age', bucket.key)),
    ],
    rows: IMPACT_SEVERITIES.map((severity) => [
      translate('severity_impact', severity),
      ...matrix.map(({ counts }) => counts[severity] ?? 0),
    ]),
  };
}

function RootCauseCharts({
  data,
  organization,
}: Readonly<{ data: RiskData; organization: string }>) {
  const navigate = useNavigate();
  const counted = (items: RiskData['rules'], title: string): CsvTable => ({
    header: [title, translate('analytics.quality.issues')],
    rows: items.map(({ count, name }) => [name, count]),
  });

  return (
    <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
      <ChartCard
        action={
          <ExportCsvButton
            fileName={exportFileName('open-security-issues-by-age', organization)}
            getTable={() => ageTable(data.ageMatrix)}
          />
        }
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
        action={
          <ExportCsvButton
            fileName={exportFileName('most-frequent-rules', organization)}
            getTable={() => counted(data.rules, translate('analytics.quality.rule'))}
          />
        }
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
        action={
          <ExportCsvButton
            fileName={exportFileName('open-issues-by-technology', organization)}
            getTable={() => counted(data.languages, translate('analytics.risk.technology'))}
          />
        }
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
    </div>
  );
}

const AGE_COLUMNS = ['24%', '19%', '19%', '19%', '19%'];

function SecurityAgeTable({
  matrix,
  organization,
}: Readonly<{ matrix: RiskData['ageMatrix']; organization: string }>) {
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
