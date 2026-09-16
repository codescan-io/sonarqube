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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.TempFolder;

/**
 * Computes line-level mapping between the previous (DB) and current (report) version of a file by shelling out to
 * {@code git diff --no-index --diff-algorithm=myers --indent-heuristic} and parsing its unified-diff output. Myers
 * (with the indent heuristic) is git's default algorithm and is what GitHub renders, so this matches the diff users see
 * in a GitHub pull request.
 * <p>
 * Returned array is indexed by current-file line (0-based): matchingLineArray[i] = the previous-file line number
 * (1-based) that current line i+1 corresponds to, or 0 if the current line was added.
 */
class GitDiffFinder {

    private static final Logger LOG = LoggerFactory.getLogger(GitDiffFinder.class);

    // Matches a unified-diff hunk header, e.g.  "@@ -12,5 +14,7 @@"
    //   group 1 = previous-file start line
    //   group 2 = previous-file line count (optional; defaults to 1 when omitted)
    //   group 3 = current-file  start line
    //   group 4 = current-file  line count  (optional; defaults to 1 when omitted)
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    private static final long GIT_DIFF_TIMEOUT_MINUTES = 1;

    private static final long GIT_VERSION_TIMEOUT_SECONDS = 10;

    private final TempFolder tempFolder;

    GitDiffFinder(TempFolder tempFolder) {
        this.tempFolder = tempFolder;
    }

    int[] findMatchingLines(List<String> previousVersionLines, List<String> currentVersionLines)
            throws IOException, InterruptedException {

        int totalReportLines = currentVersionLines.size();
        int totalDbLines = previousVersionLines.size();

        int[] matchingLineArray = new int[totalReportLines];

        if (previousVersionLines.isEmpty() || currentVersionLines.isEmpty()) {
            return matchingLineArray;
        }

        if (!isGitAvailable()) {
            throw new IOException("'git' is not usable (not found in PATH, or it did not respond). "
                    + "It is required for Diff generation b/w files");
        }

        Path diffWorkingDirectory = null;
        try {
            diffWorkingDirectory = tempFolder.newDir().toPath();
            Path previousVersionFile = diffWorkingDirectory.resolve("previous.txt");
            Path currentVersionFile = diffWorkingDirectory.resolve("current.txt");
            Path diffOutputFile = diffWorkingDirectory.resolve("diff.out");

            Files.write(previousVersionFile, previousVersionLines, StandardCharsets.UTF_8);
            Files.write(currentVersionFile, currentVersionLines, StandardCharsets.UTF_8);

            executeDiffAndParseOutput(previousVersionFile, currentVersionFile, diffOutputFile, matchingLineArray,
                    totalDbLines, totalReportLines);

        } finally {
            deleteTempFilesAndDirectory(diffWorkingDirectory);
        }

        return matchingLineArray;
    }

    private boolean isGitAvailable() {
        Process versionProcess = null;
        try {
            ProcessBuilder versionProbe = new ProcessBuilder("git", "--version");
            versionProbe.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            versionProbe.redirectError(ProcessBuilder.Redirect.DISCARD);

            versionProcess = versionProbe.start();
            if (!versionProcess.waitFor(GIT_VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("'git --version' did not complete within {} second(s); treating git as unavailable",
                        GIT_VERSION_TIMEOUT_SECONDS);
                return false;
            }
            return versionProcess.exitValue() == 0;

        } catch (IOException e) {
            LOG.debug("Unable to execute 'git --version'; treating git as unavailable", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (versionProcess != null && versionProcess.isAlive()) {
                versionProcess.destroyForcibly();
            }
        }
    }

    private void executeDiffAndParseOutput(Path previousVersionFile, Path currentVersionFile, Path diffOutputFile,
            int[] matchingLineArray, int totalDbLines, int totalReportLines) throws IOException, InterruptedException {

        ProcessBuilder gitDiffProcess = getProcessBuilder(previousVersionFile, currentVersionFile, diffOutputFile);
        gitDiffProcess.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process runningProcess = gitDiffProcess.start();
        try {
            if (!runningProcess.waitFor(GIT_DIFF_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IOException("git diff --no-index timed out after " + GIT_DIFF_TIMEOUT_MINUTES
                        + " minutes, b/w files diff generation failed");
            }

            int processExitCode = runningProcess.exitValue();
            if (processExitCode > 1) {
                throw new IOException("git diff --no-index failed with exit code " + processExitCode);
            }

            boolean anyHunkParsed;
            try (BufferedReader diffOutputReader = Files.newBufferedReader(diffOutputFile, StandardCharsets.UTF_8)) {
                anyHunkParsed = parseUnifiedDiffOutput(diffOutputReader, matchingLineArray, totalDbLines,
                        totalReportLines);
            }

            if (processExitCode == 1 && !anyHunkParsed) {
                throw new IOException("git diff --no-index reported differences but produced no unified-diff hunks; "
                        + "b/w files diff generation failed");
            }

        } finally {
            if (runningProcess.isAlive()) {
                runningProcess.destroyForcibly();
            }
        }
    }

    @NotNull
    private static ProcessBuilder getProcessBuilder(Path previousVersionFile, Path currentVersionFile,
            Path diffOutputFile) {

        ProcessBuilder gitDiffProcess = new ProcessBuilder("git", "diff", "--no-index", "--no-color",
                "--diff-algorithm=myers", "--indent-heuristic", previousVersionFile.toAbsolutePath().toString(),
                currentVersionFile.toAbsolutePath().toString());

        gitDiffProcess.redirectOutput(diffOutputFile.toFile());
        return gitDiffProcess;
    }

    private static boolean parseUnifiedDiffOutput(BufferedReader diffOutputReader, int[] matchingLineArray,
            int totalDbLines, int totalReportLines) throws IOException {

        UnifiedDiffParser parser = new UnifiedDiffParser(matchingLineArray);

        String rawDiffLine;
        while ((rawDiffLine = diffOutputReader.readLine()) != null) {
            parser.consume(rawDiffLine);
        }

        return parser.finish(totalDbLines, totalReportLines);
    }

    private static final class UnifiedDiffParser {

        private final int[] matchingLineArray;

        private int currentDbLinePosition = 1;
        private int currentReportLinePosition = 1;
        private boolean insideHunk = false;

        private int hunkStartInPreviousFile = 0;
        private int hunkLengthInPreviousFile = 0;
        private int hunkStartInCurrentFile = 0;
        private int hunkLengthInCurrentFile = 0;

        private UnifiedDiffParser(int[] matchingLineArray) {
            this.matchingLineArray = matchingLineArray;
        }

        private void consume(String rawDiffLine) {
            Matcher hunkHeaderMatcher = HUNK_HEADER_PATTERN.matcher(rawDiffLine);

            if (hunkHeaderMatcher.find()) {
                startHunk(hunkHeaderMatcher);
            } else if (insideHunk && !rawDiffLine.isEmpty()) {
                consumeHunkBodyLine(rawDiffLine.charAt(0));
            }
        }

        private void startHunk(Matcher hunkHeaderMatcher) {
            jumpToEndOfCurrentHunk();

            hunkStartInPreviousFile = Integer.parseInt(hunkHeaderMatcher.group(1));
            hunkLengthInPreviousFile = parseHunkLength(hunkHeaderMatcher, 2);
            hunkStartInCurrentFile = Integer.parseInt(hunkHeaderMatcher.group(3));
            hunkLengthInCurrentFile = parseHunkLength(hunkHeaderMatcher, 4);

            fillIdenticalLinesBetweenHunks(hunkStartInPreviousFile, hunkStartInCurrentFile);

            currentDbLinePosition = hunkStartInPreviousFile;
            currentReportLinePosition = hunkStartInCurrentFile;
            insideHunk = true;
        }

        private void consumeHunkBodyLine(char lineTypePrefix) {
            if (lineTypePrefix == ' ') {
                mapReportLine(currentReportLinePosition, currentDbLinePosition);
                currentDbLinePosition++;
                currentReportLinePosition++;

            } else if (lineTypePrefix == '+') {
                currentReportLinePosition++;

            } else if (lineTypePrefix == '-') {
                currentDbLinePosition++;
            }
        }

        /**
         * @return true if at least one {@code @@} hunk header was parsed.
         */
        private boolean finish(int totalDbLines, int totalReportLines) {
            jumpToEndOfCurrentHunk();
            fillIdenticalLinesBetweenHunks(totalDbLines + 1, totalReportLines + 1);
            return insideHunk;
        }

        /**
         * Moves both cursors past the hunk being parsed, so the untouched lines that follow it can be paired up.
         */
        private void jumpToEndOfCurrentHunk() {
            if (insideHunk) {
                currentDbLinePosition = hunkStartInPreviousFile + hunkLengthInPreviousFile;
                currentReportLinePosition = hunkStartInCurrentFile + hunkLengthInCurrentFile;
            }
        }

        private void fillIdenticalLinesBetweenHunks(int untilDbLine, int untilReportLine) {
            int dbLinePointer = currentDbLinePosition;
            int reportLinePointer = currentReportLinePosition;

            while (dbLinePointer < untilDbLine && reportLinePointer < untilReportLine) {
                mapReportLine(reportLinePointer, dbLinePointer);
                dbLinePointer++;
                reportLinePointer++;
            }
        }

        private void mapReportLine(int reportLine, int dbLine) {
            int reportArrayIndex = reportLine - 1;
            if (reportArrayIndex >= 0 && reportArrayIndex < matchingLineArray.length) {
                matchingLineArray[reportArrayIndex] = dbLine;
            }
        }

        private static int parseHunkLength(Matcher hunkHeaderMatcher, int lengthGroup) {
            String rawLength = hunkHeaderMatcher.group(lengthGroup);
            return rawLength != null ? Integer.parseInt(rawLength) : 1;
        }
    }

    private static void deleteTempFilesAndDirectory(Path diffWorkingDirectory) {
        if (diffWorkingDirectory == null) {
            return;
        }
        if (!FileUtils.deleteQuietly(diffWorkingDirectory.toFile())) {
            LOG.warn("Git diff temp directory cleanup failed for '{}'. Non-fatal — the CE temp folder is reclaimed "
                    + "when the task ends.", diffWorkingDirectory);
        }
    }
}
