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
import OrganizationNavigationHeader from './OrganizationNavigationHeader';
import OrganizationNavigationMenu from './OrganizationNavigationMenu';
import OrganizationNavigationMeta from './OrganizationNavigationMeta';
import { rawSizes } from '../../../app/theme';
import ContextNavBar from "../../../components/ui/ContextNavBar";
import { Organization } from "../../../types/types";
import { getRawNotificationsForOrganization } from '../../../../js/api/codescan';
import { throwGlobalError } from '../../../../js/helpers/error';

interface Props {
  location: { pathname: string };
  organization: Organization;
  userOrganizations: Organization[];
}

interface State{
  error: string,
}

export class OrganizationNavigation extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = {
    error: ''
  };

 componentDidMount() {
    this.mounted = true;
    this.fetchNotifications();
  }

  componentDidUpdate() {
    this.fetchNotifications();
  }

  async fetchNotifications() {
    const { organization } = { ...this.props }
    await getRawNotificationsForOrganization(organization.kee).then((data:any) => {
      const notification = data?.[0];
      if(notification?.type === "ERROR") {
        this.setState({ error: notification.message });
      } else {
        this.setState({ error: '' });  
      }
    }).catch(throwGlobalError)
  }


  componentWillUnmount() {
    this.mounted = false;
  }

  render(){
    const { contextNavHeightRaw, contextNavHeightWithError } = rawSizes;
    const {location,organization,userOrganizations} = {...this.props}  
    const {error} = {...this.state};
    
    const height = (error?.length>0) ? contextNavHeightWithError : contextNavHeightRaw;
    return (
      <>
      <ContextNavBar height={height} id="context-navigation">
        <div className="navbar-context-justified">
          <OrganizationNavigationHeader
              organization={organization}
              organizations={userOrganizations}
          />
          <OrganizationNavigationMeta
              organization={organization}
          />
        </div>
        <OrganizationNavigationMenu
            location={location}
            organization={organization}
        />
        {
        error?.length>0 ? (
        <div className='org-alert'>
          <div className='org-alert-inner'>
            <div className='icon'>
              x
            </div>
            <div className='msg'>
              {error}
            </div>
          </div>
        </div>
        ) : (<></>)
      }
      </ContextNavBar>
    </>
  );
  }

}

export default OrganizationNavigation;
