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
import * as React from 'react';
import { renderComponent } from '../../../../../../../helpers/testReactTestingUtils';
import { ComponentQualifier } from '../../../../../../../types/component';
import MetaKey, { MetaKeyProps } from '../MetaKey';

it('should render correctly', () => {
  renderMetaKey();
  expect(
    screen.getByLabelText(`overview.project_key.${ComponentQualifier.Project}`)
  ).toBeInTheDocument();
  expect(
    screen.getByRole('button', { name: 'overview.project_key.click_to_copy' })
  ).toBeInTheDocument();
});

function renderMetaKey(props: Partial<MetaKeyProps> = {}) {
  return renderComponent(
    <MetaKey componentKey="foo" qualifier={ComponentQualifier.Project} {...props} />
  );
}
