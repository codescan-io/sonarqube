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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.ce.task.projectanalysis.source.SourceLinesDiffFinder.DIFF_COMPLEXITY_THRESHOLD;

public class SourceLinesDiffFinderTest {

  /**
   * When left.size() * right.size() > DIFF_COMPLEXITY_THRESHOLD, findMatchingLines()
   * must short-circuit and return a zeroed array of length right.size() instead of
   * running the O(N^2) Myers diff.
   *
   * Without the fix this call would run the full Myers diff on 100K*1K = 100M cells,
   * taking minutes. With the fix it must complete in well under 2 seconds.
   *
   * Regression test for RCA boi-241415000220432413.
   */
  @Test
  public void shouldShortCircuitWhenInputSizeExceedsComplexityThreshold() {
    // left.size() * right.size() = 100_001 * 1_001 = 100_101_001 >> DIFF_COMPLEXITY_THRESHOLD
    int leftSize = 100_001;
    int rightSize = 1_001;
    List<String> left = buildDisjointLines(leftSize, "db-line-");
    List<String> right = buildDisjointLines(rightSize, "rpt-line-");

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(left, right);
    long elapsed = System.currentTimeMillis() - start;

    // Must return a zeroed array of length right.size()
    assertThat(result).hasSize(rightSize);
    assertThat(result).containsOnly(0);
    // Must complete in well under 2 seconds (the guard makes this effectively O(1))
    assertThat(elapsed)
      .as("findMatchingLines must short-circuit above DIFF_COMPLEXITY_THRESHOLD in < 2000ms, but took " + elapsed + "ms")
      .isLessThan(2_000L);
  }

  /**
   * Normal-sized inputs (well below the threshold) must continue to use Myers diff
   * and produce the correct matching array. Guards against the fix gating too aggressively.
   *
   * Uses the exact data from shouldDetectModifiedLinesInMiddleOfTheFile.
   */
  @Test
  public void shouldNotShortCircuitForNormalSizedInputs() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");
    database.add("line - 4");
    database.add("line - 5");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2 - modified");
    report.add("line - 3 - modified");
    report.add("line - 4");
    report.add("line - 5");

    // 6 * 6 = 36 cells — far below the threshold; Myers diff must run normally
    assertThat((long) database.size() * report.size())
      .as("test inputs must be below DIFF_COMPLEXITY_THRESHOLD")
      .isLessThan(DIFF_COMPLEXITY_THRESHOLD);

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    // Same golden expectation as shouldDetectModifiedLinesInMiddleOfTheFile
    assertThat(diff).containsExactly(1, 2, 0, 0, 5, 6);
  }

  private static List<String> buildDisjointLines(int n, String prefix) {
    List<String> lines = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      lines.add(prefix + i);
    }
    return lines;
  }

  @Test
  public void shouldFindNothingWhenContentAreIdentical() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");
    database.add("line - 4");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2");
    report.add("line - 3");
    report.add("line - 4");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  public void shouldFindNothingWhenContentAreIdentical2() {
    List<String> database = new ArrayList<>();
    database.add("package sample;\n");
    database.add("\n");
    database.add("public class Sample {\n");
    database.add("\n");
    database.add("    private String myMethod() {\n");
    database.add("    }\n");
    database.add("}\n");

    List<String> report = new ArrayList<>();
    report.add("package sample;\n");
    report.add("\n");
    report.add("public class Sample {\n");
    report.add("\n");
    report.add("    private String attr;\n");
    report.add("\n");
    report.add("    public Sample(String attr) {\n");
    report.add("        this.attr = attr;\n");
    report.add("    }\n");
    report.add("\n");
    report.add("    private String myMethod() {\n");
    report.add("    }\n");
    report.add("}\n");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    assertThat(diff).containsExactly(1, 2, 3, 4, 0, 0, 0, 0, 0, 0, 5, 6, 7);
  }

  @Test
  public void shouldDetectWhenStartingWithModifiedLines() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");

    List<String> report = new ArrayList<>();
    report.add("line - 0 - modified");
    report.add("line - 1 - modified");
    report.add("line - 2");
    report.add("line - 3");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(0, 0, 3, 4);
  }

  @Test
  public void shouldDetectWhenEndingWithModifiedLines() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2 - modified");
    report.add("line - 3 - modified");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 0, 0);
  }

  @Test
  public void shouldDetectModifiedLinesInMiddleOfTheFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");
    database.add("line - 4");
    database.add("line - 5");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2 - modified");
    report.add("line - 3 - modified");
    report.add("line - 4");
    report.add("line - 5");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 0, 0, 5, 6);
  }

  @Test
  public void shouldDetectNewLinesAtBeginningOfFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");

    List<String> report = new ArrayList<>();
    report.add("line - new");
    report.add("line - new");
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(0, 0, 1, 2, 3);
  }

  @Test
  public void shouldDetectNewLinesInMiddleOfFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - new");
    report.add("line - new");
    report.add("line - 2");
    report.add("line - 3");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 0, 0, 3, 4);
  }

  @Test
  public void shouldDetectNewLinesAtEndOfFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2");
    report.add("line - new");
    report.add("line - new");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 3, 0, 0);
  }

  @Test
  public void shouldIgnoreDeletedLinesAtEndOfFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");
    database.add("line - 4");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 2");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 3);
  }

  @Test
  public void shouldIgnoreDeletedLinesInTheMiddleOfFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");
    database.add("line - 4");
    database.add("line - 5");

    List<String> report = new ArrayList<>();
    report.add("line - 0");
    report.add("line - 1");
    report.add("line - 4");
    report.add("line - 5");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(1, 2, 5, 6);
  }

  @Test
  public void shouldIgnoreDeletedLinesAtTheStartOfTheFile() {
    List<String> database = new ArrayList<>();
    database.add("line - 0");
    database.add("line - 1");
    database.add("line - 2");
    database.add("line - 3");

    List<String> report = new ArrayList<>();
    report.add("line - 2");
    report.add("line - 3");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).containsExactly(3, 4);
  }
}
