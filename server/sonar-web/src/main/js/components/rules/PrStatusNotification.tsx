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

import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { FlagMessage } from '~design-system';
import { getPullRequestStatus } from '../../api/ai-codefix';
import { translate } from '../../helpers/l10n';
import { ErrorText, PrStatusNotification as PrStatusNotificationRoot } from './FixDiffStyles';

export function useCodefixPrStatusQuery(jobId: string | undefined, issueKey?: string) {
  return useQuery({
    queryKey: ['codefix-get-pr-status', jobId, issueKey],
    queryFn: () => getPullRequestStatus(jobId!, issueKey),
    enabled: Boolean(jobId),
    retry: (_, error) => {
      const res = error as unknown as Response;
      return res?.status !== 401 && res?.status !== 403;
    },
  });
}

export interface CodefixPrStatusBannerProps {
  /** Included so expandable error state resets when switching jobs */
  jobId?: string;
  prStatusType: string | undefined;
  prStatusMessage: string | undefined;
}

/**
 * PR outcome banner (success link or error with expandable log). Omits warning / empty states.
 */
export function CodefixPrStatusBanner({
  jobId,
  prStatusType,
  prStatusMessage,
}: Readonly<CodefixPrStatusBannerProps>) {
  const [prErrorDetailsOpen, setPrErrorDetailsOpen] = React.useState(false);

  React.useEffect(() => {
    setPrErrorDetailsOpen(false);
  }, [jobId, prStatusType]);

  if (prStatusType === undefined || prStatusType === 'warning') {
    return null;
  }

  return (
    <PrStatusNotificationRoot>
      <FlagMessage
        role="status"
        className="sw-w-full"
        variant={prStatusType === 'success' ? 'success' : 'error'}
      >
        {prStatusType === 'error' && prStatusMessage && (
          <div style={{ width: '100%' }}>
            <div>
              <span>{translate(`issues.code_fix.pr_status.error.text.part_one`)}</span>
              <button
                type="button"
                className="sw-ml-1 sw-bg-transparent sw-border-none sw-p-0 sw-cursor-pointer sw-underline"
                style={{ background: 'transparent', color: '#5D6CD0', marginLeft: '4px', marginRight: '4px' }}
                onClick={() => setPrErrorDetailsOpen((open) => !open)}
              >
                {translate('issues.code_fix.pr_status.error.review_the_error')}
              </button>
              <span>{translate(`issues.code_fix.pr_status.error.text.part_two`)}</span>
            </div>
            {prErrorDetailsOpen && (
              <ErrorText role="region" aria-label={translate('issues.code_fix.pr_status.error.details_label')}>
                {prStatusMessage}
              </ErrorText>
            )}
          </div>
        )}

        {prStatusType === 'success' && prStatusMessage && (
          <>
            <span>{translate(`issues.code_fix.pr_status.success.text`)}</span>{' '}
            <a
              href={prStatusMessage}
              target="_blank"
              rel="noopener noreferrer"
              className="sw-ml-1 sw-underline"
              style={{ color: '#5D6CD0' }}
            >
              {translate(`issues.code_fix.pr_status.success.click_to_view`)}
            </a>
          </>
        )}
      </FlagMessage>
    </PrStatusNotificationRoot>
  );
}
