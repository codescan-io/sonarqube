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
package org.sonar.server.issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.server.ServerSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.workflow.IssueWorkflow;


@ServerSide
@ComputeEngineSide
public class IssueMarkingProliferationService {

  private static final Logger LOG = LoggerFactory.getLogger(IssueMarkingProliferationService.class);

  /** @see io.codescan.cloud.DeveloperPlugin#KEY_CODESCAN_RESOLUTION_PROLIFERATION_ENABLED */
  public static final String KEY_RESOLUTION_PROLIFERATION_ENABLED = "codescan.cloud.resolutionProliferation.enabled";

  /** Only these markings proliferate; the other transitions (reopen, confirm, resolve as fixed) are untouched. */
  private static final Set<String> PROLIFERABLE_TRANSITIONS = Set.of(
    DefaultTransitions.EXCEPTION, DefaultTransitions.ACCEPT, DefaultTransitions.FALSE_POSITIVE,
    DefaultTransitions.WONT_FIX);

  /**
   * Candidate statuses on the other branches. An issue already resolved there is left alone - its own marking wins,
   * and re-stating it would add a duplicate comment on every subsequent run.
   */
  private static final Set<String> TARGET_STATUSES = Set.of(Issue.STATUS_OPEN, Issue.STATUS_REOPENED);

  /** How many issues are written per commit. Bounds both the transaction and the heap a thread holds. */
  private static final int SAVE_BATCH_SIZE = 500;

  private final DbClient dbClient;
  private final IssueWorkflow issueWorkflow;
  private final ProliferationIssueStorage issueStorage;
  private final IssueFieldsSetter issueFieldsSetter;

  public IssueMarkingProliferationService(DbClient dbClient, IssueWorkflow issueWorkflow,
    ProliferationIssueStorage issueStorage, IssueFieldsSetter issueFieldsSetter) {
    this.dbClient = dbClient;
    this.issueWorkflow = issueWorkflow;
    this.issueStorage = issueStorage;
    this.issueFieldsSetter = issueFieldsSetter;
  }


  public Set<String> proliferateToBranch(DbSession dbSession, Collection<IssueDto> sourceIssues,
    Map<Signature, List<IssueDto>> targetsBySignature, String targetBranchUuid, ProliferatedMarking marking) {

    Set<String> touchedComponents = new LinkedHashSet<>();
    if (!PROLIFERABLE_TRANSITIONS.contains(marking.transitionKey()) || targetsBySignature.isEmpty()) {
      return touchedComponents;
    }

    List<DefaultIssue> pending = new ArrayList<>(SAVE_BATCH_SIZE);
    Set<String> alreadyPrepared = new HashSet<>();
    List<String> written = new ArrayList<>();
    long markedAt = marking.context().date().getTime();
    for (IssueDto source : sourceIssues) {
      try {
        for (DefaultIssue prepared : prepareMatches(source, targetsBySignature, alreadyPrepared, marking)) {
          pending.add(prepared);
          touchedComponents.add(prepared.componentUuid());
          if (pending.size() >= SAVE_BATCH_SIZE) {
            written.addAll(issueStorage.save(dbSession, pending, targetBranchUuid, markedAt,
              marking.context().userUuid(), TARGET_STATUSES));
            pending.clear();
          }
        }
      } catch (RuntimeException e) {
        LOG.error("Failed to proliferate marking of issue {} to branch {}", source.getKey(), targetBranchUuid, e);
      }
    }
    written.addAll(issueStorage.save(dbSession, pending, targetBranchUuid, markedAt, marking.context().userUuid(),
      TARGET_STATUSES));

    // Once for the branch, after every batch is committed. Indexing per batch is what put two rows per issue
    // through es_queue and took the search index down on a million-issue run.
    try {
      issueStorage.indexBranch(targetBranchUuid, written);
    } catch (RuntimeException e) {
      // The issues are correct in the database; they are stale in the index until the branch is next indexed.
      LOG.error("Proliferated {} issue(s) on branch {} but could not index them", written.size(), targetBranchUuid, e);
    }

    if (!written.isEmpty()) {
      LOG.info("Proliferated marking '{}' from branch {} to {} issue(s) on branch {}",
        marking.transitionKey(), marking.sourceBranchLabel(), written.size(), targetBranchUuid);
    }
    return touchedComponents;
  }

  /**
   * The targets of one source issue, transitioned in memory and ready to be written.
   * <p>
   * {@code alreadyPrepared} is carried across sources on purpose: two source issues can share a signature - the
   * same rule reported with the same message on the same line - and both would otherwise mark the same target.
   * Nothing in the database stops it either, because the target is still open there until the batch is written,
   * so it matches both.
   */
  private List<DefaultIssue> prepareMatches(IssueDto source, Map<Signature, List<IssueDto>> targetsBySignature,
    Set<String> alreadyPrepared, ProliferatedMarking marking) {

    List<DefaultIssue> prepared = new ArrayList<>();
    for (IssueDto candidate : matchesOf(source, targetsBySignature)) {
      if (!alreadyPrepared.add(candidate.getKey())) {
        continue;
      }
      DefaultIssue issue = prepare(candidate, source, marking);
      if (issue != null) {
        prepared.add(issue);
      }
    }
    return prepared;
  }

  /**
   * What decides whether a marking applies to an issue: its rule, file, line and message. A record, so equality is
   * component-wise and {@code line} compares as {@code Objects.equals} - which is the {@code line is null} / {@code
   * line = ?} distinction the SQL made, kept exactly.
   */
  public record Signature(String ruleUuid, String filePath, @Nullable Integer line, String message) {

    /**
     * Null when the issue has no file, message or rule: without all of them the signature is not well defined, and
     * matching on what is left would mark every issue of that rule on the file.
     */
    @CheckForNull
    static Signature of(IssueDto dto) {
      String filePath = dto.getFilePath();
      String message = dto.getMessage();
      String ruleUuid = dto.getRuleUuid();
      if (filePath == null || message == null || ruleUuid == null) {
        return null;
      }
      return new Signature(ruleUuid, filePath, dto.getLine(), message);
    }
  }

  /** The grouping the lookup is batched by: one statement per rule and file, covering every target branch. */
  private record RuleAndFile(String ruleUuid, String filePath) {
  }

  public Map<String, Map<Signature, List<IssueDto>>> indexTargetsBySignature(DbSession dbSession,
    Collection<IssueDto> sourceIssues, Collection<String> targetBranchUuids) {
    return indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids, 0, 1);
  }


  public Map<String, Map<Signature, List<IssueDto>>> indexTargetsBySignature(DbSession dbSession,
    Collection<IssueDto> sourceIssues, Collection<String> targetBranchUuids, int slice, int sliceCount) {

    Map<String, Map<Signature, List<IssueDto>>> byBranch = new HashMap<>();
    for (String branchUuid : targetBranchUuids) {
      byBranch.put(branchUuid, new HashMap<>());
    }
    if (sourceIssues.isEmpty() || targetBranchUuids.isEmpty()) {
      return byBranch;
    }

    Set<Signature> wanted = signaturesOf(sourceIssues);
    for (RuleAndFile group : groupsOfSlice(sourceIssues, slice, sliceCount)) {
      try {
        List<IssueDto> candidates = dbClient.issueDao().selectUnresolvedByRuleAndFileInBranches(dbSession,
          targetBranchUuids, group.ruleUuid(), group.filePath(), TARGET_STATUSES);
        index(candidates, wanted, byBranch);
      } catch (RuntimeException e) {
        // Same tolerance the per-issue lookup had: one unreadable group must not abandon the rest of the task.
        LOG.error("Failed to load proliferation candidates for rule {} in {}", group.ruleUuid(), group.filePath(), e);
      }
    }
    return byBranch;
  }

  /**
   * The signatures the sources carry. The lookup below is deliberately broader than a signature, so this is what
   * bounds the index by the matches that can actually happen rather than by the rule's whole footprint on a file.
   */
  private static Set<Signature> signaturesOf(Collection<IssueDto> sourceIssues) {
    Set<Signature> wanted = new HashSet<>();
    for (IssueDto source : sourceIssues) {
      Signature signature = Signature.of(source);
      if (signature != null) {
        wanted.add(signature);
      }
    }
    return wanted;
  }

  /**
   * The (rule, file) pairs this slice is responsible for. Every slice of one pass walks the same ordered set and
   * takes a disjoint share of it, so between them they cover it exactly once and never twice.
   */
  private static List<RuleAndFile> groupsOfSlice(Collection<IssueDto> sourceIssues, int slice, int sliceCount) {
    Set<RuleAndFile> allGroups = new LinkedHashSet<>();
    for (IssueDto source : sourceIssues) {
      Signature signature = Signature.of(source);
      if (signature != null) {
        allGroups.add(new RuleAndFile(signature.ruleUuid(), signature.filePath()));
      }
    }

    List<RuleAndFile> groups = new ArrayList<>();
    int position = 0;
    for (RuleAndFile group : allGroups) {
      if (position++ % sliceCount == slice) {
        groups.add(group);
      }
    }
    return groups;
  }

  /** Files one group's candidates under their branch, keeping only the signatures a source actually carries. */
  private static void index(Collection<IssueDto> candidates, Set<Signature> wanted,
    Map<String, Map<Signature, List<IssueDto>>> byBranch) {

    for (IssueDto candidate : candidates) {
      Signature signature = Signature.of(candidate);
      if (signature == null || !wanted.contains(signature)) {
        continue;
      }
      // getProjectUuid() is the branch the issue belongs to; issues are partitioned per branch by that column.
      Map<Signature, List<IssueDto>> ofBranch = byBranch.get(candidate.getProjectUuid());
      if (ofBranch != null) {
        ofBranch.computeIfAbsent(signature, k -> new ArrayList<>()).add(candidate);
      }
    }
  }

  /** Folds the slices of a parallel index read back into one map. */
  public static Map<String, Map<Signature, List<IssueDto>>> mergeIndexes(
    Collection<Map<String, Map<Signature, List<IssueDto>>>> slices) {

    Map<String, Map<Signature, List<IssueDto>>> merged = new HashMap<>();
    for (Map<String, Map<Signature, List<IssueDto>>> slice : slices) {
      slice.forEach((branchUuid, bySignature) -> {
        Map<Signature, List<IssueDto>> target = merged.computeIfAbsent(branchUuid, k -> new HashMap<>());
        bySignature.forEach((signature, issues) ->
          target.computeIfAbsent(signature, k -> new ArrayList<>()).addAll(issues));
      });
    }
    return merged;
  }

  /**
   * The targets on this branch carrying the source's signature, from the map {@link #indexTargetsBySignature}
   * built. Nothing when the source has no well-defined signature, as before.
   */
  private static List<IssueDto> matchesOf(IssueDto source, Map<Signature, List<IssueDto>> targetsBySignature) {
    Signature signature = Signature.of(source);
    if (signature == null) {
      return List.of();
    }
    return targetsBySignature.getOrDefault(signature, List.of());
  }


  @Nullable
  private DefaultIssue prepare(IssueDto targetDto, IssueDto sourceDto, ProliferatedMarking marking) {
    IssueChangeContext context = marking.context();
    DefaultIssue target = targetDto.toDefaultIssue();
    if (!issueWorkflow.doManualTransition(target, marking.transitionKey(), context)) {
      return null;
    }

    target.setIssueResolutionExpiresAt(sourceDto.getIssueResolutionExpiresAt());

    Date markedAt = context.date();

    if (DefaultTransitions.EXCEPTION.equals(marking.transitionKey()) && marking.exceptionReason() != null) {
      issueFieldsSetter.addExceptionReason(target, marking.exceptionReason(), context);
    }
    if (marking.comment() != null) {
      issueFieldsSetter.addComment(target, marking.comment(), context, markedAt);
    }
    issueFieldsSetter.addComment(target, proliferationNote(marking.sourceBranchLabel()), context, markedAt);
    return target;
  }

  /**
   * The marking being copied: everything about it that is the same for every target on every branch, so one run
   * carries it as a single value instead of threading five arguments through every hop.
   *
   * @param context           the change context of the original marking, reused so every copy records the same
   *                          timestamp and the same author as the marking it came from
   * @param transitionKey     the transition the user applied; only {@link #PROLIFERABLE_TRANSITIONS} are copied
   * @param comment           the user's comment on the original marking, propagated verbatim; may be null
   * @param exceptionReason   the reason text of an Exception marking, which lives apart from the comment; null for
   *                          every other transition
   * @param sourceBranchLabel the branch the marking came from, as it should read in the provenance note
   */
  public record ProliferatedMarking(IssueChangeContext context, String transitionKey, @Nullable String comment,
    @Nullable String exceptionReason, String sourceBranchLabel) {
  }

  public static String proliferationNote(String sourceBranchLabel) {
    return "Resolution proliferated from " + sourceBranchLabel + " branch";
  }

  public static String branchLabel(BranchDto branch) {
    return branch.getBranchType() == BranchType.PULL_REQUEST ? "#" + branch.getKey() : branch.getKey();
  }

  /** Exposed for the callers that need to know whether to bother queueing anything. */
  public boolean isProliferable(String transitionKey) {
    return PROLIFERABLE_TRANSITIONS.contains(transitionKey);
  }

  Collection<String> targetStatuses() {
    return TARGET_STATUSES;
  }
}
