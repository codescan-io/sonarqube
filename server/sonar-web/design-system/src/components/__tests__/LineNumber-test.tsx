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
<<<<<<<< HEAD:server/sonar-web/src/main/js/apps/users/__tests__/Header-test.tsx
import { shallow } from 'enzyme';
import * as React from 'react';
import { click } from '../../../helpers/testUtils';
import Header from '../Header';

it('should render correctly', () => {
  expect(getWrapper()).toMatchSnapshot();
});

it('should open the user creation form', () => {
  const wrapper = getWrapper();
  click(wrapper.find('#users-create'));
  expect(wrapper.find('UserForm').exists()).toBe(true);
});

function getWrapper(props = {}) {
  return shallow(<Header loading={true} onUpdateUsers={jest.fn()} {...props} />);
========
import { screen } from '@testing-library/react';
import { render } from '../../helpers/testUtils';
import { FCProps } from '../../types/misc';
import { LineNumber } from '../code-line/LineNumber';

it('should a popup when clicked', async () => {
  const { user } = setupWithProps();

  expect(screen.getByRole('button', { name: 'aria-label' })).toBeVisible();

  await user.click(screen.getByRole('button', { name: 'aria-label' }));
  expect(screen.getByText('Popup')).toBeVisible();
});

function setupWithProps(props: Partial<FCProps<typeof LineNumber>> = {}) {
  return render(
    <LineNumber
      ariaLabel="aria-label"
      displayOptions
      firstLineNumber={1}
      lineNumber={16}
      popup={<div>Popup</div>}
      {...props}
    />
  );
>>>>>>>> a2597f5b3d3 (SONAR-19174 Migrating code viewer to MIUI):server/sonar-web/design-system/src/components/__tests__/LineNumber-test.tsx
}
