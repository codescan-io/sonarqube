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
import { COLOR_CRITICAL, COLOR_LOW, COLOR_HEALTHY } from "../../../helpers/constants";

export const SIZE = 26;          
export const CX   = SIZE / 2;     
export const CY   = SIZE / 2;     
export const R    = SIZE / 2 - 1;

export function getFillColor(remainingPct: number): string {
    if (remainingPct <= 10) { return COLOR_CRITICAL; }
    if (remainingPct <= 25) { return COLOR_LOW; }
    return COLOR_HEALTHY;
}

/**
 * Snaps the actual remaining % to one of 4 fixed visual states:
 *   > 75 %          → full circle  (100 %)
 *   > 25 % and ≤ 75 → three-quarter (75 %)
 *   > 10 % and ≤ 25 → quarter       (25 %)
 *   ≥  0 % and ≤ 10 → half-quarter  (12.5 %)
 */
export function snapToDisplayPct(remainingPct: number): number {
    if (remainingPct > 75)  { return 100;  }
    if (remainingPct > 25)  { return 75;   }
    if (remainingPct > 10)  { return 25;   }
    return 12.5;
}

export function buildPiePath(pct: number): string | null {
    if (pct <= 0 || pct >= 100) { return null; }
  
    const startAngle = -Math.PI / 2;
    const endAngle   = startAngle + (pct / 100) * 2 * Math.PI;
  
    const x1 = CX + R * Math.cos(startAngle);
    const y1 = CY + R * Math.sin(startAngle);
    const x2 = CX + R * Math.cos(endAngle);
    const y2 = CY + R * Math.sin(endAngle);
  
    const largeArc = pct > 50 ? 1 : 0;
  
    return [
      `M ${CX} ${CY}`,
      `L ${x1.toFixed(3)} ${y1.toFixed(3)}`,
      `A ${R} ${R} 0 ${largeArc} 1 ${x2.toFixed(3)} ${y2.toFixed(3)}`,
      'Z',
    ].join(' ');
}