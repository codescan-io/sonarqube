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
import {
  ItemButton,
  ItemDivider,
  ItemHeader,
  ItemHeaderHighlight,
  ItemNavLink,
} from 'design-system';
import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { translate } from '../../../../helpers/l10n';
import { LoggedInUser } from '../../../../types/users';
import { sortBy } from "lodash";
import OrganizationListItem from "../../../../apps/organizations/components/OrganizationListItem";
import { Organization } from "../../../../types/types";

interface UserAccountMenuProps {
  currentUser: LoggedInUser;
  userOrganizations: Organization[];
}

export function GlobalNavUserMenu({ currentUser, userOrganizations }: UserAccountMenuProps) {
  const navigateTo = useNavigate();
  const firstItemRef = React.useRef<HTMLAnchorElement>(null);

  const handleLogout = React.useCallback(() => {
    navigateTo('/sessions/logout');
  }, [navigateTo]);

  React.useEffect(() => {
    firstItemRef.current?.focus();
  }, [firstItemRef]);

  return (
    <>
      <ItemHeader>
        <ItemHeaderHighlight title={currentUser.name}>{currentUser.name}</ItemHeaderHighlight>
        {currentUser.email != null && (
          <div className="sw-mt-1" title={currentUser.email}>
            {currentUser.email}
          </div>
        )}
      </ItemHeader>
      <ItemDivider />
      <ItemNavLink end to="/account" innerRef={firstItemRef}>
        {translate('my_account.page')}
      </ItemNavLink>
      <ItemDivider/>
      <ItemNavLink to="/account/organizations">
        {translate('my_organizations')}
      </ItemNavLink>
      {sortBy(userOrganizations, org => org.name.toLowerCase()).map(organization => (
        <OrganizationListItem key={organization.kee} organization={organization} />
      ))}
      <ItemDivider/>
      <ItemButton onClick={handleLogout}>{translate('layout.logout')}</ItemButton>
    </>
  );
}
