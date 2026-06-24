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
import javax.annotation.CheckForNull;

/**
 * Re-implementation of {@link difflib.myers.MyersDiff#buildPath(List, List)} that enforces a hard
 * bound on the amount of work performed. The algorithm, node types and resulting path are identical
 * to the library version; the only difference is that the search counts every diagonal step and
 * every line comparison, and gives up (returns {@code null}) once the budget is exhausted.
 *
 * <p>Bounding the search this way — instead of estimating cost up-front from the input sizes —
 * means a cheap diff is never refused: a 100k-line file with a handful of changed lines completes
 * in O(D * (N + M)) work no matter where in the file the changes sit, while a pathological input
 * (fully rewritten file, disjoint scanner delta) burns at most the budget before bailing out.
 *
 * <p>Work accounting, relied upon by the up-front skip in {@link SourceLinesDiffFinder}: each
 * (d, k) iteration charges {@code 1 + snakeLength} units, checked after the termination test, so
 * the terminating iteration itself is uncharged. A search that ends at depth D therefore charges
 * at least D * (D + 1) / 2 units (full depths 0..D-1), and exactly that on the cheapest shapes.
 */
class BoundedMyersDiff {

  private BoundedMyersDiff() {
    // static use only
  }

  /**
   * @return the final {@link PathNode} of the minimal diff path, or {@code null} if the search
   *         exceeded {@code maxWork} units (one unit = one diagonal step or one line comparison)
   */
  @CheckForNull
  static PathNode buildPath(List<String> orig, List<String> rev, long maxWork) {
    final int n = orig.size();
    final int m = rev.size();

    final int max = n + m + 1;
    final int size = 1 + 2 * max;
    final int middle = size / 2;
    final PathNode[] diagonal = new PathNode[size];

    diagonal[middle + 1] = new Snake(0, -1, null);
    long work = 0;
    for (int d = 0; d < max; d++) {
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

        diagonal[kminus] = null;

        int j = i - k;

        PathNode node = new DiffNode(i, j, prev);

        int snakeStart = i;
        while (i < n && j < m && orig.get(i).equals(rev.get(j))) {
          i++;
          j++;
        }
        if (i > snakeStart) {
          node = new Snake(i, j, node);
        }

        diagonal[kmiddle] = node;

        if (i >= n && j >= m) {
          return diagonal[kmiddle];
        }

        work += 1L + (i - snakeStart);
        if (work > maxWork) {
          return null;
        }
      }
      diagonal[middle + d - 1] = null;
    }
    // unreachable: Myers always finds a path of length <= N + M
    return null;
  }
}
