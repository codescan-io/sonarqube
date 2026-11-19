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

import { LinkStandalone, Text, TextSize } from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import {
  Badge,
  ContentCell,
  FlagMessage,
  NumericalCell,
  Spinner,
  Table,
  TableRow,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { CustomRuleStats, getCustomRules } from '../../../api/analytics';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getOrgIssuesUrl } from '../../../helpers/urls';
import { exportFileName } from '../csv';
import { TUNING_DISMISSAL_RATE, customRulesTable, needsTuning } from '../governance';
import { periodRequest, useLoaded } from '../useLoaded';
import { SEVERITIES } from '../utils';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, SEVERITY_COLORS } from './charts/colors';
import ColumnTimeline from './charts/ColumnTimeline';
import Sparkline from './charts/Sparkline';
import ExportCsvButton from './ExportCsvButton';

const COLUMNS = ['26%', '8%', '22%', '10%', '10%', '12%', '12%'];
const TREND_RULES = 5;
/** Hues for the most active rules in the trend chart: slots 1-5 of the reference palette. */
const RULE_COLORS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#8a63d2'];

/** Which custom rules raise findings, how severe, how they trend, and which ones get dismissed so often they need tuning. */
export default function CustomRulesPanel({
  organization,
  period,
}: Readonly<{ organization: string; period: number }>) {
  const { data, error, loading } = useLoaded(
    () => getCustomRules({ organization, ...periodRequest(period) }),
    [organization, period],
  );

  if (data === undefined) {
    return error ? <LoadError /> : <Spinner loading={loading} />;
  }
  const { rules } = data;
  const tuning = rules.filter(needsTuning);

  return (
    <div
      aria-busy={loading}
      className={`sw-grid sw-grid-cols-12 sw-gap-6 ${loading ? 'sw-opacity-50' : ''}`}
    >
      {error && (
        <div className="sw-col-span-12">
          <LoadError />
        </div>
      )}
      <ChartCard
        action={
          <ExportCsvButton
            fileName={exportFileName('custom-rules', organization)}
            getTable={() => customRulesTable(rules)}
          />
        }
        className="sw-col-span-12"
        description={translateWithParameters(
          'analytics.governance.rules.description',
          Math.round(TUNING_DISMISSAL_RATE * 100),
        )}
        title={translate('analytics.governance.rules')}
      >
        {rules.length === 0 ? (
          <Text isSubdued>{translate('analytics.governance.rules.none')}</Text>
        ) : (
          <>
            {tuning.length > 0 && (
              <FlagMessage className="sw-mb-4" variant="warning">
                {translateWithParameters('analytics.governance.rules.tuning_count', tuning.length)}
              </FlagMessage>
            )}
            <RulesTable organization={organization} rules={rules} />
          </>
        )}
      </ChartCard>

      {rules.some((rule) => rule.introduced + rule.closed > 0) && (
        <TrendCard period={period} rules={rules} />
      )}
    </div>
  );
}

function LoadError() {
  return (
    <FlagMessage className="sw-mb-4" variant="error">
      {translate('analytics.load_error')}
    </FlagMessage>
  );
}

function RulesTable({
  organization,
  rules,
}: Readonly<{ organization: string; rules: CustomRuleStats[] }>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  return (
    <Table
      columnCount={COLUMNS.length}
      columnWidths={COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
          <NumericalCell>{translate('analytics.governance.rules.open')}</NumericalCell>
          <ContentCell>{translate('analytics.governance.rules.by_severity')}</ContentCell>
          <NumericalCell>{translate('analytics.governance.rules.dismissed')}</NumericalCell>
          <NumericalCell>{translate('analytics.governance.rules.fixed')}</NumericalCell>
          <NumericalCell>{translate('analytics.governance.rules.introduced')}</NumericalCell>
          <ContentCell>{translate('analytics.governance.rules.trend')}</ContentCell>
        </TableRow>
      }
    >
      {rules.map((rule) => (
        <TableRow key={rule.key}>
          <ContentCell>
            <span className="sw-flex sw-flex-col sw-min-w-0">
              <span className="sw-flex sw-items-center sw-gap-2">
                <span className="sw-truncate" title={rule.key}>
                  {rule.name ?? rule.key}
                </span>
                {needsTuning(rule) && (
                  <Badge variant="deleted">{translate('analytics.governance.rules.tune')}</Badge>
                )}
              </span>
              <Text isSubdued size={TextSize.Small}>
                {[rule.language, rule.severity && translate('severity', rule.severity), rule.key]
                  .filter(Boolean)
                  .join(' · ')}
              </Text>
            </span>
          </ContentCell>
          <NumericalCell>
            {rule.issues.open > 0 ? (
              <LinkStandalone
                to={getOrgIssuesUrl(
                  { issueStatuses: 'OPEN,CONFIRMED', rules: rule.key },
                  organization,
                )}
              >
                {integer(rule.issues.open)}
              </LinkStandalone>
            ) : (
              '0'
            )}
          </NumericalCell>
          <ContentCell>
            {rule.issues.open > 0 ? (
              <span className="sw-flex sw-flex-wrap sw-gap-x-3 sw-gap-y-1">
                {SEVERITIES.filter(
                  (severity) => (rule.issues.openBySeverity[severity] ?? 0) > 0,
                ).map((severity) => (
                  <span className="sw-inline-flex sw-items-center sw-gap-1" key={severity}>
                    <span
                      aria-hidden
                      className="sw-inline-block sw-w-2 sw-h-2 sw-rounded-1"
                      style={{ backgroundColor: SEVERITY_COLORS[severity] }}
                    />
                    {`${integer(rule.issues.openBySeverity[severity] ?? 0)} ${translate('severity', severity)}`}
                  </span>
                ))}
              </span>
            ) : (
              <Text isSubdued>-</Text>
            )}
          </ContentCell>
          <NumericalCell>
            <span className="sw-flex sw-flex-col">
              <span>{integer(rule.issues.accepted + rule.issues.falsePositive)}</span>
              {rule.dismissalRate !== undefined && (
                <Text isSubdued size={TextSize.Small}>
                  {`${Math.round(rule.dismissalRate * 100)}%`}
                </Text>
              )}
            </span>
          </NumericalCell>
          <NumericalCell>{integer(rule.issues.fixed)}</NumericalCell>
          <NumericalCell>{`+${integer(rule.introduced)} / −${integer(rule.closed)}`}</NumericalCell>
          <ContentCell>
            <Sparkline
              color={MAGNITUDE_COLOR}
              values={rule.timeline.map((point) => point.introduced)}
              width={96}
            />
          </ContentCell>
        </TableRow>
      ))}
    </Table>
  );
}

function TrendCard({ period, rules }: Readonly<{ period: number; rules: CustomRuleStats[] }>) {
  const weekly = period > 31;
  const top = [...rules].sort((a, b) => b.introduced - a.introduced).slice(0, TREND_RULES);
  const dates = top[0]?.timeline.map((point) => point.date) ?? [];
  return (
    <ChartCard
      className="sw-col-span-12"
      description={translateWithParameters(
        'analytics.governance.rules.trend.description',
        TREND_RULES,
      )}
      title={translate('analytics.governance.rules.trend_title')}
    >
      <ColumnTimeline
        columns={dates.map((date, i) => ({
          key: date,
          label: weekly
            ? translateWithParameters(
                'analytics.activity.week_of',
                format(parseDate(date), 'MMM d'),
              )
            : format(parseDate(date), 'MMM d'),
          values: Object.fromEntries(
            top.map((rule) => [rule.key, rule.timeline[i]?.introduced ?? 0]),
          ),
        }))}
        series={top.map((rule, i) => ({
          color: RULE_COLORS[i % RULE_COLORS.length],
          key: rule.key,
          label: rule.name ?? rule.key,
        }))}
      />
      <Text as="p" className="sw-mt-2" isSubdued size={TextSize.Small}>
        {translate('analytics.governance.rules.retention_note')}
      </Text>
    </ChartCard>
  );
}
