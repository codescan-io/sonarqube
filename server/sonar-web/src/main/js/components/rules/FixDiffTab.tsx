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

import { useQuery } from '@tanstack/react-query';
import * as React from 'react';
import { FlagMessage, Spinner } from '~design-system';
import { requestFixDiff } from '../../api/fix-diff';
import { getIssueContext } from '../../api/issues';
import { getBranchLikeDisplayName } from '../../helpers/branch-like';
import { translate } from '../../helpers/l10n';
import { useRawSourceQuery } from '../../queries/sources';
import { getBranchLikeQuery } from '../../sonar-aligned/helpers/branch-like';
import { BranchLike } from '../../types/branch-like';
import { Issue, SourceViewerFile } from '../../types/types';
import { FixDiffHeader } from './FixDiffHeader';
import { FixDiffTable } from './FixDiffTable';
import { DiffSourceLine } from './fixDiffTypes';
import { determineSnippetLineCount, mergeSnippetIntoSource } from './fixDiffUtils';
import { useFixDiffSnippetRange } from './useFixDiffSnippetRange';
import { useFixDiffSourceLines } from './useFixDiffSourceLines';

interface FixDiffTabProps {
  branchLike?: BranchLike;
  issue: Issue;
}

const EXPAND_BY_LINES = 50;

export default function FixDiffTab({ branchLike, issue }: Readonly<FixDiffTabProps>) {
  const branchParams = React.useMemo(() => getBranchLikeQuery(branchLike), [branchLike]);
  const branchKey = 'branch' in branchParams ? (branchParams.branch ?? '') : '';
  const pullRequestKey = 'pullRequest' in branchParams ? (branchParams.pullRequest ?? '') : '';

  const {
    data: originalSource,
    isLoading: isSourceLoading,
    isError: isSourceError,
  } = useRawSourceQuery({ ...branchParams, key: issue.component }, { enabled: !!issue.component });

  const issueContextQuery = useQuery({
    queryKey: ['issue-context', issue.key],
    queryFn: () => getIssueContext({ issue: issue.key, componentKey: issue.component }),
    enabled: Boolean(issue.component),
  });

  const fixDiffQuery = useQuery({
    queryKey: [
      'fix-diff',
      issue.key,
      branchKey,
      pullRequestKey,
      originalSource?.length ?? 0,
      issueContextQuery.data?.sourceSnippetStartLine ??
        issueContextQuery.data?.snippetViolationLine ??
        0,
      issueContextQuery.data?.sourceSnippetEndLine ?? 0,
    ],
    queryFn: () =>
      requestFixDiff({
        componentKey: issue.component,
        issueContext: issueContextQuery.data!,
        issueKey: issue.key,
        originalSource: originalSource!,
      }),
    enabled: Boolean(originalSource && issueContextQuery.data),
  });

  const issueContext = issueContextQuery.data;
  const fixSnippet = fixDiffQuery.data?.details?.file_changes?.file_changes?.[0]?.content;

  const mergedSource = React.useMemo(() => {
    if (!originalSource || !issueContext || !fixSnippet) {
      return undefined;
    }

    const snippetStart = issueContext.sourceSnippetStartLine ?? issueContext.snippetViolationLine;
    const snippetEnd =
      issueContext.sourceSnippetEndLine ??
      snippetStart + determineSnippetLineCount(issueContext.codesnippet) - 1;

    return mergeSnippetIntoSource(originalSource, fixSnippet, snippetStart, snippetEnd);
  }, [originalSource, issueContext, fixSnippet]);

  const hasChanges = React.useMemo(() => {
    if (!originalSource || !mergedSource) return false;
    // Check if there are any differences
    return originalSource !== mergedSource;
  }, [originalSource, mergedSource]);

  // Get language from file extension
  const language = React.useMemo(() => {
    if (!issue.component) {
      return 'plaintext';
    }
    const extension = issue.component.split('.').pop()?.toLowerCase() || '';
    const languageMap: { [key: string]: string } = {
      java: 'java',
      js: 'javascript',
      jsx: 'javascript',
      ts: 'typescript',
      tsx: 'typescript',
      py: 'python',
      cs: 'csharp',
      cpp: 'cpp',
      cc: 'cpp',
      cxx: 'cpp',
      c: 'c',
      go: 'go',
      kt: 'kotlin',
      swift: 'swift',
      php: 'php',
      rb: 'ruby',
      scala: 'scala',
      xml: 'xml',
      json: 'json',
      yaml: 'yaml',
      yml: 'yaml',
      sql: 'sql',
      cls: 'apex',
    };
    return languageMap[extension] || 'plaintext';
  }, [issue.component]);

  // Generate source lines from diff
  const sourceLines = useFixDiffSourceLines({
    originalSource,
    mergedSource,
    language,
  });

  // Calculate snippet range
  const { snippetRange, setSnippetRange, minLineNumber, maxLineNumber } = useFixDiffSnippetRange({
    sourceLines,
    issueContext,
  });

  // Filter source lines to show only the snippet
  const displayedLines = React.useMemo(() => {
    return sourceLines.filter((line) => {
      const diffLine = line as DiffSourceLine;
      const originalNum = diffLine.originalLineNumber;
      const modifiedNum = diffLine.modifiedLineNumber;

      // Check if original line number is in range (for removed/unchanged lines)
      const originalInRange =
        originalNum !== undefined &&
        originalNum >= snippetRange.start &&
        originalNum <= snippetRange.end;

      // Check if modified line number is in range (for added/unchanged lines)
      const modifiedInRange =
        modifiedNum !== undefined &&
        modifiedNum >= snippetRange.start &&
        modifiedNum <= snippetRange.end;

      // Include if either original or modified line number is in range
      return originalInRange || modifiedInRange;
    });
  }, [sourceLines, snippetRange]);

  // Get the first and last line numbers from displayed lines (similar to SnippetViewer)
  // Use the actual line numbers from the diff (originalLineNumber or modifiedLineNumber)
  const firstDisplayedLineNumber = React.useMemo(() => {
    if (displayedLines.length === 0) return 0;
    const firstLine = displayedLines[0] as DiffSourceLine;
    // Use originalLineNumber if available (for removed/unchanged), otherwise modifiedLineNumber
    return firstLine.originalLineNumber ?? firstLine.modifiedLineNumber ?? firstLine.line ?? 0;
  }, [displayedLines]);

  const lastDisplayedLineNumber = React.useMemo(() => {
    if (displayedLines.length === 0) return 0;
    const lastLine = displayedLines[displayedLines.length - 1] as DiffSourceLine;
    // Use modifiedLineNumber if available (for added/unchanged), otherwise originalLineNumber
    return lastLine.modifiedLineNumber ?? lastLine.originalLineNumber ?? lastLine.line ?? 0;
  }, [displayedLines]);

  // Expand functions
  const expandUp = React.useCallback(() => {
    setSnippetRange((current) => ({
      start: Math.max(minLineNumber, current.start - EXPAND_BY_LINES),
      end: current.end,
    }));
  }, [setSnippetRange, minLineNumber]);

  const expandDown = React.useCallback(() => {
    setSnippetRange((current) => ({
      start: current.start,
      end: Math.min(maxLineNumber, current.end + EXPAND_BY_LINES),
    }));
  }, [setSnippetRange, maxLineNumber]);

  // Get file path from issue componentLongName (just the path, not including project)
  const filePath = issue.componentLongName || issue.component || '';
  const branchDisplayName = branchLike ? getBranchLikeDisplayName(branchLike) : 'MainAnalysis';

  if (isSourceLoading || issueContextQuery.isLoading || fixDiffQuery.isLoading) {
    return (
      <div className="sw-flex sw-justify-center sw-py-8">
        <Spinner ariaLabel={translate('code_viewer.loading')} />
      </div>
    );
  }

  if (isSourceError || issueContextQuery.isError || fixDiffQuery.isError) {
    return (
      <FlagMessage variant="warning">
        {translate('issues.code_fix.not_able_to_generate_fix')}
      </FlagMessage>
    );
  }

  if (!originalSource || !mergedSource || !hasChanges) {
    return <FlagMessage variant="info">{translate('issue.tabs.fix_diff.empty')}</FlagMessage>;
  }

  return (
    <div className="sw-flex sw-flex-col sw-gap-0">
      <FixDiffHeader
        filePath={filePath}
        branchDisplayName={branchDisplayName}
        projectKey={issue.projectKey}
        projectName={issue.projectName}
        branchLike={branchLike}
      />
      <FixDiffTable
        branchLike={branchLike}
        displayedLines={displayedLines}
        file={{
          key: issue.component,
          longName: issue.component,
          measures: {
            lines: String(sourceLines.length),
          },
        } as SourceViewerFile}
        firstDisplayedLineNumber={firstDisplayedLineNumber}
        lastDisplayedLineNumber={lastDisplayedLineNumber}
        minLineNumber={minLineNumber}
        maxLineNumber={maxLineNumber}
        onExpandUp={expandUp}
        onExpandDown={expandDown}
      />
    </div>
  );
}
