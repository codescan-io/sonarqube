import * as classNames from 'classnames';
import React from 'react';
import {Link} from 'react-router';
import Dropdown from 'sonar-ui-common/components/controls/Dropdown';
import DropdownIcon from 'sonar-ui-common/components/icons/DropdownIcon';
import {translate} from 'sonar-ui-common/helpers/l10n';
import {Button} from "sonar-ui-common/components/controls/buttons";
import { getGrcActivityUrl, getGrcDashboardUrl, getGrcInventoryUrl, getGrcOverviewUrl, getGrcProfilesUrl, getGrcProjectSettingsUrl, getGrcRulesUrl, getGrcViolationsUrl } from '../../../helpers/urls';

interface Props {
  appState: Pick<T.AppState, 'canAdmin' | 'globalPages' | 'organizationsEnabled' | 'qualifiers'>;
  currentUser: T.CurrentUser;
  location: { pathname: string , query:any};
}

export default function GrcGlobalNavMenu(props: Props) {

  const {location} = props;
  const projectKey = location.query.id;

  function renderDashboard() {
    const active = location.pathname === '/grc';

    return (
        <li>
          <Link className={classNames({active})} to={getGrcDashboardUrl(projectKey)}>
            Dashboard
          </Link>
        </li>
    );
  }

  function renderViolations() {
    const active = location.pathname === '/grc/violations';

    return (
        <li>
          <Link className={classNames({active})} to={getGrcViolationsUrl(projectKey)}>
            Violations
          </Link>
        </li>
    );
  }

  function renderRerunAnalysis() {
    return (
        <li>
          <Button>Rerun Analysis</Button>
        </li>
    );
  }

  function renderMoreMenu() {
    const morePages = [
      {
        key: 'overview',
        path: getGrcOverviewUrl(projectKey),
        name: 'Overview'
      },
      {
        key: 'activity',
        path: getGrcActivityUrl(projectKey),
        name: 'Activity'
      },
      {
        key: 'profiles',
        path: getGrcProfilesUrl(projectKey),
        name: 'Profiles'
      },
      {
        key: 'rules',
        path: getGrcRulesUrl(projectKey),
        name: 'Rules'
      },
      {
        key: 'inventory',
        path: getGrcInventoryUrl(projectKey),
        name: 'Inventory'
      },
      {
        key: 'projectSettings',
        path: getGrcProjectSettingsUrl(projectKey),
        name: 'Project Settings'
      }
    ];

    return (
        <Dropdown
            overlay={<ul className="menu">{morePages.map(page => (
                <li key={page.key}>
                  <Link to={page.path}>{page.name}</Link>
                </li>
            ))}</ul>}
            tagName="li">
          {({onToggleClick, open}) => (
              <a
                  aria-expanded={open}
                  aria-haspopup="true"
                  className={classNames('dropdown-toggle', {active: open})}
                  href="#"
                  id="global-navigation-more"
                  onClick={onToggleClick}>
                {translate('more')}
                <DropdownIcon className="little-spacer-left text-middle"/>
              </a>
          )}
        </Dropdown>
    );
  }

  return (
      <ul className="global-navbar-menu">
        {renderDashboard()}
        {renderViolations()}
        {renderMoreMenu()}
        {renderRerunAnalysis()}
      </ul>
  );
}
