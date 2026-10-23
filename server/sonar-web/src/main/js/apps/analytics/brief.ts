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
  IssueResolutions,
  OrganizationActivity,
  QualityTrendPoint,
  SecurityRemediation,
} from '../../api/analytics';
import { AiValue, OrganizationHealth } from '../../api/codescan-analytics';
import { translate, translateWithParameters } from '../../helpers/l10n';
import { getChange, getSuccessRate } from './activity';
import { Health, computeHealth, share } from './health';
import { formatHours } from './roi';
import { AnalyticsProject, RecencyBucket, getRecencyBucket } from './utils';

/** Everything the brief is written from. Optional parts are left out when their service does not answer. */
export interface BriefData {
  activity?: OrganizationActivity;
  ai?: AiValue;
  qualityGateChanges?: OrganizationHealth['qualityGateChanges'];
  resolutions?: IssueResolutions;
  security?: SecurityRemediation;
  trend: QualityTrendPoint[];
}

// What the organization health is made of, by weight.
export const ORGANIZATION_HEALTH_WEIGHTS = {
  qualityGates: 30,
  security: 25,
  velocity: 15,
  reliability: 15,
  freshness: 15,
};

/**
 * Organization health from five signals: quality gate pass rate, open security issues within their SLA, issues
 * resolved against issues introduced, analysis success rate, and projects analyzed in the last 30 days.
 */
export function computeOrganizationHealth(
  data: BriefData,
  projects: AnalyticsProject[],
  now: Date,
): Health {
  const last = data.trend[data.trend.length - 1];
  const security = data.security?.totals;
  const resolutions = data.resolutions?.totals;
  const fresh = projects.filter((project) => {
    const bucket = getRecencyBucket(project.analysisDate, now);
    return bucket === RecencyBucket.Week || bucket === RecencyBucket.Month;
  }).length;
  return computeHealth([
    {
      key: 'qualityGates',
      value: last ? share(last.passed, last.passed + last.failed) : undefined,
      weight: ORGANIZATION_HEALTH_WEIGHTS.qualityGates,
    },
    {
      key: 'security',
      value: security
        ? (share(security.open - security.openPastSla, security.open) ?? 100)
        : undefined,
      weight: ORGANIZATION_HEALTH_WEIGHTS.security,
    },
    {
      key: 'velocity',
      value: resolutions ? velocity(resolutions.resolved, resolutions.introduced) : undefined,
      weight: ORGANIZATION_HEALTH_WEIGHTS.velocity,
    },
    {
      key: 'reliability',
      value: data.activity ? getSuccessRate(data.activity.analyses) : undefined,
      weight: ORGANIZATION_HEALTH_WEIGHTS.reliability,
    },
    {
      key: 'freshness',
      value: share(fresh, projects.length),
      weight: ORGANIZATION_HEALTH_WEIGHTS.freshness,
    },
  ]);
}

/** Resolving at least as many issues as are introduced is full marks; resolving none of them is zero. */
function velocity(resolved: number, introduced: number) {
  if (introduced === 0) {
    return resolved > 0 ? 100 : undefined;
  }
  return Math.min(100, (resolved / introduced) * 100);
}

export type InsightTone = 'good' | 'bad' | 'neutral';

export interface Insight {
  key: string;
  text: string;
  tone: InsightTone;
  /** How much it matters, to rank the insights. */
  weight: number;
}

const MIN_CHANGE_PERCENT = 1;
const MAX_INSIGHTS = 6;
const NAMED_PROJECTS = 3;

/**
 * The few sentences that sum up the period, most significant first. Each one is only written when its data is there
 * and something actually moved.
 */
export function buildInsights(data: BriefData, limit = MAX_INSIGHTS): Insight[] {
  const insights: Insight[] = [];
  const integer = (value: number) => new Intl.NumberFormat().format(Math.round(Math.abs(value)));
  const first = data.trend[0];
  const last = data.trend[data.trend.length - 1];

  if (first && last) {
    const passThen = share(first.passed, first.passed + first.failed);
    const passNow = share(last.passed, last.passed + last.failed);
    if (passThen !== undefined && passNow !== undefined && Math.round(passNow - passThen) !== 0) {
      const points = passNow - passThen;
      insights.push({
        key: 'pass_rate',
        tone: points > 0 ? 'good' : 'bad',
        weight: Math.abs(points) * 2,
        text: translateWithParameters(
          `analytics.brief.insight.pass_rate_${points > 0 ? 'up' : 'down'}`,
          integer(points),
          Math.round(passNow),
        ),
      });
    }

    const vulnerabilities = getChange(last.vulnerabilities, first.vulnerabilities);
    if (vulnerabilities !== undefined && Math.abs(vulnerabilities) >= MIN_CHANGE_PERCENT) {
      insights.push({
        key: 'vulnerabilities',
        tone: vulnerabilities < 0 ? 'good' : 'bad',
        weight: Math.abs(vulnerabilities) * 1.5,
        text: translateWithParameters(
          `analytics.brief.insight.vulnerabilities_${vulnerabilities < 0 ? 'down' : 'up'}`,
          integer(vulnerabilities),
          integer(last.vulnerabilities),
        ),
      });
    }

    const issues = getChange(last.openIssues, first.openIssues);
    if (issues !== undefined && Math.abs(issues) >= MIN_CHANGE_PERCENT) {
      insights.push({
        key: 'open_issues',
        tone: issues < 0 ? 'good' : 'bad',
        weight: Math.abs(issues),
        text: translateWithParameters(
          `analytics.brief.insight.open_issues_${issues < 0 ? 'down' : 'up'}`,
          integer(issues),
          integer(last.openIssues - first.openIssues),
        ),
      });
    }

    const effort = last.remediationEffortMinutes - first.remediationEffortMinutes;
    const effortChange = getChange(last.remediationEffortMinutes, first.remediationEffortMinutes);
    if (effortChange !== undefined && Math.abs(effortChange) >= MIN_CHANGE_PERCENT) {
      insights.push({
        key: 'remediation',
        tone: effort < 0 ? 'good' : 'bad',
        weight: Math.abs(effortChange),
        text: translateWithParameters(
          `analytics.brief.insight.remediation_${effort < 0 ? 'down' : 'up'}`,
          formatHours(Math.abs(effort)),
        ),
      });
    }
  }

  const security = data.security;
  if (security) {
    const now = security.totals.medianTimeToFixMs;
    const then = security.previous.medianTimeToFixMs;
    const change = security.previous.complete ? getChange(now, then) : undefined;
    if (
      now !== undefined &&
      then !== undefined &&
      change !== undefined &&
      Math.abs(change) >= MIN_CHANGE_PERCENT
    ) {
      insights.push({
        key: 'mttr',
        tone: change < 0 ? 'good' : 'bad',
        weight: Math.abs(change),
        text: translateWithParameters(
          `analytics.brief.insight.mttr_${change < 0 ? 'faster' : 'slower'}`,
          formatDays(now),
          formatDays(then),
        ),
      });
    }
    if (security.totals.openPastSla > 0) {
      insights.push({
        key: 'past_sla',
        tone: 'bad',
        weight: 50 + security.totals.openPastSla,
        text: translateWithParameters(
          'analytics.brief.insight.past_sla',
          integer(security.totals.openPastSla),
          integer(security.totals.open),
        ),
      });
    } else if (security.totals.open > 0) {
      insights.push({
        key: 'within_sla',
        tone: 'good',
        weight: 10,
        text: translateWithParameters(
          'analytics.brief.insight.within_sla',
          integer(security.totals.open),
        ),
      });
    }
  }

  const resolutions = data.resolutions?.totals;
  if (resolutions && (resolutions.resolved > 0 || resolutions.introduced > 0)) {
    const shrinking = resolutions.resolved >= resolutions.introduced;
    insights.push({
      key: 'backlog',
      tone: shrinking ? 'good' : 'bad',
      weight:
        Math.abs(
          share(
            resolutions.resolved - resolutions.introduced,
            Math.max(resolutions.introduced, 1),
          ) ?? 0,
        ) / 2,
      text: translateWithParameters(
        `analytics.brief.insight.backlog_${shrinking ? 'shrinking' : 'growing'}`,
        integer(resolutions.resolved),
        integer(resolutions.introduced),
      ),
    });
  }

  if (data.activity) {
    const now = getSuccessRate(data.activity.analyses);
    const then = getSuccessRate(data.activity.previous.analyses);
    if (now !== undefined && then !== undefined && Math.abs(now - then) >= 2) {
      insights.push({
        key: 'reliability',
        tone: now > then ? 'good' : 'bad',
        weight: Math.abs(now - then) * 1.5,
        text: translateWithParameters(
          `analytics.brief.insight.reliability_${now > then ? 'up' : 'down'}`,
          Math.round(now),
          Math.round(then),
        ),
      });
    }
  }

  const ai = data.ai?.fixJobs;
  if (ai && ai.issuesResolved > 0) {
    insights.push({
      key: 'ai',
      tone: 'good',
      weight: 30,
      text: translateWithParameters(
        'analytics.brief.insight.ai',
        integer(ai.issuesResolved),
        formatHours(ai.effortSavedMinutes),
      ),
    });
  }

  const newlyFailing = (data.qualityGateChanges ?? []).filter(({ status }) => isFailing(status));
  if (newlyFailing.length > 0) {
    const names = [
      ...new Set(newlyFailing.map(({ projectKey, projectName }) => projectName ?? projectKey)),
    ];
    insights.push({
      key: 'gate_failures',
      tone: 'bad',
      weight: 40 + names.length * 5,
      text: translateWithParameters(
        'analytics.brief.insight.gate_failures',
        names.length,
        names.slice(0, NAMED_PROJECTS).join(', ') +
          (names.length > NAMED_PROJECTS
            ? ` ${translateWithParameters('analytics.brief.and_more', names.length - NAMED_PROJECTS)}`
            : ''),
      ),
    });
  }

  return insights.sort((a, b) => b.weight - a.weight).slice(0, limit);
}

/** Quality gate events are stored as text, e.g. "Failed", "Red (was Green)". */
function isFailing(status: string) {
  return /^(failed|error|red)/i.test(status.trim());
}

/** "12 days", "1 day", "<1 day" */
export function formatDays(ms: number) {
  const days = ms / (24 * 60 * 60 * 1000);
  if (days < 1) {
    return translate('analytics.brief.less_than_a_day');
  }
  const rounded = Math.round(days);
  return translateWithParameters(
    rounded === 1 ? 'analytics.brief.day' : 'analytics.brief.days',
    rounded,
  );
}
