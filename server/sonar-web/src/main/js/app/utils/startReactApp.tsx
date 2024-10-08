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
import { ThemeProvider } from '@emotion/react';
import { lightTheme } from 'design-system';
import * as React from 'react';
import { render } from 'react-dom';
import { Helmet, HelmetProvider } from 'react-helmet-async';
import { IntlProvider } from 'react-intl';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import accountRoutes from '../../apps/account/routes';
import auditLogsRoutes from '../../apps/audit-logs/routes';
import backgroundTasksRoutes from '../../apps/background-tasks/routes';
import ChangeAdminPasswordApp from '../../apps/change-admin-password/ChangeAdminPasswordApp';
import codeRoutes from '../../apps/code/routes';
import componentMeasuresRoutes from '../../apps/component-measures/routes';
import groupsRoutes from '../../apps/groups/routes';
import { globalIssuesRoutes, projectIssuesRoutes } from '../../apps/issues/routes';
import maintenanceRoutes from '../../apps/maintenance/routes';
import marketplaceRoutes from '../../apps/marketplace/routes';
import overviewRoutes from '../../apps/overview/routes';
import permissionTemplatesRoutes from '../../apps/permission-templates/routes';
import { globalPermissionsRoutes, projectPermissionsRoutes } from '../../apps/permissions/routes';
import projectActivityRoutes from '../../apps/projectActivity/routes';
import projectBaselineRoutes from '../../apps/projectBaseline/routes';
import projectBranchesRoutes from '../../apps/projectBranches/routes';
import ProjectDeletionApp from '../../apps/projectDeletion/App';
import ProjectKeyApp from '../../apps/projectKey/Key';
import ProjectLinksApp from '../../apps/projectLinks/App';
import projectQualityGateRoutes from '../../apps/projectQualityGate/routes';
import projectQualityProfilesRoutes from '../../apps/projectQualityProfiles/routes';
import projectsRoutes from '../../apps/projects/routes';
import projectsManagementRoutes from '../../apps/projectsManagement/routes';
import SecurityHotspotsApp from '../../apps/security-hotspots/SecurityHotspotsApp';
import sessionsRoutes from '../../apps/sessions/routes';
import settingsRoutes from '../../apps/settings/routes';
import systemRoutes from '../../apps/system/routes';
import tutorialsRoutes from '../../apps/tutorials/routes';
import usersRoutes from '../../apps/users/routes';
import webAPIRoutes from '../../apps/web-api/routes';
import webhooksRoutes from '../../apps/webhooks/routes';
import { translate } from '../../helpers/l10n';
import { getBaseUrl } from '../../helpers/system';
import { AppState } from '../../types/appstate';
import { Feature } from '../../types/features';
import { CurrentUser } from '../../types/users';
import AdminContainer from '../components/AdminContainer';
import App from '../components/App';
import { DEFAULT_APP_STATE } from '../components/app-state/AppStateContext';
import AppStateContextProvider from '../components/app-state/AppStateContextProvider';
import {
  AvailableFeaturesContext,
  DEFAULT_AVAILABLE_FEATURES,
} from '../components/available-features/AvailableFeaturesContext';
import ComponentContainer from '../components/ComponentContainer';
import CurrentUserContextProvider from '../components/current-user/CurrentUserContextProvider';
import DocumentationRedirect from '../components/DocumentationRedirect';
import GlobalAdminPageExtension from '../components/extensions/GlobalAdminPageExtension';
import GlobalPageExtension from '../components/extensions/GlobalPageExtension';
import PortfolioPage from '../components/extensions/PortfolioPage';
import PortfoliosPage from '../components/extensions/PortfoliosPage';
import ProjectAdminPageExtension from '../components/extensions/ProjectAdminPageExtension';
import ProjectPageExtension from '../components/extensions/ProjectPageExtension';
import FormattingHelp from '../components/FormattingHelp';
import GlobalContainer from '../components/GlobalContainer';
import GlobalMessagesContainer from '../components/GlobalMessagesContainer';
import Landing from '../components/Landing';
import MigrationContainer from '../components/MigrationContainer';
import NonAdminPagesContainer from '../components/NonAdminPagesContainer';
import NotFound from '../components/NotFound';
import PluginRiskConsent from '../components/PluginRiskConsent';
import ProjectAdminContainer from '../components/ProjectAdminContainer';
import ResetPassword from '../components/ResetPassword';
import SimpleContainer from '../components/SimpleContainer';
import SonarLintConnection from '../components/SonarLintConnection';
import exportModulesAsGlobals from './exportModulesAsGlobals';
import organizationsRoutes from '../../apps/organizations/routes';
import { Organization } from "../../types/types";
import Home from '../components/Home';

function renderComponentRoutes() {
  return (
    <Route element={<ComponentContainer />}>
      {/* This container is a catch-all for all non-admin pages */}
      <Route element={<NonAdminPagesContainer />}>
        {codeRoutes()}
        {componentMeasuresRoutes()}
        {overviewRoutes()}
        <Route path="portfolio" element={<PortfolioPage />} />
        {projectActivityRoutes()}
        <Route
          path="project/extension/:pluginKey/:extensionKey"
          element={<ProjectPageExtension />}
        />
        {projectIssuesRoutes()}
        <Route path="security_hotspots" element={<SecurityHotspotsApp />} />
        {projectQualityGateRoutes()}
        {projectQualityProfilesRoutes()}

        {tutorialsRoutes()}
      </Route>
      <Route element={<ProjectAdminContainer />}>
        <Route path="project">
          <Route
            path="admin/extension/:pluginKey/:extensionKey"
            element={<ProjectAdminPageExtension />}
          />
          {backgroundTasksRoutes()}
          {projectBaselineRoutes()}
          {projectBranchesRoutes()}
          {settingsRoutes()}
          {webhooksRoutes()}

          <Route path="deletion" element={<ProjectDeletionApp />} />
          <Route path="links" element={<ProjectLinksApp />} />
          <Route path="key" element={<ProjectKeyApp />} />
        </Route>
        {projectPermissionsRoutes()}
      </Route>
    </Route>
  );
}

function renderAdminRoutes(canAdmin: boolean) {
  return (
      <Route path="admin" element={<AdminContainer />}>
        <Route path="extension/:pluginKey/:extensionKey" element={<GlobalAdminPageExtension />} />
        {settingsRoutes()}
        {backgroundTasksRoutes()}

        {canAdmin && (
            <>
              {auditLogsRoutes()}
              {groupsRoutes()}
              {permissionTemplatesRoutes()}
              {globalPermissionsRoutes()}
              {projectsManagementRoutes()}
              {systemRoutes()}
              {marketplaceRoutes()}
              {usersRoutes()}
              {webhooksRoutes()}
            </>
        )}
      </Route>
  );
}

function renderRedirects() {
  return (
    <>
      {/*
       * This redirect enables analyzers and PDFs to link to the correct version of the
       * documentation without having to compute the direct links themselves (DRYer).
       */}
      <Route path="/documentation/*" element={<DocumentationRedirect />} />
    </>
  );
}

export default function startReactApp(
  lang: string,
  userOrganizations?: Organization[],
  currentUser?: CurrentUser,
  appState?: AppState,
  availableFeatures?: Feature[]
) {
  exportModulesAsGlobals();

  const el = document.getElementById('content');
  const canAdmin = appState?.canAdmin;

  render(
    <HelmetProvider>
      <AppStateContextProvider appState={appState ?? DEFAULT_APP_STATE}>
        <AvailableFeaturesContext.Provider value={availableFeatures ?? DEFAULT_AVAILABLE_FEATURES}>
          <CurrentUserContextProvider currentUser={currentUser} userOrganizations={userOrganizations}>
            <IntlProvider defaultLocale={lang} locale={lang}>
              <ThemeProvider theme={lightTheme}>
                <GlobalMessagesContainer />
                <BrowserRouter basename={getBaseUrl()}>
                  <Helmet titleTemplate={translate('page_title.template.default')} />
                  <Routes>
                    {renderRedirects()}

                    <Route path="formatting/help" element={<FormattingHelp />} />

                    <Route element={<SimpleContainer />}>{maintenanceRoutes()}</Route>

                    <Route element={<MigrationContainer />}>
                      {sessionsRoutes()}

                      <Route path="/" element={<App />}>
                        <Route index={true} element={<Landing />} />

                        <Route element={<GlobalContainer />}>
                          {accountRoutes()}

                          <Route
                            path="extension/:pluginKey/:extensionKey"
                            element={<GlobalPageExtension />}
                          />

                          {globalIssuesRoutes()}

                          {organizationsRoutes()}

                          {projectsRoutes()}

                          <Route path="portfolios" element={<PortfoliosPage />} />

                          <Route path="sonarlint/auth" element={<SonarLintConnection />} />

                          {webAPIRoutes()}

                          {renderComponentRoutes()}

                          {renderAdminRoutes(canAdmin!)}
                        </Route>
                        <Route
                          // We don't want this route to have any menu.
                          // That is why we can not have it under the accountRoutes
                          path="account/reset_password"
                          element={<ResetPassword />}
                        />

                        <Route
                          // We don't want this route to have any menu. This is why we define it here
                          // rather than under the admin routes.
                          path="admin/change_admin_password"
                          element={<ChangeAdminPasswordApp />}
                        />

                        <Route
                          // We don't want this route to have any menu. This is why we define it here
                          // rather than under the admin routes.
                          path="admin/plugin_risk_consent"
                          element={<PluginRiskConsent />}
                        />
                        <Route path="home" element={<Home />} />
                        <Route path="not_found" element={<NotFound />} />
                        <Route path="*" element={<NotFound />} />
                      </Route>
                    </Route>
                  </Routes>
                </BrowserRouter>
              </ThemeProvider>
            </IntlProvider>
          </CurrentUserContextProvider>
        </AvailableFeaturesContext.Provider>
      </AppStateContextProvider>
    </HelmetProvider>,
    el
  );
}
