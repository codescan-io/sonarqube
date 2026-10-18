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

import { screen } from '@testing-library/react';
import { Route, useLocation } from 'react-router-dom';
import { getOrganization, getOrganizationNavigation } from '../../../../api/organizations';
import { HttpStatus } from '../../../../helpers/request';
import { renderAppRoutes } from '../../../../helpers/testReactTestingUtils';
import OrganizationApp from '../OrganizationApp';

jest.mock('../../../../api/organizations', () => ({
  getOrganization: jest.fn(),
  getOrganizationNavigation: jest.fn(),
  getOrganizationBillingDetails: jest.fn().mockResolvedValue({}),
}));

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

afterEach(() => {
  jest.clearAllMocks();
});

it('opens the blocked page when the organization is blocked by its IP allowlist', async () => {
  jest
    .mocked(getOrganization)
    .mockRejectedValue(
      new Response(JSON.stringify({ errors: [{ msg: MSG }] }), { status: HttpStatus.Forbidden }),
    );
  jest.mocked(getOrganizationNavigation).mockResolvedValue({} as never);

  renderOrganizationApp();

  expect(await screen.findByText(`/sessions/ip-blocked|${MSG}`)).toBeInTheDocument();
});

function BlockedPageProbe() {
  const location = useLocation();
  return <div>{`${location.pathname}|${(location.state as { message?: string })?.message}`}</div>;
}

function renderOrganizationApp() {
  return renderAppRoutes(
    '/organizations/acme',
    () => (
      <>
        <Route
          path="/organizations/:organizationKey"
          element={<OrganizationApp userOrganizations={[]} />}
        />
        <Route path="/sessions/ip-blocked" element={<BlockedPageProbe />} />
      </>
    ),
    { navigateTo: '/organizations/acme' },
  );
}
