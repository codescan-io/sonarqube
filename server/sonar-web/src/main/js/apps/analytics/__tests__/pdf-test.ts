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

import { collectBreaks, paginate } from '../pdf';

describe('paginate', () => {
  it('keeps a section that fits on one page', () => {
    expect(paginate(500, 1000, [100, 200])).toEqual([{ start: 0, end: 500 }]);
  });

  it('ends each page at the last break that fits', () => {
    expect(paginate(2500, 1000, [300, 900, 1200, 1800, 2100])).toEqual([
      { start: 0, end: 900 },
      { start: 900, end: 1800 },
      { start: 1800, end: 2500 },
    ]);
  });

  it('cuts a block taller than a page where the page ends', () => {
    expect(paginate(2500, 1000, [2400])).toEqual([
      { start: 0, end: 1000 },
      { start: 1000, end: 2000 },
      { start: 2000, end: 2500 },
    ]);
  });

  it('fits what it can below what sits on the first page', () => {
    expect(paginate(1500, 1000, [200, 700], 400)).toEqual([
      { start: 0, end: 200 },
      { start: 200, end: 700 },
      { start: 700, end: 1500 },
    ]);
  });

  it('starts on the next page when nothing fits below what sits on the first page', () => {
    expect(paginate(1500, 1000, [600, 1100], 400)).toEqual([
      { start: 0, end: 0 },
      { start: 0, end: 600 },
      { start: 600, end: 1500 },
    ]);
  });
});

describe('collectBreaks', () => {
  function element(top: number, height: number, children: HTMLElement[] = []) {
    const node = document.createElement('div');
    children.forEach((child) => node.appendChild(child));
    node.getBoundingClientRect = () => ({ top, bottom: top + height, height }) as DOMRect;
    return node;
  }

  it('breaks around blocks that fit on a page, and inside the ones that do not', () => {
    const root = element(100, 3000, [
      element(100, 400),
      element(500, 2000, [element(500, 600), element(1100, 1400)]),
      element(2500, 0),
    ]);

    expect(collectBreaks(root, 1000)).toEqual([0, 400, 400, 1000, 1000, 2400]);
  });
});
