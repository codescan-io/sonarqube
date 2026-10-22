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
import { translateWithParameters } from '../../../helpers/l10n';
import { Grade } from '../health';
import { STATUS_COLORS, tint } from './charts/colors';

export const GRADE_COLORS: Record<Grade, string> = {
  A: STATUS_COLORS.success,
  B: STATUS_COLORS.success,
  C: STATUS_COLORS.warning,
  D: STATUS_COLORS.danger,
  E: STATUS_COLORS.danger,
};

interface Props {
  grade?: Grade;
  score?: number;
  size?: 'sm' | 'lg';
}

/** A health grade in a tinted disc, with the score it comes from as its accessible name. */
export default function HealthGrade({ grade, score, size = 'sm' }: Readonly<Props>) {
  const color = grade ? GRADE_COLORS[grade] : STATUS_COLORS.neutral;
  const label =
    grade && score !== undefined
      ? translateWithParameters('analytics.health.grade_label', grade, score)
      : undefined;
  return (
    <Disc
      aria-label={label}
      role={label ? 'img' : undefined}
      size={size === 'lg' ? 72 : 28}
      style={{ backgroundColor: tint(color, 18), borderColor: color, color }}
      title={label}
    >
      {grade ?? '-'}
    </Disc>
  );
}

const Disc = styled.span<{ size: number }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: ${({ size }) => size}px;
  height: ${({ size }) => size}px;
  border: 2px solid;
  border-radius: 50%;
  font-size: ${({ size }) => (size > 40 ? '2rem' : '0.875rem')};
  font-weight: 700;
  line-height: 1;
`;
