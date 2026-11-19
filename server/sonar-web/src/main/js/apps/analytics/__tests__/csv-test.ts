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

import { escapeCsv, exportFileName, fetchAllPages, toCsv } from '../csv';

describe('escapeCsv', () => {
  it('quotes values with separators, quotes or line breaks', () => {
    expect(escapeCsv('plain')).toBe('plain');
    expect(escapeCsv('a,b')).toBe('"a,b"');
    expect(escapeCsv('say "hi"')).toBe('"say ""hi"""');
    expect(escapeCsv('two\nlines')).toBe('"two\nlines"');
  });

  it('writes nothing for missing values and keeps numbers and booleans', () => {
    expect(escapeCsv(undefined)).toBe('');
    expect(escapeCsv(null)).toBe('');
    expect(escapeCsv(-3)).toBe('-3');
    expect(escapeCsv(true)).toBe('true');
  });

  it('keeps user text from running as a spreadsheet formula', () => {
    expect(escapeCsv('=HYPERLINK("x")')).toBe('"\'=HYPERLINK(""x"")"');
    expect(escapeCsv('+1')).toBe("'+1");
    expect(escapeCsv('@SUM(A1)')).toBe("'@SUM(A1)");
  });
});

it('writes a header and rows with CRLF line ends', () => {
  expect(
    toCsv({
      header: ['Rule', 'Count'],
      rows: [
        ['apex:A', 3],
        ['apex:B', undefined],
      ],
    }),
  ).toBe('Rule,Count\r\napex:A,3\r\napex:B,');
});

it('names exports after the report, the organization and the day', () => {
  expect(exportFileName('exceptions', 'acme', new Date(2026, 10, 3))).toBe(
    'exceptions-acme-2026-11-03.csv',
  );
});

describe('fetchAllPages', () => {
  const pages = (total: number) =>
    jest.fn((p: number, ps: number) => {
      const start = (p - 1) * ps;
      const items = Array.from(
        { length: Math.max(0, Math.min(ps, total - start)) },
        (_, i) => start + i,
      );
      return Promise.resolve({ items, paging: { pageIndex: p, pageSize: ps, total } });
    });

  it('reads every page', async () => {
    const fetchPage = pages(250);

    const result = await fetchAllPages(fetchPage);

    expect(result.items).toHaveLength(250);
    expect(result.truncated).toBe(false);
    expect(fetchPage).toHaveBeenCalledTimes(3);
  });

  it('stops at the maximum', async () => {
    const result = await fetchAllPages(pages(500), 150);

    expect(result.items).toHaveLength(150);
    expect(result.truncated).toBe(true);
  });
});
