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
   * Maximum Myers edit distance (number of single-line insertions + deletions) the line
   * diff is allowed to compute. Myers' greedy algorithm walks d = 0, 1, 2, … and finishes
   * at d = D, the actual edit distance, so its cost grows with D, not with file size: a
   * large file with few changed lines is cheap, while two largely-unrelated files force D
   * towards N + M and the algorithm degrades to ~O(D^2).
   *
   * <p>This is exactly the shape that previously hung analysis for up to ~19 minutes — a
   * small scanner delta sent against a large, unrelated reference-branch file (ARM
   * EZ-Commit on the Bank of Ireland instance). Capping D bounds that worst case to well
   * under a second while leaving every realistic diff (which has a small D) completely
   * unaffected: when the algorithm finishes within the cap the result is identical to an
   * uncapped Myers diff. Only when D exceeds the cap do we stop early and fall back to
   * {@link #matchCommonPrefixSuffix} (see there for why that is still correct).
   */
  static final int MAX_EDIT_DISTANCE = 5_000;

  public int[] findMatchingLines(List<String> left, List<String> right) {
    int[] index = new int[right.size()];

    PathNode node = buildPathWithinEditDistance(left, right, MAX_EDIT_DISTANCE);
    if (node == null) {
      // Edit distance exceeds the cap: a full line-level diff would be ~O(D^2) and is what
      // previously hung analysis. We do NOT discard the whole mapping — we still match the
      // common leading/trailing lines (which are unchanged and aligned 1:1) and leave only
      // the divergent middle as new. See matchCommonPrefixSuffix.
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
   * Fallback used only when the edit distance exceeds {@link #MAX_EDIT_DISTANCE} and a full
   * line-level diff is therefore too expensive to compute. It maps the common leading lines
   * and the common trailing lines — which are unchanged and aligned 1:1 between the two
   * versions — and leaves the divergent middle unmatched (= new).
   *
   * <p>This never produces a wrong match: a leading line {@code i} that is byte-identical in
   * both versions IS the same line, likewise for trailing lines counted from the end. It only
   * ever <em>under</em>-reports matches (some middle lines that a full diff might have paired
   * are reported as new), which is the safe direction. It is strictly more accurate than
   * treating the whole file as new: a large file with an unchanged head and tail keeps that
   * head and tail correctly tracked, while two genuinely disjoint files (no common head/tail)
   * still map to all-new — the correct outcome for the EZ-Commit / Bank of Ireland shape.
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
   * {@code difflib.myers.MyersDiff#buildPath} (java-diff-utils 1.3.0) with a single
   * change: the outer loop stops once the edit distance {@code d} exceeds
   * {@code maxEditDistance}. Reuses difflib's own {@link PathNode}/{@link DiffNode}/
   * {@link Snake} nodes and the same diagonal bookkeeping, so for any input whose edit
   * distance is within the cap it returns a path identical to the upstream algorithm.
   *
   * @return the end {@link PathNode} of the shortest edit path, or {@code null} if no path
   *         is found within {@code maxEditDistance} edits.
   */
  private static PathNode buildPathWithinEditDistance(List<String> orig, List<String> rev, int maxEditDistance) {
    final int n = orig.size();
    final int m = rev.size();

    final int max = n + m + 1;

    // The greedy search only explores diagonals k in [-d, d] for d up to the cap, so just a
    // band of width O(maxEditDistance) around the middle diagonal is ever touched. Size the
    // array to that band (with one slot of slack on each side) rather than to O(n + m), so a
    // diff of a large file does not allocate a multi-MB array it never uses. The middle index
    // is an arbitrary base offset — only the relative diagonal k matters — so this produces
    // the exact same result as the original O(n + m)-sized array.
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
