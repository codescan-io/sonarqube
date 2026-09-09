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

import EmbedDocsPopupHelper from '../../../../components/embed-docs-modal/EmbedDocsPopupHelper';
import { CurrentUser, isLoggedIn } from '../../../../types/users';
import withCurrentUserContext from '../../current-user/withCurrentUserContext';
import GlobalSearch from '../../global-search/GlobalSearch';
import GlobalNavMenu from './GlobalNavMenu';
import { GlobalNavUser } from './GlobalNavUser';
import MainSonarQubeBar from './MainSonarQubeBar';
import { Organization } from "../../../../types/types";
import GlobalNavPlus from "./GlobalNavPlus";
import TrialStatusPill from './TrialStatusPill';
import { isNonStandardUser } from '../../../utils/userAccess';
import { AiCreditsIndicator } from './AiCreditsIndicator';
import { useAiCreditsContext } from '../../ai-credits/AiCreditsContext';
import { useContext } from 'react';
import { AppStateContext } from '../../app-state/AppStateContext';
import { GlobalSettingKeys } from '../../../../types/settings';

export interface GlobalNavProps {
  currentUser: CurrentUser;
  userOrganizations: Organization[];
  location: { pathname: string };
}

export function GlobalNav(props: GlobalNavProps) {
  const { currentUser, userOrganizations, location } = props;
  const { creditsData, isLoading } = useAiCreditsContext();
  const showTrialPill = isLoggedIn(currentUser) && !isNonStandardUser(currentUser);
  const { canAdmin, canCustomerAdmin, settings } = useContext(AppStateContext);
  const isAdmin = Boolean(canAdmin || canCustomerAdmin);
  const anyoneCanCreate = settings[GlobalSettingKeys.OrganizationsAnyoneCanCreate] === 'true';
  const canCreateProject = isAdmin || userOrganizations.length > 0;
  const canCreateOrganization = isAdmin || anyoneCanCreate;

  return (
    <MainSonarQubeBar>
      <div className="sw-flex" id="global-navigation">
        <div className="it__global-navbar-menu sw-flex sw-justify-start sw-items-center sw-flex-1">
          <GlobalNavMenu currentUser={currentUser} location={location} />
          <div className="sw-px-8 sw-flex-1">
          {!isNonStandardUser(currentUser)&& <GlobalSearch />}
          </div>
        </div>

        <div className="sw-flex sw-items-center sw-ml-2">
          {showTrialPill && <TrialStatusPill />}
          <div className="sw-flex sw-items-center sw-mr-1">
            {!isLoading && creditsData && creditsData.allocatedCredits > 0 && <AiCreditsIndicator data={creditsData} />}
          </div>
          <EmbedDocsPopupHelper />
          {isLoggedIn(currentUser) && !(isNonStandardUser(currentUser) && (currentUser?.platformOrgs?.length ?? 0) > 0) && (canCreateProject || canCreateOrganization) && (
            <div style={{ height: 36, width: 36 }} className="sw-flex sw-items-center sw-justify-center sw-mr-2">
              <GlobalNavPlus />
            </div>
          )}
          <div className="sw-ml-4">
            <GlobalNavUser currentUser={currentUser} userOrganizations={userOrganizations} />
          </div>
        </div>
      </div>
    </MainSonarQubeBar>
  );
}

export default withCurrentUserContext(GlobalNav);
