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
import * as React from 'react';
import { getPendingInvitations } from '../../../api/organizations';
import { renderComponent } from '../../../helpers/testReactTestingUtils';
import { Organization } from '../../../types/types';
import PendingInvitations from '../PendingInvitations';

jest.mock('../../../api/organizations');

const organization: Organization = {
  kee: 'my-org',
  name: 'My Org',
  inviteUsersEnabled: true,
  actions: { admin: true },
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('renders the invitations count and pagination from the nested page envelope', async () => {
  jest.mocked(getPendingInvitations).mockResolvedValueOnce({
    content: [
      { id: '1', email: 'jane.doe@example.com', userType: 'STANDARD', invitedOn: '2024-01-01' },
    ],
    page: {
      size: 50,
      number: 0,
      totalElements: 3,
      totalPages: 1,
    },
  } as never);

  renderComponent(<PendingInvitations organization={organization} />);

  expect(await screen.findByText('Jane Doe')).toBeInTheDocument();
  // ListFooter renders "x_of_y_shown.<count>.<total>" (translateWithParameters is mocked to join with '.')
  expect(await screen.findByText('x_of_y_shown.1.3')).toBeInTheDocument();
});
