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

import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { getValues } from '../../../../api/settings';
import { mockAppState, mockLoggedInUser } from '../../../../helpers/testMocks';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { GlobalSettingKeys } from '../../../../types/settings';
import { Organization } from '../../../../types/types';
import OrganizationNavigationAdministration from '../OrganizationNavigationAdministration';

jest.mock('../../../../api/settings', () => ({
  ...jest.requireActual('../../../../api/settings'),
  getValues: jest.fn(),
}));

const IP_ALLOWLIST = 'IP Allowlist';
const RESOLUTION_TRANSFER = 'Resolution Transfer';
const WEBHOOK_LOG = 'Webhook log';

function mockFlags(flags: Partial<Record<GlobalSettingKeys, string>>) {
  jest
    .mocked(getValues)
    .mockResolvedValue(
      Object.entries(flags).map(([key, value]) => ({ key, value, inherited: false })),
    );
}

beforeEach(() => {
  jest.mocked(getValues).mockReset();
});

it('shows the IP Allowlist entry when the feature is on', async () => {
  mockFlags({ [GlobalSettingKeys.CodescanIpAllowlistEnabled]: 'true' });
  await openMenu();

  expect(await screen.findByRole('menuitem', { name: IP_ALLOWLIST })).toBeInTheDocument();
});

it('hides the IP Allowlist entry when the feature is off', async () => {
  mockFlags({ [GlobalSettingKeys.CodescanIpAllowlistEnabled]: 'false' });
  await openMenu();

  expect(await screen.findByRole('menuitem', { name: WEBHOOK_LOG })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: IP_ALLOWLIST })).not.toBeInTheDocument();
});

it('hides the IP Allowlist entry when the feature was never set', async () => {
  mockFlags({});
  await openMenu();

  expect(await screen.findByRole('menuitem', { name: WEBHOOK_LOG })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: IP_ALLOWLIST })).not.toBeInTheDocument();
});

it('hides the IP Allowlist entry while the setting is loading', async () => {
  jest.mocked(getValues).mockReturnValue(new Promise(() => {}));
  await openMenu();

  expect(await screen.findByRole('menuitem', { name: WEBHOOK_LOG })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: IP_ALLOWLIST })).not.toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: RESOLUTION_TRANSFER })).not.toBeInTheDocument();
});

it('hides the IP Allowlist entry when the settings request fails', async () => {
  const failure = Promise.reject(new Error('settings request failed'));
  failure.catch(() => {});
  jest.mocked(getValues).mockReturnValue(failure);
  await openMenu();

  await waitFor(() => expect(getValues).toHaveBeenCalled());
  await failure.catch(() => {});
  expect(await screen.findByRole('menuitem', { name: WEBHOOK_LOG })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: IP_ALLOWLIST })).not.toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: RESOLUTION_TRANSFER })).not.toBeInTheDocument();
});

it('reads both feature settings in one request and shows each entry by its own setting', async () => {
  mockFlags({
    [GlobalSettingKeys.CodescanResolutionTransferEnabled]: 'true',
    [GlobalSettingKeys.CodescanIpAllowlistEnabled]: 'false',
  });
  await openMenu();

  expect(await screen.findByRole('menuitem', { name: RESOLUTION_TRANSFER })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: IP_ALLOWLIST })).not.toBeInTheDocument();
  expect(getValues).toHaveBeenCalledTimes(1);
  expect(getValues).toHaveBeenCalledWith({
    keys: [
      GlobalSettingKeys.CodescanResolutionTransferEnabled,
      GlobalSettingKeys.CodescanIpAllowlistEnabled,
    ],
  });
});

async function openMenu() {
  const user = userEvent.setup();
  renderOrganizationNavigationAdministration();
  await user.click(screen.getByRole('link', { name: 'layout.settings' }));
}

function renderOrganizationNavigationAdministration() {
  const organization: Organization = {
    kee: 'acme',
    name: 'Acme',
    inviteUsersEnabled: false,
    adminPages: [
      { key: 'developer/ip_allowlist', name: IP_ALLOWLIST },
      { key: 'developer/resolution_transfer', name: RESOLUTION_TRANSFER },
      { key: 'developer/webhook_log', name: WEBHOOK_LOG },
    ],
  };
  return renderComponent(
    <OrganizationNavigationAdministration
      location={{ pathname: '/organizations/acme/edit' }}
      organization={organization}
    />,
    '/',
    { appState: mockAppState({ canAdmin: true }), currentUser: mockLoggedInUser() },
  );
}
