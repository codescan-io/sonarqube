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

import difflib.myers.DiffNode;
import difflib.myers.PathNode;
import difflib.myers.Snake;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceLinesDiffFinder {
  private static final Logger LOG = LoggerFactory.getLogger(SourceLinesDiffFinder.class);

  /**
   * Maximum Myers edit distance the line diff is allowed to compute. Myers' cost grows with
   * the edit distance D, not file size, so near-identical files stay cheap while two largely
   * unrelated files push D towards N + M and degrade to ~O(D^2). Capping D bounds that worst
   * case; within the cap the result is identical to an uncapped Myers diff, and beyond it we
   * fall back to {@link #matchCommonPrefixSuffix}.
   */
  static final int MAX_EDIT_DISTANCE = 5_000;

  public int[] findMatchingLines(List<String> left, List<String> right) {
    int[] index = new int[right.size()];

    PathNode node = buildPathWithinEditDistance(left, right, MAX_EDIT_DISTANCE);
    if (node == null) {
      // Edit distance exceeds the cap; fall back to matching the common prefix/suffix only.
      return matchCommonPrefixSuffix(left, right, index);
    }

    int dbLine = left.size();
    int reportLine = right.size();
    while (node.prev != null) {
      PathNode prevNode = node.prev;

      if (!node.isSnake()) {
        // additions
        reportLine -= (node.j - prevNode.j);
        // removals
        dbLine -= (node.i - prevNode.i);
      } else {
        // matches
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

  /**
   * Fallback used when the edit distance exceeds {@link #MAX_EDIT_DISTANCE}. Maps the common
   * leading and trailing lines, which are unchanged and aligned 1:1, and leaves the divergent
   * middle unmatched (= new). This only ever under-reports matches, never produces a wrong one.
   */
  private static int[] matchCommonPrefixSuffix(List<String> left, List<String> right, int[] index) {
    int n = left.size();
    int m = right.size();

    int prefix = 0;
    int maxPrefix = Math.min(n, m);
    while (prefix < maxPrefix && left.get(prefix).equals(right.get(prefix))) {
      index[prefix] = prefix + 1;
      prefix++;
    }

    int suffix = 0;
    int maxSuffix = Math.min(n, m) - prefix;
    while (suffix < maxSuffix && left.get(n - 1 - suffix).equals(right.get(m - 1 - suffix))) {
      index[m - 1 - suffix] = n - suffix;
      suffix++;
    }

    LOG.warn("Line diff between DB ({} lines) and report ({} lines) exceeds the {}-edit cap; "
      + "mapped {} common leading and {} common trailing lines, treating the divergent middle as new.",
      n, m, MAX_EDIT_DISTANCE, prefix, suffix);
    return index;
  }

  /**
   * Greedy Myers shortest-edit-script search, transcribed from
   * {@code difflib.myers.MyersDiff#buildPath} (java-diff-utils 1.3.0) with one change: the
   * outer loop stops once the edit distance {@code d} exceeds {@code maxEditDistance}.
   *
   * @return the end {@link PathNode} of the shortest edit path, or {@code null} if no path
   *         is found within {@code maxEditDistance} edits.
   */
  private static PathNode buildPathWithinEditDistance(List<String> orig, List<String> rev, int maxEditDistance) {
    final int n = orig.size();
    final int m = rev.size();

    final int max = n + m + 1;

    // The greedy search only explores diagonals k in [-d, d] up to the cap, so size the array
    // to that band rather than to O(n + m) to avoid allocating a large array it never uses.
    final int dLimit = Math.min(max, maxEditDistance + 1);
    final int middle = dLimit + 1;
    final int size = 2 * middle + 1;
    final PathNode[] diagonal = new PathNode[size];

    diagonal[middle + 1] = new Snake(0, -1, null);

    for (int d = 0; d < dLimit; d++) {
      for (int k = -d; k <= d; k += 2) {
        final int kmiddle = middle + k;
        final int kplus = kmiddle + 1;
        final int kminus = kmiddle - 1;
        PathNode prev;

        int i;
        if ((k == -d) || (k != d && diagonal[kminus].i < diagonal[kplus].i)) {
          i = diagonal[kplus].i;
          prev = diagonal[kplus];
        } else {
          i = diagonal[kminus].i + 1;
          prev = diagonal[kminus];
        }

        diagonal[kminus] = null; // no longer used

        int j = i - k;

        PathNode node = new DiffNode(i, j, prev);

        // orig and rev are zero-based but the algorithm is one-based, that's why there is
        // no +1 when indexing the sequences.
        while (i < n && j < m && orig.get(i).equals(rev.get(j))) {
          i++;
          j++;
        }
        if (i > node.i) {
          node = new Snake(i, j, node);
        }

        diagonal[kmiddle] = node;

        if (i >= n && j >= m) {
          // reached the end
          return diagonal[kmiddle];
        }
      }
      diagonal[middle + d - 1] = null;
    }
    // Either the edit distance exceeds maxEditDistance, or (per Myers) no path exists.
    return null;
  }

}
