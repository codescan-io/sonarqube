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

import * as React from 'react';
import { byLabelText, byRole, byText } from 'testing-library-selector';
import UsersServiceMock from '../../../api/mocks/UsersServiceMock';
import { renderApp } from '../../../helpers/testReactTestingUtils';
import UsersApp from '../UsersApp';

jest.mock('../../../api/users');
jest.mock('../../../api/system');

const handler = new UsersServiceMock();

const ui = {
  createUserButton: byRole('button', { name: 'users.create_user' }),
  infoManageMode: byText(/users\.page\.managed_description/),
  description: byText('users.page.description'),
  allFilter: byRole('button', { name: 'all' }),
  managedFilter: byRole('button', { name: 'managed' }),
  localFilter: byRole('button', { name: 'local' }),
  aliceRow: byRole('row', { name: 'AM Alice Merveille alice.merveille never' }),
  aliceRowWithLocalBadge: byRole('row', {
    name: 'AM Alice Merveille alice.merveille local never',
  }),
  aliceUpdateGroupButton: byRole('button', { name: 'users.update_users_groups.alice.merveille' }),
  aliceUpdateButton: byRole('button', { name: 'users.manage_user.alice.merveille' }),
  alicedDeactivateButton: byRole('button', { name: 'users.deactivate' }),
  bobUpdateGroupButton: byRole('button', { name: 'users.update_users_groups.bob.marley' }),
  bobUpdateButton: byRole('button', { name: 'users.manage_user.bob.marley' }),
  bobRow: byRole('row', { name: 'BM Bob Marley bob.marley never' }),
  loginInput: byRole('textbox', { name: /login/ }),
  userNameInput: byRole('textbox', { name: /name/ }),
  passwordInput: byLabelText(/password/),
  scmAddButton: byRole('button', { name: 'add_verb' }),
  createUserDialogButton: byRole('button', { name: 'create' }),
  dialogSCMInputs: byRole('textbox', { name: /users.create_user.scm_account/ }),
  dialogSCMInput: (value?: string) =>
    byRole('textbox', { name: `users.create_user.scm_account_${value ? `x.${value}` : 'new'}` }),
  deleteSCMButton: (value?: string) =>
    byRole('button', {
      name: `remove_x.users.create_user.scm_account_${value ? `x.${value}` : 'new'}`,
    }),
  jackRow: byRole('row', { name: /Jack/ }),
};

afterAll(() => {
  handler.reset();
});

it('should render list of user in non manage mode', async () => {
  handler.setIsManaged(false);
  renderUsersApp();

    expect(await ui.description.find()).toBeInTheDocument();
    expect(ui.createUserButton.get()).toBeEnabled();
    await userEvent.click(ui.createUserButton.get());

    await userEvent.type(ui.loginInput.get(), 'Login');
    await userEvent.type(ui.userNameInput.get(), 'Jack');
    await userEvent.type(ui.passwordInput.get(), 'Password');
    // Add SCM account
    expect(ui.dialogSCMInputs.queryAll()).toHaveLength(0);
    await userEvent.click(ui.scmAddButton.get());
    expect(ui.dialogSCMInputs.getAll()).toHaveLength(1);
    await userEvent.type(ui.dialogSCMInput().get(), 'SCM');
    expect(ui.dialogSCMInput('SCM').get()).toBeInTheDocument();
    // Remove SCM account
    await userEvent.click(ui.deleteSCMButton('SCM').get());
    expect(ui.dialogSCMInputs.queryAll()).toHaveLength(0);

    await userEvent.click(ui.createUserDialogButton.get());
    expect(ui.jackRow.get()).toBeInTheDocument();
  });

it('should render list of user in manage mode', async () => {
  handler.setIsManaged(true);
  renderUsersApp();

  expect(await ui.infoManageMode.find()).toBeInTheDocument();
  expect(ui.createUserButton.get()).toBeDisabled();
});

function renderUsersApp() {
  renderApp('admin/users', <UsersApp />);
}
