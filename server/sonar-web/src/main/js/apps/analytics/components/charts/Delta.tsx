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
import { IconArrowDownRight, IconArrowRight, IconArrowUpRight } from '@sonarsource/echoes-react';

export type DeltaTone = 'good' | 'bad' | 'neutral';

export interface DeltaProps {
  /** Shown to screen readers and as a tooltip, e.g. "vs previous 30 days". */
  context?: string;
  direction: 'up' | 'down' | 'flat';
  tone: DeltaTone;
  /** Signed, with its unit, e.g. "+12%" or "-3 pts". */
  value: string;
}

const TONES: Record<DeltaTone, { background: string; color: string }> = {
  good: {
    background: 'var(--echoes-color-background-success-weak)',
    color: 'var(--echoes-color-text-success-bold)',
  },
  bad: {
    background: 'var(--echoes-color-background-danger-weak-default)',
    color: 'var(--echoes-color-text-danger-bold)',
  },
  neutral: {
    background: 'var(--echoes-color-border-weaker)',
    color: 'var(--echoes-color-text-default)',
  },
};

/**
 * A change against a reference, as a pill: an arrow for the direction, a tint for whether that direction is good.
 * The value is always written out, so the color is never the only cue.
 */
export default function Delta({ context, direction, tone, value }: Readonly<DeltaProps>) {
  const { background, color } = TONES[tone];
  const Icon = { up: IconArrowUpRight, down: IconArrowDownRight, flat: IconArrowRight }[direction];
  return (
    <Pill style={{ backgroundColor: background, color }} title={context}>
      <Icon />
      <span>{value}</span>
      {context && <span className="sw-sr-only">{context}</span>}
    </Pill>
  );
}

/**
 * Builds a delta from a numeric change. {@code upIsGood} says which direction is an improvement; changes that
 * round to zero are neutral.
 */
export function toDelta(
  change: number,
  format: (rounded: number) => string,
  upIsGood: boolean,
  context?: string,
): DeltaProps {
  const rounded = Math.round(change);
  if (rounded === 0) {
    return { direction: 'flat', tone: 'neutral', value: format(0), context };
  }
  const up = rounded > 0;
  return {
    direction: up ? 'up' : 'down',
    tone: up === upIsGood ? 'good' : 'bad',
    value: format(rounded),
    context,
  };
}

const Pill = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 8px 1px 6px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1.25rem;
  white-space: nowrap;
`;
