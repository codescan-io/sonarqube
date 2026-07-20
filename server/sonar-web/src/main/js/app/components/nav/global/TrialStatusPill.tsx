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

import classNames from 'classnames';
import { useCurrentOrganizationKey } from '../../current-organization/CurrentOrganizationKeyContext';
import { translate, translateWithParameters } from '../../../../helpers/l10n';
import './TrialStatusPill.css';

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
    <div className={classNames('trial-status-pill', `trial-status-pill--${state.variant}`, 'sw-mr-2')}>
      <span
        aria-hidden
        className={classNames('trial-status-pill__icon', `trial-status-pill__icon--${state.icon}`)}
      />
      <span>{state.label}</span>
    </div>
  );
}
