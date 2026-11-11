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
import java.util.LinkedHashMap;
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
import org.sonar.db.issue.ProliferationCandidateDto;
import org.sonar.server.issue.workflow.IssueWorkflow;


@ServerSide
@ComputeEngineSide
public class IssueMarkingProliferationService {

  private static final Logger LOG = LoggerFactory.getLogger(IssueMarkingProliferationService.class);

  /** @see io.codescan.cloud.DeveloperPlugin#KEY_CODESCAN_RESOLUTION_PROLIFERATION_ENABLED */
  public static final String KEY_RESOLUTION_PROLIFERATION_ENABLED = "codescan.cloud.resolutionProliferation.enabled";


  public static final String CHARACTERISTIC_TRANSITION = "proliferationTransition";
  public static final String CHARACTERISTIC_MARKED_AT = "proliferationMarkedAt";
  public static final String CHARACTERISTIC_AUTHOR = "proliferationAuthor";

  public static final String CHARACTERISTIC_SOURCE_BRANCH_LABEL = "proliferationSourceBranch";


  private static final String TRANSITION_WONT_FIX = "wontfix";
  private static final String STATUS_OPEN = "OPEN";
  private static final String STATUS_REOPENED = "REOPENED";
  private static final String RESOLUTION_FALSE_POSITIVE = "FALSE-POSITIVE";
  private static final String RESOLUTION_WONT_FIX = "WONTFIX";

  /** Only these markings proliferate; the other transitions (reopen, confirm, resolve as fixed) are untouched. */
  private static final Set<String> PROLIFERABLE_TRANSITIONS = Set.of(
    DefaultTransitions.EXCEPTION, DefaultTransitions.ACCEPT, DefaultTransitions.FALSE_POSITIVE,
    TRANSITION_WONT_FIX);

  /**
   * Candidate statuses on the other branches. An issue already resolved there is left alone - its own marking wins,
   * and re-stating it would add a duplicate comment on every subsequent run.
   */
  private static final Set<String> TARGET_STATUSES = Set.of(STATUS_OPEN, STATUS_REOPENED);

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


  /**
   * Writes the marking to the matching issues of one branch.
   * <p>
   * The matches arrive as keys: the full issues are loaded here, {@link #SAVE_BATCH_SIZE} at a time, so a thread
   * holds one batch rather than every match of the branch. A match whose status left the candidate statuses since
   * it was read is skipped. A match that fails to prepare is counted and skipped; a failed statement is not caught,
   * because the session is unusable after it on some databases and the rest of the branch would fail anyway.
   *
   * @return the components of the issues written, the only ones whose measures change, and how many matches or
   * index steps failed
   */
  public BranchOutcome proliferateToBranch(DbSession dbSession, Collection<IssueDto> sourceIssues,
    Map<Signature, List<String>> targetKeysBySignature, String targetBranchUuid, ProliferatedMarking marking) {

    if (!PROLIFERABLE_TRANSITIONS.contains(marking.transitionKey()) || targetKeysBySignature.isEmpty()) {
      return new BranchOutcome(Set.of(), 0);
    }

    BranchWriter writer = new BranchWriter(dbSession, targetBranchUuid, marking);
    // Carried across sources on purpose: two source issues can share a signature - the same rule reported with the
    // same message on the same line - and both would otherwise mark the same target. Nothing in the database stops
    // it either, because the target is still open there until the batch is written, so it matches both.
    Set<String> alreadyQueued = new HashSet<>();
    for (IssueDto source : sourceIssues) {
      for (String targetKey : matchesOf(source, targetKeysBySignature)) {
        if (alreadyQueued.add(targetKey)) {
          writer.queue(targetKey, source);
        }
      }
    }
    writer.flush();

    int failures = writer.failures;
    // Once for the branch, after every batch is committed. Indexing per batch is what put two rows per issue
    // through es_queue and took the search index down on a million-issue run.
    try {
      issueStorage.indexBranch(targetBranchUuid, writer.written);
    } catch (RuntimeException e) {
      failures++;
      LOG.error("Proliferated {} issue(s) on branch {} but could not index them", writer.written.size(),
        targetBranchUuid, e);
    }

    if (!writer.written.isEmpty()) {
      LOG.info("Proliferated marking '{}' from branch {} to {} issue(s) on branch {}",
        marking.transitionKey(), marking.sourceBranchLabel(), writer.written.size(), targetBranchUuid);
    }
    return new BranchOutcome(writer.writtenComponents, failures);
  }

  /**
   * What one branch's write produced.
   *
   * @param writtenComponents the components of the issues written
   * @param failures          matches that could not be prepared, plus one if the branch could not be indexed
   */
  public record BranchOutcome(Set<String> writtenComponents, int failures) {
  }

  /** Queues matched keys with their source and writes them in batches. */
  private final class BranchWriter {
    private final DbSession dbSession;
    private final String branchUuid;
    private final ProliferatedMarking marking;
    private final Map<String, IssueDto> sourceByTargetKey = new LinkedHashMap<>();
    private final List<String> written = new ArrayList<>();
    private final Set<String> writtenComponents = new LinkedHashSet<>();
    private int failures;

    private BranchWriter(DbSession dbSession, String branchUuid, ProliferatedMarking marking) {
      this.dbSession = dbSession;
      this.branchUuid = branchUuid;
      this.marking = marking;
    }

    void queue(String targetKey, IssueDto source) {
      sourceByTargetKey.put(targetKey, source);
      if (sourceByTargetKey.size() >= SAVE_BATCH_SIZE) {
        flush();
      }
    }

    void flush() {
      if (sourceByTargetKey.isEmpty()) {
        return;
      }
      List<DefaultIssue> prepared = new ArrayList<>(sourceByTargetKey.size());
      for (IssueDto target : dbClient.issueDao().selectByKeys(dbSession, List.copyOf(sourceByTargetKey.keySet()))) {
        if (!TARGET_STATUSES.contains(target.getStatus())) {
          continue;
        }
        IssueDto source = sourceByTargetKey.get(target.getKey());
        try {
          DefaultIssue issue = prepare(target, source, marking);
          if (issue != null) {
            prepared.add(issue);
          }
        } catch (RuntimeException e) {
          failures++;
          LOG.error("Failed to proliferate marking of issue {} to issue {} on branch {}", source.getKey(),
            target.getKey(), branchUuid, e);
        }
      }
      sourceByTargetKey.clear();
      if (prepared.isEmpty()) {
        return;
      }
      Map<String, String> componentByKey = new HashMap<>(prepared.size());
      prepared.forEach(issue -> componentByKey.put(issue.key(), issue.componentUuid()));
      for (String key : issueStorage.save(dbSession, prepared, branchUuid, marking.context().date().getTime(),
        marking.context().userUuid(), TARGET_STATUSES)) {
        written.add(key);
        String componentUuid = componentByKey.get(key);
        if (componentUuid != null) {
          writtenComponents.add(componentUuid);
        }
      }
    }
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

  /**
   * The grouping the lookup is batched by: one statement per rule and file, covering every target branch.
   * {@code fileKey} is the file's component key, which every branch of the project shares.
   */
  private record RuleAndFile(String ruleUuid, String filePath, String fileKey) {
  }

  /**
   * The candidates of every target branch, as keys filed by signature, and how many (rule, file) groups could not
   * be read.
   */
  public record TargetIndex(Map<String, Map<Signature, List<String>>> byBranch, int failedGroups) {

    public boolean hasMatches(String branchUuid) {
      return !byBranch.getOrDefault(branchUuid, Map.of()).isEmpty();
    }

    public Map<Signature, List<String>> of(String branchUuid) {
      return byBranch.getOrDefault(branchUuid, Map.of());
    }
  }

  public TargetIndex indexTargetsBySignature(DbSession dbSession, Collection<IssueDto> sourceIssues,
    Collection<String> targetBranchUuids) {
    return indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids, 0, 1);
  }

  /**
   * Finds, for this slice's (rule, file) groups, the issues on the target branches that carry a source's
   * signature. Line is filtered in SQL, message here, and only keys are kept: memory is bounded by the matches,
   * not by the rule's footprint on the file across every branch.
   */
  public TargetIndex indexTargetsBySignature(DbSession dbSession, Collection<IssueDto> sourceIssues,
    Collection<String> targetBranchUuids, int slice, int sliceCount) {

    Map<String, Map<Signature, List<String>>> byBranch = new HashMap<>();
    for (String branchUuid : targetBranchUuids) {
      byBranch.put(branchUuid, new HashMap<>());
    }
    if (sourceIssues.isEmpty() || targetBranchUuids.isEmpty()) {
      return new TargetIndex(byBranch, 0);
    }

    int failedGroups = 0;
    // One resolution per file rather than per (rule, file): a file carrying issues of six rules is the same set
    // of components each time.
    Map<String, List<String>> componentsByFilePath = new HashMap<>();
    for (Map.Entry<RuleAndFile, Set<Signature>> group : groupsOfSlice(sourceIssues, slice, sliceCount).entrySet()) {
      RuleAndFile ruleAndFile = group.getKey();
      try {
        List<String> componentUuids = componentsByFilePath.computeIfAbsent(ruleAndFile.filePath(),
          filePath -> dbClient.componentDao().selectUuidsByKeyAndPathInBranches(dbSession, ruleAndFile.fileKey(),
            filePath, targetBranchUuids));
        if (componentUuids.isEmpty()) {
          // The file does not exist on any target branch, so neither can any issue on it.
          continue;
        }
        Set<Signature> wanted = group.getValue();
        Set<Integer> lines = new HashSet<>();
        boolean includeNullLine = false;
        for (Signature signature : wanted) {
          if (signature.line() == null) {
            includeNullLine = true;
          } else {
            lines.add(signature.line());
          }
        }
        dbClient.issueDao().scrollProliferationCandidates(dbSession, componentUuids, ruleAndFile.ruleUuid(),
          TARGET_STATUSES, lines, includeNullLine, candidate -> index(candidate, ruleAndFile, wanted, byBranch));
      } catch (RuntimeException e) {
        failedGroups++;
        LOG.error("Failed to load proliferation candidates for rule {} in {}", ruleAndFile.ruleUuid(),
          ruleAndFile.filePath(), e);
        // A failed statement leaves the transaction aborted on some databases; without this every later group of
        // the slice would fail too. The session only reads.
        dbSession.rollback();
      }
    }
    return new TargetIndex(byBranch, failedGroups);
  }

  /**
   * The (rule, file) groups this slice is responsible for, each with the signatures the sources carry in it. Every
   * slice of one pass walks the same ordered set and takes a disjoint share of it, so between them they cover it
   * exactly once and never twice.
   */
  private static Map<RuleAndFile, Set<Signature>> groupsOfSlice(Collection<IssueDto> sourceIssues, int slice,
    int sliceCount) {

    Map<RuleAndFile, Set<Signature>> allGroups = new LinkedHashMap<>();
    for (IssueDto source : sourceIssues) {
      Signature signature = Signature.of(source);
      String fileKey = source.getComponentKey();
      if (signature != null && fileKey != null) {
        allGroups.computeIfAbsent(new RuleAndFile(signature.ruleUuid(), signature.filePath(), fileKey),
          k -> new HashSet<>()).add(signature);
      }
    }

    Map<RuleAndFile, Set<Signature>> groups = new LinkedHashMap<>();
    int position = 0;
    for (Map.Entry<RuleAndFile, Set<Signature>> group : allGroups.entrySet()) {
      if (position++ % sliceCount == slice) {
        groups.put(group.getKey(), group.getValue());
      }
    }
    return groups;
  }

  /** Files one candidate under its branch when its signature is one a source actually carries. */
  private static void index(ProliferationCandidateDto candidate, RuleAndFile group, Set<Signature> wanted,
    Map<String, Map<Signature, List<String>>> byBranch) {

    if (candidate.getMessage() == null) {
      return;
    }
    Signature signature = new Signature(group.ruleUuid(), group.filePath(), candidate.getLine(),
      candidate.getMessage());
    if (!wanted.contains(signature)) {
      return;
    }
    // The branch uuid is the issue's project_uuid; issues are partitioned per branch by that column.
    Map<Signature, List<String>> ofBranch = byBranch.get(candidate.getBranchUuid());
    if (ofBranch != null) {
      ofBranch.computeIfAbsent(signature, k -> new ArrayList<>()).add(candidate.getIssueKey());
    }
  }

  /** Folds the slices of a parallel index read back into one index. */
  public static TargetIndex mergeIndexes(Collection<TargetIndex> slices) {
    Map<String, Map<Signature, List<String>>> merged = new HashMap<>();
    int failedGroups = 0;
    for (TargetIndex slice : slices) {
      failedGroups += slice.failedGroups();
      slice.byBranch().forEach((branchUuid, bySignature) -> {
        Map<Signature, List<String>> target = merged.computeIfAbsent(branchUuid, k -> new HashMap<>());
        bySignature.forEach((signature, keys) ->
          target.computeIfAbsent(signature, k -> new ArrayList<>()).addAll(keys));
      });
    }
    return new TargetIndex(merged, failedGroups);
  }

  /**
   * The keys of the targets on this branch carrying the source's signature, from the index
   * {@link #indexTargetsBySignature} built. Nothing when the source has no well-defined signature.
   */
  private static List<String> matchesOf(IssueDto source, Map<Signature, List<String>> targetKeysBySignature) {
    Signature signature = Signature.of(source);
    if (signature == null) {
      return List.of();
    }
    return targetKeysBySignature.getOrDefault(signature, List.of());
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

  public static boolean carriesTransitionResolution(@Nullable String resolution, String transitionKey) {
    if (resolution == null) {
      return false;
    }
    return switch (transitionKey) {
      case DefaultTransitions.FALSE_POSITIVE -> RESOLUTION_FALSE_POSITIVE.equals(resolution);
      case DefaultTransitions.ACCEPT, TRANSITION_WONT_FIX -> RESOLUTION_WONT_FIX.equals(resolution);
      case DefaultTransitions.EXCEPTION -> Issue.RESOLUTION_EXCEPTION.equals(resolution);
      default -> false;
    };
  }

  Collection<String> targetStatuses() {
    return TARGET_STATUSES;
  }
}
