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

import { act, screen } from '@testing-library/react';
import { Route, useLocation, useNavigationType } from 'react-router-dom';
import { addGlobalErrorMessage } from '~design-system';
import { getJSON } from '~sonar-aligned/helpers/request';
import { IP_ALLOWLIST_TOAST_ID } from '../../../helpers/ipAllowlist';
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

const NAVIGATION_URL = '/api/navigation/component'; // its wrapper shows a toast on failure
const COMPONENT_URL = '/api/components/show'; // its wrapper does not

function forbidden() {
  return new Response(JSON.stringify({ errors: [{ msg: MSG }] }), {
    status: HttpStatus.Forbidden,
  });
}

function failAfter(ms: number) {
  return new Promise((_resolve, reject) => {
    setTimeout(() => reject(forbidden()), ms);
  });
}

function mockRequests(handlers: Record<string, () => Promise<unknown>>) {
  jest.mocked(getJSON).mockImplementation((url: string) => {
    return handlers[url]?.() ?? Promise.resolve({});
  });
}

afterEach(() => {
  jest.resetAllMocks();
});

it('opens the blocked page, replacing the component page in the history', async () => {
  mockRequests({ [NAVIGATION_URL]: () => Promise.reject(forbidden()) });
  renderComponentContainer();

  expect(await screen.findByText(`/sessions/ip-blocked|REPLACE|${MSG}`)).toBeInTheDocument();
});

it('removes the allowlist toast that was already showing when it redirects', async () => {
  mockRequests({ [NAVIGATION_URL]: () => failAfter(200) });
  renderComponentContainer();
  act(() => {
    addGlobalErrorMessage(MSG, { toastId: IP_ALLOWLIST_TOAST_ID });
  });
  expect(toastOf(MSG)).toHaveAttribute('data-in', 'true');

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  await settle();
  expect(toastOf(MSG)).toHaveAttribute('data-in', 'false');
});

it('shows no error toast when the request without a toast fails first and the other follows', async () => {
  mockRequests({
    [COMPONENT_URL]: () => Promise.reject(forbidden()),
    [NAVIGATION_URL]: () => failAfter(30),
  });
  renderComponentContainer();

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  await settle();
  expect(screen.queryByText(MSG)).not.toBeInTheDocument();
});

it('leaves unrelated toasts alone', async () => {
  mockRequests({ [NAVIGATION_URL]: () => Promise.reject(forbidden()) });
  renderComponentContainer();
  act(() => {
    addGlobalErrorMessage('Unrelated problem');
  });

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  // eslint-disable-next-line testing-library/no-node-access
  const toast = screen.getByText('Unrelated problem').closest('.Toastify__toast');
  expect(toast).toHaveAttribute('data-in', 'true');
});

// Gives a toast that was just added time to finish showing, so a missing dismissal is noticed.
async function settle() {
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 100);
    });
  });
}

// A dismissed toast stays in the page until its exit animation ends, which jsdom never fires.
// Its "data-in" flag turns false as soon as it is dismissed, so use that.
function toastOf(text: string) {
  // eslint-disable-next-line testing-library/no-node-access
  return screen.getByText(text).closest('.Toastify__toast');
}

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
