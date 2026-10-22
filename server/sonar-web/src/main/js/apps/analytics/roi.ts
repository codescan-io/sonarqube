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

/** Remediation effort in whole engineer-hours. */
export function toHours(minutes: number) {
  return Math.round(minutes / 60);
}

/** "1,240 h", "8 h", "<1 h" */
export function formatHours(minutes: number) {
  if (minutes > 0 && minutes < 30) {
    return '<1 h';
  }
  return `${new Intl.NumberFormat().format(toHours(minutes))} h`;
}
