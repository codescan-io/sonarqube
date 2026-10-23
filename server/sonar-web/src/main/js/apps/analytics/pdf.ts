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

/** A4 portrait, in millimetres. */
const PAGE = { width: 210, height: 297 };
const MARGIN = { side: 12, top: 20, bottom: 16 };
const CONTENT_WIDTH = PAGE.width - 2 * MARGIN.side;
const CONTENT_HEIGHT = PAGE.height - MARGIN.top - MARGIN.bottom;
// Large enough for crisp text, small enough for the browser's canvas size limits on long sections.
const MAX_PIXEL_RATIO = 2;
const MAX_CANVAS_HEIGHT = 16_000;
const JPEG_QUALITY = 0.92;
const COVER_GAP = 6;

export interface PageSlice {
  end: number;
  start: number;
}

/**
 * Splits a height into pages, each ending at the last break that fits so that no card, chart or table row is cut in two;
 * a block taller than a page is cut where the page ends. The first page can be shorter, when something sits above it.
 */
export function paginate(
  height: number,
  pageHeight: number,
  breaks: number[],
  firstPageHeight = pageHeight,
): PageSlice[] {
  const sorted = [...new Set(breaks)].filter((b) => b > 0 && b < height).sort((a, b) => a - b);
  const lastBreakWithin = (from: number, to: number) =>
    sorted.filter((b) => b > from && b <= to).pop();

  const slices: PageSlice[] = [];
  let room = firstPageHeight;
  // Nothing fits below what sits on the first page: leave it to that, and start on the next page.
  if (
    firstPageHeight < pageHeight &&
    height > firstPageHeight &&
    lastBreakWithin(0, firstPageHeight) === undefined
  ) {
    slices.push({ start: 0, end: 0 });
    room = pageHeight;
  }
  let start = 0;
  while (start < height) {
    const limit = start + room;
    const end = limit >= height ? height : (lastBreakWithin(start, limit) ?? limit);
    slices.push({ start, end });
    start = end;
    room = pageHeight;
  }
  return slices;
}

/**
 * Where a page may end inside an element: the top and bottom of every block that fits on a page, looking inside the
 * blocks that do not, down to table rows. Offsets are in CSS pixels from the top of the element.
 */
export function collectBreaks(root: HTMLElement, pageHeight: number): number[] {
  const top = root.getBoundingClientRect().top;
  const breaks: number[] = [];
  const visit = (element: Element) => {
    const rect = element.getBoundingClientRect();
    if (rect.height === 0) {
      return;
    }
    if (rect.height <= pageHeight || element.children.length === 0) {
      breaks.push(rect.top - top, rect.bottom - top);
      return;
    }
    Array.from(element.children).forEach(visit);
  };
  Array.from(root.children).forEach(visit);
  return breaks;
}

export interface PdfReport {
  /** Starts the first page, above the first section. */
  cover: HTMLElement;
  fileName: string;
  /** Printed at the top of every page. */
  header: string;
  onProgress?: (done: number, total: number) => void;
  /** Footer of every page, from its number and the page count. */
  pageLabel: (page: number, pages: number) => string;
  /** Each starts a new page. */
  sections: HTMLElement[];
}

/** Captures the report as the browser draws it and saves it as an A4 PDF, page breaks between blocks. */
export async function downloadPdf(report: PdfReport) {
  const [{ jsPDF }, { toCanvas }] = await Promise.all([import('jspdf'), import('html-to-image')]);
  const pdf = new jsPDF({ format: 'a4', orientation: 'portrait', unit: 'mm' });
  const total = report.sections.length + 1;

  const capture = async (element: HTMLElement) => {
    const pixelRatio = Math.min(
      MAX_PIXEL_RATIO,
      MAX_CANVAS_HEIGHT / Math.max(1, element.offsetHeight),
    );
    const canvas = await toCanvas(element, { backgroundColor: '#ffffff', pixelRatio });
    return { canvas, scale: canvas.width / element.offsetWidth };
  };

  const place = (
    canvas: HTMLCanvasElement,
    scale: number,
    slice: PageSlice,
    top: number,
    pxPerMm: number,
  ) => {
    const piece = document.createElement('canvas');
    piece.width = canvas.width;
    piece.height = Math.max(1, Math.round((slice.end - slice.start) * scale));
    piece
      .getContext('2d')
      ?.drawImage(
        canvas,
        0,
        Math.round(slice.start * scale),
        canvas.width,
        piece.height,
        0,
        0,
        canvas.width,
        piece.height,
      );
    pdf.addImage(
      piece.toDataURL('image/jpeg', JPEG_QUALITY),
      'JPEG',
      MARGIN.side,
      top,
      CONTENT_WIDTH,
      (slice.end - slice.start) / pxPerMm,
    );
  };

  // The cover opens the first page, and the first section follows right below it.
  const cover = await capture(report.cover);
  const coverPxPerMm = report.cover.offsetWidth / CONTENT_WIDTH;
  const coverHeight = report.cover.offsetHeight / coverPxPerMm + COVER_GAP;
  place(
    cover.canvas,
    cover.scale,
    { start: 0, end: report.cover.offsetHeight },
    MARGIN.top,
    coverPxPerMm,
  );
  report.onProgress?.(1, total);

  for (const [index, section] of report.sections.entries()) {
    const pxPerMm = section.offsetWidth / CONTENT_WIDTH;
    const pageHeight = CONTENT_HEIGHT * pxPerMm;
    const belowCover = index === 0;
    const slices = paginate(
      section.offsetHeight,
      pageHeight,
      collectBreaks(section, pageHeight),
      belowCover ? (CONTENT_HEIGHT - coverHeight) * pxPerMm : pageHeight,
    );
    // eslint-disable-next-line no-await-in-loop -- one section at a time keeps memory bounded
    const { canvas, scale } = await capture(section);
    slices.forEach((slice, i) => {
      const onCoverPage = belowCover && i === 0;
      if (!onCoverPage) {
        pdf.addPage();
      }
      if (slice.end > slice.start) {
        place(canvas, scale, slice, onCoverPage ? MARGIN.top + coverHeight : MARGIN.top, pxPerMm);
      }
    });
    report.onProgress?.(index + 2, total);
  }

  const pages = pdf.getNumberOfPages();
  for (let page = 1; page <= pages; page++) {
    pdf.setPage(page);
    pdf.setFontSize(8);
    pdf.setTextColor(110);
    pdf.text(report.header, MARGIN.side, 10);
    pdf.text(report.pageLabel(page, pages), PAGE.width - MARGIN.side, PAGE.height - 8, {
      align: 'right',
    });
  }
  pdf.save(report.fileName);
}
