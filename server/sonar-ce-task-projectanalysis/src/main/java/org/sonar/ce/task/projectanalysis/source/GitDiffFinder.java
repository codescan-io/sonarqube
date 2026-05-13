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
package org.sonar.ce.task.projectanalysis.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitDiffFinder {

    private static final Logger LOG = LoggerFactory.getLogger(GitDiffFinder.class);

    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    int[] findMatchingLines(List<String> previousVersionLines, List<String> currentVersionLines)
            throws IOException, InterruptedException {

        int totalReportLines = currentVersionLines.size();
        int totalDbLines = previousVersionLines.size();

        int[] matchingLineArray = new int[totalReportLines];

        if (previousVersionLines.isEmpty() || currentVersionLines.isEmpty()) {
            return matchingLineArray;
        }

        Path diffWorkingDirectory = null;
        try {
            diffWorkingDirectory = Files.createTempDirectory("sonar-histogram-diff-");
            Path previousVersionFile = diffWorkingDirectory.resolve("previous.txt");
            Path currentVersionFile = diffWorkingDirectory.resolve("current.txt");

            Files.write(previousVersionFile, previousVersionLines, StandardCharsets.UTF_8);
            Files.write(currentVersionFile, currentVersionLines, StandardCharsets.UTF_8);

            executeDiffAndParseOutput(previousVersionFile, currentVersionFile, matchingLineArray, totalDbLines,
                    totalReportLines);

        } finally {
            deleteTempFilesAndDirectory(diffWorkingDirectory);
        }

        return matchingLineArray;
    }

    private void executeDiffAndParseOutput(Path previousVersionFile, Path currentVersionFile, int[] matchingLineArray,
            int totalDbLines, int totalReportLines) throws IOException, InterruptedException {

        ProcessBuilder gitDiffProcess = new ProcessBuilder("git", "diff", "--no-index", "--no-color", "--histogram",
                previousVersionFile.toAbsolutePath().toString(), currentVersionFile.toAbsolutePath().toString());
        gitDiffProcess.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process runningProcess = gitDiffProcess.start();

        try (BufferedReader diffOutputReader = new BufferedReader(
                new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
            parseUnifiedDiffOutput(diffOutputReader, matchingLineArray, totalDbLines, totalReportLines);
        }

        int processExitCode = runningProcess.waitFor();
        if (processExitCode > 1) {
            throw new IOException("git diff --no-index failed with exit code " + processExitCode);
        }
    }

    private static void parseUnifiedDiffOutput(BufferedReader diffOutputReader, int[] matchingLineArray,
            int totalDbLines, int totalReportLines) throws IOException {

        int currentDbLinePosition = 1;
        int currentReportLinePosition = 1;

        String rawDiffLine;
        boolean insideHunk = false;

        int hunkStartInPreviousFile = 0;
        int hunkLengthInPreviousFile = 0;
        int hunkStartInCurrentFile = 0;
        int hunkLengthInCurrentFile = 0;

        while ((rawDiffLine = diffOutputReader.readLine()) != null) {
            Matcher hunkHeaderMatcher = HUNK_HEADER_PATTERN.matcher(rawDiffLine);

            if (hunkHeaderMatcher.find()) {
                if (insideHunk) {
                    currentDbLinePosition = hunkStartInPreviousFile + hunkLengthInPreviousFile;
                    currentReportLinePosition = hunkStartInCurrentFile + hunkLengthInCurrentFile;
                }

                hunkStartInPreviousFile = Integer.parseInt(hunkHeaderMatcher.group(1));
                hunkLengthInPreviousFile =
                        hunkHeaderMatcher.group(2) != null ? Integer.parseInt(hunkHeaderMatcher.group(2)) : 1;
                hunkStartInCurrentFile = Integer.parseInt(hunkHeaderMatcher.group(3));
                hunkLengthInCurrentFile =
                        hunkHeaderMatcher.group(4) != null ? Integer.parseInt(hunkHeaderMatcher.group(4)) : 1;

                fillIdenticalLinesBetweenHunks(matchingLineArray, currentDbLinePosition, currentReportLinePosition,
                        hunkStartInPreviousFile, hunkStartInCurrentFile);

                currentDbLinePosition = hunkStartInPreviousFile;
                currentReportLinePosition = hunkStartInCurrentFile;
                insideHunk = true;

            } else if (insideHunk && !rawDiffLine.isEmpty()) {
                char lineTypePrefix = rawDiffLine.charAt(0);

                if (lineTypePrefix == ' ') {
                    int reportArrayIndex = currentReportLinePosition - 1;
                    if (reportArrayIndex >= 0 && reportArrayIndex < matchingLineArray.length) {
                        matchingLineArray[reportArrayIndex] = currentDbLinePosition;
                    }
                    currentDbLinePosition++;
                    currentReportLinePosition++;

                } else if (lineTypePrefix == '+') {
                    currentReportLinePosition++;

                } else if (lineTypePrefix == '-') {
                    currentDbLinePosition++;
                }
            }
        }

        if (insideHunk) {
            currentDbLinePosition = hunkStartInPreviousFile + hunkLengthInPreviousFile;
            currentReportLinePosition = hunkStartInCurrentFile + hunkLengthInCurrentFile;
        }
        fillIdenticalLinesBetweenHunks(matchingLineArray, currentDbLinePosition, currentReportLinePosition,
                totalDbLines + 1, totalReportLines + 1);
    }

    private static void fillIdenticalLinesBetweenHunks(int[] matchingLineArray, int fromDbLine, int fromReportLine,
            int untilDbLine, int untilReportLine) {

        int dbLinePointer = fromDbLine;
        int reportLinePointer = fromReportLine;

        while (dbLinePointer < untilDbLine && reportLinePointer < untilReportLine) {
            int reportArrayIndex = reportLinePointer - 1;
            if (reportArrayIndex >= 0 && reportArrayIndex < matchingLineArray.length) {
                matchingLineArray[reportArrayIndex] = dbLinePointer;
            }
            dbLinePointer++;
            reportLinePointer++;
        }
    }

    private static void deleteTempFilesAndDirectory(Path diffWorkingDirectory) {
        if (diffWorkingDirectory == null) {
            return;
        }
        try {
            Files.deleteIfExists(diffWorkingDirectory.resolve("previous.txt"));
            Files.deleteIfExists(diffWorkingDirectory.resolve("current.txt"));
            Files.deleteIfExists(diffWorkingDirectory);
        } catch (IOException cleanupException) {
            LOG.warn("Git Diff Temp file cleanup failed for directory '{}'. "
                            + "Reason: {}. Non-fatal — OS will reclaim on reboot.", diffWorkingDirectory,
                    cleanupException.getMessage());
        }
    }
}
