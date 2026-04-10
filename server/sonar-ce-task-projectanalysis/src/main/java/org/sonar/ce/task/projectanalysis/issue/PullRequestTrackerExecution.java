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
package org.sonar.ce.task.projectanalysis.issue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.issue.Issue;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.component.ConfigurationRepository;
import org.sonar.ce.task.projectanalysis.source.NewLinesRepository;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.tracking.Input;
import org.sonar.core.issue.tracking.Tracker;
import org.sonar.core.issue.tracking.Tracking;

/**
 * Executes issue tracking for pull request analyses in three steps:
 * <ol>
 *   <li><b>Step 1 — Changed-line filtering:</b> When the {@code codescan.pullRequest.filterIssuesByChangedLines}
 *       configuration property is enabled (default {@code true}), only raw issues that have at least one
 *       location on a changed line are retained for further tracking.</li>
 *   <li><b>Step 2 — Target-branch resolved tracking:</b> If a target-branch input is provided, issues that are
 *       already resolved on the target branch are matched and removed from the unmatched set, preventing
 *       them from being surfaced as new findings on the pull request.</li>
 *   <li><b>Step 3 — Previous-analysis tracking:</b> The remaining unmatched issues are tracked against the
 *       previous analysis of the same pull request so that pre-existing PR issues keep their identity.</li>
 * </ol>
 * <p>Several early-exit and deferred-load optimisations are applied to avoid unnecessary database or
 * hash-sequence work when intermediate result sets are empty.</p>
 */
public class PullRequestTrackerExecution {
  private final TrackerBaseInputFactory baseInputFactory;
  private final Tracker<DefaultIssue, DefaultIssue> tracker;
  private final NewLinesRepository newLinesRepository;
  private final ConfigurationRepository config;
  private final TrackerTargetBranchInputFactory targetBranchInputFactory;

  public PullRequestTrackerExecution(TrackerBaseInputFactory baseInputFactory,
    Tracker<DefaultIssue, DefaultIssue> tracker, NewLinesRepository newLinesRepository, ConfigurationRepository config,
    TrackerTargetBranchInputFactory targetBranchInputFactory) {
    this.baseInputFactory = baseInputFactory;
    this.tracker = tracker;
    this.newLinesRepository = newLinesRepository;
    this.config = config;
    this.targetBranchInputFactory = targetBranchInputFactory;
  }

  /**
   * Runs the three-step pull request issue tracking pipeline for the given {@code component}.
   *
   * <p><b>Step 1</b> filters {@code rawInput} issues to those touching changed lines (when
   * {@code codescan.pullRequest.filterIssuesByChangedLines} is {@code true}).
   * If no issues survive this filter an early exit is taken and a trivial {@link Tracking} is returned
   * immediately, avoiding all further database and hash-sequence I/O.</p>
   *
   * <p><b>Step 2</b> matches the filtered raws against resolved issues on the target branch (when
   * {@code targetInput} is non-null). When {@code filterIssuesByChangedLines} is {@code true} a
   * resolved-only target input is obtained from {@link TrackerTargetBranchInputFactory} to avoid
   * loading the full target-branch issue set. If no resolved target issues exist the hash-sequence
   * load is also skipped.</p>
   *
   * <p><b>Step 3</b> tracks the remaining unmatched issues against the previous analysis of the same
   * pull request.</p>
   *
   * @param component   the component being analysed
   * @param rawInput    issues detected in the current analysis
   * @param targetInput issues from the target branch, or {@code null} if unavailable
   * @return the combined non-closed tracking result
   */
  public Tracking<DefaultIssue, DefaultIssue> track(Component component, Input<DefaultIssue> rawInput, @Nullable Input<DefaultIssue> targetInput) {
    // Step 1: only keep issues on changed lines
    List<DefaultIssue> filteredRaws;
    boolean filterIssuesByChangedLines = config.getConfiguration().getBoolean("codescan.pullRequest.filterIssuesByChangedLines").orElse(Boolean.TRUE);
    if (filterIssuesByChangedLines) {
      filteredRaws = keepIssuesHavingAtLeastOneLocationOnChangedLines(component, rawInput.getIssues());
    } else {
      filteredRaws = rawInput.getIssues().stream().toList();
    }

    if (filteredRaws.isEmpty()) {
      Input<DefaultIssue> emptyInput = createInput(rawInput, Collections.emptyList());
      return tracker.trackNonClosed(emptyInput, emptyInput);
    }

    Input<DefaultIssue> unmatchedRawsAfterChangedLineFiltering = createInput(rawInput, filteredRaws);

    // Step 2: remove issues that are resolved in the target branch
    Input<DefaultIssue> unmatchedRawsAfterTargetResolvedTracking;
    if (targetInput != null) {
      Input<DefaultIssue> effectiveTargetInput;
      if (filterIssuesByChangedLines) {
        // Only need resolved issues — use dedicated resolved-only input to avoid loading all target branch issues
        effectiveTargetInput = targetBranchInputFactory.createResolvedForTargetBranch(component);
      } else {
        effectiveTargetInput = targetInput;
      }
      List<DefaultIssue> resolvedTargetIssues = effectiveTargetInput.getIssues().stream()
          .filter(i -> !filterIssuesByChangedLines || Issue.STATUS_RESOLVED.equals(i.status()))
          .toList();
      Input<DefaultIssue> resolvedTargetInput;
      if (resolvedTargetIssues.isEmpty()) {
        // No resolved target issues — avoid expensive hash sequence loading for huge files.
        resolvedTargetInput = createInput(rawInput, Collections.emptyList());
      } else {
        resolvedTargetInput = createInput(effectiveTargetInput, resolvedTargetIssues);
      }
      Tracking<DefaultIssue, DefaultIssue> prResolvedTracking = tracker.trackNonClosed(unmatchedRawsAfterChangedLineFiltering, resolvedTargetInput);
      unmatchedRawsAfterTargetResolvedTracking = createInput(rawInput, prResolvedTracking.getUnmatchedRaws().toList());
    } else {
      unmatchedRawsAfterTargetResolvedTracking = unmatchedRawsAfterChangedLineFiltering;
    }

    // Step 3: track issues with previous analysis of the current PR
    Input<DefaultIssue> previousAnalysisInput = baseInputFactory.create(component);
    return tracker.trackNonClosed(unmatchedRawsAfterTargetResolvedTracking, previousAnalysisInput);
  }

  private static Input<DefaultIssue> createInput(Input<DefaultIssue> input, Collection<DefaultIssue> issues) {
    return new DefaultTrackingInput(issues, input.getLineHashSequence(), input.getBlockHashSequence());
  }

  /**
   * Filters {@code issues} to those that have at least one location on a changed line for the given
   * {@code component}.
   *
   * <p>Returns an empty list immediately when:
   * <ul>
   *   <li>the component is not a {@link Component.Type#FILE};</li>
   *   <li>no changed-line information is available from {@link NewLinesRepository}; or</li>
   *   <li>the set of new lines is empty (short-circuit to avoid stream overhead).</li>
   * </ul>
   * </p>
   *
   * @param component the file component whose changed lines are inspected
   * @param issues    the candidate issues to filter
   * @return issues that touch at least one changed line, or an empty list
   */
  private List<DefaultIssue> keepIssuesHavingAtLeastOneLocationOnChangedLines(Component component, Collection<DefaultIssue> issues) {
    if (component.getType() != Component.Type.FILE) {
      return Collections.emptyList();
    }
    final Optional<Set<Integer>> newLinesOpt = newLinesRepository.getNewLines(component);
    if (newLinesOpt.isEmpty()) {
      return Collections.emptyList();
    }
    final Set<Integer> newLines = newLinesOpt.get();
    if (newLines.isEmpty()) {
      return Collections.emptyList();
    }
    return issues.stream()
      .filter(i -> IssueLocations.allLinesFor(i, component.getUuid()).anyMatch(newLines::contains))
      .toList();
  }

}
