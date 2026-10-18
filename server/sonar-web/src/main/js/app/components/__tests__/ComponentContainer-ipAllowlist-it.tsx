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
import { Route, useLocation, useNavigationType } from 'react-router-dom';
import { getJSON } from '~sonar-aligned/helpers/request';
import { HttpStatus } from '../../../helpers/request';
import { renderAppRoutes } from '../../../helpers/testReactTestingUtils';
import ComponentContainer from '../ComponentContainer';
import { CurrentOrgContext } from '../nav/organization/CurrentOrgContext';

// Only the lowest-level request is mocked, so the API wrappers' own throwGlobalError runs for real.
jest.mock('~sonar-aligned/helpers/request', () => ({
  ...jest.requireActual('~sonar-aligned/helpers/request'),
  getJSON: jest.fn(),
}));

// This query uses axios, not the request helper, and has nothing to do with the redirect.
jest.mock('../../../queries/mode', () => ({
  useStandardExperienceModeQuery: jest.fn(),
}));

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

beforeEach(() => {
  jest.mocked(getJSON).mockImplementation((url: string) => {
    if (url === '/api/navigation/component') {
      return Promise.reject(
        new Response(JSON.stringify({ errors: [{ msg: MSG }] }), { status: HttpStatus.Forbidden }),
      );
    }
    return Promise.resolve({});
  });
});

afterEach(() => {
  jest.resetAllMocks();
});

it('opens the blocked page, replacing the component page in the history', async () => {
  renderComponentContainer();

  expect(await screen.findByText(`/sessions/ip-blocked|REPLACE|${MSG}`)).toBeInTheDocument();
});

it('does not leave the error message toast on top of the blocked page', async () => {
  renderComponentContainer();

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();

  // A dismissed toast stays in the page until its exit animation ends, which jsdom never fires.
  // Its "data-in" flag turns false as soon as it is dismissed, so use that.
  // eslint-disable-next-line testing-library/no-node-access
  const toast = screen.getByText(MSG).closest('.Toastify__toast');
  expect(toast).toHaveAttribute('data-in', 'false');
});

function BlockedPageProbe() {
  const location = useLocation();
  const navigationType = useNavigationType();
  const message = (location.state as { message?: string } | null)?.message;
  return <div>{`${location.pathname}|${navigationType}|${message}`}</div>;
}

function renderComponentContainer() {
  const orgContext = { orgKee: null, setOrgKee: jest.fn() };
  return renderAppRoutes(
    '/dashboard',
    () => (
      <>
        <Route
          element={
            <CurrentOrgContext.Provider value={orgContext}>
              <ComponentContainer />
            </CurrentOrgContext.Provider>
          }
        >
          <Route path="dashboard" element={<div>component page</div>} />
        </Route>
        <Route path="/sessions/ip-blocked" element={<BlockedPageProbe />} />
      </>
    ),
    { navigateTo: '/dashboard?id=foo' },
  );
}
