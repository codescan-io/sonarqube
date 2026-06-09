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
import { isNonStandardUser } from '../../../utils/userAccess';
import { AiCreditsIndicator } from './AiCreditsIndicator';
import { useLocation, useSearchParams } from 'react-router-dom';
import { getCredits } from '../../../../api/ai-codefix';
import { useCurrentOrg } from '../organization/CurrentOrgContext';

export interface GlobalNavProps {
  currentUser: CurrentUser;
  userOrganizations: Organization[];
  location: { pathname: string };
}

export function getOrgKee() {
  const { pathname } = useLocation();
  const [ searchParams ] = useSearchParams();
  const { orgKee } = useCurrentOrg();

  // /organizations/beforetwo/extension/billing
  const orgMatch = pathname.match(/\/organizations\/([^/]+)/);
  const orgKey = orgMatch?.[1] ?? null;

  // ?id=123
  const id = searchParams.get("id");
  if(orgKey)
    return { orgKee: orgKey };

  if(!id) 
    return { orgKee: null };

  if(orgKee)
    return { orgKee: orgKee };

  return { orgKee: null };
}

export function GlobalNav(props: GlobalNavProps) {
  const { currentUser, userOrganizations, location } = props;
  const { data } = getCredits();

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
          <div className="sw-flex sw-items-center sw-mr-1">
            {data && <AiCreditsIndicator data={data} />}
          </div>
          <EmbedDocsPopupHelper />
          {isLoggedIn(currentUser) && (!isNonStandardUser(currentUser))  && (
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
