import * as classNames from 'classnames';
import React, { useEffect, useState } from 'react';
import {Link} from 'react-router';
import Dropdown from 'sonar-ui-common/components/controls/Dropdown';
import DropdownIcon from 'sonar-ui-common/components/icons/DropdownIcon';
import {translate} from 'sonar-ui-common/helpers/l10n';
import {Button} from "sonar-ui-common/components/controls/buttons";
import { searchProjects } from '../../../api/components';

interface Props {
  appState: Pick<T.AppState, 'canAdmin' | 'globalPages' | 'organizationsEnabled' | 'qualifiers'>;
  currentUser: T.CurrentUser;
  location: { pathname: string };
}

export default function GrcGlobalNavMenu(props: Props) {
    const [hasProjects, setHasProjects] = useState(false);

  const {location} = props;

  useEffect(() => {
    searchProjects({filter: 'tags=grc'}).then(({components}) => {
      if (components.length) {
        setHasProjects(true);
      }
    });
  }, []);

  function renderDashboard() {
    const active = location.pathname === '/grc/dashboard';

    return (
        <li>
          <Link className={classNames({active})} to="/grc/dashboard">
            Dashboard
          </Link>
        </li>
    );
  }

  function renderViolations() {
    const active = location.pathname === '/grc/violations';

    return (
        <li>
          <Link className={classNames({active})} to="/grc/violations">
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
        path: '/grc/overview',
        name: 'Overview'
      },
      {
        key: 'activity',
        path: '/grc/activity',
        name: 'Activity'
      },
      {
        key: 'profiles',
        path: '/grc/profiles',
        name: 'Profiles'
      },
      {
        key: 'rules',
        path: '/grc/rules',
        name: 'Rules'
      },
      {
        key: 'inventory',
        path: '/grc/inventory',
        name: 'Inventory'
      },
      {
        key: 'projectSettings',
        path: '/grc/settings',
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
        {hasProjects && renderDashboard()}
        {hasProjects && renderViolations()}
        {hasProjects && renderMoreMenu()}
        {hasProjects && renderRerunAnalysis()}
      </ul>
  );
}
