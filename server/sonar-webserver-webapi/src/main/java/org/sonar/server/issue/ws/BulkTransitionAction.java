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
package org.sonar.server.issue.ws;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueExceptionSourceDto;
import org.sonar.db.issue.IssueKeyStatusDto;
import org.sonar.server.issue.IssueChangePostProcessor;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.MediaTypes;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * CodeScan: resolves a large set of issues in one request, for callers that have already decided which issues to
 * move and do not need the per-issue machinery {@link BulkChangeAction} provides.
 *
 * <p><b>Why this exists.</b> {@code bulkChange} is capped at 500 issues and spends roughly 80% of each call on
 * per-issue work: one {@code UPDATE} and two changelog inserts per issue through the ORM, plus a full live-measure
 * and quality-gate recomputation at the end of <em>every</em> call. Transferring a million resolutions that way is
 * two thousand calls and three million single-row statements. This action does the same thing set-based: one
 * update statement per partition, batched changelog inserts, one bulk reindex, and no measure refresh at all —
 * the caller refreshes once when it has finished.
 *
 * <p><b>What it deliberately does not do.</b> No notifications, no quality-gate event per call, and no per-issue
 * permission check: it requires a system administrator, because the only caller is a server-side job acting on a
 * whole project. It supports only the resolving transitions whose workflow reduces cleanly to a predicate: false
 * positive, accept and exception. An exception is copied from the source issue the caller's resolution transfer
 * paired each issue with, which this action reads from the transfer's report rows: the source's expiry date and its
 * latest reason. Neither is sent, so one request carries exceptions with any mix of them, and a source without a
 * reason gives an exception without one.
 *
 * <p><b>{@code changeAuthor} is taken on trust.</b> It is written to the changelog as the author of every row in
 * the chunk and is never checked against the caller, so a system administrator can attribute changes to any user.
 * That is intended: the caller is a transfer job replaying who resolved each issue on the source project, and it
 * is the system-administrator requirement that makes it safe. Do not expose this action to end users.
 */
public class BulkTransitionAction implements IssuesWsAction {

  private static final Logger LOG = LoggerFactory.getLogger(BulkTransitionAction.class);

  public static final String ACTION = "bulk_transition";

  private static final String PARAM_ISSUES = "issues";
  private static final String PARAM_TRANSITION = "transition";
  private static final String PARAM_CHANGE_AUTHOR = "changeAuthor";
  private static final String PARAM_COMMENT = "comment";
  private static final String PARAM_REFRESH_MEASURES = "refreshMeasures";
  private static final String PARAM_BRANCH_UUID = "branchUuid";
  private static final String PARAM_TRANSFER_UUID = "transferUuid";

  /** Keeps one request's key list, its changelog rows and its index batch to a size the heap does not notice. */
  static final int MAX_ISSUES = 50_000;

  private static final Map<String, String> TRANSITION_TO_RESOLUTION = Map.of(
    DefaultTransitions.FALSE_POSITIVE, Issue.RESOLUTION_FALSE_POSITIVE,
    DefaultTransitions.ACCEPT, Issue.RESOLUTION_WONT_FIX,
    DefaultTransitions.EXCEPTION, Issue.RESOLUTION_EXCEPTION);

  /** The issue status each resolution presents as, so the changelog reads the same as a manual bulk change. */
  private static final Map<String, String> RESOLUTION_TO_ISSUE_STATUS = Map.of(
    Issue.RESOLUTION_FALSE_POSITIVE, "FALSE_POSITIVE",
    Issue.RESOLUTION_WONT_FIX, "ACCEPTED",
    Issue.RESOLUTION_EXCEPTION, "EXCEPTION");

  /** The order the issue page shows an issue's exception reasons in, so the latest is the one it displays. */
  private static final Comparator<IssueChangeDto> LATEST_CHANGE = Comparator
    .comparing(IssueChangeDto::getIssueChangeCreationDate, Comparator.nullsFirst(Comparator.naturalOrder()))
    .thenComparing(IssueChangeDto::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

  private final DbClient dbClient;
  private final UserSession userSession;
  private final IssueIndexer issueIndexer;
  private final UuidFactory uuidFactory;
  private final System2 system2;
  private final IssueChangePostProcessor issueChangePostProcessor;

  public BulkTransitionAction(DbClient dbClient, UserSession userSession, IssueIndexer issueIndexer,
    UuidFactory uuidFactory, System2 system2, IssueChangePostProcessor issueChangePostProcessor) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.issueIndexer = issueIndexer;
    this.uuidFactory = uuidFactory;
    this.system2 = system2;
    this.issueChangePostProcessor = issueChangePostProcessor;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION)
      .setDescription("Resolve a large set of issues in one request. Requires system administration permission.")
      .setSince("24.12")
      .setInternal(true)
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_ISSUES)
      .setDescription("Comma-separated list of issue keys, at most " + MAX_ISSUES + ". May be omitted when "
        + PARAM_REFRESH_MEASURES + " is true, which asks for the branch's recomputation on its own.");
    action.createParam(PARAM_TRANSITION)
      .setDescription("Transition to apply. Required unless " + PARAM_ISSUES + " is empty.")
      .setPossibleValues(TRANSITION_TO_RESOLUTION.keySet());
    action.createParam(PARAM_BRANCH_UUID)
      .setDescription("Branch uuid the issues belong to. Recorded on the changelog rows, which is what "
        + "issues.project_uuid holds, and reindexed once the issues are written.")
      .setRequired(true);
    action.createParam(PARAM_CHANGE_AUTHOR)
      .setDescription("Uuid of the user the change is attributed to. Defaults to the caller.");
    action.createParam(PARAM_COMMENT)
      .setDescription("Comment to add to every resolved issue. Omitted means no comment is written.");
    action.createParam(PARAM_TRANSFER_UUID)
      .setDescription("Resolution transfer the issues belong to. Each issue takes the expiry date and the latest "
        + "reason of the source exception that transfer paired it with. Required with the '"
        + DefaultTransitions.EXCEPTION + "' transition and rejected with any other.");
    action.createParam(PARAM_REFRESH_MEASURES)
      .setDescription("Recompute the branch's live measures and quality gate once this chunk is written. The "
        + "caller sets it on its last chunk only: doing it per chunk is the per-call cost this action exists to "
        + "avoid, but skipping it entirely would leave the project's counts and gate stale until its next analysis.")
      .setBooleanPossibleValues()
      .setDefaultValue("false");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkLoggedIn().checkIsSystemAdministrator();

    List<String> issueKeys = Optional.ofNullable(request.paramAsStrings(PARAM_ISSUES)).orElseGet(List::of);
    checkArgument(issueKeys.size() <= MAX_ISSUES, "Number of issues is limited to %s", MAX_ISSUES);
    String branchUuid = request.mandatoryParam(PARAM_BRANCH_UUID);
    String changeAuthor = request.param(PARAM_CHANGE_AUTHOR);
    String comment = request.param(PARAM_COMMENT);
    boolean refreshMeasures = request.mandatoryParamAsBoolean(PARAM_REFRESH_MEASURES);
    String resolution = null;
    if (issueKeys.isEmpty()) {
      checkArgument(refreshMeasures, "%s is required unless %s is true", PARAM_ISSUES, PARAM_REFRESH_MEASURES);
    } else {
      String transition = request.mandatoryParam(PARAM_TRANSITION);
      resolution = TRANSITION_TO_RESOLUTION.get(transition);
      checkArgument(resolution != null, "Unsupported transition: %s", transition);
    }

    String transferUuid = transferUuid(request, resolution);

    Result result = apply(issueKeys, resolution, transferUuid, branchUuid, changeAuthor, comment, refreshMeasures);

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject()
        .prop("requested", issueKeys.size())
        .prop("transitioned", result.transitioned.size());
      json.name("notTransitioned").beginArray().values(result.notTransitioned).endArray();
      json.endObject();
    }
    response.stream().setMediaType(MediaTypes.JSON);
  }

  /** The transfer an exception is copied through; null for the other transitions, which must not name one. */
  @Nullable
  private static String transferUuid(Request request, @Nullable String resolution) {
    String transferUuid = trimToNull(request.param(PARAM_TRANSFER_UUID));
    if (!Issue.RESOLUTION_EXCEPTION.equals(resolution)) {
      checkArgument(transferUuid == null, "'%s' applies only to the '%s' transition", PARAM_TRANSFER_UUID,
        DefaultTransitions.EXCEPTION);
      return null;
    }
    checkArgument(transferUuid != null, "'%s' is required with the '%s' transition", PARAM_TRANSFER_UUID,
      DefaultTransitions.EXCEPTION);
    return transferUuid;
  }

  /**
   * The expiry and reason each movable issue takes from the source exception the transfer paired it with, read in
   * the chunk's session. An issue paired with several sources takes the one with the lowest key, so the choice does
   * not depend on row order. An issue whose source is no longer an exception gets neither, as does one whose source's
   * latest reason is blank.
   */
  private ExceptionFields exceptionFields(DbSession dbSession, List<String> movableKeys, String transferUuid) {
    Map<String, IssueExceptionSourceDto> sourceByIssue = new HashMap<>();
    for (IssueExceptionSourceDto source : dbClient.issueDao().selectExceptionSourcesOfTransfer(dbSession,
      transferUuid, movableKeys)) {
      sourceByIssue.merge(source.getIssueKey(), source,
        (kept, other) -> kept.getSourceKey().compareTo(other.getSourceKey()) <= 0 ? kept : other);
    }
    Set<String> sourceKeys = sourceByIssue.values().stream().map(IssueExceptionSourceDto::getSourceKey)
      .collect(Collectors.toSet());
    Map<String, String> reasonBySource = dbClient.issueChangeDao()
      .selectByTypeAndIssueKeys(dbSession, sourceKeys, IssueChangeDto.TYPE_EXCEPTION_REASON).stream()
      .collect(Collectors.groupingBy(IssueChangeDto::getIssueKey, Collectors.collectingAndThen(
        Collectors.maxBy(LATEST_CHANGE), latest -> latest.map(IssueChangeDto::getChangeData).orElse(null))));

    Map<String, Long> expiresAt = new HashMap<>();
    Map<String, String> reason = new HashMap<>();
    for (String key : movableKeys) {
      IssueExceptionSourceDto source = sourceByIssue.get(key);
      expiresAt.put(key, source == null ? null : source.getExpiresAt());
      String sourceReason = source == null ? null : reasonBySource.get(source.getSourceKey());
      if (!isBlank(sourceReason)) {
        reason.put(key, sourceReason);
      }
    }
    return new ExceptionFields(expiresAt, reason);
  }

  /** Keys sharing a value, in the order they came, so one statement per distinct value writes them all. */
  private static <V> Map<V, List<String>> keysByValue(Map<String, V> valueByKey, List<String> keys) {
    Map<V, List<String>> grouped = new LinkedHashMap<>();
    for (String key : keys) {
      grouped.computeIfAbsent(valueByKey.get(key), v -> new ArrayList<>()).add(key);
    }
    return grouped;
  }

  private Result apply(List<String> issueKeys, @Nullable String resolution, @Nullable String transferUuid,
    String branchUuid, @Nullable String changeAuthor, @Nullable String comment, boolean refreshMeasures) {
    if (issueKeys.isEmpty()) {
      // A caller asking only for the recomputation its run owes: a run that was stopped, failed, or never got an
      // answer for the chunk that was going to carry it. Nothing is read or written, so no session is opened.
      LOG.info("bulk_transition refresh only: measures={}ms", refreshMeasuresOfBranch(branchUuid));
      return new Result(List.of(), List.of());
    }
    long startedAt = system2.now();
    long now = startedAt;
    List<IssueKeyStatusDto> movable;
    // One session for the whole chunk: the issues and their changelog are written together or not at all. The read
    // runs first, under the same predicate the update uses, so the changelog can be written from what it returned
    // without reading anything back.
    //
    // The two are not locked against each other. Another writer - an analysis closing an issue, a user resolving
    // one - can change a row between them, and the update then skips it while its changelog row has already been
    // written. A row lock would close that window, but it would be held across the changelog inserts and the
    // commit, on up to MAX_ISSUES rows, and the one writer it would block is the analysis that is trying to close
    // those very issues. The window is one statement wide and the loser is a changelog line, so it is left open.
    try (DbSession dbSession = dbClient.openSession(true)) {
      movable = dbClient.issueDao().selectTransitionable(dbSession, issueKeys, branchUuid);
      if (!movable.isEmpty()) {
        List<String> movableKeys = movable.stream().map(IssueKeyStatusDto::getKee).toList();
        ExceptionFields exception = transferUuid == null ? null
          : exceptionFields(dbSession, movableKeys, transferUuid);
        // Runs before the update, because the diff row records the status the update overwrites.
        writeChangelog(dbSession, movableKeys, resolution, exception, branchUuid, changeAuthor, comment, now);
        dbSession.flushStatements();
        if (exception == null) {
          dbClient.issueDao().bulkTransition(dbSession, movableKeys, branchUuid, resolution, null, now);
        } else {
          keysByValue(exception.expiresAt(), movableKeys).forEach((expiresAt, keys) ->
            dbClient.issueDao().bulkTransition(dbSession, keys, branchUuid, resolution, expiresAt, now));
        }
        dbSession.commit();
      }
    }

    if (movable.isEmpty()) {
      // The caller may still be asking for the recomputation its run owes: a run that was stopped or failed asks
      // this way, naming keys it has already resolved.
      long onlyMeasuresMs = refreshMeasures ? refreshMeasuresOfBranch(branchUuid) : 0;
      LOG.info("bulk_transition 0 issues on branch {}: measures={}ms", branchUuid, onlyMeasuresMs);
      return new Result(List.of(), issueKeys);
    }

    List<String> transitioned = movable.stream().map(IssueKeyStatusDto::getKee).toList();
    issueIndexer.indexOnAnalysis(branchUuid, transitioned);
    long measuresMs = refreshMeasures ? refreshMeasuresOfBranch(branchUuid) : 0;
    LOG.info("bulk_transition {} issue(s) on branch {}: total={}ms measures={}ms",
      transitioned.size(), branchUuid, system2.now() - startedAt, measuresMs);

    Set<String> movedKeys = new HashSet<>(transitioned);
    List<String> notTransitioned = issueKeys.stream().filter(key -> !movedKeys.contains(key)).toList();
    return new Result(transitioned, notTransitioned);
  }

  /**
   * Recomputes the whole branch. A run sends one chunk per transition-and-author group and only its last chunk asks
   * for this, so scoping the refresh to that chunk's components would leave every file the earlier chunks resolved
   * carrying its pre-transfer counts, and the project total summing them. It costs around half as much again as a
   * chunk-scoped refresh on a project of 6,000 files, and a run pays it once.
   */
  private long refreshMeasuresOfBranch(String branchUuid) {
    long startedAt = system2.now();
    try (DbSession dbSession = dbClient.openSession(false)) {
      List<ComponentDto> components = dbClient.componentDao().selectByBranchUuid(branchUuid, dbSession);
      if (components.isEmpty()) {
        return 0;
      }
      issueChangePostProcessor.process(dbSession, List.of(), components, false);
      dbSession.commit();
    }
    return system2.now() - startedAt;
  }

  /**
   * One statement per partition rather than one insert per row, built by the database from the issues themselves.
   * Runs before the update, because the diff row records the status the update overwrites.
   */
  private void writeChangelog(DbSession dbSession, List<String> movableKeys, String resolution,
    @Nullable ExceptionFields exception, String branchUuid, @Nullable String changeAuthor, @Nullable String comment,
    long now) {
    dbClient.issueChangeDao().insertTransitionDiffs(dbSession, movableKeys, resolution,
      RESOLUTION_TO_ISSUE_STATUS.get(resolution), branchUuid, changeAuthor, now, uuidFactory);
    if (exception != null) {
      List<String> withReason = movableKeys.stream().filter(exception.reason()::containsKey).toList();
      keysByValue(exception.reason(), withReason).forEach((reason, keys) -> dbClient.issueChangeDao()
        .insertTransitionExceptionReasons(dbSession, keys, reason, branchUuid, changeAuthor, now, uuidFactory));
    }
    if (comment != null) {
      dbClient.issueChangeDao().insertTransitionComments(dbSession, movableKeys, comment, branchUuid, changeAuthor,
        now, uuidFactory);
    }
  }

  private record Result(List<String> transitioned, List<String> notTransitioned) {
  }

  /**
   * Per movable issue: {@code expiresAt} holds every issue, with a null value for an exception that never expires;
   * {@code reason} holds only the issues whose source has one.
   */
  private record ExceptionFields(Map<String, Long> expiresAt, Map<String, String> reason) {
  }
}
