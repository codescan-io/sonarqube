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
import { useCurrentOrganizationKey } from '../../current-organization/CurrentOrganizationKeyContext';
import { translate, translateWithParameters } from '../../../../helpers/l10n';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

// Trial has more than a week left: informational only.
const NEUTRAL_THRESHOLD_DAYS = 7;

type TrialPillVariant = 'neutral' | 'warning' | 'error';

interface TrialPillState {
  icon: 'clock' | 'warning';
  label: string;
  variant: TrialPillVariant;
}

/**
 * Maps the remaining trial time to the pill's variant, icon and label. Returns
 * `undefined` when no trial pill should be shown (not a trial, no end date, or the
 * trial has already ended).
 */
export function getTrialPillState(
  trialEnds: number | undefined,
  subscriptionId: string | null | undefined,
  now: number,
): TrialPillState | undefined {
  // A subscription id means the org is on a paid subscription, not a trial. The backend
  // omits it (via @JsonInclude(NON_NULL)) when the DB column is null, which is exactly
  // how the backend defines a trial (BillingService#isTrialBilling).
  const hasPaidSubscription =
    subscriptionId !== undefined && subscriptionId !== null && subscriptionId !== '';

  if (hasPaidSubscription) {
    return undefined;
  }

  if (trialEnds === undefined || trialEnds <= 0) {
    return undefined;
  }

  const msLeft = trialEnds - now;

  // Trial already ended: the "trial ended" state is handled elsewhere.
  if (msLeft <= 0) {
    return undefined;
  }

  const daysLeft = Math.ceil(msLeft / MS_PER_DAY);

  if (daysLeft <= 1) {
    return { variant: 'error', icon: 'warning', label: translate('billing.trial.ends_today') };
  }

  if (daysLeft <= NEUTRAL_THRESHOLD_DAYS) {
    return {
      variant: 'warning',
      icon: 'clock',
      label: translateWithParameters('billing.trial.days_left', daysLeft),
    };
  }

  return {
    variant: 'neutral',
    icon: 'clock',
    label: translateWithParameters('billing.trial.days', daysLeft),
  };
}

export default function TrialStatusPill() {
  const { organizationKey, billing } = useCurrentOrganizationKey();

  // Only use billing that belongs to the org currently in view, so we never render
  // one org's trial for another while navigation is in flight.
  if (!organizationKey || billing?.organizationKey !== organizationKey) {
    return null;
  }

  const state = getTrialPillState(billing.details.trialEnds, billing.details.subscriptionId, Date.now());

  if (!state) {
    return null;
  }

  return (
    <StyledTrialPill className="sw-mr-2" variant={state.variant}>
      {state.icon === 'warning' ? <WarningIcon /> : <ClockIcon />}
      <span>{state.label}</span>
    </StyledTrialPill>
  );
}

function ClockIcon() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height="16"
      viewBox="0 0 16 16"
      width="16"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M8 1.5C6.71442 1.5 5.45772 1.88122 4.3888 2.59545C3.31988 3.30968 2.48676 4.32484 1.99479 5.51256C1.50282 6.70028 1.37409 8.00721 1.6249 9.26809C1.8757 10.529 2.49477 11.6872 3.40381 12.5962C4.31285 13.5052 5.47104 14.1243 6.73192 14.3751C7.99279 14.6259 9.29973 14.4972 10.4874 14.0052C11.6752 13.5132 12.6903 12.6801 13.4046 11.6112C14.1188 10.5423 14.5 9.28558 14.5 8C14.4982 6.27665 13.8128 4.62441 12.5942 3.40582C11.3756 2.18722 9.72335 1.50182 8 1.5ZM8 13.5C6.91221 13.5 5.84884 13.1774 4.94437 12.5731C4.0399 11.9687 3.33495 11.1098 2.91867 10.1048C2.50238 9.09977 2.39347 7.9939 2.60568 6.927C2.8179 5.86011 3.34173 4.8801 4.11092 4.11091C4.8801 3.34172 5.86011 2.8179 6.92701 2.60568C7.9939 2.39346 9.09977 2.50238 10.1048 2.91866C11.1098 3.33494 11.9687 4.03989 12.5731 4.94436C13.1774 5.84883 13.5 6.9122 13.5 8C13.4983 9.45818 12.9184 10.8562 11.8873 11.8873C10.8562 12.9184 9.45819 13.4983 8 13.5ZM12 8C12 8.13261 11.9473 8.25979 11.8536 8.35355C11.7598 8.44732 11.6326 8.5 11.5 8.5H8C7.86739 8.5 7.74022 8.44732 7.64645 8.35355C7.55268 8.25979 7.5 8.13261 7.5 8V4.5C7.5 4.36739 7.55268 4.24021 7.64645 4.14645C7.74022 4.05268 7.86739 4 8 4C8.13261 4 8.25979 4.05268 8.35356 4.14645C8.44732 4.24021 8.5 4.36739 8.5 4.5V7.5H11.5C11.6326 7.5 11.7598 7.55268 11.8536 7.64645C11.9473 7.74021 12 7.86739 12 8Z"
        fill="currentColor"
      />
    </svg>
  );
}

function WarningIcon() {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height="16"
      viewBox="0 0 16 16"
      width="16"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M14.8 11.7556L9.33437 2.26373C9.19779 2.03118 9.00281 1.83837 8.76875 1.70439C8.53469 1.57042 8.26969 1.49994 8 1.49994C7.73031 1.49994 7.4653 1.57042 7.23124 1.70439C6.99719 1.83837 6.8022 2.03118 6.66562 2.26373L1.2 11.7556C1.06858 11.9805 0.999329 12.2364 0.999329 12.4969C0.999329 12.7574 1.06858 13.0132 1.2 13.2381C1.33483 13.4721 1.52948 13.6659 1.76397 13.7998C1.99847 13.9337 2.26436 14.0028 2.53437 14H13.4656C13.7354 14.0026 14.0011 13.9334 14.2353 13.7995C14.4696 13.6656 14.664 13.4719 14.7987 13.2381C14.9304 13.0133 14.9998 12.7575 15 12.497C15.0003 12.2365 14.9312 11.9806 14.8 11.7556ZM13.9331 12.7375C13.8855 12.8188 13.8171 12.8859 13.7349 12.932C13.6528 12.9782 13.5598 13.0016 13.4656 13H2.53437C2.44017 13.0016 2.34723 12.9782 2.26508 12.932C2.18293 12.8859 2.11452 12.8188 2.06687 12.7375C2.02371 12.6644 2.00095 12.5811 2.00095 12.4962C2.00095 12.4114 2.02371 12.3281 2.06687 12.255L7.5325 2.76311C7.58111 2.68221 7.64983 2.61528 7.73197 2.56881C7.81411 2.52234 7.90688 2.49791 8.00125 2.49791C8.09562 2.49791 8.18839 2.52234 8.27053 2.56881C8.35267 2.61528 8.42139 2.68221 8.47 2.76311L13.9356 12.255C13.9784 12.3283 14.0007 12.4117 14.0003 12.4966C13.9999 12.5814 13.9767 12.6646 13.9331 12.7375ZM7.5 8.99998V6.49998C7.5 6.36737 7.55268 6.24019 7.64645 6.14643C7.74021 6.05266 7.86739 5.99998 8 5.99998C8.13261 5.99998 8.25978 6.05266 8.35355 6.14643C8.44732 6.24019 8.5 6.36737 8.5 6.49998V8.99998C8.5 9.13259 8.44732 9.25977 8.35355 9.35353C8.25978 9.4473 8.13261 9.49998 8 9.49998C7.86739 9.49998 7.74021 9.4473 7.64645 9.35353C7.55268 9.25977 7.5 9.13259 7.5 8.99998ZM8.75 11.25C8.75 11.3983 8.70601 11.5433 8.6236 11.6667C8.54119 11.79 8.42406 11.8861 8.28701 11.9429C8.14997 11.9997 7.99917 12.0145 7.85368 11.9856C7.7082 11.9566 7.57456 11.8852 7.46967 11.7803C7.36478 11.6754 7.29335 11.5418 7.26441 11.3963C7.23547 11.2508 7.25032 11.1 7.30709 10.963C7.36385 10.8259 7.45998 10.7088 7.58332 10.6264C7.70666 10.544 7.85166 10.5 8 10.5C8.19891 10.5 8.38968 10.579 8.53033 10.7197C8.67098 10.8603 8.75 11.0511 8.75 11.25Z"
        fill="currentColor"
      />
    </svg>
  );
}

const VARIANT_STYLES: Record<
  TrialPillVariant,
  { background: string; border: string; color: string }
> = {
  warning: {
    border: 'var(--color-status-warning-border, #FADC79)',
    background: 'var(--color-status-warning-bg, #FCF5E4)',
    color: 'var(--color-status-warning-text, #8C5E1E)',
  },
  error: {
    border: '#FECACA',
    background: '#FEF2F2',
    color: 'var(--color-status-error, #D92D20)',
  },
  neutral: {
    border: 'var(--color-text-tertiary, #A6ADC2)',
    background: '#F3F4F6',
    color: 'var(--color-text-chart-label, #5F666C)',
  },
};

const StyledTrialPill = styled.div<{ variant: TrialPillVariant }>`
  display: flex;
  height: 30px;
  padding: 4px 10px;
  align-items: center;
  gap: 4px;
  border-radius: var(--radius-pill, 10000px);
  border: 1px solid ${({ variant }) => VARIANT_STYLES[variant].border};
  background: ${({ variant }) => VARIANT_STYLES[variant].background};
  color: ${({ variant }) => VARIANT_STYLES[variant].color};
  font-family: Inter, sans-serif;
  font-size: 12px;
  font-style: normal;
  font-weight: 600;
  line-height: normal;
  white-space: nowrap;

  svg {
    flex-shrink: 0;
  }
`;
