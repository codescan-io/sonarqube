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
import { DropdownMenu } from '@sonarsource/echoes-react';
import { NavBarTabLink } from '~design-system';
import * as React from 'react';
import { AppStateContext } from '../../../app/components/app-state/AppStateContext';
import { useCurrentUser } from '../../../app/components/current-user/CurrentUserContext';
import { translate } from '../../../helpers/l10n';
import { isDeploymentForAmazon } from '../../../helpers/urls';
import { GlobalSettingKeys } from '../../../types/settings';
import { Organization, OrganizationBillingDetails } from '../../../types/types';
import { useAiCreditsContext } from '../../../app/components/ai-credits/AiCreditsContext';

const SALESFORCE_CONNECTION_PAGE_KEY = 'developer/salesforce_connection';
const RESOLUTION_TRANSFER_PAGE_KEY = 'developer/resolution_transfer';

interface Props {
  billing?: OrganizationBillingDetails;
  location: { pathname: string };
  organization: Organization;
}

const ADMIN_PATHS = [
  'edit',
  'groups',
  'permissions',
  'permission_templates',
  'projects_management',
  'webhooks',
];

export const BILLING_PAGE_KEY = 'billing/billing';

export default function OrganizationNavigationAdministration({ billing, location, organization }: Props) {
  const { creditsData, isLoading } = useAiCreditsContext();

  const { adminPages = [] } = organization;
  const appState = React.useContext(AppStateContext);
  const { currentUser } = useCurrentUser();

  // Resolution Transfer feature flag, surfaced into appState.settings by GlobalAction. Reading it here (rather
  // than via a separate request) hides/shows the menu entry with no restart. Fail closed (hidden) if unset.
  const resolutionTransferEnabled =
    appState.settings?.[GlobalSettingKeys.CodescanResolutionTransferEnabled] === 'true';


  const canSeeAdministration =
    currentUser.isLoggedIn &&
    ((appState.canAdmin) || (!appState.canAdmin && appState.canCustomerAdmin));

  const { whiteLabel } = appState;
  const subscriptionId = billing?.subscriptionId;
  const hasPaidSubscription =
    subscriptionId !== undefined && subscriptionId !== null && subscriptionId !== '';

  // Salesforce is not available to trial organizations.
  const isTrialOrganization = organization.isTrial === true;

  const adminPathsWithExtensions = adminPages.map((e) => `extension/${e.key}`).concat(ADMIN_PATHS);
  const adminActive = adminPathsWithExtensions.some((path) =>
    location.pathname.endsWith(`organizations/${organization.kee}/${path}`),
  );

  return (
    <DropdownMenu.Root
      id="organization-nav"
      items={
        <>
          <DropdownMenu.ItemLink isMatchingFullPath to={`/organizations/${organization.kee}/edit`}>
            {translate('organization.settings')}
          </DropdownMenu.ItemLink>

          {adminPages
            .filter((e) => organization.inviteUsersEnabled || e.key !== 'developer/invite_users')
            .filter(
              (e) =>
                e.key !== 'billing/ai_billing' ||
                (!isLoading && creditsData && creditsData.allocatedCredits > 0),
            )
            .filter(
                (e) =>
                isDeploymentForAmazon(whiteLabel) || hasPaidSubscription || canSeeAdministration || e.key !== BILLING_PAGE_KEY
            )
            .filter((e) => !isTrialOrganization || e.key !== SALESFORCE_CONNECTION_PAGE_KEY)
            .filter((e) => resolutionTransferEnabled || e.key !== RESOLUTION_TRANSFER_PAGE_KEY)
            .map((extension) => (
            <DropdownMenu.ItemLink
              isMatchingFullPath
              to={`/organizations/${organization.kee}/extension/${extension.key}`}
              key={extension.key}
            >
              {extension.name}
            </DropdownMenu.ItemLink>
          ))}

          <DropdownMenu.ItemLink
            isMatchingFullPath
            to={`/organizations/${organization.kee}/groups`}
          >
            {translate('user_groups.page')}
          </DropdownMenu.ItemLink>

          <DropdownMenu.ItemLink
            isMatchingFullPath
            to={`/organizations/${organization.kee}/permissions`}
          >
            {translate('permissions.page')}
          </DropdownMenu.ItemLink>

          <DropdownMenu.ItemLink
            isMatchingFullPath
            to={`/organizations/${organization.kee}/permission_templates`}
          >
            {translate('permission_templates')}
          </DropdownMenu.ItemLink>

          <DropdownMenu.ItemLink
            isMatchingFullPath
            to={`/organizations/${organization.kee}/projects_management`}
          >
            {translate('projects_management')}
          </DropdownMenu.ItemLink>

          <DropdownMenu.ItemLink
            isMatchingFullPath
            to={`/organizations/${organization.kee}/webhooks`}
          >
            {translate('webhooks.page')}
          </DropdownMenu.ItemLink>
        </>
      }
    >
      <NavBarTabLink
        active={adminActive}
        text={translate('layout.settings')}
        withChevron
        to={{}}
        preventDefault
      />
    </DropdownMenu.Root>
  );
}
