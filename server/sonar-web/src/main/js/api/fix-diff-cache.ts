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

import { get, remove, save } from '../helpers/storage';
import { FixDiffResponse } from './fix-diff';
import { IssueContextResponse } from './issues';

const FIX_DIFF_CACHE_PREFIX = 'fix-diff-cache';
const ISSUE_CONTEXT_CACHE_PREFIX = 'issue-context-cache';
const ORIGINAL_SOURCE_CACHE_PREFIX = 'original-source-cache';
const MERGED_SOURCE_CACHE_PREFIX = 'merged-source-cache';

function getCacheKey(prefix: string, issueKey: string): string {
  return `${prefix}.${issueKey}`;
}

export interface FixDiffCacheData {
  issueContext: IssueContextResponse;
  fixDiffResponse: FixDiffResponse;
  originalSource?: string;
  mergedSource?: string;
}

export function saveFixDiffCache(
  issueKey: string,
  issueContext: IssueContextResponse,
  fixDiffResponse: FixDiffResponse,
  originalSource?: string,
  mergedSource?: string,
): void {
  try {
    const contextKey = getCacheKey(ISSUE_CONTEXT_CACHE_PREFIX, issueKey);
    const fixDiffKey = getCacheKey(FIX_DIFF_CACHE_PREFIX, issueKey);
    const originalSourceKey = getCacheKey(ORIGINAL_SOURCE_CACHE_PREFIX, issueKey);
    const mergedSourceKey = getCacheKey(MERGED_SOURCE_CACHE_PREFIX, issueKey);
    
    save(contextKey, JSON.stringify(issueContext));
    save(fixDiffKey, JSON.stringify(fixDiffResponse));
    
    if (originalSource) {
      save(originalSourceKey, originalSource);
    }
    
    if (mergedSource) {
      save(mergedSourceKey, mergedSource);
    }
  } catch (error) {
    console.error('Failed to save fix diff cache:', error);
  }
}

export function getFixDiffCache(issueKey: string): {
  issueContext: IssueContextResponse | null;
  fixDiffResponse: FixDiffResponse | null;
  originalSource: string | null;
  mergedSource: string | null;
} {
  try {
    const contextKey = getCacheKey(ISSUE_CONTEXT_CACHE_PREFIX, issueKey);
    const fixDiffKey = getCacheKey(FIX_DIFF_CACHE_PREFIX, issueKey);
    const originalSourceKey = getCacheKey(ORIGINAL_SOURCE_CACHE_PREFIX, issueKey);
    const mergedSourceKey = getCacheKey(MERGED_SOURCE_CACHE_PREFIX, issueKey);
    
    const contextData = get(contextKey);
    const fixDiffData = get(fixDiffKey);
    const originalSourceData = get(originalSourceKey);
    const mergedSourceData = get(mergedSourceKey);
    
    return {
      issueContext: contextData ? JSON.parse(contextData as string) : null,
      fixDiffResponse: fixDiffData ? JSON.parse(fixDiffData as string) : null,
      originalSource: originalSourceData as string | null,
      mergedSource: mergedSourceData as string | null,
    };
  } catch (error) {
    console.error('Failed to get fix diff cache:', error);
    return {
      issueContext: null,
      fixDiffResponse: null,
      originalSource: null,
      mergedSource: null,
    };
  }
}

export function clearFixDiffCache(issueKey: string): void {
  const contextKey = getCacheKey(ISSUE_CONTEXT_CACHE_PREFIX, issueKey);
  const fixDiffKey = getCacheKey(FIX_DIFF_CACHE_PREFIX, issueKey);
  const originalSourceKey = getCacheKey(ORIGINAL_SOURCE_CACHE_PREFIX, issueKey);
  const mergedSourceKey = getCacheKey(MERGED_SOURCE_CACHE_PREFIX, issueKey);
  
  remove(contextKey);
  remove(fixDiffKey);
  remove(originalSourceKey);
  remove(mergedSourceKey);
}

export function hasFixDiffCache(issueKey: string): boolean {
  const fixDiffKey = getCacheKey(FIX_DIFF_CACHE_PREFIX, issueKey);
  return get(fixDiffKey) !== null;
}

