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

export type Grade = 'A' | 'B' | 'C' | 'D' | 'E';

/** One signal of the health score, as a share from 0 (worst) to 100 (best); undefined when there is no data. */
export interface HealthComponent {
  key: string;
  value?: number;
  weight: number;
}

export interface Health {
  components: HealthComponent[];
  grade?: Grade;
  /** 0 to 100, missing when no component has data. */
  score?: number;
}

/**
 * Weighted average of the components that have data: a missing signal never drags the score down, its weight goes
 * to the others.
 */
export function computeHealth(components: HealthComponent[]): Health {
  const known = components.filter(
    (component): component is HealthComponent & { value: number } => component.value !== undefined,
  );
  const weight = known.reduce((sum, component) => sum + component.weight, 0);
  if (weight === 0) {
    return { components };
  }
  const score = Math.round(
    known.reduce((sum, component) => sum + clamp(component.value) * component.weight, 0) / weight,
  );
  return { components, grade: gradeOf(score), score };
}

export function gradeOf(score: number): Grade {
  if (score >= 85) {
    return 'A';
  }
  if (score >= 70) {
    return 'B';
  }
  if (score >= 55) {
    return 'C';
  }
  if (score >= 40) {
    return 'D';
  }
  return 'E';
}

/** part / total as a percentage, or undefined when there is nothing to measure. */
export function share(part: number, total: number) {
  return total > 0 ? (part / total) * 100 : undefined;
}

function clamp(value: number) {
  return Math.min(100, Math.max(0, value));
}
