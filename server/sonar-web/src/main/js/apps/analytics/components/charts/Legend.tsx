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

import { Text } from '@sonarsource/echoes-react';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';

export interface LegendItem {
  color: string;
  key: string;
  label: string;
  value?: number;
}

export default function Legend({ items }: Readonly<{ items: LegendItem[] }>) {
  return (
    <ul className="sw-flex sw-flex-wrap sw-gap-x-6 sw-gap-y-2">
      {items.map(({ color, key, label, value }) => (
        <li className="sw-flex sw-items-center sw-gap-2" key={key}>
          <span
            aria-hidden
            className="sw-inline-block sw-w-3 sw-h-3 sw-rounded-1 sw-shrink-0"
            style={{ backgroundColor: color }}
          />
          <Text>{label}</Text>
          {value !== undefined && (
            <Text className="sw-typo-semibold">{formatMeasure(value, MetricType.Integer)}</Text>
          )}
        </li>
      ))}
    </ul>
  );
}
