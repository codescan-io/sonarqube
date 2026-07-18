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

import { replaceEqualDeep, useQuery, useQueryClient } from '@tanstack/react-query';
import classNames from 'classnames';
import * as React from 'react';
import { getCodefixStatus } from '../../../api/ai-codefix';
import { getValues } from '../../../api/settings';
import { AiCodefixIconKind, AiCodefixStatusIcon } from '../../icons/AiCodefixStatusIcon';
import { Issue } from '../../../types/types';

const AI_CODE_ASSISTANT_ASSIGNEE = 'ai-code-assistant';
/** Project-level setting key that decides whether PRs are created automatically. */
const CODESCAN_AUTOMATION_MODE_KEY = 'codescan.cloud.automationMode';

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
  if (s === 'PULL_REQUEST_IN_PROGRESS') {
    return {
      kind: 'pull-request',
      text: 'Pull Request In Progress',
      textClassName: 'pull-request-text',
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
  return {
    kind: 'available',
    text: 'AI Fix Available',
    textClassName: 'magic-wand-text',
  };
}

/** Cache status for a while to avoid repeated getStatus calls; invalidate after user actions (assign, create PR). */
const CODEFIX_STATUS_STALE_MS = 60_000; // 1 minute
/** Poll while a job is active so transitions show without user action. */
const CODEFIX_ACTIVE_POLL_MS = 5_000; // 5 seconds
/** FIX_GENERATED may transition to PULL_REQUEST_CREATED or FAILED via the auto scheduler — poll at scheduler interval. */
const CODEFIX_FIX_GENERATED_POLL_MS = 30_000; // 30 seconds (matches scheduler interval)
/** How long "AI Fix Generated" stays visible before flipping to "Pull Request In Progress" (automatic mode). */
const CODEFIX_FIX_GENERATED_VISIBLE_MS = 2_000; // 2 seconds

export default function AiCodefixBadge({ issue }: { issue: Issue }) {
  const hasAiFix = hasAiCodefix(issue);
  const assignedToAi =
    issue.assignee === AI_CODE_ASSISTANT_ASSIGNEE || issue.assigneeLogin === AI_CODE_ASSISTANT_ASSIGNEE;
  const queryClient = useQueryClient();

  // Automation mode tells us whether a FIX_GENERATED job will get a PR automatically. No staleTime, but the
  // per-project query key dedupes to ~1 settings call per project, only on mount (never polled).
  const { data: automationMode } = useQuery({
    queryKey: ['codefix-automation-mode', issue.project],
    queryFn: () =>
      getValues({ keys: [CODESCAN_AUTOMATION_MODE_KEY], component: issue.project }).then(
        (values) => values[0]?.value ?? '',
      ),
    enabled: hasAiFix && Boolean(issue.project),
  });
  // Mirror the backend: automatic ONLY when the value is exactly "Automatic"; absent/blank/anything else = manual.
  const isAutomaticMode = (automationMode ?? '').toLowerCase() === 'automatic';

  const { data: statusData, isLoading, isError } = useQuery({
    queryKey: ['codefix-status', issue.key],
    queryFn: () => getCodefixStatus(issue.key),
    enabled: hasAiFix && Boolean(issue.key),
    staleTime: CODEFIX_STATUS_STALE_MS,
    refetchOnWindowFocus: false,
    structuralSharing: (oldData, newData) => {
      if (newData && !newData.status && oldData?.status) {
        return oldData;
      }
      return replaceEqualDeep(oldData, newData);
    },
    refetchInterval: (query) => {
      const status = query.state.data?.status || issue.codefixStatus;
      if (
        status === 'PENDING' ||
        status === 'IN_PROGRESS' ||
        status === 'PULL_REQUEST_IN_PROGRESS'
      ) {
        return CODEFIX_ACTIVE_POLL_MS;
      }
      if (status === 'FIX_GENERATED') {
        // Automatic: PR is being created, poll fast to catch it. Manual: waits on the user, poll slower.
        return isAutomaticMode ? CODEFIX_ACTIVE_POLL_MS : CODEFIX_FIX_GENERATED_POLL_MS;
      }
      if (!status && assignedToAi) {
        return CODEFIX_ACTIVE_POLL_MS;
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

  const rawStatus = statusData?.status || issue.codefixStatus;
  const isAutomaticFixGenerated = rawStatus === 'FIX_GENERATED' && isAutomaticMode;

  // Automatic FIX_GENERATED means the PR is being created: show it as "Pull Request In Progress", but only
  // after a short beat so "AI Fix Generated" stays visible instead of being skipped.
  const [prInProgressVisible, setPrInProgressVisible] = React.useState(false);
  React.useEffect(() => {
    if (!isAutomaticFixGenerated) {
      setPrInProgressVisible(false);
      return undefined;
    }
    const timer = setTimeout(() => setPrInProgressVisible(true), CODEFIX_FIX_GENERATED_VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [isAutomaticFixGenerated]);

  const displayStatus =
    isAutomaticFixGenerated && prInProgressVisible ? 'PULL_REQUEST_IN_PROGRESS' : rawStatus;

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
