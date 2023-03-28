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
import DocLink from '../../components/common/DocLink';
import InstanceMessage from '../../components/common/InstanceMessage';
import Link from '../../components/common/Link';
import { Alert } from '../../components/ui/Alert';
import { getEdition } from '../../helpers/editions';
import { translate, translateWithParameters } from '../../helpers/l10n';
import { AppState } from '../../types/appstate';
import withAppStateContext from './app-state/withAppStateContext';
import GlobalFooterBranding from './GlobalFooterBranding';
import { getYear } from 'date-fns';
import { translate } from '../../helpers/l10n';

export interface GlobalFooterProps {
  hideLoggedInInfo?: boolean;
  appState?: AppState;
}

export function GlobalFooter({ hideLoggedInInfo, appState }: GlobalFooterProps) {
  const currentEdition = appState?.edition && getEdition(appState.edition);

  return (
    <div className="page-footer page-container" id="footer">
      {appState?.productionDatabase === false && (
        <Alert display="inline" id="evaluation_warning" variant="warning">
          <p className="big">{translate('footer.production_database_warning')}</p>
          <p>
            <InstanceMessage message={translate('footer.production_database_explanation')} />
          </p>
        </Alert>
      )}

      <GlobalFooterBranding />

      <ul className="page-footer-menu">
        {!hideLoggedInInfo && currentEdition && (
          <li className="page-footer-menu-item">{currentEdition.name}</li>
        )}
        {!hideLoggedInInfo && appState?.version && (
          <li className="page-footer-menu-item">
            {translateWithParameters('footer.version_x', appState.version)}
          </li>
        )}
        <li className="page-footer-menu-item">
          <a
            href="https://www.gnu.org/licenses/lgpl-3.0.txt"
            rel="noopener noreferrer"
            target="_blank"
          >
            {translate('footer.license')}
          </a>
        </li>
        <li className="page-footer-menu-item">
          <a
            href="https://community.sonarsource.com/c/help/sq"
            rel="noopener noreferrer"
            target="_blank"
          >
            {translate('footer.community')}
          </a>
        </li>
        <li className="page-footer-menu-item">
          <DocLink to="/">{translate('footer.documentation')}</DocLink>
        </li>
        <li className="page-footer-menu-item">
          <DocLink to="/instance-administration/plugin-version-matrix/">
            {translate('footer.plugins')}
          </DocLink>
        </li>
        {!hideLoggedInInfo && (
          <li className="page-footer-menu-item">
            <Link to="/web_api">{translate('footer.web_api')}</Link>
          </li>
        )}
          <li>
            © 2017-{getYear(new Date())} <a
            href="https://www.codescan.io"
            rel="noopener noreferrer"
            target="_blank"
            title="CodeScan Enterprises LLC">
            CodeScan Enterprises LLC
            </a>
            . All rights reserved.
          </li>
          <li className="page-footer-menu-item">
            Version {translate('footer.codescan_version')}
          </li>
          <li className="page-footer-menu-item">
              <a rel="noopener noreferrer" target="_blank" href="https://www.gnu.org/licenses/lgpl-3.0.txt">LGPL v3</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://www.codescan.io/tos/">{translate('footer.terms')}</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://www.linkedin.com/company/code-scan">Linkedin</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://www.facebook.com/CodeScanForSalesforce/">Facebook</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://twitter.com/CodeScanforSFDC">Twitter</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://www.codescan.io/contact/">{translate('footer.help')}</a>
          </li>
          <li className="page-footer-menu-item">
            <a rel="noopener noreferrer" target="_blank" href="https://knowledgebase.autorabit.com/codescan/docs/codescan-getting-started">{'About'}</a>
          </li>
      </ul>

    </div>
  );
}

export default withAppStateContext(GlobalFooter);
