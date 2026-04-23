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
   * Maximum number of cells (left.size() * right.size()) the Myers diff algorithm is
   * allowed to process. Beyond this threshold the algorithm's worst-case O((N+M)·D)
   * complexity collapses to O(N²) for disjoint inputs (D ≈ N+M), producing multi-minute
   * runtimes for large Salesforce metadata files.
   *
   * <p>Calibration (Apple M-series, diffutils-1.3.0, Java 21):
   * <ul>
   *   <li>1 M cells (N=10K × M=100) → ~555 ms</li>
   *   <li>5 M cells (N=50K × M=100) → ~12 s</li>
   *   <li>10 M cells (N=100K × M=100) → ~71 s</li>
   * </ul>
   * A threshold of 4 M cells is chosen so that:
   * <ul>
   *   <li>N=50K×M=100 (5 M) is gated (customer scenario boi-241415000220432413)</li>
   *   <li>N=2K×M=2K (4 M) is NOT gated (symmetric near-identical files stay on Myers)</li>
   *   <li>On 2–4× slower cloud VMs, N=50K×M=100 un-gated would take 50–200 s</li>
   * </ul>
   *
   * <p>Package-private (not private) so that unit tests can assert on the exact value
   * without relying on wall-clock timing.
   *
   * <p>Follow-up: expose as a {@code sonar.ce.sourceLinesDiff.maxCells} server property
   * so operators on beefy instances can raise the limit.
   */
  static final long DIFF_COMPLEXITY_THRESHOLD = 4_000_000L;

  /**
   * Returns an array of length {@code right.size()} where {@code result[i]} is the
   * 1-based line number in {@code left} that matches {@code right.get(i)}, or 0 if
   * the right-side line has no match in {@code left}.
   *
   * <p>When {@code left.size() * (long) right.size() > DIFF_COMPLEXITY_THRESHOLD} the
   * Myers diff is skipped and a zeroed array is returned immediately (treating all
   * right-side lines as new). This is semantically identical to the degraded result
   * already returned by the {@link difflib.myers.DifferentiationFailedException} catch
   * below, and all downstream callers already handle a zeroed array correctly (they
   * treat unmatched lines as new code).
   */
  public int[] findMatchingLines(List<String> left, List<String> right) {
    int[] index = new int[right.size()];

    long cellBudget = (long) left.size() * right.size();
    if (cellBudget > DIFF_COMPLEXITY_THRESHOLD) {
      LOG.warn(
        "Skipping Myers diff for file with {} DB lines vs {} report lines ({} cells > threshold {}). " +
          "All report lines will be treated as new. " +
          "This typically happens for large Salesforce metadata files on ARM branches.",
        left.size(), right.size(), cellBudget, DIFF_COMPLEXITY_THRESHOLD
      );
      return index;
    }

    int dbLine = left.size();
    int reportLine = right.size();
    try {
      PathNode node = new MyersDiff<String>().buildPath(left, right);

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
    } catch (DifferentiationFailedException e) {
      LOG.error("Error finding matching lines", e);
      return index;
    }
    return index;
  }

}
