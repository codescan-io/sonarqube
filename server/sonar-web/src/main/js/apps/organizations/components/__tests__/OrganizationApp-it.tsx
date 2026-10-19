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
import { HttpStatus } from '../../../../helpers/request';
import { renderAppRoutes } from '../../../../helpers/testReactTestingUtils';
import OrganizationApp from '../OrganizationApp';

// Only the lowest-level request is mocked, so the API wrappers' own throwGlobalError runs for real.
jest.mock('~sonar-aligned/helpers/request', () => ({
  ...jest.requireActual('~sonar-aligned/helpers/request'),
  getJSON: jest.fn(),
}));

function forbidden(msg: string) {
  return new Response(JSON.stringify({ errors: [{ msg }] }), { status: HttpStatus.Forbidden });
}

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

const NAVIGATION_URL = '/api/navigation/organization'; // its wrapper shows a toast on failure
const ORGANIZATION_URL = '/_codescan/organizations/acme'; // its wrapper does not

function failAfter(ms: number) {
  return new Promise((_resolve, reject) => {
    setTimeout(() => reject(forbidden(MSG)), ms);
  });
}

function mockRequests(handlers: Record<string, () => Promise<unknown>>) {
  jest.mocked(getJSON).mockImplementation((url: string) => {
    return handlers[url]?.() ?? Promise.resolve({ kee: 'acme' });
  });
}

afterEach(() => {
  jest.resetAllMocks();
});

it('opens the blocked page, replacing the organization page in the history', async () => {
  mockRequests({ [NAVIGATION_URL]: () => Promise.reject(forbidden(MSG)) });
  renderOrganizationApp();

  expect(await screen.findByText(`/sessions/ip-blocked|REPLACE|${MSG}`)).toBeInTheDocument();
});

it('shows no error toast on the blocked page', async () => {
  mockRequests({ [NAVIGATION_URL]: () => Promise.reject(forbidden(MSG)) });
  renderOrganizationApp();

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  expect(screen.queryByText(MSG)).not.toBeInTheDocument();
});

it('shows no error toast when the request without a toast fails first and the other follows', async () => {
  mockRequests({
    [ORGANIZATION_URL]: () => Promise.reject(forbidden(MSG)),
    [NAVIGATION_URL]: () => failAfter(30),
  });
  renderOrganizationApp();

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  await act(async () => {
    await new Promise((resolve) => {
      setTimeout(resolve, 100);
    });
  });
  expect(screen.queryByText(MSG)).not.toBeInTheDocument();
});

it('leaves unrelated toasts alone', async () => {
  mockRequests({ [NAVIGATION_URL]: () => Promise.reject(forbidden(MSG)) });
  renderOrganizationApp();
  act(() => {
    addGlobalErrorMessage('Unrelated problem');
  });

  expect(await screen.findByText(/^\/sessions\/ip-blocked\|/)).toBeInTheDocument();
  // A dismissed toast stays in the page until its exit animation ends, which jsdom never fires.
  // Its "data-in" flag turns false as soon as it is dismissed, so use that.
  // eslint-disable-next-line testing-library/no-node-access
  const toast = screen.getByText('Unrelated problem').closest('.Toastify__toast');
  expect(toast).toHaveAttribute('data-in', 'true');
});

function BlockedPageProbe() {
  const location = useLocation();
  const navigationType = useNavigationType();
  const message = (location.state as { message?: string } | null)?.message;
  return <div>{`${location.pathname}|${navigationType}|${message}`}</div>;
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
