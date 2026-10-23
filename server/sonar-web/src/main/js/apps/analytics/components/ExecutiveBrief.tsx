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
import {
  IconArrowDownRight,
  IconArrowRight,
  IconArrowUpRight,
  IconCheckCircle,
  IconClock,
  IconSecurityFinding,
  IconSparkle,
  Text,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { Card, FlagMessage, Spinner, ToggleButton } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  getIssueResolutions,
  getOrganizationActivity,
  getQualityTrend,
  getSecurityRemediation,
} from '../../../api/analytics';
import { getAiValue, getOrganizationHealth } from '../../../api/codescan-analytics';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { formatChange, getActivityRequest, getChange } from '../activity';
import { BriefData, Insight, buildInsights, computeOrganizationHealth, formatDays } from '../brief';
import { Health, share } from '../health';
import { QUALITY_PERIODS, QualityPeriod, getFocusList } from '../quality';
import { formatHours } from '../roi';
import { computeTeams, hasTeams } from '../teams';
import { useOverviewData } from '../useOverviewData';
import { UNTAGGED } from '../utils';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS, tint } from './charts/colors';
import { toDelta } from './charts/Delta';
import InlineBar from './charts/InlineBar';
import HealthGrade, { GRADE_COLORS } from './HealthGrade';
import StatTile from './StatTile';

interface Props {
  /** Period shown first; the report sets it for every tab. */
  defaultPeriod?: QualityPeriod;
  organization: string;
}

const DEFAULT_BRIEF_PERIOD: QualityPeriod = 30;
const TOP_RISKS = 3;
const TEAMS_SHOWN = 3;

/** Every part but the quality trend is optional: a part that fails is left out of the brief. */
async function fetchBrief(organization: string, days: number): Promise<BriefData> {
  const request = { organization, ...getActivityRequest(days, new Date()) };
  const optional = <T,>(promise: Promise<T>) => promise.catch(() => undefined);
  const [trend, resolutions, security, activity, ai, health] = await Promise.all([
    getQualityTrend(request),
    optional(getIssueResolutions(request)),
    optional(getSecurityRemediation(request)),
    optional(getOrganizationActivity(request)),
    optional(getAiValue(organization, days)),
    optional(getOrganizationHealth(organization)),
  ]);
  return {
    activity,
    ai,
    qualityGateChanges: health?.qualityGateChanges,
    resolutions,
    security,
    trend: trend.timeline,
  };
}

/**
 * One page for leadership: an organization health grade and what it is made of, the headline numbers with how they
 * moved, a written summary of what changed, and where to act. It prints to a clean PDF and copies as plain text.
 */
export default function ExecutiveBrief({
  defaultPeriod = DEFAULT_BRIEF_PERIOD,
  organization,
}: Readonly<Props>) {
  const [now] = React.useState(() => new Date());
  const [period, setPeriod] = React.useState<QualityPeriod>(defaultPeriod);
  const [data, setData] = React.useState<BriefData>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);
  const overview = useOverviewData(organization);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    fetchBrief(organization, period).then(
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

  const health = React.useMemo(
    () => (data ? computeOrganizationHealth(data, overview.projects, now) : undefined),
    [data, overview.projects, now],
  );
  const insights = React.useMemo(() => (data ? buildInsights(data) : []), [data]);

  return (
    <div>
      <div className="sw-mb-6">
        <ToggleButton
          label={translate('analytics.activity.period')}
          onChange={(value) => setPeriod(value as QualityPeriod)}
          options={QUALITY_PERIODS.map((days) => ({
            label: translate('analytics.quality.period', String(days)),
            value: days,
          }))}
          value={period}
        />
      </div>

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.load_error')}
        </FlagMessage>
      )}

      {data === undefined || health === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-6">
            <HealthHero
              className="sw-col-span-5"
              health={health}
              loading={overview.loadingProjects}
            />
            <InsightsCard className="sw-col-span-7" insights={insights} />
          </div>

          <Kpis data={data} />

          <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-6">
            <TopRisks className="sw-col-span-7" now={now} overview={overview} />
            <TeamsAtAGlance className="sw-col-span-5" now={now} overview={overview} />
          </div>

          <Text as="p" isSubdued>
            {translate('analytics.brief.footnote')}
          </Text>
        </div>
      )}
    </div>
  );
}

const COMPONENT_LABELS: Record<string, string> = {
  qualityGates: 'analytics.health.component.quality_gates',
  security: 'analytics.health.component.security',
  velocity: 'analytics.health.component.velocity',
  reliability: 'analytics.health.component.reliability',
  freshness: 'analytics.health.component.freshness',
};

function HealthHero({
  className,
  health,
  loading,
}: Readonly<{ className: string; health: Health; loading: boolean }>) {
  return (
    <Card className={className}>
      <div className="sw-flex sw-items-center sw-gap-4 sw-mb-6">
        <HealthGrade grade={health.grade} score={health.score} size="lg" />
        <div>
          <Text as="p" isSubdued>
            {translate('analytics.health.title')}
          </Text>
          <span className="sw-flex sw-items-baseline sw-gap-1">
            <Score style={{ color: health.grade ? GRADE_COLORS[health.grade] : undefined }}>
              {health.score ?? '-'}
            </Score>
            <Text isSubdued>/ 100</Text>
          </span>
        </div>
      </div>
      <ul aria-busy={loading} className="sw-flex sw-flex-col sw-gap-3">
        {health.components.map(({ key, value, weight }) => (
          <li
            className="sw-grid sw-items-center sw-gap-3"
            key={key}
            style={{ gridTemplateColumns: '1fr 96px 40px' }}
          >
            <Text>
              {translate(COMPONENT_LABELS[key])} <Text isSubdued>({weight}%)</Text>
            </Text>
            <InlineBar
              color={
                value === undefined || value >= 80 ? STATUS_COLORS.success : STATUS_COLORS.warning
              }
              max={100}
              value={value ?? 0}
            />
            <Text className="sw-text-right">
              {value === undefined ? '-' : `${Math.round(value)}%`}
            </Text>
          </li>
        ))}
      </ul>
    </Card>
  );
}

const INSIGHT_ICONS = {
  good: { Icon: IconArrowUpRight, color: STATUS_COLORS.success },
  bad: { Icon: IconArrowDownRight, color: STATUS_COLORS.danger },
  neutral: { Icon: IconArrowRight, color: STATUS_COLORS.neutral },
};

function InsightsCard({
  className,
  insights,
}: Readonly<{ className: string; insights: Insight[] }>) {
  return (
    <ChartCard
      className={className}
      description={translate('analytics.brief.insights.description')}
      title={translate('analytics.brief.insights')}
    >
      {insights.length === 0 ? (
        <Text isSubdued>{translate('analytics.brief.insights.none')}</Text>
      ) : (
        <ul className="sw-flex sw-flex-col sw-gap-3">
          {insights.map(({ key, text, tone }) => {
            const { Icon, color } = INSIGHT_ICONS[tone];
            return (
              <li className="sw-flex sw-items-start sw-gap-3" key={key}>
                <InsightIcon
                  aria-label={translate('analytics.brief.tone', tone)}
                  role="img"
                  style={{ backgroundColor: tint(color, 15), color }}
                >
                  <Icon />
                </InsightIcon>
                <Text>{text}</Text>
              </li>
            );
          })}
        </ul>
      )}
    </ChartCard>
  );
}

function Kpis({ data }: Readonly<{ data: BriefData }>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const first = data.trend[0];
  const last = data.trend[data.trend.length - 1];
  const sinceStart = translate('analytics.quality.since_period_start');
  const vsPrevious = translate('analytics.activity.vs_previous_period');
  const passNow = last ? share(last.passed, last.passed + last.failed) : undefined;
  const passThen = first ? share(first.passed, first.passed + first.failed) : undefined;
  const vulnerabilityChange = getChange(last?.vulnerabilities, first?.vulnerabilities);
  const security = data.security;
  const mttrChange = security?.previous.complete
    ? getChange(security.totals.medianTimeToFixMs, security.previous.medianTimeToFixMs)
    : undefined;
  const compliance = security
    ? share(security.totals.fixedWithinSla, security.totals.fixed)
    : undefined;
  const backlogChange = getChange(last?.remediationEffortMinutes, first?.remediationEffortMinutes);
  const saved = data.ai?.fixJobs.effortSavedMinutes;

  return (
    <div className="sw-grid sw-grid-cols-6 sw-gap-4 sw-mb-6">
      <StatTile
        color={STATUS_COLORS.success}
        delta={
          passNow === undefined || passThen === undefined
            ? undefined
            : toDelta(passNow - passThen, (r) => formatChange(r, ' pts'), true, sinceStart)
        }
        icon={<IconCheckCircle />}
        label={translate('analytics.quality.pass_rate')}
        trend={data.trend.map((point) => share(point.passed, point.passed + point.failed))}
      >
        {passNow === undefined ? '-' : `${Math.round(passNow)}%`}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.danger}
        delta={
          vulnerabilityChange === undefined
            ? undefined
            : toDelta(vulnerabilityChange, (r) => formatChange(r), false, sinceStart)
        }
        icon={<IconSecurityFinding />}
        label={translate('analytics.quality.vulnerabilities')}
        trend={data.trend.map(({ vulnerabilities }) => vulnerabilities)}
      >
        {last ? integer(last.vulnerabilities) : '-'}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.info}
        delta={
          mttrChange === undefined
            ? undefined
            : toDelta(mttrChange, (r) => formatChange(r), false, vsPrevious)
        }
        icon={<IconClock />}
        label={translate('analytics.security.mttr')}
      >
        {security?.totals.medianTimeToFixMs === undefined
          ? '-'
          : formatDays(security.totals.medianTimeToFixMs)}
      </StatTile>
      <StatTile
        color={
          compliance === undefined || compliance >= 90
            ? STATUS_COLORS.success
            : STATUS_COLORS.warning
        }
        footnote={
          security
            ? translateWithParameters(
                'analytics.brief.past_sla_footnote',
                integer(security.totals.openPastSla),
              )
            : undefined
        }
        icon={<IconCheckCircle />}
        label={translate('analytics.security.compliance')}
      >
        {compliance === undefined ? '-' : `${Math.round(compliance)}%`}
      </StatTile>
      <StatTile
        color={MAGNITUDE_COLOR}
        delta={
          backlogChange === undefined
            ? undefined
            : toDelta(backlogChange, (r) => formatChange(r), false, sinceStart)
        }
        footnote={
          last
            ? translateWithParameters('analytics.roi.backlog_footnote', integer(last.openIssues))
            : undefined
        }
        icon={<IconClock />}
        label={translate('analytics.roi.backlog')}
      >
        {last ? formatHours(last.remediationEffortMinutes) : '-'}
      </StatTile>
      <StatTile
        color={STATUS_COLORS.success}
        footnote={
          saved === undefined
            ? translate('analytics.roi.ai_unavailable')
            : translateWithParameters(
                'analytics.roi.ai_footnote',
                integer(data.ai?.fixJobs.issuesResolved ?? 0),
              )
        }
        icon={<IconSparkle />}
        label={translate('analytics.roi.ai_saved')}
      >
        {saved === undefined ? '-' : formatHours(saved)}
      </StatTile>
    </div>
  );
}

function TopRisks({
  className,
  now,
  overview,
}: Readonly<{ className: string; now: Date; overview: ReturnType<typeof useOverviewData> }>) {
  const items = React.useMemo(
    () => getFocusList(overview.projects, overview.matrices, overview.taskStatuses, now, TOP_RISKS),
    [overview.projects, overview.matrices, overview.taskStatuses, now],
  );
  return (
    <ChartCard
      className={className}
      description={translate('analytics.brief.risks.description')}
      loading={overview.loadingIssues || overview.loadingTasks}
      title={translate('analytics.brief.risks')}
    >
      {items.length === 0 ? (
        <Text isSubdued>{translate('analytics.focus.none')}</Text>
      ) : (
        <ol className="sw-flex sw-flex-col sw-gap-3">
          {items.map(({ project, reasons }, i) => (
            <li className="sw-flex sw-items-start sw-gap-3" key={project.key}>
              <Rank>{i + 1}</Rank>
              <span className="sw-flex sw-flex-col">
                <Text className="sw-typo-semibold">{project.name}</Text>
                <Text isSubdued>
                  {reasons
                    .map(({ count, reason }) =>
                      count === undefined
                        ? translate('analytics.focus.reason', reason)
                        : translateWithParameters(`analytics.focus.reason.${reason}`, count),
                    )
                    .join(' · ')}
                </Text>
              </span>
            </li>
          ))}
        </ol>
      )}
    </ChartCard>
  );
}

function TeamsAtAGlance({
  className,
  now,
  overview,
}: Readonly<{
  className: string;
  now: Date;
  overview: ReturnType<typeof useOverviewData>;
}>) {
  const teams = React.useMemo(
    () =>
      computeTeams({
        effortMinutes: overview.effortMinutes,
        matrices: overview.matrices,
        now,
        projects: overview.projects,
        taskStatuses: overview.taskStatuses,
      }).filter(({ tag }) => tag !== UNTAGGED),
    [overview, now],
  );
  if (!hasTeams(overview.projects)) {
    return (
      <ChartCard className={className} title={translate('analytics.teams.title')}>
        <Text isSubdued>{translate('analytics.teams.no_tags')}</Text>
      </ChartCard>
    );
  }
  const best = teams.slice(0, TEAMS_SHOWN);
  const worst = teams.length > TEAMS_SHOWN ? teams.slice(-TEAMS_SHOWN).reverse() : [];
  const row = (team: (typeof teams)[number]) => (
    <li className="sw-flex sw-items-center sw-justify-between sw-gap-3" key={team.tag}>
      <span className="sw-flex sw-items-center sw-gap-2 sw-min-w-0">
        <HealthGrade grade={team.health.grade} score={team.health.score} />
        <Text className="sw-truncate">{team.tag}</Text>
      </span>
      <Text isSubdued>{formatHours(team.remediationMinutes)}</Text>
    </li>
  );
  return (
    <ChartCard
      className={className}
      description={translate('analytics.brief.teams.description')}
      loading={overview.loadingIssues || overview.loadingTasks}
      title={translate('analytics.teams.title')}
    >
      <Text as="p" className="sw-typo-semibold sw-mb-2">
        {translate('analytics.brief.teams.leading')}
      </Text>
      <ul className="sw-flex sw-flex-col sw-gap-2 sw-mb-4">{best.map(row)}</ul>
      {worst.length > 0 && (
        <>
          <Text as="p" className="sw-typo-semibold sw-mb-2">
            {translate('analytics.brief.teams.lagging')}
          </Text>
          <ul className="sw-flex sw-flex-col sw-gap-2">{worst.map(row)}</ul>
        </>
      )}
    </ChartCard>
  );
}

const Score = styled.span`
  font-size: 2.5rem;
  font-weight: 700;
  line-height: 1;
`;

const InsightIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
`;

const Rank = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: var(--echoes-color-background-accent-weak-default);
  color: var(--echoes-color-text-accent);
  font-weight: 700;
`;
