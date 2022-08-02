import * as classNames from 'classnames';
import React from 'react';
import {Link} from 'react-router';
import Dropdown from 'sonar-ui-common/components/controls/Dropdown';
import DropdownIcon from 'sonar-ui-common/components/icons/DropdownIcon';
import {translate} from 'sonar-ui-common/helpers/l10n';
import {Button} from "sonar-ui-common/components/controls/buttons";

interface Props {
  appState: Pick<T.AppState, 'canAdmin' | 'globalPages' | 'organizationsEnabled' | 'qualifiers'>;
  currentUser: T.CurrentUser;
  location: { pathname: string };
}

export default function GrcGlobalNavMenu(props: Props) {

  const {location} = props;

  function renderDashboard() {
    const active = location.pathname === '/grc';

    return (
        <li>
          <Link className={classNames({active})} to="/grc">
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
        key: 'rules',
        path: '/grc/rules',
        name: 'Rules',
      },
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
