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

import { render, screen } from '@testing-library/react';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import Delta, { toDelta } from '../charts/Delta';
import InlineBar from '../charts/InlineBar';
import SegmentedMeter from '../charts/SegmentedMeter';
import Sparkline from '../charts/Sparkline';

describe('toDelta', () => {
  const percent = (r: number) => `${r > 0 ? '+' : ''}${r}%`;

  it('marks the direction and whether it is an improvement', () => {
    expect(toDelta(12.4, percent, true)).toEqual({
      direction: 'up',
      tone: 'good',
      value: '+12%',
      context: undefined,
    });
    expect(toDelta(12.4, percent, false)).toMatchObject({ direction: 'up', tone: 'bad' });
    expect(toDelta(-3.6, percent, false)).toMatchObject({
      direction: 'down',
      tone: 'good',
      value: '-4%',
    });
  });

  it('is neutral when the change rounds to zero', () => {
    expect(toDelta(0.3, percent, true, 'vs last month')).toEqual({
      direction: 'flat',
      tone: 'neutral',
      value: '0%',
      context: 'vs last month',
    });
  });
});

it('writes the delta value and its context', () => {
  render(<Delta context="vs previous period" direction="up" tone="good" value="+12%" />);

  expect(screen.getByText('+12%')).toBeInTheDocument();
  expect(screen.getByText('vs previous period')).toHaveClass('sw-sr-only');
});

it('draws a sparkline only with at least two values', () => {
  const { rerender } = render(<Sparkline color="blue" values={[3, undefined, 5, 4]} />);
  expect(screen.getByTestId('sparkline')).toBeInTheDocument();

  rerender(<Sparkline color="blue" values={[3]} />);
  expect(screen.queryByTestId('sparkline')).not.toBeInTheDocument();
});

it('prints every segment count and share of a meter', () => {
  renderComponent(
    <SegmentedMeter
      segments={[
        { key: 'a', color: 'green', label: 'Succeeded', value: 3 },
        { key: 'b', color: 'red', label: 'Failed', value: 1 },
      ]}
    />,
  );

  expect(screen.getByText('Succeeded')).toBeInTheDocument();
  expect(screen.getByText('75%')).toBeInTheDocument();
  expect(screen.getByText('25%')).toBeInTheDocument();
});

it('keeps the number of an inline bar readable', () => {
  render(<InlineBar color="blue" max={10} value={4} />);

  expect(screen.getByText('4')).toBeInTheDocument();
});
