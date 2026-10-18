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
import { Navigate, Route } from 'react-router-dom';
import { renderAppRoutes } from '../../../../helpers/testReactTestingUtils';
import IpAllowlistBlocked from '../IpAllowlistBlocked';

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

function renderBlockedPage(state?: { message: string }) {
  return renderAppRoutes('/', () => (
    <>
      <Route path="/" element={<Navigate to="/sessions/ip-blocked" state={state} />} />
      <Route path="/sessions/ip-blocked" element={<IpAllowlistBlocked />} />
    </>
  ));
}

it('shows the server message and a link home', async () => {
  renderBlockedPage({ message: MSG });
  expect(
    await screen.findByRole('heading', { level: 1, name: 'ip_allowlist.blocked.title' }),
  ).toBeInTheDocument();
  expect(screen.getByText(MSG)).toBeInTheDocument();
  expect(screen.getByText('ip_allowlist.blocked.contact_admin')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'layout.home' })).toHaveAttribute('href', '/projects');
});

it('falls back to a generic text without state', async () => {
  renderBlockedPage();
  expect(await screen.findByText('ip_allowlist.blocked.generic')).toBeInTheDocument();
});

it('shows a message that contains markup as plain text', async () => {
  const hostile =
    "Access to organization '<img src=x onerror=alert(1)>' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";
  renderBlockedPage({ message: hostile });
  expect(await screen.findByText(hostile)).toBeInTheDocument();
  expect(screen.queryByRole('img')).not.toBeInTheDocument();
});
