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

import { TRACK_COLOR } from './colors';

interface Props {
  /** The accent for the latest point; the line itself stays in the de-emphasis hue. */
  color: string;
  height?: number;
  /** Missing values break the line. */
  values: Array<number | undefined>;
  width?: number;
}

/**
 * A word-sized trend for a stat tile: a 2px line in the de-emphasis hue, a soft wash under it, and the latest value
 * marked in the tile's accent. Decorative: the tile states the value and its change in text.
 */
export default function Sparkline({ color, height = 32, values, width = 96 }: Readonly<Props>) {
  const defined = values.filter((v): v is number => v !== undefined);
  if (defined.length < 2) {
    return null;
  }
  const min = Math.min(...defined);
  const max = Math.max(...defined);
  const span = max - min || 1;
  const pad = 4;
  const x = (i: number) => pad + (i / (values.length - 1)) * (width - 2 * pad);
  const y = (v: number) => height - pad - ((v - min) / span) * (height - 2 * pad);

  let line = '';
  let drawing = false;
  values.forEach((value, i) => {
    if (value === undefined) {
      drawing = false;
      return;
    }
    line += `${drawing ? 'L' : 'M'}${x(i).toFixed(1)},${y(value).toFixed(1)} `;
    drawing = true;
  });
  const lastIndex = values.map((v) => v !== undefined).lastIndexOf(true);
  const firstIndex = values.findIndex((v) => v !== undefined);
  const area = `${line}L${x(lastIndex).toFixed(1)},${height - pad} L${x(firstIndex).toFixed(1)},${height - pad} Z`;
  const last = values[lastIndex] as number;

  return (
    <svg
      aria-hidden
      data-testid="sparkline"
      className="sw-shrink-0"
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      width={width}
    >
      <path d={area} fill={color} opacity={0.1} />
      <path
        d={line.trim()}
        fill="none"
        stroke="var(--echoes-color-border-bold)"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
      />
      <line
        stroke={TRACK_COLOR}
        strokeWidth={1}
        x1={pad}
        x2={width - pad}
        y1={height - pad}
        y2={height - pad}
      />
      <circle
        cx={x(lastIndex)}
        cy={y(last)}
        fill={color}
        r={4}
        stroke="var(--echoes-color-background-default)"
        strokeWidth={2}
      />
    </svg>
  );
}
