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

import { useQuery, useQueryClient } from '@tanstack/react-query';
import classNames from 'classnames';
import * as React from 'react';
import { getCodefixStatus } from '../../../api/ai-codefix';
import { AiCodefixIconKind, AiCodefixStatusIcon } from '../../icons/AiCodefixStatusIcon';
import { Issue } from '../../../types/types';

const AI_CODE_ASSISTANT_ASSIGNEE = 'ai-code-assistant';

function hasAiCodefix(issue: Issue): boolean {
  return (
    issue.assignee === AI_CODE_ASSISTANT_ASSIGNEE ||
    issue.assigneeLogin === AI_CODE_ASSISTANT_ASSIGNEE ||
    Boolean(issue.codefixStatus)
  );
}

type AiCodefixStatusDisplay = {
  kind: AiCodefixIconKind;
  text: string;
  textClassName: string;
};

function getAiCodefixStatusDisplay(status: string): AiCodefixStatusDisplay {
  const s = (status || '').toUpperCase();
  if (s === 'PENDING' || s === 'IN_PROGRESS') {
    return {
      kind: 'in-progress',
      text: 'AI Fix in Progress',
      textClassName: 'spinner-text',
    };
  }
  if (s === 'FIX_GENERATED') {
    return {
      kind: 'generated',
      text: 'AI Fix Generated',
      textClassName: 'lightning-text',
    };
  }
  if (s === 'PULL_REQUEST_CREATED') {
    return {
      kind: 'pull-request',
      text: 'Pull Request Created',
      textClassName: 'pull-request-text',
    };
  }
  if (s === 'FAILED') {
    return {
      kind: 'failed',
      text: 'AI Fix Failed',
      textClassName: 'warning-text',
    };
  }
  if (s === 'NOT_SUPPORTED') {
    return {
      kind: 'not-supported',
      text: 'AI Fix not supported',
      textClassName: 'not-supported-text',
    };
  }
  return {
    kind: 'available',
    text: 'AI Fix Available',
    textClassName: 'magic-wand-text',
  };
}

/** Cache status for a while to avoid repeated getStatus calls; invalidate after user actions (assign, create PR). */
const CODEFIX_STATUS_STALE_MS = 60_000; // 1 minute
/** When job is in progress or fix is generated, poll so scheduler-driven transitions show without user action. */
const CODEFIX_ACTIVE_POLL_MS = 10_000; // 10 seconds
/** FIX_GENERATED may transition to PULL_REQUEST_CREATED or FAILED via the auto scheduler — poll at scheduler interval. */
const CODEFIX_FIX_GENERATED_POLL_MS = 30_000; // 30 seconds (matches scheduler interval)

export default function AiCodefixBadge({ issue }: { issue: Issue }) {
  const hasAiFix = hasAiCodefix(issue);
  const queryClient = useQueryClient();
  const { data: statusData, isLoading, isError } = useQuery({
    queryKey: ['codefix-status', issue.key],
    queryFn: () => getCodefixStatus(issue.key),
    enabled: hasAiFix && Boolean(issue.key),
    staleTime: CODEFIX_STATUS_STALE_MS,
    refetchOnWindowFocus: false,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'PENDING' || status === 'IN_PROGRESS') {
        return CODEFIX_ACTIVE_POLL_MS;
      }
      if (status === 'FIX_GENERATED') {
        return CODEFIX_FIX_GENERATED_POLL_MS;
      }
      return false;
    },
  });

  // When the issue list refreshes and codefixStatus changes, invalidate the badge cache so it re-fetches.
  React.useEffect(() => {
    if (issue.key && issue.codefixStatus) {
      queryClient.invalidateQueries({ queryKey: ['codefix-status', issue.key] });
    }
  }, [issue.key, issue.codefixStatus, queryClient]);

  const displayStatus = statusData?.status ?? issue.codefixStatus;

  if (!hasAiFix || isError || !displayStatus) {
    return (
      <div className="sparkle-label sw-mr-5 magic-wand-text">
        <AiCodefixStatusIcon kind="available" />
        <span className="status-text">AI Fix Available</span>
      </div>
    );
  }

  const { kind, text, textClassName } = getAiCodefixStatusDisplay(displayStatus);
  return (
    <div className={classNames('codefix-status-label sw-mr-5', textClassName)}>
      <AiCodefixStatusIcon kind={kind} spinning={kind === 'in-progress'} />
      <span className="status-text">{text}</span>
    </div>
  );
}