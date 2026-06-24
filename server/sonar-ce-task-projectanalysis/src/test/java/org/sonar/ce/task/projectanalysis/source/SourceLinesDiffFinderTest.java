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

import difflib.myers.DifferentiationFailedException;
import difflib.myers.MyersDiff;
import difflib.myers.PathNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SourceLinesDiffFinderTest {

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

  /**
   * Equivalence guarantee: for any input whose edit distance is within the cap, the bounded
   * diff must return EXACTLY what an uncapped {@code difflib.myers.MyersDiff} would. This
   * fuzzes thousands of random inputs over a tiny alphabet (so matches, repeats and
   * tie-breaks are exercised heavily) and asserts the mapping is identical to the upstream
   * library — proving the cap introduces no behavioural side effect on normal diffs.
   */
  @Test
  public void shouldMatchUpstreamMyersForEveryDiffWithinTheCap() throws DifferentiationFailedException {
    Random random = new Random(20260612L);
    String[] alphabet = {"a", "b", "c", "d"};
    for (int iteration = 0; iteration < 5_000; iteration++) {
      List<String> left = randomLines(random, alphabet, random.nextInt(40));
      List<String> right = randomLines(random, alphabet, random.nextInt(40));

      int[] actual = new SourceLinesDiffFinder().findMatchingLines(left, right);
      int[] expected = uncappedReferenceMatchingLines(left, right);

      assertThat(actual).as("iteration " + iteration + " left=" + left + " right=" + right)
        .containsExactly(expected);
    }
  }

  /**
   * Large fully-identical inputs (100K x 100K): edit distance is 0, so Myers finishes on the
   * first step and every line maps 1:1.
   */
  @Test
  public void shouldMapIdentityForLargeIdenticalInputs() {
    int size = 100_000;
    List<String> database = buildLines(size, "line-");
    List<String> report = buildLines(size, "line-");

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).hasSize(size);
    for (int i = 0; i < size; i++) {
      assertThat(diff[i]).as("line " + i).isEqualTo(i + 1);
    }
  }

  /**
   * Regression for the hang on a small delta against a large, unrelated file. The edit
   * distance (~100 100) exceeds the cap, so the diff stops early; the files are disjoint
   * (no common head/tail) so the fallback maps nothing and every report line is new. The
   * generous time bound only guards against a re-introduced hang, not micro-performance.
   */
  @Test
  public void shouldStopEarlyForLargeDisjointInputs() {
    List<String> database = buildLines(100_000, "db-");
    List<String> report = buildLines(100, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(100);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("edit-distance cap must prevent the hang — wall time was " + elapsedMs + " ms")
      .isLessThan(10_000L);
  }

  /**
   * Symmetric fully-disjoint large input (5K x 5K): every line differs, so the edit distance
   * (10 000) exceeds the cap and the diff stops early; disjoint, so the fallback maps nothing.
   */
  @Test
  public void shouldStopEarlyForLargeSymmetricDisjointInputs() {
    List<String> database = buildLines(5_000, "db-");
    List<String> report = buildLines(5_000, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(5_000);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("edit-distance cap must prevent the hang — wall time was " + elapsedMs + " ms")
      .isLessThan(10_000L);
  }

  /**
   * The over-cap behaviour that matters for correctness (review finding #1/#2): a large file
   * (90 000 lines) with an unchanged head and tail but a divergent middle block bigger than
   * the cap (6 000 changed lines → D = 12 000 > 5 000). The full diff is refused, but the
   * unchanged head and tail MUST still map 1:1 — only the divergent middle may be reported as
   * new. Previously this whole file was reported as new, corrupting new-code / new-coverage /
   * issue-creation-date for every unchanged line.
   */
  @Test
  public void shouldKeepCommonHeadAndTailWhenEditDistanceExceedsCap() {
    int head = 42_000;
    int changed = 6_000;
    int tail = 42_000;
    int total = head + changed + tail;

    List<String> database = new ArrayList<>(total);
    List<String> report = new ArrayList<>(total);
    for (int i = 0; i < head; i++) {
      String shared = "head-" + String.format("%07d", i);
      database.add(shared);
      report.add(shared);
    }
    for (int i = 0; i < changed; i++) {
      database.add("db-" + String.format("%07d", i));
      report.add("rp-" + String.format("%07d", i));
    }
    for (int i = 0; i < tail; i++) {
      String shared = "tail-" + String.format("%07d", i);
      database.add(shared);
      report.add(shared);
    }

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).hasSize(total);
    for (int i = 0; i < head; i++) {
      assertThat(diff[i]).as("head line " + i + " must map 1:1").isEqualTo(i + 1);
    }
    for (int i = head; i < head + changed; i++) {
      assertThat(diff[i]).as("changed middle line " + i + " must be new").isZero();
    }
    for (int i = head + changed; i < total; i++) {
      assertThat(diff[i]).as("tail line " + i + " must map 1:1").isEqualTo(i + 1);
    }
  }

  /**
   * Cap boundary (review finding #6). Construct an input whose edit distance is controlled
   * exactly and whose correct mapping has interior matches that the prefix/suffix fallback
   * would NOT recover — so Myers-success and fallback give DIFFERENT results. At D == cap the
   * search must still complete (every interior line matched); at D == cap + 1 it must fall
   * back (interior lines reported new). This pins the {@code dLimit = maxEditDistance + 1}
   * off-by-one.
   */
  @Test
  public void shouldRunMyersAtTheCapBoundaryAndFallBackJustAboveIt() {
    // left = a, d1, s1, d2, s2, ... dk, sk ; right = a, s1, s2, ... sk.
    // Only deletions of the k "d" lines, so edit distance D == k. Every "s" line matches an
    // interior position; the fallback (common head=a, tail=sk only) cannot recover s1..s(k-1).
    assertInterleavedDeletionDiff(SourceLinesDiffFinder.MAX_EDIT_DISTANCE, /* expectComplete = */ true);
    assertInterleavedDeletionDiff(SourceLinesDiffFinder.MAX_EDIT_DISTANCE + 1, /* expectComplete = */ false);
  }

  private static void assertInterleavedDeletionDiff(int k, boolean expectComplete) {
    // left  = a, d0, s0, d1, s1, ..., d(k-1), s(k-1)   (size 1 + 2k); s(i) sits at left index 2 + 2i
    // right = a, s0, s1, ..., s(k-1)                    (size 1 + k)
    // Only the k "d" lines are deleted, so the edit distance D == k exactly.
    List<String> database = new ArrayList<>(1 + 2 * k);
    List<String> report = new ArrayList<>(1 + k);
    database.add("a");
    report.add("a");
    for (int i = 0; i < k; i++) {
      database.add("d" + i);
      database.add("s" + i);
      report.add("s" + i);
    }
    int n = 1 + 2 * k;

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).hasSize(1 + k);
    assertThat(diff[0]).as("the leading 'a' is always matched").isEqualTo(1);
    if (expectComplete) {
      // D == k == cap: Myers completes, so every interior s(i) maps to its exact DB line 3 + 2i.
      for (int i = 0; i < k; i++) {
        assertThat(diff[1 + i]).as("s" + i + " must map to its DB line").isEqualTo(3 + 2 * i);
      }
    } else {
      // D == cap + 1: Myers bails. The fallback keeps the common head 'a' and the single common
      // tail s(k-1) (== DB line n), and reports every interior s(i) as new.
      assertThat(diff[k]).as("common tail s(k-1) mapped by suffix").isEqualTo(n);
      for (int i = 0; i < k - 1; i++) {
        assertThat(diff[1 + i]).as("interior s" + i + " must be new after fallback").isZero();
      }
    }
  }

  /**
   * Randomised contract test for the over-cap fallback (matchCommonPrefixSuffix), which the
   * within-cap fuzz test never reaches. Across varied head/tail sizes and unequal DB/report
   * lengths it asserts the two invariants that matter: (1) every non-zero entry is a REAL
   * content match (no false matches), and (2) the unchanged head and tail map 1:1 while the
   * disjoint middle is reported as new.
   */
  @Test
  public void fallbackShouldNeverProduceAFalseMatchAndMapCommonHeadAndTail() {
    // {head, tail, leftMiddle, rightMiddle}. leftMiddle + rightMiddle > MAX_EDIT_DISTANCE so the
    // diff always bails into the fallback; unequal middles exercise the n != m suffix arithmetic.
    int[][] combos = {
      {0, 0, 2_600, 2_600},
      {30, 0, 2_600, 2_800},
      {0, 45, 2_900, 2_600},
      {25, 40, 2_700, 2_700},
      {12, 70, 3_100, 2_600},
      {64, 8, 2_600, 3_300},
    };
    for (int[] c : combos) {
      assertFallbackContract(c[0], c[1], c[2], c[3]);
    }
  }

  private static void assertFallbackContract(int head, int tail, int leftMiddle, int rightMiddle) {
    List<String> left = new ArrayList<>(head + leftMiddle + tail);
    List<String> right = new ArrayList<>(head + rightMiddle + tail);
    for (int i = 0; i < head; i++) {
      String shared = "h" + i;
      left.add(shared);
      right.add(shared);
    }
    for (int i = 0; i < leftMiddle; i++) {
      left.add("L" + i);
    }
    for (int i = 0; i < rightMiddle; i++) {
      right.add("R" + i);
    }
    for (int i = 0; i < tail; i++) {
      String shared = "t" + i;
      left.add(shared);
      right.add(shared);
    }
    int n = left.size();
    int m = right.size();

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(left, right);

    assertThat(diff).hasSize(m);
    // (1) no false matches: every non-zero entry points at a DB line with identical content.
    for (int r = 0; r < m; r++) {
      int v = diff[r];
      if (v != 0) {
        assertThat(v).as("match value in range, r=" + r).isBetween(1, n);
        assertThat(left.get(v - 1)).as("match must be a real content match, r=" + r).isEqualTo(right.get(r));
      }
    }
    // (2) common head and tail map 1:1; disjoint middle is new.
    for (int i = 0; i < head; i++) {
      assertThat(diff[i]).as("head " + i).isEqualTo(i + 1);
    }
    for (int t = 0; t < tail; t++) {
      assertThat(diff[m - 1 - t]).as("tail " + t).isEqualTo(n - t);
    }
    for (int r = head; r < m - tail; r++) {
      assertThat(diff[r]).as("disjoint middle " + r).isZero();
    }
  }

  /**
   * A large file (7 500 lines) with a small but scattered change set — one edit block near the
   * top, another near the bottom. Its edit distance is tiny (~160), so Myers finishes quickly
   * and maps every unchanged line 1:1; only the changed lines are reported as new. A guard that
   * keyed off file size rather than edit distance would wrongly report the entire file as new.
   */
  @Test
  public void shouldDiffLargeFileWithSmallScatteredChanges() {
    int total = 7_500;
    int topChangeAt = 100;
    int bottomChangeAt = 7_300;
    int changeLen = 40;

    List<String> database = new ArrayList<>(total);
    List<String> report = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      boolean changed = isChanged(i, topChangeAt, bottomChangeAt, changeLen);
      database.add("line-" + String.format("%07d", i));
      report.add(changed ? "changed-" + String.format("%07d", i) : "line-" + String.format("%07d", i));
    }

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(total);
    for (int i = 0; i < total; i++) {
      if (isChanged(i, topChangeAt, bottomChangeAt, changeLen)) {
        assertThat(diff[i]).as("changed line " + i + " must be reported as new").isZero();
      } else {
        assertThat(diff[i]).as("unchanged line " + i + " must map 1:1 to its DB line").isEqualTo(i + 1);
      }
    }
    assertThat(elapsedMs).as("small edit distance must let Myers run quickly — wall time was " + elapsedMs + " ms")
      .isLessThan(2_000L);
  }

  private static boolean isChanged(int line, int topChangeAt, int bottomChangeAt, int changeLen) {
    return (line >= topChangeAt && line < topChangeAt + changeLen)
      || (line >= bottomChangeAt && line < bottomChangeAt + changeLen);
  }

  private static List<String> randomLines(Random random, String[] alphabet, int size) {
    List<String> lines = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      lines.add(alphabet[random.nextInt(alphabet.length)]);
    }
    return lines;
  }

  /**
   * The pre-existing (uncapped) implementation, used as the oracle for the fuzz test: a plain
   * walk of {@code difflib.myers.MyersDiff#buildPath} with no edit-distance cap.
   */
  private static int[] uncappedReferenceMatchingLines(List<String> left, List<String> right) throws DifferentiationFailedException {
    int[] index = new int[right.size()];
    int dbLine = left.size();
    int reportLine = right.size();

    PathNode node = new MyersDiff<String>().buildPath(left, right);
    while (node.prev != null) {
      PathNode prevNode = node.prev;
      if (!node.isSnake()) {
        reportLine -= (node.j - prevNode.j);
        dbLine -= (node.i - prevNode.i);
      } else {
        for (int i = node.i; i > prevNode.i; i--) {
          index[reportLine - 1] = dbLine;
          reportLine--;
          dbLine--;
        }
      }
      node = prevNode;
    }
    return index;
  }

  private static List<String> buildLines(int n, String prefix) {
    List<String> lines = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      lines.add(prefix + String.format("%07d", i));
    }
    return lines;
  }
}
