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
import { DropdownMenu } from '@sonarsource/echoes-react';
import * as React from 'react';
import { NavBarTabLink } from '~design-system';
import { translate } from '../../../helpers/l10n';
import { Organization } from '../../../types/types';

interface Props {
  location: { pathname: string };
  organization: Organization;
}

// Zero out the echoes content-wrapper padding so the selected item's
// highlight fills edge-to-edge (no white gap above/around the item).
const RulesMenu = styled(DropdownMenu.Root)`
  && {
    padding: 0;
    overflow: hidden;
  }
`;

// Match the design spec for the Rules dropdown items.
const RulesItemLink = styled(DropdownMenu.ItemLink)`
  && {
    display: flex;
    width: 128px;
    padding: 8px 16px;
    align-items: center;
    background: #fff;
    color: #2a2f40;
    font-family: Inter, sans-serif;
    font-size: 14px;
    font-style: normal;
    font-weight: 400;
    line-height: 20px;
    white-space: nowrap;
  }

  &&:hover,
  &&:focus,
  &&[data-highlighted] {
    background: #e8ebff;
    color: #2a2f40;
  }
`;

export default function OrganizationNavigationRules({ location, organization }: Props) {
  const rulesPath = `/organizations/${organization.kee}/rules`;
  const isActiveRoute = location.pathname.startsWith(rulesPath);

  return (
    <RulesMenu
      id="organization-nav-rules"
      items={
        <>
          <RulesItemLink isMatchingFullPath to={rulesPath}>
            {translate('coding_rules.codescan_rules')}
          </RulesItemLink>

          <RulesItemLink to={`${rulesPath}?is_template=true`}>
            {translate('coding_rules.custom_rules')}
          </RulesItemLink>
        </>
      }
    >
      <NavBarTabLink
        active={isActiveRoute}
        preventDefault
        text={translate('coding_rules.page')}
        withChevron
        to={{}}
      />
    </RulesMenu>
  );
}
