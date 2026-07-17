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

import { noop } from 'lodash';
import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { getOrganizationBillingDetails } from '../../../api/organizations';
import { OrganizationBillingDetails } from '../../../types/types';

/** Billing details tagged with the org they belong to, so consumers never show one org's data for another. */
export interface CurrentOrganizationBilling {
  details: OrganizationBillingDetails;
  organizationKey: string;
}

interface CurrentOrganizationKeyContextShape {
  billing?: CurrentOrganizationBilling;
  organizationKey?: string;
  refreshBilling: () => void;
  setBilling: (billing?: CurrentOrganizationBilling) => void;
  setOrganizationKey: (organizationKey?: string) => void;
}


export const CurrentOrganizationKeyContext =
  React.createContext<CurrentOrganizationKeyContextShape>({
    billing: undefined,
    organizationKey: undefined,
    refreshBilling: noop,
    setBilling: noop,
    setOrganizationKey: noop,
  });

export function useCurrentOrganizationKey(): CurrentOrganizationKeyContextShape {
  return React.useContext(CurrentOrganizationKeyContext);
}

export const BILLING_UPDATED_EVENT = 'codescan:billing-updated';

const ORGANIZATION_PATH_RE = /^\/organizations\/([^/]+)/;

export default function CurrentOrganizationKeyProvider({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const { pathname, search } = useLocation();
  const [organizationKey, setOrganizationKey] = React.useState<string | undefined>();
  const [billing, setBilling] = React.useState<CurrentOrganizationBilling | undefined>();

  React.useEffect(() => {
    const match = ORGANIZATION_PATH_RE.exec(pathname);

    if (match && match[1] !== 'create') {
      setOrganizationKey(match[1]);
      return;
    }

    // On component pages the organization is not in the URL; ComponentContainer owns
    // the value in that case, so we leave it untouched. Anywhere else there is no
    // organization context, so clear any previously detected key.
    if (!new URLSearchParams(search).has('id')) {
      setOrganizationKey(undefined);
    }
  }, [pathname, search]);

  const refreshBilling = React.useCallback(() => {
    if (!organizationKey) {
      return;
    }

    const key = organizationKey;
    getOrganizationBillingDetails(key)
      .then((details) => setBilling({ organizationKey: key, details }))
      .catch(() => {
        /* non-billable org or transient error → leave the banner as-is */
      });
  }, [organizationKey]);

  React.useEffect(() => {
    window.addEventListener(BILLING_UPDATED_EVENT, refreshBilling);
    return () => window.removeEventListener(BILLING_UPDATED_EVENT, refreshBilling);
  }, [refreshBilling]);

  const value = React.useMemo(
    () => ({ billing, organizationKey, refreshBilling, setBilling, setOrganizationKey }),
    [billing, organizationKey, refreshBilling],
  );

  return (
    <CurrentOrganizationKeyContext.Provider value={value}>
      {children}
    </CurrentOrganizationKeyContext.Provider>
  );
}
