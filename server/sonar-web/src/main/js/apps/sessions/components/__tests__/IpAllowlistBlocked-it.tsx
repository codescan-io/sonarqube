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
import userEvent from '@testing-library/user-event';
import { Link, Navigate, Route, useNavigate } from 'react-router-dom';
import { throwGlobalError } from '~sonar-aligned/helpers/error';
import { HttpStatus } from '../../../../helpers/request';
import { mockCurrentUser, mockLoggedInUser } from '../../../../helpers/testMocks';
import { renderAppRoutes } from '../../../../helpers/testReactTestingUtils';
import { CurrentUser } from '../../../../types/users';
import IpAllowlistBlocked from '../IpAllowlistBlocked';

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

const SIGNED_IN = mockLoggedInUser({
  login: 'luke',
  name: 'Luke Skywalker',
  email: 'luke@example.com',
});

function renderBlockedPage(
  state?: { from?: string; message?: string },
  currentUser: CurrentUser = mockCurrentUser(),
) {
  return renderAppRoutes(
    '/',
    () => (
      <>
        <Route path="/" element={<Navigate to="/sessions/ip-blocked" state={state} />} />
        <Route path="/sessions/ip-blocked" element={<IpAllowlistBlocked />} />
      </>
    ),
    { currentUser },
  );
}

it('says the organization cannot be opened from this network', async () => {
  renderBlockedPage({ message: MSG });
  expect(
    await screen.findByRole('heading', { level: 1, name: 'ip_allowlist.blocked.heading' }),
  ).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.eyebrow')).toBeInTheDocument();
});

it('shows the organization, the IP that was used and why it was refused', async () => {
  renderBlockedPage({ message: MSG });
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('ip_allowlist.blocked.organization')).toBeInTheDocument();
  expect(screen.getByText('acme')).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.ip')).toBeInTheDocument();
  expect(screen.getByText('192.0.2.1')).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.reason')).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.reason.not_listed')).toBeInTheDocument();
});

it('says so when the server could not tell the IP', async () => {
  renderBlockedPage({
    message:
      "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is unknown.",
  });
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('ip_allowlist.blocked.ip_unknown')).toBeInTheDocument();
});

it('shows who is signed in, and lets them sign out', async () => {
  renderBlockedPage({ message: MSG }, SIGNED_IN);
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('ip_allowlist.blocked.intro.signed_in')).toBeInTheDocument();
  expect(screen.getByText('luke@example.com')).toBeInTheDocument();
  expect(
    screen.getByText('ip_allowlist.blocked.user_description.Luke Skywalker'),
  ).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.signed_in')).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.footnote.signed_in')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'ip_allowlist.blocked.sign_out' }));

  expect(await screen.findByText('/sessions/logout')).toBeInTheDocument();
});

it('shows the login when the signed-in user has no email', async () => {
  renderBlockedPage(
    { message: MSG },
    mockLoggedInUser({ login: 'luke', name: 'Luke', email: undefined }),
  );
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('luke')).toBeInTheDocument();
});

it('shows no user and no sign-out to a visitor who is not signed in', async () => {
  renderBlockedPage({ message: MSG });
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('ip_allowlist.blocked.intro')).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.footnote')).toBeInTheDocument();
  expect(screen.queryByText('ip_allowlist.blocked.signed_in')).not.toBeInTheDocument();
  expect(
    screen.queryByRole('button', { name: 'ip_allowlist.blocked.sign_out' }),
  ).not.toBeInTheDocument();
});

it('takes the user to their organizations, not to a page that may open the blocked one', async () => {
  renderBlockedPage({ message: MSG }, SIGNED_IN);

  await userEvent.click(
    await screen.findByRole('button', { name: 'ip_allowlist.blocked.go_to_organizations' }),
  );

  expect(await screen.findByText('/account/organizations')).toBeInTheDocument();
});

it('tries the blocked page again', async () => {
  renderBlockedPage({ message: MSG, from: '/organizations/acme/projects?sort=name' }, SIGNED_IN);

  await userEvent.click(await screen.findByRole('button', { name: 'ip_allowlist.blocked.retry' }));

  expect(await screen.findByText('/organizations/acme/projects?sort=name')).toBeInTheDocument();
});

it('does not send Back into the blocked page after a retry', async () => {
  function Target() {
    const navigate = useNavigate();
    return (
      <button onClick={() => navigate(-1)} type="button">
        back
      </button>
    );
  }

  renderAppRoutes('/', () => (
    <>
      <Route
        path="/"
        element={
          <Link state={{ message: MSG, from: '/target' }} to="/sessions/ip-blocked">
            start
          </Link>
        }
      />
      <Route path="/sessions/ip-blocked" element={<IpAllowlistBlocked />} />
      <Route path="/target" element={<Target />} />
    </>
  ));

  await userEvent.click(await screen.findByText('start'));
  await userEvent.click(await screen.findByRole('button', { name: 'ip_allowlist.blocked.retry' }));
  await userEvent.click(await screen.findByRole('button', { name: 'back' }));

  expect(await screen.findByText('start')).toBeInTheDocument();
  expect(screen.queryByRole('heading', { level: 1 })).not.toBeInTheDocument();
});

describe('keyboard', () => {
  it('reaches and activates each button with Enter', async () => {
    const user = userEvent.setup();
    renderBlockedPage({ message: MSG, from: '/organizations/acme' }, SIGNED_IN);
    await screen.findByRole('heading', { level: 1 });

    await user.tab();
    expect(
      screen.getByRole('button', { name: 'ip_allowlist.blocked.go_to_organizations' }),
    ).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'ip_allowlist.blocked.retry' })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole('button', { name: 'ip_allowlist.blocked.sign_out' })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(await screen.findByText('/sessions/logout')).toBeInTheDocument();
  });

  it('activates Go to my organizations with Enter', async () => {
    const user = userEvent.setup();
    renderBlockedPage({ message: MSG }, SIGNED_IN);
    await screen.findByRole('heading', { level: 1 });

    await user.tab();
    await user.keyboard('{Enter}');
    expect(await screen.findByText('/account/organizations')).toBeInTheDocument();
  });

  it('activates Retry with Space', async () => {
    const user = userEvent.setup();
    renderBlockedPage({ message: MSG, from: '/organizations/acme' }, SIGNED_IN);
    await screen.findByRole('heading', { level: 1 });

    await user.tab();
    await user.tab();
    await user.keyboard(' ');
    expect(await screen.findByText('/organizations/acme')).toBeInTheDocument();
  });
});

it.each([
  ['no address', undefined],
  ['an address on another site', '//evil.example/path'],
  ['a backslash that the browser reads as another site', '/\\evil.example'],
  ['a full URL', 'https://evil.example/path'],
  ['the blocked page itself', '/sessions/ip-blocked'],
  ['the blocked page itself in other letter case', '/SESSIONS/IP-BLOCKED'],
])('offers no retry with %s', async (_, from) => {
  renderBlockedPage({ message: MSG, from }, SIGNED_IN);
  await screen.findByRole('heading', { level: 1 });

  expect(
    screen.queryByRole('button', { name: 'ip_allowlist.blocked.retry' }),
  ).not.toBeInTheDocument();
});

it('shows a message it cannot read as it is', async () => {
  renderBlockedPage({ message: 'Your request was refused by the organization.' });
  await screen.findByRole('heading', { level: 1 });

  const raw = screen.getByText('Your request was refused by the organization.');
  expect(raw).toBeInTheDocument();
  expect(screen.queryByText('ip_allowlist.blocked.organization')).not.toBeInTheDocument();
  // The list still holds only complete term and description pairs.
  expect(screen.getAllByRole('term')).toHaveLength(1);
  expect(screen.getAllByRole('definition')).toHaveLength(1);
});

it('still explains the block without a message', async () => {
  renderBlockedPage();
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText('ip_allowlist.blocked.reason.not_listed')).toBeInTheDocument();
  expect(screen.queryByText('ip_allowlist.blocked.organization')).not.toBeInTheDocument();
});

it('shows an organization key that contains markup as plain text', async () => {
  const hostileKey = '<img src=x onerror=alert(1)>';
  renderBlockedPage({
    message: `Access to organization '${hostileKey}' is blocked by its IP allowlist. Your IP address is 192.0.2.1.`,
  });
  await screen.findByRole('heading', { level: 1 });

  expect(screen.getByText(hostileKey)).toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});

describe('error toast for the same 403', () => {
  function allowlistResponse() {
    return new Response(JSON.stringify({ errors: [{ msg: MSG }] }), {
      status: HttpStatus.Forbidden,
    });
  }

  async function failWithAllowlist403() {
    await act(async () => {
      await expect(throwGlobalError(allowlistResponse())).rejects.toBeInstanceOf(Response);
    });
  }

  it('shows the toast on a page that does not redirect', async () => {
    renderAppRoutes('/', () => <Route path="/" element={<div>some other page</div>} />);

    await failWithAllowlist403();

    expect(await screen.findByTestId('global-message__ERROR')).toHaveTextContent(MSG);
  });

  it('shows no toast while the blocked page is on screen', async () => {
    renderBlockedPage({ message: MSG });
    await screen.findByRole('heading', { level: 1, name: 'ip_allowlist.blocked.heading' });

    await failWithAllowlist403();

    expect(screen.queryByTestId('global-message__ERROR')).not.toBeInTheDocument();
  });

  it('shows the toast again once the user has left the blocked page', async () => {
    renderBlockedPage({ message: MSG });
    await userEvent.click(
      await screen.findByRole('button', { name: 'ip_allowlist.blocked.go_to_organizations' }),
    );
    expect(await screen.findByText('/account/organizations')).toBeInTheDocument();

    await failWithAllowlist403();

    expect(await screen.findByTestId('global-message__ERROR')).toHaveTextContent(MSG);
  });
});
