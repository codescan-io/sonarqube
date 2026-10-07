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
import { Text } from '@sonarsource/echoes-react';
import classNames from 'classnames';
import { arc, pie, PieArcDatum } from 'd3-shape';
import * as React from 'react';
import { themeColor } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { TRACK_COLOR } from './colors';

export interface DonutItem {
  color: string;
  key: string;
  label: string;
  value: number;
}

interface Props {
  centerLabel: string;
  centerValue: string;
  items: DonutItem[];
  onSelect?: (key: string) => void;
  selectedKey?: string;
}

const SIZE = 168;
const THICKNESS = 22;
const HOVER_GROWTH = 4;

/**
 * Donut with a clickable legend. Hovering a segment (or its legend row) shows its numbers
 * in the centre; clicking toggles it as a filter.
 */
export default function Donut(props: Readonly<Props>) {
  const { centerLabel, centerValue, items, onSelect, selectedKey } = props;
  const [hovered, setHovered] = React.useState<string>();

  const total = items.reduce((sum, item) => sum + item.value, 0);
  const radius = SIZE / 2 - HOVER_GROWTH;
  const arcs = pie<DonutItem>()
    .sort(null)
    .value((item) => item.value)
    .padAngle(total > 0 ? 0.02 : 0)(items);
  const drawArc = (datum: PieArcDatum<DonutItem>, grow: boolean) =>
    arc<PieArcDatum<DonutItem>>()
      .innerRadius(radius - THICKNESS)
      .outerRadius(radius + (grow ? HOVER_GROWTH : 0))
      .cornerRadius(3)(datum) ?? undefined;

  const focus = items.find(({ key }) => key === (hovered ?? selectedKey));
  const share = (value: number) => (total > 0 ? Math.round((value / total) * 100) : 0);

  return (
    <div className="sw-flex sw-items-center sw-gap-6">
      <div className="sw-relative sw-shrink-0" style={{ height: SIZE, width: SIZE }}>
        <svg aria-hidden height={SIZE} width={SIZE}>
          <g transform={`translate(${SIZE / 2}, ${SIZE / 2})`}>
            {total === 0 && (
              <circle
                fill="none"
                r={radius - THICKNESS / 2}
                strokeWidth={THICKNESS}
                style={{ stroke: TRACK_COLOR }}
              />
            )}
            {arcs.map((datum) => {
              const { key, color } = datum.data;
              const active = hovered === key || selectedKey === key;
              const faded = (hovered ?? selectedKey) !== undefined && !active;
              return (
                <path
                  d={drawArc(datum, active)}
                  key={key}
                  onClick={onSelect ? () => onSelect(key) : undefined}
                  onMouseEnter={() => setHovered(key)}
                  onMouseLeave={() => setHovered(undefined)}
                  style={{
                    cursor: onSelect ? 'pointer' : undefined,
                    fill: color,
                    opacity: faded ? 0.35 : 1,
                    transition: 'opacity 0.2s',
                  }}
                />
              );
            })}
          </g>
        </svg>
        <div className="sw-absolute sw-inset-0 sw-flex sw-flex-col sw-items-center sw-justify-center sw-pointer-events-none">
          <span className="sw-heading-lg">{focus ? `${share(focus.value)}%` : centerValue}</span>
          <Text isSubdued>{focus ? focus.label : centerLabel}</Text>
        </div>
      </div>

      <ul className="sw-flex sw-flex-col sw-gap-1 sw-flex-1 sw-min-w-0">
        {items.map(({ color, key, label, value }) => {
          const selected = selectedKey === key;
          return (
            <li key={key}>
              <LegendRow
                aria-pressed={selected}
                className={classNames({ selected, 'sw-typo-semibold': selected })}
                disabled={!onSelect}
                onClick={() => onSelect?.(key)}
                onMouseEnter={() => setHovered(key)}
                onMouseLeave={() => setHovered(undefined)}
                type="button"
              >
                <span
                  aria-hidden
                  className="sw-inline-block sw-w-3 sw-h-3 sw-rounded-1 sw-shrink-0"
                  style={{ backgroundColor: color }}
                />
                <Text className="sw-flex-1 sw-truncate">{label}</Text>
                <Text className="sw-typo-semibold">{formatMeasure(value, MetricType.Integer)}</Text>
                <Text isSubdued>{share(value)}%</Text>
              </LegendRow>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const LegendRow = styled.button`
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 4px 8px;
  border: none;
  border-radius: 4px;
  background: none;
  cursor: pointer;
  text-align: left;

  &:disabled {
    cursor: default;
  }

  &:hover:not(:disabled),
  &.selected {
    background-color: ${themeColor('dropdownMenuHover')};
  }
`;
