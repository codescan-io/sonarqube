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
import * as React from 'react';
import NotFound from '../NotFound';
import Extension from './Extension';
import { withOrganizationContext } from "../../../apps/organizations/OrganizationContext";
import { withRouter } from '~sonar-aligned/components/hoc/withRouter';
import { Organization } from "../../../types/types";
import { getOrganization, getOrganizationBillingDetails } from "../../../api/organizations";
import { Navigate, useParams } from "react-router-dom";
import { Spinner } from '@sonarsource/echoes-react';
import { AppStateContext } from "../app-state/AppStateContext";
import { useCurrentUser } from "../current-user/CurrentUserContext";
import { useCurrentOrganizationKey } from "../current-organization/CurrentOrganizationKeyContext";
import { BILLING_PAGE_KEY } from "../../../apps/organizations/navigation/OrganizationNavigationAdministration";
import { isDeploymentForAmazon } from '../../../helpers/urls';

interface OrganizationPageExtensionProps {
  organization: Organization;
}

function OrganizationPageExtension(props: OrganizationPageExtensionProps) {

  const { extensionKey, pluginKey } = useParams();
  const { organization } = props;
  const appState = React.useContext(AppStateContext);
  const { currentUser } = useCurrentUser();
  const { billing, setBilling } = useCurrentOrganizationKey();
  const { whiteLabel } = appState;

  const requestedKey = `${pluginKey}/${extensionKey}`;
  const isBillingPage = !isDeploymentForAmazon(whiteLabel) && requestedKey === BILLING_PAGE_KEY;
  const canSeeAdministration =
    currentUser.isLoggedIn &&
    ((appState.canAdmin) || (!appState.canAdmin && appState.canCustomerAdmin));

  // Whether the context already holds billing details for *this* org. Billing is fetched
  // asynchronously and independently of the organization (both here and by OrganizationApp), so
  // on a hard reload it can still be undefined when this component first renders.
  const orgKey = organization?.kee;
  const billingLoadedForOrg = billing?.organizationKey === orgKey;

  // The billing guard below needs the subscription status. If it isn't loaded yet (e.g. hard
  // reload landing straight on the billing page), fetch it here and hold off on the redirect
  // decision until it resolves, so a paid-but-non-admin user isn't bounced to /projects during
  // the race. Skipped when the user is an admin (access granted regardless) or not the billing page.
  const needsBillingCheck = isBillingPage && !canSeeAdministration && Boolean(orgKey);
  // Tracks the org for which our own fetch has completed. This must be independent of
  // billingLoadedForOrg: a non-billable org rejects the fetch and never populates billing, and
  // OrganizationApp's parallel fetch may resolve first and cancel ours — either way we still need
  // to record "billing has been checked" so the guard can decide instead of spinning forever.
  const [checkedOrgKey, setCheckedOrgKey] = React.useState<string | undefined>();

  React.useEffect(() => {
    if (!needsBillingCheck || billingLoadedForOrg || !orgKey) {
      return;
    }
    let cancelled = false;
    getOrganizationBillingDetails(orgKey)
      .then((details) => {
        if (!cancelled) {
          setBilling({ organizationKey: orgKey, details });
        }
      })
      .catch(() => {
        /* non-billable org or transient error → treated as no subscription */
      })
      .finally(() => {
        if (!cancelled) {
          setCheckedOrgKey(orgKey);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [needsBillingCheck, billingLoadedForOrg, orgKey, setBilling]);

  // Resolved once billing is known through any path: loaded into context (by us or
  // OrganizationApp) or our fetch has settled (including the non-billable rejection).
  const billingResolved = billingLoadedForOrg || checkedOrgKey === orgKey;

  const refreshOrganization = () => {
    return props.organization && getOrganization(organization.kee);
  };

  if (!organization) {
    return null;
  }

  const { actions = {} } = organization;
  let { pages = [] } = organization;
  if (actions.admin && organization.adminPages) {
    pages = pages.concat(organization.adminPages);
  }

  if (isBillingPage && !canSeeAdministration) {
    const subscriptionId =
      billing?.organizationKey === organization.kee ? billing.details.subscriptionId : undefined;
    const hasPaidSubscription =
      subscriptionId !== undefined && subscriptionId !== null && subscriptionId !== '';

    if (!hasPaidSubscription) {
      // Don't redirect until billing has actually been resolved; "not loaded yet" must not be
      // mistaken for "no subscription".
      if (!billingResolved) {
        return <Spinner />;
      }
      return <Navigate to={`/organizations/${organization.kee}/projects`} replace={true} />;
    }
  }

  const extension = pages.find(p => p.key === requestedKey);
  return extension ? (
    <Extension
      extension={extension}
      options={{ organization, refreshOrganization }}
    />
  ) : (
    <NotFound withContainer={false} />
  );
}

export default withRouter(withOrganizationContext(OrganizationPageExtension));
