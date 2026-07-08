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
import type { IntlShape } from 'react-intl';
import type { Issue } from '../../types/types';
import { issueIsCodeException } from './helpers';

/** DB `issue_resolution_expires_at` / API `issueResolutionExpiresAt` — epoch milliseconds. */
export function resolveIssueExpiryMillis(raw: number | string | bigint | undefined): number | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  const n = typeof raw === 'bigint' ? Number(raw) : typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/**
 * Whole calendar days from "today" (UTC) to the instant's UTC calendar date.
 * Aligns with manual / auto exception expiry stored as UTC start-of-day on the server
 * (same convention as the security-hotspot exception expiry).
 */
export function calendarLocalCivilDaysUntil(expiresAtMs: number): number {
  const end = new Date(expiresAtMs);
  const start = new Date();
  const endDay = Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), end.getUTCDate());
  const startDay = Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), start.getUTCDate());
  return Math.round((endDay - startDay) / 86_400_000);
}

/** Parentheses suffix, e.g. "(Expires in 5 days)". */
export function formatExceptionExpiryRemaining(intl: IntlShape, expiresAtMs: number): string {
  const dayCount = calendarLocalCivilDaysUntil(expiresAtMs);
  if (!Number.isFinite(dayCount)) {
    return '';
  }
  if (dayCount < 0) {
    return intl.formatMessage({
      id: 'issue.exception.expiry.expired',
      defaultMessage: '(Expired)',
    });
  }
  if (dayCount === 0) {
    return intl.formatMessage({
      id: 'issue.exception.expiry.expires_today',
      defaultMessage: '(Expires today)',
    });
  }
  if (dayCount === 1) {
    return intl.formatMessage({
      id: 'issue.exception.expiry.expires_in_one',
      defaultMessage: '(Expires in 1 day)',
    });
  }
  return intl.formatMessage(
    {
      id: 'issue.exception.expiry.expires_in_days',
      defaultMessage: '(Expires in {0} days)',
    },
    { '0': String(dayCount) },
  );
}

/**
 * When the issue is an exception and an expiry instant exists, returns the countdown label; otherwise null.
 * No label for exceptions without expiry (admin off / no days / no expiry set).
 */
export function exceptionExpiryHintForIssue(
  intl: IntlShape,
  issue: Pick<Issue, 'issueResolutionExpiresAt' | 'issueStatus' | 'resolution' | 'type'>,
): string | null {
  if (!issueIsCodeException(issue)) {
    return null;
  }
  const ms = resolveIssueExpiryMillis(issue.issueResolutionExpiresAt);
  if (ms === undefined) {
    return null;
  }
  return formatExceptionExpiryRemaining(intl, ms);
}
