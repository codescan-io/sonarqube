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

import styled from '@emotion/styled';
import { Text, Tooltip } from '@sonarsource/echoes-react';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { TRACK_COLOR } from './colors';

export interface MeterSegment {
  color: string;
  key: string;
  label: string;
  value: number;
}

interface Props {
  segments: MeterSegment[];
  /** Defaults to the sum of the segments; a larger total leaves an empty track at the end. */
  total?: number;
}

/**
 * Parts of a whole on one horizontal bar, each part separated by a 2px gap, with a legend that prints every count
 * and share: the legend doubles as the table view.
 */
export default function SegmentedMeter({ segments, total }: Readonly<Props>) {
  const sum = segments.reduce((s, { value }) => s + value, 0);
  const whole = Math.max(total ?? sum, 1);
  const share = (value: number) => Math.round((value / whole) * 100);

  return (
    <div className="sw-flex sw-flex-col sw-gap-3">
      <Bar style={{ backgroundColor: TRACK_COLOR }}>
        {segments
          .filter(({ value }) => value > 0)
          .map(({ color, key, label, value }) => (
            <Tooltip
              content={`${label}: ${formatMeasure(value, MetricType.Integer)} (${share(value)}%)`}
              key={key}
            >
              <span
                aria-label={`${label}: ${value}`}
                className="sw-block sw-h-full"
                role="img"
                style={{ backgroundColor: color, width: `${(value / whole) * 100}%` }}
                tabIndex={0}
              />
            </Tooltip>
          ))}
      </Bar>
      <ul className="sw-flex sw-flex-wrap sw-gap-x-6 sw-gap-y-2">
        {segments.map(({ color, key, label, value }) => (
          <li className="sw-flex sw-items-center sw-gap-2" key={key}>
            <span
              aria-hidden
              className="sw-inline-block sw-w-3 sw-h-3 sw-rounded-1 sw-shrink-0"
              style={{ backgroundColor: color }}
            />
            <Text>{label}</Text>
            <Text className="sw-typo-semibold">{formatMeasure(value, MetricType.Integer)}</Text>
            <Text isSubdued>{share(value)}%</Text>
          </li>
        ))}
      </ul>
    </div>
  );
}

const Bar = styled.div`
  display: flex;
  gap: 2px;
  height: 16px;
  border-radius: 4px;
  overflow: hidden;

  & > span {
    transition: width 0.4s ease;
  }
`;
