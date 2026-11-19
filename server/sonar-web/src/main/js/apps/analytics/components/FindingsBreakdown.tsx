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

import { Text } from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { ToggleButton } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { QualityTrendPoint, TypeSeverityCounts } from '../../../api/analytics';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getOrgIssuesUrl } from '../../../helpers/urls';
import { exportFileName } from '../csv';
import { REPORT_ISSUE_TYPES, countOf, matrixTable } from '../governance';
import { fromFirstAnalysis } from '../period';
import { useIsReport } from '../report';
import { SEVERITIES } from '../utils';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { HOTSPOT_COLOR, ISSUE_TYPE_COLORS, SEVERITY_COLORS } from './charts/colors';
import Heatmap from './charts/Heatmap';
import LineTimeline from './charts/LineTimeline';
import ExportCsvButton from './ExportCsvButton';

interface Props {
  /** Unresolved issues of main branches now, by type and severity, security hotspots to review included. */
  matrix: TypeSeverityCounts;
  organization: string;
  trend: QualityTrendPoint[];
  weekly: boolean;
}

enum DrillDown {
  SeverityFirst = 'severity',
  TypeFirst = 'type',
}

const TYPE_COLORS: Record<string, string> = {
  ...ISSUE_TYPE_COLORS,
  SECURITY_HOTSPOT: HOTSPOT_COLOR,
};

const typeLabel = (type: string) => translate('issue.type', type, 'plural');
const severityLabel = (severity: string) => translate('severity', severity);

/**
 * Open findings by severity and by issue type, security hotspots included, each drilling down into the other, with
 * the full matrix and how both trend: what remains before a release, a penetration test or a risk assessment.
 */
export default function FindingsBreakdown({
  matrix,
  organization,
  trend,
  weekly,
}: Readonly<Props>) {
  return (
    <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
      <DrillDownCharts matrix={matrix} organization={organization} />
      <MatrixCard matrix={matrix} organization={organization} />
      <TrendCharts trend={trend} weekly={weekly} />
    </div>
  );
}

function issuesUrl(organization: string, type?: string, severity?: string) {
  return getOrgIssuesUrl(
    { issueStatuses: 'OPEN,CONFIRMED', types: type, severities: severity },
    organization,
  );
}

function DrillDownCharts({
  matrix,
  organization,
}: Readonly<{ matrix: TypeSeverityCounts; organization: string }>) {
  const isReport = useIsReport();
  const navigate = useNavigate();
  const [mode, setMode] = React.useState(DrillDown.SeverityFirst);
  const [selected, setSelected] = React.useState<string>();
  const severityFirst = mode === DrillDown.SeverityFirst;
  const primary: string[] = severityFirst ? [...SEVERITIES] : [...REPORT_ISSUE_TYPES];
  // Until a bar is picked, the breakdown shows the first item with findings rather than an empty card.
  const current = selected ?? primary.find((key) => countOf(matrix, ...pair(severityFirst, key)));

  const changeMode = (value: DrillDown) => {
    setMode(value);
    setSelected(undefined);
  };

  return (
    <>
      <ChartCard
        action={
          isReport ? undefined : (
            <ToggleButton
              label={translate('analytics.governance.security.drill_down')}
              onChange={changeMode}
              options={[
                {
                  label: translate('analytics.governance.security.by_severity'),
                  value: DrillDown.SeverityFirst,
                },
                {
                  label: translate('analytics.governance.security.by_type'),
                  value: DrillDown.TypeFirst,
                },
              ]}
              value={mode}
            />
          )
        }
        className="sw-col-span-6"
        description={translate(
          severityFirst
            ? 'analytics.governance.security.by_severity.description'
            : 'analytics.governance.security.by_type.description',
        )}
        title={translate(
          severityFirst
            ? 'analytics.governance.security.by_severity'
            : 'analytics.governance.security.by_type',
        )}
      >
        <BarList
          items={primary.map((key) => ({
            color: severityFirst ? SEVERITY_COLORS[key] : TYPE_COLORS[key],
            key,
            label: severityFirst ? severityLabel(key) : typeLabel(key),
            value: countOf(matrix, ...pair(severityFirst, key)),
          }))}
          onSelect={setSelected}
          selectedKey={current}
        />
      </ChartCard>

      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.governance.security.breakdown.description')}
        title={
          current === undefined
            ? translate('analytics.governance.security.breakdown')
            : translateWithParameters(
                'analytics.governance.security.breakdown_of',
                severityFirst ? severityLabel(current) : typeLabel(current),
              )
        }
      >
        {current === undefined ? (
          <Text isSubdued>{translate('analytics.chart.no_issues')}</Text>
        ) : (
          <BarList
            items={(severityFirst ? [...REPORT_ISSUE_TYPES] : [...SEVERITIES]).map((key) => {
              const [type, severity] = severityFirst ? [key, current] : [current, key];
              return {
                color: severityFirst ? TYPE_COLORS[key] : SEVERITY_COLORS[key],
                key,
                label: severityFirst ? typeLabel(key) : severityLabel(key),
                value: countOf(matrix, type, severity),
              };
            })}
            onSelect={(key) => {
              const [type, severity] = severityFirst ? [key, current] : [current, key];
              // hotspots are reviewed on their own page, not the issues page
              if (type !== 'SECURITY_HOTSPOT') {
                navigate(issuesUrl(organization, type, severity));
              }
            }}
          />
        )}
      </ChartCard>
    </>
  );
}

/** [type, severity] for a primary key. */
function pair(severityFirst: boolean, key: string): [string | undefined, string | undefined] {
  return severityFirst ? [undefined, key] : [key, undefined];
}

function MatrixCard({
  matrix,
  organization,
}: Readonly<{ matrix: TypeSeverityCounts; organization: string }>) {
  const navigate = useNavigate();
  const table = () =>
    matrixTable(matrix, typeLabel, severityLabel, translate('analytics.governance.total'));
  return (
    <ChartCard
      action={
        <ExportCsvButton
          fileName={exportFileName('open-findings-by-type-and-severity', organization)}
          getTable={table}
        />
      }
      className="sw-col-span-12"
      description={translate('analytics.governance.security.matrix.description')}
      title={translate('analytics.governance.security.matrix')}
    >
      <Heatmap
        columns={SEVERITIES.map((severity) => ({ key: severity, label: severityLabel(severity) }))}
        onSelect={(type, severity) => {
          if (type !== 'SECURITY_HOTSPOT') {
            navigate(issuesUrl(organization, type, severity));
          }
        }}
        rows={REPORT_ISSUE_TYPES.map((type) => ({ key: type, label: typeLabel(type) }))}
        values={Object.fromEntries(
          REPORT_ISSUE_TYPES.map((type) => [
            type,
            Object.fromEntries(
              SEVERITIES.map((severity) => [severity, countOf(matrix, type, severity)]),
            ),
          ]),
        )}
      />
    </ChartCard>
  );
}

function TrendCharts({
  trend: fullTrend,
  weekly,
}: Readonly<{ trend: QualityTrendPoint[]; weekly: boolean }>) {
  const trend = fromFirstAnalysis(fullTrend);
  const label = (date: string) =>
    weekly
      ? translateWithParameters('analytics.activity.week_of', format(parseDate(date), 'MMM d'))
      : format(parseDate(date), 'MMM d');
  const hasHistory = trend.some(({ bySeverity, projects }) => projects > 0 && bySeverity);
  const formatValue = (value: number) => formatMeasure(value, MetricType.ShortInteger);

  const chart = (
    keys: readonly string[],
    values: (point: QualityTrendPoint) => Record<string, number> | undefined,
    colors: Record<string, string>,
    keyLabel: (key: string) => string,
  ) =>
    hasHistory ? (
      <LineTimeline
        formatValue={formatValue}
        points={trend.map((point) => ({
          key: point.date,
          label: label(point.date),
          values: Object.fromEntries(keys.map((key) => [key, values(point)?.[key]])),
        }))}
        series={keys.map((key) => ({ color: colors[key], key, label: keyLabel(key) }))}
      />
    ) : (
      <Text isSubdued>{translate('analytics.quality.no_history')}</Text>
    );

  return (
    <>
      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.governance.security.severity_trend.description')}
        title={translate('analytics.governance.security.severity_trend')}
      >
        {chart(SEVERITIES, (point) => point.bySeverity, SEVERITY_COLORS, severityLabel)}
      </ChartCard>
      <ChartCard
        className="sw-col-span-6"
        description={translate('analytics.governance.security.type_trend.description')}
        title={translate('analytics.governance.security.type_trend')}
      >
        {chart(REPORT_ISSUE_TYPES, (point) => point.byType, TYPE_COLORS, typeLabel)}
      </ChartCard>
    </>
  );
}
