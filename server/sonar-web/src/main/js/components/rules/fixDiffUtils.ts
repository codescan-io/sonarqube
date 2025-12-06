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

export function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

export function mergeSnippetIntoSource(
  originalSource: string,
  fixSnippet: string,
  snippetStartLine: number,
  snippetEndLine: number,
): string {
  const originalLines = splitLines(originalSource);
  const replacementLines = splitLines(fixSnippet);
  const startIndex = Math.max(snippetStartLine - 1, 0);
  const endIndex = Math.max(snippetEndLine - 1, startIndex - 1);

  return [
    ...originalLines.slice(0, startIndex),
    ...replacementLines,
    ...originalLines.slice(Math.min(endIndex + 1, originalLines.length)),
  ].join('\n');
}

export function splitLines(content: string): string[] {
  if (content === '') {
    return [];
  }
  return content.replace(/\r/g, '').split('\n');
}

export function determineSnippetLineCount(snippet: string | undefined): number {
  if (!snippet) {
    return 1;
  }
  return splitLines(snippet).length || 1;
}

