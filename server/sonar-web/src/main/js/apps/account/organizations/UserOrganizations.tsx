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

import { useEffect, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import { ButtonPrimary } from '~design-system';
import withAppStateContext from '../../../app/components/app-state/withAppStateContext';
import withCurrentUserContext from '../../../app/components/current-user/withCurrentUserContext';
import { getAlmConnectionFromLocalStorage } from '../../../app/utils/localStorage';
import { translate } from '../../../helpers/l10n';
import { AppState } from '../../../types/appstate';
import { GlobalSettingKeys } from '../../../types/settings';
import { Organization } from '../../../types/types';
import '../projects/account.css';
import OrganizationsList from './OrganizationsList';

interface Props {
  appState: AppState;
  userOrganizations: Organization[];
}

function UserOrganizations(props: Props) {
  const {
    appState: { settings, canAdmin, canCustomerAdmin },
    userOrganizations,
  } = props;
  const anyoneCanCreate = settings[GlobalSettingKeys.OrganizationsAnyoneCanCreate] === 'true';
  const canCreateOrganizations = anyoneCanCreate || canAdmin || canCustomerAdmin;
  const [testConnection, setTestConnection] = useState<boolean>(false);

  useEffect(() => {
    const rawHash = window.location.hash.startsWith('#') ? window.location.hash.substring(1) : '';
    const params = new URLSearchParams(rawHash);
    if (params.has('error')) {
      setTestConnection(true);
      const existingConfig = getAlmConnectionFromLocalStorage();
      if (existingConfig) {
        let redirectUrl = `/organizations/${existingConfig.organizationKee}/extension/developer/alm#almId=${encodeURIComponent(existingConfig.uuid || '')}`;
        if (params.has('error')) {
          redirectUrl += `&error=${encodeURIComponent(params.get('error') || '')}`;
        }
        if (params.has('error_description')) {
          redirectUrl += `&error_description=${encodeURIComponent(params.get('error_description') || '')}`;
        }
        window.location.href = redirectUrl;
      }
    } else {
      setTestConnection(false);
    }
  }, []);

  return testConnection ? (
    <div className="global-loading">
      <i className="global-loading-spinner" />
    </div>
  ) : (
    <div className="account-body account-container organization-card-ctnr">
      <Helmet title={translate('my_account.organizations')} />

      <div className="boxed-group">
        {canCreateOrganizations && (
          <div className="clearfix">
            <div className="boxed-group-actions sw-flex sw-justify-end sw-mb-4">
              <ButtonPrimary className="button " to="/organizations/create">
                {translate('create')}
              </ButtonPrimary>
            </div>
          </div>
        )}
        <div className="boxed-group-inner">
          <OrganizationsList organizations={userOrganizations} />
        </div>
      </div>
    </div>
  );
}

export default withAppStateContext(withCurrentUserContext(UserOrganizations));
