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

import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { TRACK_COLOR } from './colors';

interface Props {
  color: string;
  max: number;
  value: number;
}

/** A data bar inside a table cell: the number stays the primary reading, the bar lets a column be scanned at a glance. */
export default function InlineBar({ color, max, value }: Readonly<Props>) {
  return (
    <span className="sw-flex sw-items-center sw-justify-end sw-gap-2 sw-w-full">
      <span
        aria-hidden
        className="sw-flex-1 sw-h-2 sw-rounded-1 sw-overflow-hidden"
        style={{ backgroundColor: TRACK_COLOR, maxWidth: 96 }}
      >
        <span
          className="sw-block sw-h-full sw-rounded-1"
          style={{ backgroundColor: color, width: `${max > 0 ? (value / max) * 100 : 0}%` }}
        />
      </span>
      <span className="sw-typo-semibold sw-min-w-6 sw-text-right">
        {formatMeasure(value, MetricType.Integer)}
      </span>
    </span>
  );
}
