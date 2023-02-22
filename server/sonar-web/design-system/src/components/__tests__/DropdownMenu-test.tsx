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
import { screen } from '@testing-library/react';
import { noop } from 'lodash';
import { render, renderWithRouter } from '../../helpers/testUtils';
import {
  DropdownMenu,
  ItemButton,
  ItemCheckbox,
  ItemCopy,
  ItemDangerButton,
  ItemDivider,
  ItemHeader,
  ItemLink,
  ItemNavLink,
  ItemRadioButton,
} from '../DropdownMenu';
import MenuIcon from '../icons/MenuIcon';
import Tooltip from '../Tooltip';

beforeEach(() => {
  jest.useFakeTimers();
});

afterEach(() => {
  jest.runOnlyPendingTimers();
  jest.useRealTimers();
});

it('should render a full menu correctly', () => {
  renderDropdownMenu();
  expect(screen.getByRole('menuitem', { name: 'My header' })).toBeInTheDocument();
  expect(screen.getByRole('menuitem', { name: 'Test menu item' })).toBeInTheDocument();
  expect(screen.getByRole('menuitem', { name: 'Test disabled item' })).toHaveClass('disabled');
});

it('menu items should work with tooltips', async () => {
  const { user } = render(
    <Tooltip overlay="test tooltip">
      <ItemButton onClick={jest.fn()}>button</ItemButton>
    </Tooltip>,
    {},
    { delay: null }
  );

  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

  await user.hover(screen.getByRole('menuitem'));
  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();

  jest.runAllTimers();
  expect(screen.getByRole('tooltip')).toBeVisible();

  await user.unhover(screen.getByRole('menuitem'));
  expect(screen.getByRole('tooltip')).toBeVisible();

  jest.runAllTimers();
  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
});

function renderDropdownMenu() {
  return renderWithRouter(
    <DropdownMenu>
      <ItemHeader>My header</ItemHeader>
      <ItemNavLink to="/test">Test menu item</ItemNavLink>
      <ItemDivider />
      <ItemLink disabled={true} to="/test-disabled">
        Test disabled item
      </ItemLink>
      <ItemButton icon={<MenuIcon />} onClick={noop}>
        Button
      </ItemButton>
      <ItemDangerButton onClick={noop}>DangerButton</ItemDangerButton>
      <ItemCopy copyValue="copy">Copy</ItemCopy>
      <ItemCheckbox checked={true} onCheck={noop}>
        Checkbox item
      </ItemCheckbox>
      <ItemRadioButton checked={false} onCheck={noop} value="radios">
        Radio item
      </ItemRadioButton>
    </DropdownMenu>
  );
}
