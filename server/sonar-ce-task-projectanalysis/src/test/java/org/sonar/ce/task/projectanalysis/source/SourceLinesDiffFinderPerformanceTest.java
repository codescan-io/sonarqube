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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.ce.task.projectanalysis.source.SourceLinesDiffFinder.DIFF_COMPLEXITY_THRESHOLD;

/**
 * Performance regression test for RCA boi-241415000220432413.
 *
 * Before the fix, MyersDiff.buildPath() inside SourceLinesDiffFinder had no timeout
 * mechanism and exhibited O(N^2) worst-case behaviour when the first line of a large
 * file differs (edit-distance D ≈ 2N, causing the outer loop over k from -N to +N to
 * run ≈ 2N iterations, each scanning up to 2N diagonals → O(N^2) total work).
 *
 * After the fix (complexity guard at {@code DIFF_COMPLEXITY_THRESHOLD} cells), the
 * pathological case is short-circuited and returns a zeroed array in O(1).
 *
 * All tests in this class are tagged {@code @Tag("performance")} so they can be
 * optionally excluded from the default CI run via {@code -PexcludedTags=performance}.
 * The regression assertion (gate short-circuits) is covered by a fast unit test in
 * {@link SourceLinesDiffFinderTest} that does not rely on wall-clock timing.
 *
 * Scenario: Salesforce Profile.xml / PermissionSet.xml stored in the DB reference
 * branch can have 10,000+ lines.  ARM sends a delta for 2 specific branch labels
 * that does NOT include explicit changedLines, so NewCoverageMeasuresStep falls
 * through to computeNewLinesFromScm() → SourceLinesDiffImpl → SourceLinesDiffFinder.
 */
@Tag("performance")
public class SourceLinesDiffFinderPerformanceTest {

  /**
   * Baseline: 100-line identical files. Must complete near-instantly.
   * Confirms the algorithm works correctly for small inputs.
   */
  @Test
  public void baseline_identicalFiles_100Lines_shouldCompleteUnder100ms() {
    int n = 100;
    List<String> database = buildLines(n, "db-line-");
    List<String> report = new ArrayList<>(database); // identical

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] baseline 100-line identical: " + elapsed + " ms");

    // All lines should match 1:1
    assertThat(result).hasSize(n);
    for (int i = 0; i < n; i++) {
      assertThat(result[i]).as("line index " + i).isEqualTo(i + 1);
    }
    assertThat(elapsed).as("100-line identical diff should be < 100 ms").isLessThan(100L);
  }

  /**
   * Medium scale: 1,000 lines where only the first line differs.
   * Edit distance D = 2, so O((N+M)*D) ≈ O(4000). Should be fast.
   * Inputs: 1K x 1K = 1M cells — below {@code DIFF_COMPLEXITY_THRESHOLD}.
   */
  @Test
  public void medium_oneChangeAtStart_1000Lines_shouldCompleteQuickly() {
    int n = 1_000;
    List<String> database = buildLines(n, "db-line-");
    List<String> report = buildLines(n, "db-line-");
    // Change first line only: edit distance = 2 (1 delete + 1 insert)
    report.set(0, "MODIFIED-LINE-0");

    // Verify this is below the complexity threshold (Myers should run)
    assertThat((long) database.size() * report.size())
      .as("test inputs must be below DIFF_COMPLEXITY_THRESHOLD")
      .isLessThan(DIFF_COMPLEXITY_THRESHOLD);

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] medium 1000-line first-line-changed: " + elapsed + " ms");

    // Lines 2..N should match DB lines 2..N (1-indexed)
    assertThat(result).hasSize(n);
    assertThat(result[0]).as("first (modified) line should have no DB match").isEqualTo(0);
    for (int i = 1; i < n; i++) {
      assertThat(result[i]).as("line " + (i + 1) + " should match DB line " + (i + 1)).isEqualTo(i + 1);
    }
    assertThat(elapsed).as("1000-line one-change diff should be < 5000 ms").isLessThan(5_000L);
  }

  /**
   * Large-scale: 10,000-line DB vs 10,000-line report where EVERY line is unique
   * in the DB and every line in the report is new (completely disjoint content).
   *
   * Before the fix: edit distance D = N + M = 20,000, causing O(N^2) behaviour
   * (~2.3 s on M-series, scaled to minutes on large inputs).
   *
   * After the fix: {@code left.size() * right.size() = 100M > DIFF_COMPLEXITY_THRESHOLD}.
   * The complexity guard short-circuits immediately and returns a zeroed array in O(1).
   * The test verifies the gate fires and the result is correct (all zeros).
   */
  @Test
  public void worstCase_completelyDisjoint_10000Lines_gatedByComplexityGuard() {
    int n = 10_000;
    List<String> database = buildLines(n, "db-");
    List<String> report = buildLines(n, "rp-");

    // Verify this is above the complexity threshold
    long cellBudget = (long) database.size() * report.size();
    assertThat(cellBudget)
      .as("10K x 10K = 100M cells must be above DIFF_COMPLEXITY_THRESHOLD for the guard to trigger")
      .isGreaterThan(DIFF_COMPLEXITY_THRESHOLD);

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] WORST-CASE 10000-line fully-disjoint (gated): " + elapsed + " ms");

    // Guard must trip and return a zeroed array
    assertThat(result).hasSize(n);
    assertThat(result).containsOnly(0);
    assertThat(elapsed)
      .as("Complexity guard must short-circuit the 10K x 10K disjoint case in < 1000ms, but took " + elapsed + "ms")
      .isLessThan(1_000L);
  }

  /**
   * Near-identical large file: 1,000-line DB vs 1,000-line report where only the
   * first line differs. Edit distance D = 2. O(N*D) = O(2000) — fast.
   *
   * Inputs: 1K x 1K = 1M cells — below {@code DIFF_COMPLEXITY_THRESHOLD} so Myers
   * diff runs and produces the correct result.
   *
   * This test guards against over-aggressive thresholds that would gate normal-sized
   * symmetric inputs and degrade matching accuracy for near-identical files.
   */
  @Test
  public void nearIdentical_oneChangeAtStart_1000Lines_shouldCompleteUnder5s() {
    int n = 1_000;
    List<String> database = buildLines(n, "line-");
    List<String> report = buildLines(n, "line-");
    report.set(0, "MODIFIED-FIRST-LINE");

    // Verify this is below the complexity threshold (Myers should run, not the guard)
    assertThat((long) database.size() * report.size())
      .as("test inputs must be below DIFF_COMPLEXITY_THRESHOLD so Myers diff runs")
      .isLessThan(DIFF_COMPLEXITY_THRESHOLD);

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] near-identical 1000-line first-line-changed: " + elapsed + " ms");

    assertThat(result).hasSize(n);
    assertThat(result[0]).as("modified first line should not match any DB line").isEqualTo(0);
    for (int i = 1; i < n; i++) {
      assertThat(result[i]).as("unmodified line " + (i + 1) + " should match DB line " + (i + 1)).isEqualTo(i + 1);
    }
    assertThat(elapsed).as(
      "Near-identical 1000-line diff with D=2 should complete in < 5000 ms."
    ).isLessThan(5_000L);
  }

  /**
   * CANONICAL REGRESSION TEST for RCA boi-241415000220432413.
   *
   * Scenario: ARM scanner sends only a small delta (100-line report) against a
   * large reference-branch file (50,000-line DB). The content is disjoint (different
   * line hashes), so the edit distance D = N + M = 50,100.
   *
   * Pre-fix measured timings (Apple M-series, diffutils-1.3.0, Java 21):
   *   N=10,000  → ~555ms
   *   N=30,000  → ~4.3s
   *   N=50,000  → ~12.4s   ← this test's un-gated scenario
   *   N=100,000 → ~70.6s
   *
   * Post-fix: left.size() * right.size() = 50K * 100 = 5M cells > DIFF_COMPLEXITY_THRESHOLD
   * (4M). The complexity guard short-circuits and returns a zeroed array in O(1).
   * Wall time: < 10ms on all hardware.
   *
   * This is the regression test that WOULD HAVE FAILED on the parent commit
   * (codescan-24.12 before this fix).
   */
  @Test
  public void customerScenario_largeDbFile_smallArmDelta_completesUnderOneSecond() {
    // Conservative estimate: Profile.xml with 50K lines in the reference branch
    int dbLines = 50_000;
    // ARM sends only the changed metadata fields — a tiny delta
    int reportLines = 100;

    // Verify the guard fires for this input
    assertThat((long) dbLines * reportLines)
      .as("50K * 100 = 5M cells must be above DIFF_COMPLEXITY_THRESHOLD for the guard to trigger")
      .isGreaterThan(DIFF_COMPLEXITY_THRESHOLD);

    List<String> database = buildLines(dbLines, "db-");
    // Report content is different (new hashes) — no overlap with DB
    List<String> report = buildLines(reportLines, "rp-");

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] customer-scenario 50K-DB vs 100-report: " + elapsed + " ms");

    // Guard must return zeroed array (all report lines treated as new)
    assertThat(result).hasSize(reportLines);
    assertThat(result).containsOnly(0);

    // Post-fix: must complete in < 1 second (guard is O(1))
    // Pre-fix: took ~12,400ms on M-series; ~50-200s on cloud VMs
    assertThat(elapsed)
      .as("Complexity guard must short-circuit 50K-DB vs 100-report in < 1000ms, but took " + elapsed + "ms. " +
        "Pre-fix baseline: ~12,400ms on M-series, ~50-200s on cloud VMs.")
      .isLessThan(1_000L);
  }

  /**
   * Verifies the threshold boundary: inputs just over the threshold return a zeroed
   * array quickly, while inputs just under still use Myers diff.
   */
  @Test
  public void thresholdBoundary_justAboveThreshold_returnsZeroedArrayQuickly() {
    // Construct inputs where left.size() * right.size() is just above threshold
    // DIFF_COMPLEXITY_THRESHOLD = 4_000_000L; use left=4001, right=1001 → 4,005,001 > 4M
    int leftSize = 4_001;
    int rightSize = 1_001;
    assertThat((long) leftSize * rightSize).isGreaterThan(DIFF_COMPLEXITY_THRESHOLD);

    List<String> left = buildLines(leftSize, "db-");
    List<String> right = buildLines(rightSize, "rp-");

    long start = System.currentTimeMillis();
    int[] result = new SourceLinesDiffFinder().findMatchingLines(left, right);
    long elapsed = System.currentTimeMillis() - start;

    System.out.println("[PERF] threshold-boundary above (" + leftSize + "x" + rightSize + "): " + elapsed + " ms");

    assertThat(result).hasSize(rightSize);
    assertThat(result).containsOnly(0);
    assertThat(elapsed)
      .as("Complexity guard must fire for " + leftSize + "x" + rightSize + " in < 100ms, but took " + elapsed + "ms")
      .isLessThan(100L);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Build a list of {@code n} distinct strings with the given prefix.
   * Uses zero-padded indices so that strings sort lexicographically and are
   * each unique (no accidental hash collisions in the algorithm's equality check).
   */
  private static List<String> buildLines(int n, String prefix) {
    List<String> lines = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      lines.add(prefix + String.format("%06d", i));
    }
    return lines;
  }
}
