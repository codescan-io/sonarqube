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

import { AnalyticsPaging } from '../../api/analytics';

export type CsvValue = string | number | boolean | null | undefined;

/** A table to export: column titles, then one array of values per row. */
export interface CsvTable {
  header: string[];
  rows: CsvValue[][];
}

/**
 * Cells a spreadsheet would run as a formula. Exported data holds user text (comments, file paths), so such cells are
 * prefixed with a quote to be read as text.
 */
const FORMULA_START = /^[=+\-@\t\r]/;

export function escapeCsv(value: CsvValue): string {
  if (value === null || value === undefined) {
    return '';
  }
  let text = String(value);
  if (typeof value === 'string' && FORMULA_START.test(text)) {
    text = `'${text}`;
  }
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** RFC 4180 text, with CRLF line ends. */
export function toCsv({ header, rows }: CsvTable): string {
  return [header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n');
}

/** Saves the table as a CSV file; the byte order mark makes spreadsheets read it as UTF-8. */
export function downloadCsv(fileName: string, table: CsvTable) {
  const blob = new Blob(['﻿', toCsv(table)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName.endsWith('.csv') ? fileName : `${fileName}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Past this many rows an export stops: enough for any review, and the browser stays responsive. */
export const MAX_EXPORT_ROWS = 10_000;
const EXPORT_PAGE_SIZE = 100;

/**
 * Every item of a paged list, page after page, up to {@link MAX_EXPORT_ROWS}. {@code truncated} tells whether some were
 * left out.
 */
export async function fetchAllPages<T>(
  fetchPage: (p: number, ps: number) => Promise<{ items: T[]; paging: AnalyticsPaging }>,
  max = MAX_EXPORT_ROWS,
): Promise<{ items: T[]; truncated: boolean }> {
  const items: T[] = [];
  let total = Infinity;
  for (let p = 1; items.length < Math.min(total, max); p++) {
    // eslint-disable-next-line no-await-in-loop -- pages come one after the other
    const page = await fetchPage(p, EXPORT_PAGE_SIZE);
    total = page.paging.total;
    items.push(...page.items);
    if (page.items.length < EXPORT_PAGE_SIZE) {
      break;
    }
  }
  return { items: items.slice(0, max), truncated: total > max };
}

/** A file name for an export: the report, the organization and today, e.g. "exceptions-acme-2026-11-03.csv". */
export function exportFileName(report: string, organization: string, now = new Date()) {
  const day = [now.getFullYear(), now.getMonth() + 1, now.getDate()]
    .map((n) => String(n).padStart(2, '0'))
    .join('-');
  return `${report}-${organization}-${day}.csv`;
}
