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

import difflib.myers.PathNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceLinesDiffFinder {
    private static final Logger LOG = LoggerFactory.getLogger(SourceLinesDiffFinder.class);

    /**
     * Hard ceiling on the work performed by a single Myers diff, in units of one diagonal
     * step or one line comparison inside {@link BoundedMyersDiff}. Myers cost is
     * O(D * (N + M)); on fully disjoint inputs D = N + M and the search degenerates to
     * O((N + M)^2) — unbounded, this took ~19 minutes on a 100k-line Salesforce profile
     * in production. 16M units is roughly 50-300 ms on production hardware, and matches
     * the de-facto allowance of the previous 4M-cell grid gate (a 2000x2000 disjoint core
     * sat exactly on that gate and costs ~16M steps).
     *
     * <p>The budget is enforced in two complementary ways:
     * <ul>
     *   <li>up-front: the edit distance D is at least |N - M|, and before a search can
     *       terminate at depth D it must fully execute depths 0..D-1 — that is
     *       D * (D + 1) / 2 diagonal iterations, each charging at least one work unit
     *       (the terminating iteration itself returns before charging). So when
     *       |N - M| * (|N - M| + 1) / 2 already exceeds the budget, the in-flight counter
     *       is guaranteed to trip first and the search is skipped without starting
     *       (the asymmetric "small scanner delta vs large reference-branch file" shape
     *       lands here). Note the bound is triangular, NOT D^2: a D^2 gate would also
     *       skip affordable diffs in the band where D^2/2 &le; budget &lt; D^2, e.g. a
     *       ~5000-line block inserted into an otherwise similar file;</li>
     *   <li>in-flight: otherwise the search runs and gives up once it has actually spent the
     *       budget ({@link BoundedMyersDiff#buildPath} returns {@code null}).</li>
     * </ul>
     *
     * <p>Unlike a gate estimated from the N*M grid size, this never refuses a cheap diff:
     * a 100k-line file with a handful of changed lines completes well within budget no
     * matter where in the file the changes sit, even when prefix/suffix trimming removes
     * nothing. When the budget is hit, unmatched report lines are conservatively treated
     * as new.
     */
    static final long MYERS_WORK_BUDGET = 16_000_000L;

    public int[] findMatchingLines(List<String> left, List<String> right) {
        long startNanos = System.nanoTime();
        int n = left.size();
        int m = right.size();
        int[] index = new int[m];

        LOG.info("[MyersDiff] findMatchingLines start: left(DB/previous)={} lines, right(report/current)={} lines, workBudget={}",
                n, m, MYERS_WORK_BUDGET);

        // 1. Trim common prefix. Prefix lines map 1:1 to DB.
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
        LOG.info("[MyersDiff] trimmed: prefix={}, suffix={}, divergent cores {}x{}", prefix, suffix, leftCore, rightCore);

        // 3. If either core is empty the remaining mapping is trivially zero (no possible
        // matches) — return what prefix/suffix already produced.
        if (leftCore == 0 || rightCore == 0) {
            logResult(index, "resolved by prefix/suffix trim", startNanos);
            return index;
        }

        // 4. Up-front budget check. The edit distance is at least |leftCore - rightCore|
        // (you cannot do fewer edits than the size difference). Before BoundedMyersDiff can
        // terminate at depth D it fully executes depths 0..D-1 — D * (D + 1) / 2 iterations,
        // each charging at least one work unit — so when that triangular floor alone exceeds
        // the budget, the in-flight counter is mathematically guaranteed to trip and the
        // search can be skipped without starting. This is exactly conservative: if the floor
        // fits the budget, the search runs and decides for itself (a D^2 gate here would
        // wrongly skip affordable diffs whose cost lies between D^2/2 and D^2).
        long minEditDistance = Math.abs((long) rightCore - leftCore);
        if (minEditDistance * (minEditDistance + 1) / 2 > MYERS_WORK_BUDGET) {
            LOG.warn("[MyersDiff] Skipping Myers diff: divergent core {}x{} (full input {}x{}) forces edit distance >= {}, "
                            + "search is guaranteed to exceed work budget {}; treating unmatched report lines as new",
                    leftCore, rightCore, n, m, minEditDistance, MYERS_WORK_BUDGET);
            logResult(index, "skipped up-front (forced edit distance too large)", startNanos);
            return index;
        }

        // 5. Run budget-bounded Myers on the divergent cores only.
        PathNode node = BoundedMyersDiff.buildPath(
                left.subList(prefix, prefix + leftCore),
                right.subList(prefix, prefix + rightCore),
                MYERS_WORK_BUDGET);
        if (node == null) {
            LOG.warn("[MyersDiff] Giving up on Myers diff: divergent core {}x{} (full input {}x{}) exhausted work budget {}; "
                            + "treating unmatched report lines as new",
                    leftCore, rightCore, n, m, MYERS_WORK_BUDGET);
            logResult(index, "gave up in-flight (work budget exhausted)", startNanos);
            return index;
        }

        // 6. Walk the path backwards, translating core positions back into full-input coordinates.
        int dbLine = leftCore;
        int reportLine = rightCore;
        while (node.prev != null) {
            PathNode prevNode = node.prev;

            if (!node.isSnake()) {
                // additions
                reportLine -= node.j - prevNode.j;
                // removals
                dbLine -= node.i - prevNode.i;
            } else {
                // matches
                for (int i = node.i; i > prevNode.i; i--) {
                    index[prefix + reportLine - 1] = prefix + dbLine;
                    reportLine--;
                    dbLine--;
                }
            }
            node = prevNode;
        }
        logResult(index, "completed", startNanos);
        return index;
    }

    private static void logResult(int[] index, String outcome, long startNanos) {
        int matched = 0;
        for (int value : index) {
            if (value > 0) {
                matched++;
            }
        }
        LOG.info("[MyersDiff] findMatchingLines end ({}): total={}, matched={}, new={}, elapsedMs={}",
                outcome, index.length, matched, index.length - matched, (System.nanoTime() - startNanos) / 1_000_000L);
        if (LOG.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < index.length; i++) {
                sb.append('[').append(i).append("]=").append(index[i]).append(' ');
            }
            LOG.trace("[MyersDiff] index contents (reportLineIdx=dbLineNo): {}", sb);
        }
    }

}
