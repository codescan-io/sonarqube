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
import { Text, TextSize, Tooltip } from '@sonarsource/echoes-react';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { niceCeiling } from '../../activity';
import { TRACK_COLOR } from './colors';
import Legend from './Legend';

export interface TimelineSeries {
  color: string;
  key: string;
  label: string;
}

export interface TimelineColumn {
  key: string;
  label: string;
  values: Record<string, number>;
}

interface Props {
  columns: TimelineColumn[];
  /** Stacked bottom to top in this order. */
  series: TimelineSeries[];
}

const PLOT_HEIGHT = 160;

/**
 * One stacked column per time bucket, all scaled to a rounded maximum. Each column's whole slot
 * is its hover target, and the tooltip lists every series of that bucket.
 */
export default function ColumnTimeline({ columns, series }: Readonly<Props>) {
  const totalOf = (column: TimelineColumn) =>
    series.reduce((sum, { key }) => sum + (column.values[key] ?? 0), 0);
  const max = niceCeiling(Math.max(...columns.map(totalOf), 0));
  const format = (value: number) => formatMeasure(value, MetricType.ShortInteger);

  return (
    <div className="sw-flex sw-flex-col sw-gap-4">
      <Legend items={series} />
      <div className="sw-flex sw-gap-2">
        <YAxis aria-hidden>
          <Text isSubdued size={TextSize.Small}>
            {format(max)}
          </Text>
          <Text isSubdued size={TextSize.Small}>
            {format(max / 2)}
          </Text>
          <Text isSubdued size={TextSize.Small}>
            0
          </Text>
        </YAxis>
        <div className="sw-flex-1 sw-min-w-0">
          <Plot>
            <Gridline style={{ top: 0 }} />
            <Gridline style={{ top: PLOT_HEIGHT / 2 }} />
            <Gridline style={{ top: PLOT_HEIGHT - 1 }} />
            {columns.map((column) => {
              const total = totalOf(column);
              return (
                <Tooltip
                  content={
                    <span className="sw-flex sw-flex-col">
                      <span>{column.label}</span>
                      {series.map(({ key, label }) => (
                        <span key={key}>
                          <strong>
                            {formatMeasure(column.values[key] ?? 0, MetricType.Integer)}
                          </strong>{' '}
                          {label}
                        </span>
                      ))}
                    </span>
                  }
                  key={column.key}
                >
                  <Slot
                    aria-label={`${column.label}: ${formatMeasure(total, MetricType.Integer)}`}
                    tabIndex={0}
                  >
                    <Column style={{ height: `${(total / max) * 100}%` }}>
                      {[...series]
                        .reverse()
                        .map(({ color, key }) =>
                          (column.values[key] ?? 0) > 0 ? (
                            <span
                              key={key}
                              style={{ backgroundColor: color, flexGrow: column.values[key] }}
                            />
                          ) : null,
                        )}
                    </Column>
                  </Slot>
                </Tooltip>
              );
            })}
          </Plot>
          <XAxis columns={columns} />
        </div>
      </div>
    </div>
  );
}

/** First, middle and last labels only, so they never collide. */
export function XAxis({ columns }: Readonly<{ columns: Array<{ key: string; label: string }> }>) {
  if (columns.length === 0) {
    return null;
  }
  const middle = Math.floor((columns.length - 1) / 2);
  const shown = new Set([0, middle, columns.length - 1]);
  return (
    <div aria-hidden className="sw-flex sw-mt-1">
      {columns.map((column, index) => (
        <span className="sw-flex-1 sw-min-w-0 sw-flex sw-justify-center" key={column.key}>
          {shown.has(index) && (
            <Text className="sw-whitespace-nowrap" isSubdued size={TextSize.Small}>
              {column.label}
            </Text>
          )}
        </span>
      ))}
    </div>
  );
}

export const YAxis = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  height: ${PLOT_HEIGHT}px;
  margin-top: -8px;
  margin-bottom: 8px;
  min-width: 32px;
`;

export const Plot = styled.div`
  position: relative;
  display: flex;
  gap: 2px;
  height: ${PLOT_HEIGHT}px;
`;

export const Gridline = styled.span`
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background-color: ${TRACK_COLOR};
  pointer-events: none;
`;

const Slot = styled.span`
  position: relative;
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: flex-end;
  justify-content: center;
  height: 100%;
  border-radius: 4px;
  outline: none;

  &:hover,
  &:focus-visible {
    background-color: color-mix(in srgb, ${TRACK_COLOR} 50%, transparent);
  }
`;

const Column = styled.span`
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  max-width: 24px;
  border-radius: 4px 4px 0 0;
  overflow: hidden;
  transition: height 0.4s ease;

  & > span {
    display: block;
    min-height: 2px;
  }
`;
