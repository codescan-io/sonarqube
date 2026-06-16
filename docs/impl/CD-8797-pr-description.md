# CD-8797 — `SourceLinesDiffFinder`: prefix/suffix trim + asymmetry gate

## Summary

Stops the Compute Engine from freezing on the production workload that prompted this ticket (19-minute `Compute new coverage` step on a 250K-line Salesforce metadata file with a 100-line ARM EZ-Commit delta) without changing the behaviour of `MyersDiff` for any case it already handles correctly.

Two minimal changes to `SourceLinesDiffFinder.findMatchingLines()`:

1. **Prefix / suffix trim.** Textbook diff preprocessing used by git, GNU diff, JGit. The optimal LCS of `(left, right)` provably equals `common-prefix + LCS(cores) + common-suffix`, so we strip the matching ends and recurse on the divergent cores. Zero correctness risk.
2. **Asymmetry quick-reject.** When `max/min > 100` and `max ≥ 5 000`, edit distance `D` is forced to be at least `|max − min|`, so Myers work is at least quadratic in `max` regardless of content. This shape is the ARM EZ-Commit signature (ratio 2 500 on the production 19-minute run). Returning a zero-filled `int[]` for it is semantically identical to what Myers itself produces for purely disjoint asymmetric inputs.

Everything else falls through to Myers exactly as before this PR.

## Why a caller-side fix doesn't generalise

`NewLinesRepository.getNewLines()` is invoked from six places in the CE pipeline; only two are `ComputationStep`s, the rest are visitors, classifiers, and trackers:

| Caller | Type |
|---|---|
| `NewCoverageMeasuresStep` | step |
| `NewSizeMeasuresStep` | step |
| `NewMaintainabilityMeasuresVisitor` | visitor |
| `IsNewLineReader` | source-viewer line reader |
| `NewIssueClassifier` | issue-tracking helper |
| `PullRequestTrackerExecution` | PR tracker helper |

The earlier fix in #697 / `82308bbde2e` patched only `NewCoverageMeasuresStep`. The slowness then moved to `NewSizeMeasuresStep`, and would have moved to the next caller as each was patched. The honest place for the guard is the algorithm itself.

## Root cause

`SourceLinesDiffFinder.findMatchingLines()` calls `new MyersDiff<String>().buildPath(left, right)` from `diffutils-1.3.0` with no size guard, no timeout, no iteration ceiling. Myers diff cost is `O(D × (N + M))` where `D` is the edit distance. When inputs are highly asymmetric and disjoint (a small scanner delta against a large reference-branch file), `D` is forced toward `max(N, M)`, collapsing complexity to `O(N²)`. Bytecode inspection of `MyersDiff.buildPath()` confirms the outer loop has no bailout. The single `catch (DifferentiationFailedException)` arm in the existing code is dead — that exception is only thrown when `2·(N + M + 1)` iterations exhaust without reaching the corner, which cannot happen for valid inputs.

Empirical scaling on M-series hardware (disjoint M = 100):

| N | Wall time |
|---:|---:|
| 10 000 | 555 ms |
| 30 000 | 4.3 s |
| 50 000 | 12.4 s |
| 100 000 | 70.6 s |
| 200 000 | 311 s |
| 250 000 (production observed) | ~10–15 min |

On production cloud VMs (2–4× slower) the times are correspondingly worse.

## The fix in code

```java
public int[] findMatchingLines(List<String> left, List<String> right) {
  int n = left.size();
  int m = right.size();
  int[] index = new int[m];

  // 1. Trim common prefix and suffix.
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
  if (leftCore == 0 || rightCore == 0) {
    return index;
  }

  // 2. Asymmetry quick-reject.
  int maxCore = Math.max(leftCore, rightCore);
  int minCore = Math.min(leftCore, rightCore);
  if (maxCore >= DIFF_ASYMMETRY_MIN_SIZE && maxCore / minCore > DIFF_ASYMMETRY_RATIO) {
    LOG.warn(...);
    return index; // zero-fill — identical to Myers output for disjoint asymmetric inputs
  }

  // 3. Run Myers on the divergent cores.
  // ... existing Myers buildPath loop, unchanged ...
}
```

Constants:

```java
static final int DIFF_ASYMMETRY_RATIO    = 100;
static final int DIFF_ASYMMETRY_MIN_SIZE = 5_000;
```

## Coverage matrix

| Case | Path taken | Outcome | vs. pre-PR behaviour |
|---|---|---|---|
| 250K × 100 disjoint (the production-observed bug) | asymmetry gate, zero-fill | ~1 ms | 19 min freeze → ~1 ms ✓ |
| 30K × 50 disjoint (ARM EZ-Commit shape) | asymmetry gate, zero-fill | ~1 ms | ~10 s → ~1 ms |
| 100K × 100K identical | trim only | 19 ms | Myers on full input, slow → trim, fast |
| 80K × 80K with 100-line middle diff | trim → 100×100 core → Myers | 3 ms | trim makes it faster |
| 70K × 70K with a few lines changed at both boundaries | Myers on full 70K × 70K | 3 ms | unchanged (Myers handles this fine, D ≈ 10) |
| 70K × 70K with 100 lines changed at start AND 100 at end | Myers on full 70K × 70K | 5 ms | unchanged (Myers handles this, D ≈ 400) |
| 5K × 5K symmetric disjoint | Myers on full input | ~500 ms–1 s | unchanged |
| 50K × 50K fully disjoint (rare) | Myers on full input, slow | minutes | unchanged |
| 11 existing golden small-input tests | trim or Myers as before | ms | preserved |

## What this PR explicitly does NOT do

Earlier iterations on this branch introduced and then removed a work-budget gate (based on an edit-distance estimate from line-hash multiset overlap) and a hash-matching fallback. Both replaced the well-trusted Myers algorithm in cases where Myers already worked correctly — for example, a 70K × 70K file with 100-line boundary changes, where Myers completes in milliseconds with the correct LCS-based mapping. That was over-reach; this PR removes it and keeps Myers as the default everywhere except the asymmetric-shape gate.

## Behavioural compatibility

When the asymmetry gate fires, the method returns an `int[]` filled with zeros — the same shape and content Myers produces for purely disjoint asymmetric inputs (no LCS → every report line is new). Downstream consumers (`IssueAssigner`, `IssueCreationDateCalculator`, `LastCommitVisitor`, `SourceLineReadersFactory`, `GeneratedScmInfo.create`) already tolerate zero entries (zero → "new line, attribute to analysis date").

Known small degradation: asymmetric inputs that happen to share *some* content with the DB get zero-fill instead of partial matches. Myers itself would have found those matches; the gate loses them. For ARM EZ-Commit workflows where the report is just the changed delta, coincidental matches against existing DB content are rare.

Constants are package-private so tests can reference them; they can be promoted to `sonar.*` server properties in a follow-up if per-instance tuning is needed.

## Test plan

All 17 tests in `SourceLinesDiffFinderTest` pass via a standalone JUnit-shaped harness running against the compiled main classpath. The module-level Gradle test task is currently blocked by pre-existing test-source compile errors in unrelated files on `codescan-24.12` (`PullRequestTrackerExecution`, `RuleRepositoryImpl`, `PersistComponentsStep`, etc.) — not introduced by this PR.

| Test | Shape | Outcome | Wall time |
|---|---|---|---:|
| 11 existing golden cases | small inputs | preserved | ms |
| `shouldReturnIdentityForLargeIdenticalInputsViaPrefixTrim` | 100K × 100K identical | trim, no Myers | 19 ms |
| `shouldRunMyersOnSmallCoreForLargeNearIdenticalInputs` | 80K × 80K, 100-line middle diff | trim → 100×100 core | 3 ms |
| `shouldShortCircuitOnAsymmetricDisjointInputs_30kVs50` | 30K × 50 disjoint | asymmetry gate | 0 ms |
| `shouldShortCircuitOnBoiScale_100kVs100Disjoint` | 100K × 100 disjoint | asymmetry gate | 0 ms |
| `shouldRunMyersForLargeNearIdenticalWithChangesAtBothBoundaries` | 70K × 70K, 5 changed at start + 5 at end | Myers on full input | 3 ms |
| `shouldRunMyersForLargeNearIdenticalWith100ChangesAtEachBoundary` | 70K × 70K, 100 changed at start + 100 at end | Myers on full input | 5 ms |

Also recommended before merging: smoke-test on a staging deployment with a real asymmetric-shape PR (large reference-branch file vs small ARM EZ-Commit delta).

## Commits

- `ac44a2efd89` — initial iteration: prefix/suffix trim + asymmetry + cell gates *(superseded)*
- `759d406015b` — replaced cell gate with D-estimate gate + hash-matching fallback *(superseded)*
- `d3746fabb74` — **dropped the work-budget gate and the hash-matching fallback; keeps Myers as the default for every case it can already handle**

## Risks and follow-ups

- **Symmetric large fully-disjoint files** (e.g. a 50K-line file where every line changed) remain slow under Myers. This was the pre-PR behaviour for years and is not on any known customer report; if it surfaces in production we can revisit with measured data instead of pre-emptive guard tuning.
- **Asymmetric inputs that share some content with the DB** get zero-fill from the asymmetry gate, losing those incidental matches. Rare in ARM EZ-Commit workflows.
- **Scanner-side root cause.** ARM/EZ-Commit omits explicit `changedLines` for Salesforce Profile/PermissionSet metadata, forcing the SCM fallback path that reaches this code. Fixing the scanner so `getChangedLinesFromReport()` returns early would mean this gate is never even consulted on that workload. Separate ticket recommended.
- **Time-budgeted Myers diff.** A long-term robustness improvement is to fork `diffutils-1.3.0` to add an elapsed-time bailout inside `MyersDiff.buildPath()` itself. Larger change, separate ticket.

## Linked context

- Original RCA case: see the customer support ticket linked in the Jira issue. Investigation artifacts are under the gitignored `.rca/` workspace.
- Prior partial fix: #697 / `82308bbde2e` (`NewCoverageMeasuresStep` short-circuit).
