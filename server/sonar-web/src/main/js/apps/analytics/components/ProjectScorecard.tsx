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
import { IconCheckCircle, IconError, LinkStandalone, Text } from '@sonarsource/echoes-react';
import * as React from 'react';
import {
  FlagMessage,
  MetricsRatingBadge,
  Modal,
  QualityGateIndicator,
  Spinner,
  themeBorder,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { getTasksForComponent } from '../../../api/ce';
import { searchIssues } from '../../../api/issues';
import { getMeasures } from '../../../api/measures';
import { getQualityGateProjectStatus } from '../../../api/quality-gates';
import DateTimeFormatter from '../../../components/intl/DateTimeFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  getComponentBackgroundTaskUrl,
  getComponentIssuesUrl,
  getProjectUrl,
} from '../../../helpers/urls';
import { QualityGateProjectStatusCondition } from '../../../types/quality-gates';
import { Task } from '../../../types/tasks';
import { Measure } from '../../../types/types';
import { ReadinessReason, SLA_SEVERITIES, getReadiness } from '../quality';
import { STATUS_COLORS, tint } from './charts/colors';

interface Props {
  /** Last analysis date when already known; CE history alone forgets analyses after 180 days. */
  analysisDate?: string;
  name?: string;
  onClose: () => void;
  projectKey: string;
}

const METRICS = [
  'alert_status',
  'reliability_rating',
  'security_rating',
  'sqale_rating',
  'coverage',
  'duplicated_lines_density',
  'ncloc',
  'violations',
  'new_violations',
  'vulnerabilities',
  'security_hotspots',
];

const RATINGS: Array<{ key: string; label: string }> = [
  { key: 'reliability_rating', label: 'metric_domain.Reliability' },
  { key: 'security_rating', label: 'metric_domain.Security' },
  { key: 'sqale_rating', label: 'metric_domain.Maintainability' },
];

const RATING_LABELS = ['A', 'B', 'C', 'D', 'E'] as const;

interface ScorecardData {
  conditions: QualityGateProjectStatusCondition[];
  criticalVulnerabilities: number;
  lastTask?: Task;
  measures: Record<string, Measure>;
  qualityGate?: string;
}

async function fetchScorecard(projectKey: string): Promise<ScorecardData> {
  const [status, measures, vulnerabilities, tasks] = await Promise.all([
    getQualityGateProjectStatus({ projectKey }).catch(() => undefined),
    getMeasures({ component: projectKey, metricKeys: METRICS.join() }),
    searchIssues({
      components: projectKey,
      issueStatuses: 'OPEN,CONFIRMED',
      impactSoftwareQualities: 'SECURITY',
      impactSeverities: SLA_SEVERITIES.join(),
      ps: 1,
    }),
    getTasksForComponent(projectKey),
  ]);
  return {
    qualityGate: status?.status,
    conditions: (status?.conditions ?? []).filter((c) => c.status === 'ERROR'),
    measures: Object.fromEntries(measures.map((m) => [m.metric, m])),
    criticalVulnerabilities: vulnerabilities?.paging.total ?? 0,
    lastTask: tasks.current,
  };
}

/**
 * Everything needed to decide whether a project's main branch can ship, on one screen.
 */
export default function ProjectScorecard(props: Readonly<Props>) {
  const { analysisDate, name, onClose, projectKey } = props;
  const [data, setData] = React.useState<ScorecardData>();
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setData(undefined);
    setError(false);
    fetchScorecard(projectKey).then(
      (result) => active && setData(result),
      () => active && setError(true),
    );
    return () => {
      active = false;
    };
  }, [projectKey]);

  return (
    <Modal
      body={
        <>
          {error && <FlagMessage variant="error">{translate('analytics.load_error')}</FlagMessage>}
          {!error && data === undefined && <Spinner />}
          {data && (
            <ScorecardBody analysisDate={analysisDate} data={data} projectKey={projectKey} />
          )}
        </>
      }
      headerDescription={translate('analytics.scorecard.description')}
      headerTitle={translateWithParameters('analytics.scorecard.title', name ?? projectKey)}
      isLarge
      isScrollable
      onClose={onClose}
      secondaryButtonLabel={translate('close')}
    />
  );
}

function ScorecardBody(
  props: Readonly<{ analysisDate?: string; data: ScorecardData; projectKey: string }>,
) {
  const { data, projectKey } = props;
  const { measures } = data;
  const value = (metric: string) => measures[metric]?.value;
  const newValue = (metric: string) => measures[metric]?.period?.value;
  const integer = (raw?: string) =>
    raw === undefined ? '-' : formatMeasure(raw, MetricType.Integer);
  const analysisDate =
    props.analysisDate ?? data.lastTask?.executedAt ?? data.lastTask?.submittedAt;
  const { ready, reasons } = getReadiness({
    analysisDate,
    criticalVulnerabilities: data.criticalVulnerabilities,
    lastTaskStatus: data.lastTask?.status,
    now: new Date(),
    qualityGate: data.qualityGate,
  });

  return (
    <div className="sw-flex sw-flex-col sw-gap-6">
      <Verdict
        role="status"
        style={{
          backgroundColor: tint(ready ? STATUS_COLORS.success : STATUS_COLORS.danger, 10),
          borderColor: ready ? STATUS_COLORS.success : STATUS_COLORS.danger,
        }}
      >
        <span
          className="sw-text-[1.5rem] sw-flex"
          style={{ color: ready ? STATUS_COLORS.success : STATUS_COLORS.danger }}
        >
          {ready ? <IconCheckCircle /> : <IconError />}
        </span>
        <div>
          <p className="sw-heading-sm">
            {translate(ready ? 'analytics.scorecard.ready' : 'analytics.scorecard.not_ready')}
          </p>
          {reasons.length > 0 && (
            <ul className="sw-mt-1 sw-list-disc sw-pl-5">
              {reasons.map((reason) => (
                <li key={reason}>
                  <Text>
                    {reason === ReadinessReason.CriticalVulnerabilities
                      ? translateWithParameters(
                          'analytics.scorecard.reason.critical_vulnerabilities',
                          data.criticalVulnerabilities,
                        )
                      : translate('analytics.scorecard.reason', reason)}
                  </Text>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Verdict>

      <section>
        <h4 className="sw-heading-xs sw-mb-2">{translate('analytics.chart.quality_gate')}</h4>
        <div className="sw-flex sw-items-center sw-gap-2">
          <QualityGateIndicator
            size="sm"
            status={
              data.qualityGate === 'OK' || data.qualityGate === 'ERROR' ? data.qualityGate : 'NONE'
            }
          />
          <Text>
            {translate(
              'analytics.status',
              data.qualityGate === 'OK'
                ? 'passed'
                : data.qualityGate === 'ERROR'
                  ? 'failed'
                  : 'not_available',
            )}
          </Text>
        </div>
        {data.conditions.length > 0 && (
          <ul className="sw-mt-2 sw-flex sw-flex-col sw-gap-1">
            {data.conditions.map((condition) => (
              <li key={`${condition.metricKey}-${condition.periodIndex}`}>
                <Text>
                  {translateWithParameters(
                    'analytics.scorecard.failed_condition',
                    translate('metric', condition.metricKey, 'name'),
                    condition.actualValue,
                    condition.errorThreshold,
                  )}
                </Text>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="sw-grid sw-grid-cols-3 sw-gap-4">
        {RATINGS.map(({ key, label }) => {
          const rating = value(key);
          return (
            <Figure key={key}>
              <MetricsRatingBadge
                label={translate(label)}
                rating={rating ? RATING_LABELS[Math.round(Number(rating)) - 1] : undefined}
                size="md"
              />
              <Text>{translate(label)}</Text>
            </Figure>
          );
        })}
      </section>

      <section className="sw-grid sw-grid-cols-4 sw-gap-4">
        <Stat
          label={translate('analytics.quality.open_issues')}
          value={integer(value('violations'))}
        />
        <Stat
          label={translate('analytics.scorecard.new_issues')}
          value={integer(newValue('new_violations') ?? value('new_violations'))}
        />
        <Stat
          label={translateWithParameters(
            'analytics.scorecard.critical_vulnerabilities',
            SLA_SEVERITIES.length,
          )}
          value={formatMeasure(data.criticalVulnerabilities, MetricType.Integer)}
        />
        <Stat
          label={translate('analytics.scorecard.hotspots')}
          value={integer(value('security_hotspots'))}
        />
        <Stat
          label={translate('metric.coverage.name')}
          value={
            value('coverage') === undefined
              ? '-'
              : formatMeasure(value('coverage'), MetricType.Percent)
          }
        />
        <Stat
          label={translate('metric.duplicated_lines_density.name')}
          value={
            value('duplicated_lines_density') === undefined
              ? '-'
              : formatMeasure(value('duplicated_lines_density'), MetricType.Percent)
          }
        />
        <Stat
          label={translate('metric.ncloc.name')}
          value={
            value('ncloc') === undefined
              ? '-'
              : formatMeasure(value('ncloc'), MetricType.ShortInteger)
          }
        />
        <div className="sw-flex sw-flex-col">
          <Text isSubdued>{translate('analytics.table.last_analysis')}</Text>
          {analysisDate ? (
            <Text className="sw-typo-semibold">
              <DateTimeFormatter date={analysisDate} short />
              {' · '}
              {translate('background_task.status', data.lastTask?.status ?? '')}
            </Text>
          ) : (
            <Text>-</Text>
          )}
        </div>
      </section>

      <section className="sw-flex sw-gap-6">
        <LinkStandalone to={getProjectUrl(projectKey)}>
          {translate('analytics.scorecard.open_project')}
        </LinkStandalone>
        <LinkStandalone to={getComponentIssuesUrl(projectKey, { issueStatuses: 'OPEN,CONFIRMED' })}>
          {translate('analytics.chart.view_issues')}
        </LinkStandalone>
        <LinkStandalone to={getComponentBackgroundTaskUrl(projectKey)}>
          {translate('background_tasks.page')}
        </LinkStandalone>
      </section>
    </div>
  );
}

function Stat({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="sw-flex sw-flex-col">
      <Text isSubdued>{label}</Text>
      <span className="sw-heading-md">{value}</span>
    </div>
  );
}

const Verdict = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid;
  border-radius: 8px;
`;

const Figure = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: ${themeBorder('default')};
  border-radius: 8px;
`;
