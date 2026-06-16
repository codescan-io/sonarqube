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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceLinesDiffFinder {
  private static final Logger LOG = LoggerFactory.getLogger(SourceLinesDiffFinder.class);

  /**
   * Upper bound on the estimated Myers work {@code D_est * (core_left + core_right)} for the
   * divergent core after prefix/suffix trimming. Myers diff cost is O(D * (N + M)) where D is
   * the actual edit distance; estimating D from the line-hash multiset overlap lets us gate on
   * predicted cost rather than on input size, so near-identical large files (where D is small
   * but trim cannot help because changes sit at both boundaries) keep using Myers and produce
   * a correct mapping.
   *
   * <p>5 * 10^7 keeps the worst-case allowed run under ~1 s on production hardware
   * (~19 ns per op observed on cloud VMs) while permitting the common case of large files with
   * a few scattered edits.
   */
  static final long DIFF_WORK_BUDGET = 50_000_000L;

  /**
   * Quick-reject: when one core is much larger than the other, edit distance D is forced to be
   * at least {@code |max - min|}, so Myers work is at least quadratic in {@code max} regardless
   * of content. This catches the ARM EZ-Commit signature (small scanner delta against a large
   * reference-branch file) in O(1) before the more expensive multiset overlap pass runs.
   */
  static final int DIFF_ASYMMETRY_RATIO = 100;

  /**
   * Floor below which the asymmetry ratio is not enforced. For small files the absolute cost is
   * negligible no matter the ratio.
   */
  static final int DIFF_ASYMMETRY_MIN_SIZE = 5_000;

  public int[] findMatchingLines(List<String> left, List<String> right) {
    int n = left.size();
    int m = right.size();
    int[] index = new int[m];

    // 1. Trim common prefix and suffix. Prefix/suffix lines map 1:1 to DB.
    int prefix = 0;
    int maxPrefix = Math.min(n, m);
    while (prefix < maxPrefix && left.get(prefix).equals(right.get(prefix))) {
      index[prefix] = prefix + 1;
      prefix++;
    }
    int suffix = 0;
    int maxSuffix = Math.min(n, m) - prefix;
    while (suffix < maxSuffix
      && left.get(n - 1 - suffix).equals(right.get(m - 1 - suffix))) {
      index[m - 1 - suffix] = n - suffix;
      suffix++;
    }

    int leftCore = n - prefix - suffix;
    int rightCore = m - prefix - suffix;

    // 2. If either core is empty the remaining mapping is trivially zero.
    if (leftCore == 0 || rightCore == 0) {
      return index;
    }

    int leftCoreStart = prefix;
    int leftCoreEnd = n - suffix;
    int rightCoreStart = prefix;
    int rightCoreEnd = m - suffix;

    // 3. Quick-reject asymmetric cores in O(1).
    int maxCore = Math.max(leftCore, rightCore);
    int minCore = Math.min(leftCore, rightCore);
    if (maxCore >= DIFF_ASYMMETRY_MIN_SIZE && maxCore / minCore > DIFF_ASYMMETRY_RATIO) {
      LOG.warn("Skipping Myers diff: asymmetric core {}x{} (full input {}x{}, ratio {}) exceeds ratio threshold {}; falling back to hash-based line matching.",
        leftCore, rightCore, n, m, maxCore / minCore, DIFF_ASYMMETRY_RATIO);
      fillIndexViaHashMatching(left, right, index, leftCoreStart, leftCoreEnd, rightCoreStart, rightCoreEnd);
      return index;
    }

    // 4. Estimate edit distance from line-hash multiset overlap (O(coreLeft + coreRight)).
    // overlap <= LCS(coreLeft, coreRight); D >= (coreLeft - overlap) + (coreRight - overlap).
    int overlap = computeMultisetOverlap(left, right, leftCoreStart, leftCoreEnd, rightCoreStart, rightCoreEnd);
    long dEstimate = (long) (leftCore - overlap) + (rightCore - overlap);
    long workEstimate = dEstimate * ((long) leftCore + rightCore);

    if (workEstimate > DIFF_WORK_BUDGET) {
      LOG.warn("Skipping Myers diff: estimated work {} (D_est {}, overlap {}, core {}x{}, full input {}x{}) exceeds budget {}; falling back to hash-based line matching.",
        workEstimate, dEstimate, overlap, leftCore, rightCore, n, m, DIFF_WORK_BUDGET);
      fillIndexViaHashMatching(left, right, index, leftCoreStart, leftCoreEnd, rightCoreStart, rightCoreEnd);
      return index;
    }

    // 5. Run Myers on the divergent cores.
    List<String> leftCoreList = left.subList(leftCoreStart, leftCoreEnd);
    List<String> rightCoreList = right.subList(rightCoreStart, rightCoreEnd);

    int dbLine = leftCore;
    int reportLine = rightCore;
    try {
      PathNode node = new MyersDiff<String>().buildPath(leftCoreList, rightCoreList);

      while (node.prev != null) {
        PathNode prevNode = node.prev;

        if (!node.isSnake()) {
          // additions
          reportLine -= (node.j - prevNode.j);
          // removals
          dbLine -= (node.i - prevNode.i);
        } else {
          // matches — translate core positions back into full-input coordinates
          for (int i = node.i; i > prevNode.i; i--) {
            index[prefix + reportLine - 1] = prefix + dbLine;
            reportLine--;
            dbLine--;
          }
        }
        node = prevNode;
      }
    } catch (DifferentiationFailedException e) {
      LOG.error("Error finding matching lines", e);
      return index;
    }
    return index;
  }

  /**
   * Counts how many report-core lines have a matching line-hash in db-core, treated as
   * multisets (so duplicate lines are paired one-to-one). The returned value is an upper bound
   * on the LCS of the two cores, so {@code (coreLeft - overlap) + (coreRight - overlap)} is a
   * lower bound on the edit distance D — which makes the resulting work estimate conservative
   * (we never under-estimate cost). O(coreLeft + coreRight) time, O(unique hashes) space.
   */
  private static int computeMultisetOverlap(List<String> left, List<String> right,
    int leftStart, int leftEndExclusive, int rightStart, int rightEndExclusive) {
    Map<String, int[]> counts = new HashMap<>();
    for (int i = leftStart; i < leftEndExclusive; i++) {
      counts.computeIfAbsent(left.get(i), k -> new int[1])[0]++;
    }
    int overlap = 0;
    for (int j = rightStart; j < rightEndExclusive; j++) {
      int[] c = counts.get(right.get(j));
      if (c != null && c[0] > 0) {
        c[0]--;
        overlap++;
      }
    }
    return overlap;
  }

  /**
   * Greedy hash-based fallback used when Myers diff would be too expensive on the divergent
   * core. For each report-core line, pair it with the first remaining db-core line with the
   * same line-hash. Does not preserve order, so the produced mapping is not an LCS — but for
   * the consumers of this API (coverage / size / maintainability metrics, issue new-line
   * classification, source-viewer "is this line new" marker, SCM author attribution) the
   * relevant signal is whether a report line has *any* prior counterpart with the same
   * content. Far better than treating every report line as new, which is what the previous
   * zero-fill fallback did.
   *
   * <p>O(coreLeft + coreRight) time, O(coreLeft) space.
   */
  private static void fillIndexViaHashMatching(List<String> left, List<String> right, int[] index,
    int leftStart, int leftEndExclusive, int rightStart, int rightEndExclusive) {
    Map<String, ArrayDeque<Integer>> dbByHash = new HashMap<>();
    for (int i = leftStart; i < leftEndExclusive; i++) {
      dbByHash.computeIfAbsent(left.get(i), k -> new ArrayDeque<>()).add(i + 1);
    }
    for (int j = rightStart; j < rightEndExclusive; j++) {
      ArrayDeque<Integer> positions = dbByHash.get(right.get(j));
      if (positions != null && !positions.isEmpty()) {
        index[j] = positions.pollFirst();
      }
    }
  }

}
