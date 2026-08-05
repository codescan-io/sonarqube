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
import { COLOR_GREEN, COLOR_PURPLE, COLOR_AMBER, COLOR_RED } from "../../../helpers/constants";

export const SIZE         = 28;
export const STROKE_WIDTH = 2.5;
export const RADIUS       = (SIZE - STROKE_WIDTH) / 2;   // 12.75
export const CX           = SIZE / 2;                     // 14
export const CY           = SIZE / 2;                     // 14
export const CIRCUMFERENCE = 2 * Math.PI * RADIUS;        // ≈ 80.1

export function getArcColor(usedPct: number): string {
    if (usedPct <= 25) { return COLOR_GREEN; } 
    if (usedPct <= 50) { return COLOR_PURPLE; } 
    if (usedPct <= 75) { return COLOR_AMBER; } 
    return COLOR_RED;                         
}

