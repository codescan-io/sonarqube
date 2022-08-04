/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import { sortBy } from 'lodash';
import * as React from 'react';
import { Link, WithRouterProps } from 'react-router';
import Dropdown from 'sonar-ui-common/components/controls/Dropdown';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getBaseUrl } from 'sonar-ui-common/helpers/urls';
import { Router, withRouter } from '../../../../components/hoc/withRouter';
import Avatar from '../../../../components/ui/Avatar';
import OrganizationListItem from '../../../../components/ui/OrganizationListItem';
import { isLoggedIn } from '../../../../helpers/users';
import { rawSizes } from '../../../theme';
import { setPendoInitialized } from "../../../../store/appState";
import getStore from "../../../utils/getStore";
import AppSwitchLink from "../../../../apps/grc/nav/AppSwitchLink";
import GrcProjects from "../../../../apps/grc/nav/GrcProjects";

interface Props {
  appState: { organizationsEnabled?: boolean, grc: boolean };
  currentUser: T.CurrentUser;
  organizations: T.Organization[];
  pendoInitialized: boolean;
  router: Pick<Router, 'push'>;
  location: { pathname: string };
}

export class GlobalNavUser extends React.PureComponent<Props & WithRouterProps> {
  handleLogin = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    const shouldReturnToCurrentPage = window.location.pathname !== `${getBaseUrl()}/about`;
    if (shouldReturnToCurrentPage) {
      const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href =
        getBaseUrl() + `/sessions/new?return_to=${returnTo}${window.location.hash}`;
    } else {
      window.location.href = `${getBaseUrl()}/sessions/new`;
    }
  };

  handleLogout = (event: React.SyntheticEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    this.props.router.push('/sessions/logout');
  };

  renderAuthenticated() {
    const { appState, organizations, pendoInitialized } = this.props;
    const currentUser = this.props.currentUser as T.LoggedInUser;
    const hasOrganizations = this.props.appState.organizationsEnabled && organizations.length > 0;

    if (isLoggedIn(currentUser) && hasOrganizations && !pendoInitialized) {
      const script = document.createElement('script');

      const orgKeys = organizations.map(o => o.key).join(',');

      script.innerHTML =
          "      pendo.initialize({\n" +
          "        visitor: {\n" +
          "          id: '" + (currentUser.email ? currentUser.email : currentUser.login) + "'\n" +
          "        },\n" +
          "        account: {\n" +
          "          id: '" + orgKeys + "'\n" +
          "        }\n" +
          "      });";

      document.body.appendChild(script);

      getStore().dispatch(setPendoInitialized());
    }

    return (
      <Dropdown
        className="js-user-authenticated"
        overlay={
          <ul className="menu">
            <li className="menu-item">
              <div className="text-ellipsis text-muted" title={currentUser.name}>
                <strong>{currentUser.name}</strong>
              </div>
              {currentUser.email != null && (
                <div
                  className="little-spacer-top text-ellipsis text-muted"
                  title={currentUser.email}>
                  {currentUser.email}
                </div>
              )}
            </li>
            <li className="divider" />
            <li>
              <Link to="/account">{translate('my_account.page')}</Link>
            </li>
            {!this.props.location.pathname.includes('/home') && <><li className="divider" /><AppSwitchLink appState={appState} currentUser={currentUser} /></>}
            { appState.grc && <GrcProjects /> }
            { !this.props.location.pathname.includes('/home') && !appState.grc && (<><li className="divider" role="separator" /><li>
              <Link to="/account/organizations">{translate('my_organizations')}</Link>
            </li></>
            )}
            { !this.props.location.pathname.includes('/home') && !appState.grc &&
              sortBy(organizations, org => org.name.toLowerCase()).map(organization => (
                <OrganizationListItem key={organization.key} organization={organization} />
              ))
            }
            <li className="divider" role="separator" />
            <li>
              <a href="#" onClick={this.handleLogout}>
                {translate('layout.logout')}
              </a>
            </li>
          </ul>
        }
        tagName="li">
        <a className="dropdown-toggle navbar-avatar" href="#" title={currentUser.name}>
          <Avatar
            hash={currentUser.avatar}
            name={currentUser.name}
            size={rawSizes.globalNavContentHeightRaw}
          />
        </a>
      </Dropdown>
    );
  }

  renderAnonymous() {
    return (
      <li>
        <a className="navbar-login" href="/sessions/new" onClick={this.handleLogin}>
          {translate('layout.login')}
        </a>
      </li>
    );
  }

  render() {
    return isLoggedIn(this.props.currentUser) ? this.renderAuthenticated() : this.renderAnonymous();
  }
}

export default withRouter(GlobalNavUser);
