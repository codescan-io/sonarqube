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

import { format, subDays } from 'date-fns';
import { AnalysisStats, AnalyticsInterval, AnalyticsTaskSort } from '../../api/analytics';

export const ACTIVITY_PERIODS = [7, 30, 90] as const;
export type ActivityPeriod = (typeof ACTIVITY_PERIODS)[number];
export const DEFAULT_ACTIVITY_PERIOD: ActivityPeriod = 30;

/** Past this many days the timeline switches from daily to weekly buckets. */
const MAX_DAILY_BUCKETS = 31;

/** A queued task waiting longer than this is called out on the queue tile. */
export const SLOW_QUEUE_MS = 10 * 60 * 1000;

/** The last {@link days} days, today included, read in the browser's time zone. */
export function getActivityRequest(days: number, now: Date) {
  const interval: AnalyticsInterval = days > MAX_DAILY_BUCKETS ? 'week' : 'day';
  return {
    from: format(subDays(now, days - 1), 'yyyy-MM-dd'),
    to: format(now, 'yyyy-MM-dd'),
    interval,
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  };
}

/** Successful share of the analyses that finished, ignoring canceled ones. */
export function getSuccessRate(stats: AnalysisStats) {
  const finished = stats.success + stats.failed;
  return finished > 0 ? (stats.success / finished) * 100 : undefined;
}

/** Relative change in percent, or undefined when there is nothing to compare with. */
export function getChange(current: number | undefined, previous: number | undefined) {
  if (current === undefined || previous === undefined || previous === 0) {
    return undefined;
  }
  return ((current - previous) / previous) * 100;
}

/** "+12%", "-3%", or "0%" */
export function formatChange(change: number, unit = '%') {
  const rounded = Math.round(change);
  const sign = rounded > 0 ? '+' : '';
  return `${sign}${rounded}${unit}`;
}

/** Compact duration: "850ms", "48s", "3m 02s", "1h 04m". */
export function formatDuration(ms: number) {
  if (ms < 1000) {
    return `${Math.round(ms)}ms`;
  }
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  const totalMinutes = Math.floor(totalSeconds / 60);
  if (totalMinutes < 60) {
    return `${totalMinutes}m ${pad(totalSeconds % 60)}s`;
  }
  return `${Math.floor(totalMinutes / 60)}h ${pad(totalMinutes % 60)}m`;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

/** The smallest 1, 2, 2.5 or 5 times a power of ten that is at least {@link value}. */
export function niceCeiling(value: number) {
  if (value <= 0) {
    return 1;
  }
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * magnitude >= value) ?? 10;
  return step * magnitude;
}

/** What a clicked headline tile lists underneath it. */
export enum ActivityDrilldown {
  Analyses = 'analyses',
  Projects = 'projects',
  Failures = 'failures',
  Slowest = 'slowest',
  LongestWaits = 'longest_waits',
  Queued = 'queued',
}

export const DRILLDOWN_TASK_QUERIES: Partial<
  Record<ActivityDrilldown, { s: AnalyticsTaskSort; statuses?: string }>
> = {
  [ActivityDrilldown.Analyses]: { s: 'submittedAt' },
  [ActivityDrilldown.Failures]: { s: 'submittedAt', statuses: 'FAILED' },
  [ActivityDrilldown.Slowest]: { s: 'executionTime', statuses: 'SUCCESS' },
  [ActivityDrilldown.LongestWaits]: { s: 'queueTime' },
};
