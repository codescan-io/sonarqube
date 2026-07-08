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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceLinesDiffFinder {
  private static final Logger LOG = LoggerFactory.getLogger(SourceLinesDiffFinder.class);

  /**
   * Asymmetry quick-reject threshold. When one input is at least this many times the size
   * of the other (and both meet {@link #DIFF_ASYMMETRY_MIN_SIZE}), edit distance D is
   * forced to be at least {@code |N - M|}, so Myers diff work is at least quadratic in
   * {@code max(N, M)} regardless of content. This shape — a small scanner delta against
   * a large reference-branch file — has been observed in production to produce CE step
   * times on the order of tens of minutes.
   *
   * <p>Returning an all-zero index here is semantically equivalent to what Myers itself
   * produces for purely disjoint asymmetric inputs (no LCS, every report line is new).
   * For asymmetric inputs that happen to share some content with the DB, this is a small
   * known degradation — see the PR description for the trade-off rationale.
   */
  static final int DIFF_ASYMMETRY_RATIO = 100;
  static final int DIFF_ASYMMETRY_MIN_SIZE = 5_000;

  public int[] findMatchingLines(List<String> left, List<String> right) {
    int n = left.size();
    int m = right.size();
    int[] index = new int[m];

    // 1. Trim common prefix and suffix. Standard diff preprocessing: the optimal LCS of
    // (left, right) is (common-prefix) + LCS(cores) + (common-suffix), so we can recurse
    // on the divergent cores. Used by git, GNU diff, JGit, etc. Zero correctness risk.
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

    // If either core is empty the remaining mapping is trivially zero.
    if (leftCore == 0 || rightCore == 0) {
      return index;
    }

    // 2. Asymmetry quick-reject. Catches the small-delta-vs-large-DB pathology in O(1).
    int maxCore = Math.max(leftCore, rightCore);
    int minCore = Math.min(leftCore, rightCore);
    if (maxCore >= DIFF_ASYMMETRY_MIN_SIZE && maxCore / minCore > DIFF_ASYMMETRY_RATIO) {
      LOG.warn("Skipping Myers diff: asymmetric core {}x{} (full input {}x{}, ratio {}) exceeds ratio threshold {}; treating unmatched report lines as new.",
        leftCore, rightCore, n, m, maxCore / minCore, DIFF_ASYMMETRY_RATIO);
      return index;
    }

    // 3. Run Myers on the divergent cores. Symmetric cases — including large near-identical
    // files where the trim could not reduce — go through here exactly as before.
    List<String> leftCoreList = left.subList(prefix, prefix + leftCore);
    List<String> rightCoreList = right.subList(prefix, prefix + rightCore);

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

}
