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
import * as React from 'react';
import { niceCeiling } from '../../activity';
import { TRACK_COLOR } from './colors';
import { Gridline, Plot, XAxis, YAxis } from './ColumnTimeline';
import Legend from './Legend';

export interface LineSeries {
  color: string;
  dashed?: boolean;
  key: string;
  label: string;
}

export interface LinePoint {
  key: string;
  label: string;
  /** A missing value breaks the line, so empty buckets are never drawn as zero. */
  values: Record<string, number | undefined>;
}

interface Props {
  formatValue: (value: number) => string;
  points: LinePoint[];
  series: LineSeries[];
}

const PLOT_HEIGHT = 160;

/**
 * One 2px line per series on a single axis. A vertical hairline snaps to the hovered bucket,
 * and its tooltip lists every series at that bucket.
 */
export default function LineTimeline({ formatValue, points, series }: Readonly<Props>) {
  const [hovered, setHovered] = React.useState<number>();
  const all = points.flatMap(({ values }) =>
    series.map(({ key }) => values[key]).filter((v): v is number => v !== undefined),
  );
  const max = niceCeiling(Math.max(...all, 0));
  const x = (index: number) => ((index + 0.5) / points.length) * 100;
  const y = (value: number) => PLOT_HEIGHT - (value / max) * PLOT_HEIGHT;

  const paths = series.map(({ key }) => {
    let d = '';
    let drawing = false;
    points.forEach(({ values }, index) => {
      const value = values[key];
      if (value === undefined) {
        drawing = false;
        return;
      }
      d += `${drawing ? 'L' : 'M'}${x(index)},${y(value)} `;
      drawing = true;
    });
    return d.trim();
  });

  // A soft wash under solid lines; one closed shape per unbroken run of values.
  const areas = series.map(({ key }) => {
    const runs: number[][] = [];
    points.forEach(({ values }, index) => {
      if (values[key] === undefined) {
        return;
      }
      const run = runs[runs.length - 1];
      if (run && run[run.length - 1] === index - 1) {
        run.push(index);
      } else {
        runs.push([index]);
      }
    });
    return runs
      .filter((run) => run.length > 1)
      .map(
        (run) =>
          `M${x(run[0])},${PLOT_HEIGHT} ` +
          run.map((i) => `L${x(i)},${y(points[i].values[key] as number)}`).join(' ') +
          ` L${x(run[run.length - 1])},${PLOT_HEIGHT} Z`,
      )
      .join(' ');
  });

  // The latest value of the first solid series is labeled at the end of its line.
  const labeled = series.find(({ dashed }) => !dashed);
  const lastIndex = labeled
    ? points.map(({ values }) => values[labeled.key] !== undefined).lastIndexOf(true)
    : -1;
  const lastValue = lastIndex >= 0 && labeled ? points[lastIndex].values[labeled.key] : undefined;

  // A bucket with no neighbour would be an invisible zero-length line: give it a dot.
  const isolated = (key: string, index: number) =>
    points[index].values[key] !== undefined &&
    points[index - 1]?.values[key] === undefined &&
    points[index + 1]?.values[key] === undefined;

  return (
    <div className="sw-flex sw-flex-col sw-gap-4">
      <Legend items={series.map((s) => ({ ...s, shape: s.dashed ? 'dashed-line' : 'line' }))} />
      <div className="sw-flex sw-gap-2">
        <YAxis aria-hidden>
          <Text isSubdued size={TextSize.Small}>
            {formatValue(max)}
          </Text>
          <Text isSubdued size={TextSize.Small}>
            {formatValue(max / 2)}
          </Text>
          <Text isSubdued size={TextSize.Small}>
            0
          </Text>
        </YAxis>
        <div className="sw-flex-1 sw-min-w-0">
          <Plot onMouseLeave={() => setHovered(undefined)} style={{ gap: 0 }}>
            <Gridline style={{ top: 0 }} />
            <Gridline style={{ top: PLOT_HEIGHT / 2 }} />
            <Gridline style={{ top: PLOT_HEIGHT - 1 }} />
            <Svg aria-hidden preserveAspectRatio="none" viewBox={`0 0 100 ${PLOT_HEIGHT}`}>
              {series.map(({ color, dashed, key }, i) =>
                dashed || areas[i] === '' ? null : (
                  <path d={areas[i]} fill={color} key={`area-${key}`} opacity={0.1} stroke="none" />
                ),
              )}
              {series.map(({ color, dashed, key }, i) => (
                <path
                  d={paths[i]}
                  fill="none"
                  key={key}
                  stroke={color}
                  strokeDasharray={dashed ? '6 4' : undefined}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  vectorEffect="non-scaling-stroke"
                />
              ))}
            </Svg>
            {hovered !== undefined && <Crosshair style={{ left: `${x(hovered)}%` }} />}
            {labeled && lastValue !== undefined && hovered === undefined && (
              <>
                <Dot
                  style={{
                    backgroundColor: labeled.color,
                    left: `${x(lastIndex)}%`,
                    top: y(lastValue),
                  }}
                />
                <EndLabel
                  aria-hidden
                  style={{
                    left: `${x(lastIndex)}%`,
                    top: y(lastValue),
                    transform: `translate(${x(lastIndex) > 80 ? '-100%' : '-50%'}, -150%)`,
                  }}
                >
                  {formatValue(lastValue)}
                </EndLabel>
              </>
            )}
            {series.flatMap(({ color, key }) =>
              points.map((point, index) => {
                const value = point.values[key];
                if (value === undefined || (hovered !== index && !isolated(key, index))) {
                  return null;
                }
                return (
                  <Dot
                    key={`${key}-${point.key}`}
                    style={{ backgroundColor: color, left: `${x(index)}%`, top: y(value) }}
                  />
                );
              }),
            )}
            {points.map((point, index) => (
              <Tooltip
                content={
                  <span className="sw-flex sw-flex-col">
                    <span>{point.label}</span>
                    {series.map(({ key, label }) => {
                      const value = point.values[key];
                      return (
                        <span key={key}>
                          <strong>{value === undefined ? '-' : formatValue(value)}</strong> {label}
                        </span>
                      );
                    })}
                  </span>
                }
                key={point.key}
              >
                <HitSlot
                  aria-label={`${point.label}: ${series
                    .map(({ key, label }) => {
                      const value = point.values[key];
                      return `${label} ${value === undefined ? '-' : formatValue(value)}`;
                    })
                    .join(', ')}`}
                  onBlur={() => setHovered(undefined)}
                  onFocus={() => setHovered(index)}
                  onMouseEnter={() => setHovered(index)}
                  tabIndex={0}
                />
              </Tooltip>
            ))}
          </Plot>
          <XAxis columns={points} />
        </div>
      </div>
    </div>
  );
}

const Svg = styled.svg`
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
`;

const HitSlot = styled.span`
  position: relative;
  flex: 1;
  min-width: 0;
  height: 100%;
  outline: none;
`;

const Crosshair = styled.span`
  position: absolute;
  top: 0;
  bottom: 0;
  width: 1px;
  background-color: ${TRACK_COLOR};
  pointer-events: none;
`;

const Dot = styled.span`
  position: absolute;
  width: 12px;
  height: 12px;
  margin: -6px 0 0 -6px;
  border: 2px solid var(--echoes-color-background-default);
  border-radius: 50%;
  pointer-events: none;
`;

const EndLabel = styled.span`
  position: absolute;
  padding: 0 4px;
  border-radius: 4px;
  background-color: var(--echoes-color-background-default);
  color: var(--echoes-color-text-bold);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.25rem;
  white-space: nowrap;
  pointer-events: none;
`;
