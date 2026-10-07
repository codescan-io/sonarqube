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
import classNames from 'classnames';
import { themeColor } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import Legend from './Legend';

export interface StackedSeries {
  color: string;
  key: string;
  label: string;
}

export interface StackedRow {
  key: string;
  label: string;
  values: Record<string, number>;
}

interface Props {
  onSelect?: (key: string) => void;
  rows: StackedRow[];
  selectedKey?: string;
  series: StackedSeries[];
}

/**
 * One horizontal stacked bar per row, all scaled to the largest row total. Clicking a row
 * toggles it as a filter.
 */
export default function StackedBars({ onSelect, rows, selectedKey, series }: Readonly<Props>) {
  const totalOf = (row: StackedRow) =>
    series.reduce((sum, { key }) => sum + (row.values[key] ?? 0), 0);
  const max = Math.max(...rows.map(totalOf), 1);

  return (
    <div className="sw-flex sw-flex-col sw-gap-4">
      <Legend items={series} />
      <ul className="sw-flex sw-flex-col sw-gap-1">
        {rows.map((row) => {
          const total = totalOf(row);
          const selected = selectedKey === row.key;
          return (
            <li key={row.key}>
              <Row
                aria-pressed={selected}
                className={classNames({
                  selected,
                  'sw-opacity-40': selectedKey !== undefined && !selected,
                })}
                disabled={!onSelect}
                onClick={() => onSelect?.(row.key)}
                type="button"
              >
                <Text
                  className={classNames('sw-col-span-3 sw-truncate sw-text-left', {
                    'sw-typo-semibold': selected,
                  })}
                >
                  {row.label}
                </Text>
                <span className="sw-col-span-8 sw-flex sw-h-4">
                  <Bar style={{ width: `${(total / max) * 100}%` }}>
                    {series
                      .filter(({ key }) => (row.values[key] ?? 0) > 0)
                      .map(({ color, key, label }) => (
                        <Tooltip
                          content={`${row.label} · ${label}: ${formatMeasure(row.values[key], MetricType.Integer)}`}
                          key={key}
                        >
                          <span
                            className="sw-block sw-h-full"
                            style={{ backgroundColor: color, flexGrow: row.values[key] }}
                          />
                        </Tooltip>
                      ))}
                  </Bar>
                </span>
                <Text className="sw-col-span-1 sw-text-right sw-typo-semibold">
                  {formatMeasure(total, MetricType.Integer)}
                </Text>
              </Row>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const Bar = styled.span`
  display: flex;
  gap: 2px;
  height: 100%;
  border-radius: 4px;
  overflow: hidden;
  transition: width 0.4s ease;
`;

const Row = styled.button`
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 6px 8px;
  border: none;
  border-radius: 4px;
  background: none;
  cursor: pointer;
  transition: opacity 0.2s;

  &:disabled {
    cursor: default;
  }

  &:hover:not(:disabled),
  &.selected {
    background-color: ${themeColor('dropdownMenuHover')};
  }
`;
