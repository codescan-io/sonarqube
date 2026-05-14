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
   * Upper bound on {@code core_left * core_right} cells of work permitted inside
   * {@link MyersDiff#buildPath(List, List)} after common-prefix / common-suffix trimming.
   * Myers diff cost is O(D * (N + M)); when the divergent cores are large and disjoint,
   * D approaches N + M and the algorithm collapses to O(N^2). 4 000 000 cells caps the
   * worst-case in-memory work at roughly 1 s on production hardware.
   */
  static final long DIFF_COMPLEXITY_THRESHOLD = 4_000_000L;

  /**
   * Upper bound on {@code max(core_left, core_right) / min(core_left, core_right)} after
   * common-prefix / common-suffix trimming. When one side is much larger than the other,
   * the edit distance D is forced to be at least {@code |N - M|} and Myers diff has to
   * touch ~D * (N + M) cells regardless of content. The asymmetric shape is the signature
   * of "small scanner delta against a large reference-branch file" (e.g. ARM EZ-Commit
   * sending 50 changed lines against a 30 000-line Salesforce metadata file).
   */
  static final int DIFF_ASYMMETRY_RATIO = 100;

  /**
   * Floor below which the asymmetry ratio is not enforced. For small files the absolute
   * cost is negligible no matter the ratio (e.g. 100 x 1 has ratio 100 but completes in
   * microseconds), so this avoids tripping the gate on trivial inputs.
   */
  static final int DIFF_ASYMMETRY_MIN_SIZE = 5_000;

  public int[] findMatchingLines(List<String> left, List<String> right) {
    int n = left.size();
    int m = right.size();
    int[] index = new int[m];

    // 1. Trim common prefix (cheap: equal hashes only). Prefix lines map 1:1 to DB.
    int prefix = 0;
    int maxPrefix = Math.min(n, m);
    while (prefix < maxPrefix && left.get(prefix).equals(right.get(prefix))) {
      index[prefix] = prefix + 1;
      prefix++;
    }

    // 2. Trim common suffix. Suffix lines map 1:1 to the tail of the DB.
    int suffix = 0;
    int maxSuffix = Math.min(n, m) - prefix;
    while (suffix < maxSuffix
      && left.get(n - 1 - suffix).equals(right.get(m - 1 - suffix))) {
      index[m - 1 - suffix] = n - suffix;
      suffix++;
    }

    int leftCore = n - prefix - suffix;
    int rightCore = m - prefix - suffix;

    // 3. If either core is empty the remaining mapping is trivially zero (no possible
    // matches) — return what prefix/suffix already produced.
    if (leftCore == 0 || rightCore == 0) {
      return index;
    }

    // 4. Apply gates against the CORE sizes (not the raw inputs). We only refuse work
    // that is *forced* to be expensive — never near-identical large files whose prefix
    // and suffix trim away most of the bulk.
    if ((long) leftCore * rightCore > DIFF_COMPLEXITY_THRESHOLD) {
      LOG.warn("Skipping Myers diff: divergent core {}x{} (full input {}x{}) exceeds complexity threshold {}; treating unmatched report lines as new.",
        leftCore, rightCore, n, m, DIFF_COMPLEXITY_THRESHOLD);
      return index;
    }
    int maxCore = Math.max(leftCore, rightCore);
    int minCore = Math.min(leftCore, rightCore);
    if (maxCore >= DIFF_ASYMMETRY_MIN_SIZE && maxCore / minCore > DIFF_ASYMMETRY_RATIO) {
      LOG.warn("Skipping Myers diff: asymmetric divergent core {}x{} (full input {}x{}, ratio {}) exceeds ratio threshold {}; treating unmatched report lines as new.",
        leftCore, rightCore, n, m, maxCore / minCore, DIFF_ASYMMETRY_RATIO);
      return index;
    }

    // 5. Run Myers on the divergent cores only.
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
