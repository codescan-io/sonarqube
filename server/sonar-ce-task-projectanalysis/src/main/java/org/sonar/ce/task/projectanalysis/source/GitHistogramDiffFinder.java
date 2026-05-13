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

/**
 * Uses native git's histogram diff algorithm via {@code git diff --no-index --histogram}
 * to compute matching lines between two file versions. This produces better results than
 * Myers algorithm on files with repeated structures (XML, JSON, etc.).
 *
 * Throws on failure — caller is responsible for fallback.
 */
class GitHistogramDiffFinder {

  private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

  /**
   * Computes matching lines between the DB version (left/old) and the report version (right/new)
   * using git's histogram algorithm.
   *
   * @param dbLines     actual source lines from the reference branch (DB)
   * @param reportLines actual source lines from the current analysis (report)
   * @return int[] where index i corresponds to report line (i+1), value is the matching DB line number (1-based) or 0 if new
   * @throws IOException if git process cannot be started or temp files cannot be written
   * @throws InterruptedException if the thread is interrupted while waiting for git
   */
  int[] findMatchingLines(List<String> dbLines, List<String> reportLines) throws IOException, InterruptedException {
    int[] result = new int[reportLines.size()];

    if (dbLines.isEmpty() || reportLines.isEmpty()) {
      return result;
    }

    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("sonar-histogram-diff-");
      Path oldFile = tempDir.resolve("old.txt");
      Path newFile = tempDir.resolve("new.txt");

      Files.write(oldFile, dbLines, StandardCharsets.UTF_8);
      Files.write(newFile, reportLines, StandardCharsets.UTF_8);

      runDiffAndParse(oldFile, newFile, result, dbLines.size(), reportLines.size());
    } finally {
      cleanupTempDir(tempDir);
    }

    return result;
  }

  private void runDiffAndParse(Path oldFile, Path newFile, int[] result, int dbLineCount, int reportLineCount)
    throws IOException, InterruptedException {

    ProcessBuilder pb = new ProcessBuilder(
      "git", "diff", "--no-index", "--no-color", "--histogram",
      oldFile.toAbsolutePath().toString(),
      newFile.toAbsolutePath().toString()
    );
    pb.redirectError(ProcessBuilder.Redirect.DISCARD);

    Process process = pb.start();

    try (BufferedReader reader = new BufferedReader(
      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      parseUnifiedDiff(reader, result, dbLineCount, reportLineCount);
    }

    int exitCode = process.waitFor();
    // git diff --no-index: 0 = no differences, 1 = differences found, >1 = error
    if (exitCode > 1) {
      throw new IOException("git diff --no-index failed with exit code " + exitCode);
    }
  }

  private static void parseUnifiedDiff(BufferedReader reader, int[] result, int dbLineCount, int reportLineCount)
    throws IOException {

    int oldPos = 1;
    int newPos = 1;

    String line;
    boolean inHunk = false;
    int hunkOldStart = 0;
    int hunkOldCount = 0;
    int hunkNewStart = 0;
    int hunkNewCount = 0;

    while ((line = reader.readLine()) != null) {
      Matcher m = HUNK_HEADER.matcher(line);
      if (m.find()) {
        if (inHunk) {
          oldPos = hunkOldStart + hunkOldCount;
          newPos = hunkNewStart + hunkNewCount;
        }

        hunkOldStart = Integer.parseInt(m.group(1));
        hunkOldCount = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
        hunkNewStart = Integer.parseInt(m.group(3));
        hunkNewCount = m.group(4) != null ? Integer.parseInt(m.group(4)) : 1;

        // Fill matches for lines between previous hunk end and this hunk start
        fillMatches(result, oldPos, newPos, hunkOldStart, hunkNewStart);
        oldPos = hunkOldStart;
        newPos = hunkNewStart;
        inHunk = true;

      } else if (inHunk && !line.isEmpty()) {
        char firstChar = line.charAt(0);
        if (firstChar == ' ') {
          int reportIdx = newPos - 1;
          if (reportIdx >= 0 && reportIdx < result.length) {
            result[reportIdx] = oldPos;
          }
          oldPos++;
          newPos++;
        } else if (firstChar == '+') {
          newPos++;
        } else if (firstChar == '-') {
          oldPos++;
        }
      }
    }

    // After last hunk, fill remaining matching lines to end of file
    if (inHunk) {
      oldPos = hunkOldStart + hunkOldCount;
      newPos = hunkNewStart + hunkNewCount;
    }
    fillMatches(result, oldPos, newPos, dbLineCount + 1, reportLineCount + 1);
  }

  private static void fillMatches(int[] result, int currentOld, int currentNew, int targetOld, int targetNew) {
    while (currentOld < targetOld && currentNew < targetNew) {
      int reportIdx = currentNew - 1;
      if (reportIdx >= 0 && reportIdx < result.length) {
        result[reportIdx] = currentOld;
      }
      currentOld++;
      currentNew++;
    }
  }

  private static void cleanupTempDir(Path tempDir) {
    if (tempDir == null) {
      return;
    }
    try {
      Files.deleteIfExists(tempDir.resolve("old.txt"));
      Files.deleteIfExists(tempDir.resolve("new.txt"));
      Files.deleteIfExists(tempDir);
    } catch (IOException e) {
      // best-effort cleanup
    }
  }
}
