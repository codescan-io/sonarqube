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
   * Large fully-identical inputs (100K x 100K) — prefix trim consumes everything,
   * Myers diff is never invoked. Verifies that identity is returned via the trim path.
   */
  @Test
  public void shouldReturnIdentityForLargeIdenticalInputsViaPrefixTrim() {
    int size = 100_000;
    List<String> database = buildLines(size, "line-");
    List<String> report = buildLines(size, "line-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(size);
    for (int i = 0; i < size; i++) {
      assertThat(diff[i]).as("line " + i).isEqualTo(i + 1);
    }
    assertThat(elapsedMs).as("prefix trim must short-circuit Myers — wall time was " + elapsedMs + " ms")
      .isLessThan(2_000L);
  }

  /**
   * Large near-identical inputs (80K x 80K, 100 lines changed in the middle).
   * Prefix and suffix together trim ~79 800 lines; Myers diff runs only on the
   * 100x100 core. Result must be correct AND fast.
   */
  @Test
  public void shouldRunMyersOnSmallCoreForLargeNearIdenticalInputs() {
    int total = 80_000;
    int prefixLen = 39_950;
    int divergent = 100;

    List<String> database = new ArrayList<>(total);
    List<String> report = new ArrayList<>(total);
    for (int i = 0; i < prefixLen; i++) {
      String shared = "shared-" + i;
      database.add(shared);
      report.add(shared);
    }
    for (int i = 0; i < divergent; i++) {
      database.add("db-divergent-" + i);
      report.add("rp-divergent-" + i);
    }
    int suffixLen = total - prefixLen - divergent;
    for (int i = 0; i < suffixLen; i++) {
      String shared = "tail-" + i;
      database.add(shared);
      report.add(shared);
    }

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(total);
    for (int i = 0; i < prefixLen; i++) {
      assertThat(diff[i]).as("prefix line " + i).isEqualTo(i + 1);
    }
    for (int i = prefixLen; i < prefixLen + divergent; i++) {
      assertThat(diff[i]).as("divergent line " + i).isZero();
    }
    for (int i = prefixLen + divergent; i < total; i++) {
      assertThat(diff[i]).as("suffix line " + i).isEqualTo(i + 1);
    }
    assertThat(elapsedMs).as("trim should leave only a 100x100 core — wall time was " + elapsedMs + " ms")
      .isLessThan(2_000L);
  }

  /**
   * Asymmetric customer scenario: 30 000-line DB file vs 50-line scanner delta
   * with no overlapping content (ARM EZ-Commit pattern). Prefix/suffix trim
   * removes nothing; the asymmetry gate must fire and short-circuit Myers.
   */
  @Test
  public void shouldShortCircuitOnAsymmetricDisjointInputs_30kVs50() {
    List<String> database = buildLines(30_000, "db-");
    List<String> report = buildLines(50, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(50);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("asymmetry gate must short-circuit Myers — wall time was " + elapsedMs + " ms")
      .isLessThan(500L);
  }

  /**
   * BOI worst case: 100 000-line DB file vs 100-line scanner delta with no
   * overlapping content. Both the asymmetry gate and the cell gate apply;
   * either is sufficient to short-circuit. Without a guard this took ~70 s
   * on M-series hardware and ~19 minutes on the production cloud VM.
   */
  @Test
  public void shouldShortCircuitOnBoiScale_100kVs100Disjoint() {
    List<String> database = buildLines(100_000, "db-");
    List<String> report = buildLines(100, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(100);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("BOI-scale guarded call must complete fast — wall time was " + elapsedMs + " ms")
      .isLessThan(500L);
  }

  /**
   * Symmetric fully-disjoint large input (5K x 5K): trim removes nothing, ratio
   * is 1 so the asymmetry gate does not fire, but the cell gate (5 000 x 5 000
   * = 25 M cells > 4 M threshold) must short-circuit.
   */
  @Test
  public void shouldShortCircuitOnLargeSymmetricDisjointInputs() {
    List<String> database = buildLines(5_000, "db-");
    List<String> report = buildLines(5_000, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(5_000);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("cell gate must short-circuit Myers — wall time was " + elapsedMs + " ms")
      .isLessThan(500L);
  }

  /**
   * Small asymmetric input (4K x 50) — below {@code DIFF_ASYMMETRY_MIN_SIZE} so
   * the asymmetry gate does NOT fire. Hash overlap is zero (disjoint content),
   * so the work-budget gate fires and the hash-matching fallback runs; result
   * is still all-zeros because no content is shared.
   */
  @Test
  public void shouldFallbackForSmallAsymmetricDisjointInputs() {
    List<String> database = buildLines(4_000, "db-");
    List<String> report = buildLines(50, "rp-");

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(50);
    assertThat(diff).containsOnly(0);
    assertThat(elapsedMs).as("small disjoint inputs handled fast — wall time was " + elapsedMs + " ms")
      .isLessThan(5_000L);
  }

  /**
   * Regression test for the post-PR field issue: 70K-line file with a handful of
   * lines changed at BOTH boundaries (couple at start, couple at end), middle
   * unchanged. Prefix and suffix trim both abort at index 0 because the very
   * first / last lines differ — so the divergent core is the full 70K x 70K.
   *
   * <p>The previous cell-size gate (left.size * right.size > 4M) fired here and
   * returned an all-zero index, which made the PR analysis flag every issue
   * in the file (including pre-existing ones on master) as "new on this PR".
   *
   * <p>The current D-estimate gate correctly identifies that hash overlap is
   * ~70K (very high), edit distance is ~10, predicted work is ~1.4M ops (well
   * under the 50M budget), and Myers runs normally — producing the correct
   * mapping where unchanged lines point at their DB counterpart and only the
   * truly changed lines map to 0.
   */
  @Test
  public void shouldRunMyersForLargeNearIdenticalWithChangesAtBothBoundaries() {
    int total = 70_000;
    int changedAtStart = 5;
    int changedAtEnd = 5;

    List<String> database = new ArrayList<>(total);
    List<String> report = new ArrayList<>(total);
    for (int i = 0; i < changedAtStart; i++) {
      database.add("db-start-" + i);
      report.add("rp-start-" + i);
    }
    int middleStart = changedAtStart;
    int middleEnd = total - changedAtEnd;
    for (int i = middleStart; i < middleEnd; i++) {
      String shared = "shared-" + i;
      database.add(shared);
      report.add(shared);
    }
    for (int i = 0; i < changedAtEnd; i++) {
      database.add("db-end-" + i);
      report.add("rp-end-" + i);
    }

    long start = System.nanoTime();
    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(diff).hasSize(total);
    // First `changedAtStart` report lines must be flagged as new (no DB match).
    for (int i = 0; i < changedAtStart; i++) {
      assertThat(diff[i]).as("changed-at-start report line " + i + " must be new").isZero();
    }
    // Middle lines must map 1:1 to DB lines (1-indexed).
    for (int i = middleStart; i < middleEnd; i++) {
      assertThat(diff[i]).as("middle shared report line " + i + " must map to db line " + (i + 1))
        .isEqualTo(i + 1);
    }
    // Last `changedAtEnd` report lines must be flagged as new.
    for (int i = middleEnd; i < total; i++) {
      assertThat(diff[i]).as("changed-at-end report line " + i + " must be new").isZero();
    }
    assertThat(elapsedMs).as("near-identical 70K x 70K must complete fast — wall time was " + elapsedMs + " ms")
      .isLessThan(5_000L);
  }

  /**
   * Verifies that the hash-matching fallback recovers SOME mapping when content
   * is partially shared but the overall shape is too expensive for Myers. Here:
   * 8K-line DB and 8K-line report, fully disjoint at the boundaries, but with a
   * common shuffled middle block — D is high enough to trip the work-budget
   * gate, but ~half the report lines should still be paired with a DB line of
   * matching content via the hash fallback. Crucially: not all-zeros.
   */
  @Test
  public void hashFallbackShouldRecoverMatchesWhenContentIsPartiallyShared() {
    int size = 8_000;
    List<String> database = buildLines(size, "db-");
    List<String> report = buildLines(size, "rp-");
    // Inject a shared block at known DB position -> known report position.
    for (int i = 1_000; i < 5_000; i++) {
      String shared = "shared-" + i;
      database.set(i, shared);
      report.set(size - 1 - i, shared);
    }

    int[] diff = new SourceLinesDiffFinder().findMatchingLines(database, report);

    assertThat(diff).hasSize(size);
    int matched = 0;
    for (int v : diff) {
      if (v != 0) {
        matched++;
      }
    }
    assertThat(matched).as("hash fallback must recover the shared-content lines (got " + matched + " matched of " + size + ")")
      .isGreaterThanOrEqualTo(3_500);
  }

  private static List<String> buildLines(int n, String prefix) {
    List<String> lines = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      lines.add(prefix + String.format("%07d", i));
    }
    return lines;
  }
}
