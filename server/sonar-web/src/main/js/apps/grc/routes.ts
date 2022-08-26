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
import { lazyLoadComponent } from 'sonar-ui-common/components/lazyLoadComponent';

const routes = [
  {
    component: lazyLoadComponent(() => import('./components/GrcNewPage')),
    childRoutes: [
      {
        indexRoute: { component: lazyLoadComponent(() => import('./create/CreateGRCProject')) }
      },
      {
        path: 'create',
        indexRoute: { component: lazyLoadComponent(() => import('./create-project/CreateProject')) }
      },
      {
        path: 'create-organization',
        indexRoute: { component: lazyLoadComponent(() => import('./create-organization/CreateGrcOrganization')) }
      },
      {
        path: 'dashboard',
        indexRoute: { component: lazyLoadComponent(() => import('./dashboard/Dashboard')) }
      },
      {
        path: 'violations',
        component: lazyLoadComponent(() => import('../../apps/security-hotspots/SecurityHotspotsApp'))
      },
      {
        path: 'policies',
        component: lazyLoadComponent(() => import('../coding-rules/components/App'))
      },
      {
        path: 'settings',
        component: lazyLoadComponent(() => import('../settings/components/AppContainer'))
      },
      {
        path: 'activity',
        component: lazyLoadComponent(() => import('../projectActivity/components/ProjectActivityAppContainer'))
      },
      {
        path: 'overview',
        component: lazyLoadComponent(() => import('../overview/components/App')),

      },
      {
        path: 'inventory',
        component: lazyLoadComponent(() => import('../code/components/App')),
      },
      {
        path: 'profiles',
        component: lazyLoadComponent(() => import('./profiles/GRCProfiles'))
      },
      {
        path: 'profiles/show',
        component: lazyLoadComponent(() => import('./profiles/GRCProfileDetails'))
      },
      {
        path: 'analysis',
        component: lazyLoadComponent(() => import('./analysis/GrcRerunAnalysis'))
      }
    ]
  }
];

export default routes;