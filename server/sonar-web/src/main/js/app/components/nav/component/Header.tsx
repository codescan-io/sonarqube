/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import Favorite from '../../../../components/controls/Favorite';
import { ProjectAlmBindingResponse } from '../../../../types/alm-settings';
import { BranchLike } from '../../../../types/branch-like';
import { Component, Organization } from '../../../../types/types';
import { CurrentUser, isLoggedIn } from '../../../../types/users';
import withCurrentUserContext from '../../current-user/withCurrentUserContext';
import BranchLikeNavigation from './branch-like/BranchLikeNavigation';
import CurrentBranchLikeMergeInformation from './branch-like/CurrentBranchLikeMergeInformation';
import { Breadcrumb } from './Breadcrumb';
import OrganizationAvatar from "../../../../apps/organizations/components/OrganizationAvatar";
import { Link } from "design-system";

export interface HeaderProps {
  branchLikes: BranchLike[];
  component: Component;
  currentBranchLike: BranchLike | undefined;
  currentUser: CurrentUser;
  projectBinding?: ProjectAlmBindingResponse;
  organization: Organization;
  comparisonBranchesEnabled: boolean;
}

export function Header(props: HeaderProps) {
  const { branchLikes, component, currentBranchLike, currentUser, projectBinding, organization } = props;

  return (
    <div className="sw-flex sw-flex-shrink sw-items-center">
      {organization &&
        <>
          <OrganizationAvatar organization={organization}/>
          <Link
            className="navbar-context-header-breadcrumb-link link-base-color link-no-underline spacer-left"
            to={`/organizations/${organization.kee}`}
          >
            {organization.name}
          </Link>
          <span className="slash-separator"/>
        </>
      }
      <Breadcrumb component={component} currentBranchLike={currentBranchLike} currentUser={currentUser} />
      {isLoggedIn(currentUser) && (
        <Favorite
          className="spacer-left"
          component={component.key}
          favorite={Boolean(component.isFavorite)}
          qualifier={component.qualifier}
        />
      )}
      {currentBranchLike && (
        <>
          <span className="slash-separator sw-mx-2" />
          <BranchLikeNavigation
            branchLikes={branchLikes}
            component={component}
            currentBranchLike={currentBranchLike}
            projectBinding={projectBinding}
            comparisonBranchesEnabled={props.comparisonBranchesEnabled}
          />
          <CurrentBranchLikeMergeInformation currentBranchLike={currentBranchLike}/>
        </>
      )}
    </div>
  );
}

export default withCurrentUserContext(React.memo(Header));
