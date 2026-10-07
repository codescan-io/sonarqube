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

import { Text, Tooltip } from '@sonarsource/echoes-react';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { MAGNITUDE_COLOR, TRACK_COLOR, tint } from './colors';

interface Axis {
  key: string;
  label: string;
}

interface Props {
  columns: Axis[];
  onSelect?: (row: string, column: string) => void;
  rows: Axis[];
  selected?: { column?: string; row?: string };
  values: Record<string, Record<string, number>>;
}

// Darker cells than this share of the maximum carry white text.
const DARK_CELL_THRESHOLD = 0.55;

/** Rows x columns grid whose cells darken with their count; clicking a cell filters by both. */
export default function Heatmap({ columns, onSelect, rows, selected, values }: Readonly<Props>) {
  const max = Math.max(
    ...rows.flatMap((row) => columns.map((column) => values[row.key]?.[column.key] ?? 0)),
    1,
  );
  const isFaded = (row: string, column: string) =>
    (selected?.row !== undefined && selected.row !== row) ||
    (selected?.column !== undefined && selected.column !== column);

  return (
    <div
      className="sw-grid sw-gap-1 sw-items-center"
      style={{
        gridTemplateColumns: `minmax(120px, auto) repeat(${columns.length}, minmax(0, 1fr))`,
      }}
    >
      <span />
      {columns.map((column) => (
        <Text className="sw-text-center sw-truncate" isSubdued key={column.key}>
          {column.label}
        </Text>
      ))}
      {rows.map((row) => [
        <Text className="sw-truncate" key={row.key}>
          {row.label}
        </Text>,
        ...columns.map((column) => {
          const value = values[row.key]?.[column.key] ?? 0;
          const strength = value / max;
          const formatted = formatMeasure(value, MetricType.Integer);
          return (
            <Tooltip
              content={`${row.label} · ${column.label}: ${formatted}`}
              key={`${row.key}-${column.key}`}
            >
              <button
                className="sw-h-10 sw-rounded-1 sw-border-0 sw-typo-semibold sw-transition-opacity"
                disabled={!onSelect}
                onClick={() => onSelect?.(row.key, column.key)}
                style={{
                  backgroundColor:
                    value === 0
                      ? TRACK_COLOR
                      : tint(MAGNITUDE_COLOR, 15 + Math.round(strength * 85)),
                  color: strength > DARK_CELL_THRESHOLD ? 'white' : undefined,
                  cursor: onSelect ? 'pointer' : undefined,
                  opacity: isFaded(row.key, column.key) ? 0.35 : 1,
                }}
                type="button"
              >
                {formatted}
              </button>
            </Tooltip>
          );
        }),
      ])}
    </div>
  );
}
