/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
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
import { screen } from '@testing-library/dom';
import * as React from 'react';
import { renderComponent } from '../../../helpers/testReactTestingUtils';
import ColorRatingsLegend, { ColorRatingsLegendProps } from '../ColorRatingsLegend';

it('should render correctly', () => {
  renderColorRatingsLegend();
  expect(screen.getByRole('checkbox', { name: 'A' })).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: 'B' })).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: 'C' })).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: 'D' })).toBeInTheDocument();
  expect(screen.getByRole('checkbox', { name: 'E' })).toBeInTheDocument();
});

it('should react when a rating is clicked', () => {
  const onRatingClick = jest.fn();
  renderColorRatingsLegend({ onRatingClick });

  screen.getByRole('checkbox', { name: 'D' }).click();
  expect(onRatingClick).toBeCalledWith(4);
});

function renderColorRatingsLegend(props: Partial<ColorRatingsLegendProps> = {}) {
  return renderComponent(
    <ColorRatingsLegend filters={{ 2: true }} onRatingClick={jest.fn()} {...props} />
  );
}