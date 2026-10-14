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

import {
  formatChange,
  formatDuration,
  getActivityRequest,
  getChange,
  getSuccessRate,
  niceCeiling,
} from '../activity';

const stats = (success: number, failed: number, canceled = 0) => ({
  canceled,
  failed,
  projects: 1,
  success,
  total: success + failed + canceled,
});

it('builds a request for the last days, today included', () => {
  const request = getActivityRequest(7, new Date(2026, 9, 14, 15, 0));
  expect(request).toMatchObject({ from: '2026-10-08', to: '2026-10-14', interval: 'day' });
  // the browser's time zone; when it has none, the server falls back to its own
  expect(request.timeZone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  expect(getActivityRequest(90, new Date(2026, 9, 14)).interval).toBe('week');
});

it('computes the success rate without canceled analyses', () => {
  expect(getSuccessRate(stats(3, 1, 10))).toBe(75);
  expect(getSuccessRate(stats(0, 0, 2))).toBeUndefined();
});

it('computes and formats changes', () => {
  expect(getChange(120, 100)).toBe(20);
  expect(getChange(100, 0)).toBeUndefined();
  expect(getChange(undefined, 10)).toBeUndefined();
  expect(formatChange(12.4)).toBe('+12%');
  expect(formatChange(-3.6)).toBe('-4%');
  expect(formatChange(0.2, ' pts')).toBe('0 pts');
});

it.each([
  [850, '850ms'],
  [48_000, '48s'],
  [59_600, '1m 00s'],
  [182_000, '3m 02s'],
  [3_840_000, '1h 04m'],
])('formats %d ms as %s', (ms, expected) => {
  expect(formatDuration(ms)).toBe(expected);
});

it.each([
  [0, 1],
  [7, 10],
  [12, 20],
  [21, 25],
  [480, 500],
  [182_000, 200_000],
])('rounds %d up to %d', (value, expected) => {
  expect(niceCeiling(value)).toBe(expected);
});
