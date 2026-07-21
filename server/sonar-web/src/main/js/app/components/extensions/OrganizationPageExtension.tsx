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
import { getOrganization } from "../../../api/organizations";
import { Navigate, useParams } from "react-router-dom";
import { AppStateContext } from "../app-state/AppStateContext";
import { useCurrentUser } from "../current-user/CurrentUserContext";
import { useCurrentOrganizationKey } from "../current-organization/CurrentOrganizationKeyContext";
import { BILLING_PAGE_KEY } from "../../../apps/organizations/navigation/OrganizationNavigationAdministration";

interface OrganizationPageExtensionProps {
  organization: Organization;
}

function OrganizationPageExtension(props: OrganizationPageExtensionProps) {

  const { extensionKey, pluginKey } = useParams();
  const { organization } = props;
  const appState = React.useContext(AppStateContext);
  const { currentUser } = useCurrentUser();
  const { billing } = useCurrentOrganizationKey();

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

  const requestedKey = `${pluginKey}/${extensionKey}`;

  if (requestedKey === BILLING_PAGE_KEY) {
    const canSeeAdministration =
      currentUser.isLoggedIn &&
      ((appState.canAdmin) || (!appState.canAdmin && appState.canCustomerAdmin));

    const subscriptionId =
      billing?.organizationKey === organization.kee ? billing.details.subscriptionId : undefined;
    const hasPaidSubscription =
      subscriptionId !== undefined && subscriptionId !== null && subscriptionId !== '';

    if (!(canSeeAdministration || hasPaidSubscription)) {
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
