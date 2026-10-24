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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueKeyStatusDto;
import org.sonar.server.issue.IssueChangePostProcessor;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.MediaTypes;

import static org.sonar.server.issue.IssueFieldsSetter.ISSUE_STATUS;
import static org.sonar.server.issue.IssueFieldsSetter.RESOLUTION;
import static org.sonar.server.issue.IssueFieldsSetter.STATUS;
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
 * whole project. It supports only the two resolving transitions, which are the ones whose workflow reduces
 * cleanly to a predicate.
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

  /** Keeps one request's key list, its changelog rows and its index batch to a size the heap does not notice. */
  static final int MAX_ISSUES = 50_000;

  /** Issues between JDBC batch flushes, so the pending-insert buffer never grows with the chunk size. */
  private static final int CHANGELOG_FLUSH_EVERY = 2_000;

  private static final Map<String, String> TRANSITION_TO_RESOLUTION = Map.of(
    DefaultTransitions.FALSE_POSITIVE, Issue.RESOLUTION_FALSE_POSITIVE,
    DefaultTransitions.ACCEPT, Issue.RESOLUTION_WONT_FIX);

  /** The issue status each resolution presents as, so the changelog reads the same as a manual bulk change. */
  private static final Map<String, String> RESOLUTION_TO_ISSUE_STATUS = Map.of(
    Issue.RESOLUTION_FALSE_POSITIVE, "FALSE_POSITIVE",
    Issue.RESOLUTION_WONT_FIX, "ACCEPTED");

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
      .setDescription("Comma-separated list of issue keys, at most " + MAX_ISSUES)
      .setRequired(true);
    action.createParam(PARAM_TRANSITION)
      .setDescription("Transition to apply")
      .setPossibleValues(TRANSITION_TO_RESOLUTION.keySet())
      .setRequired(true);
    action.createParam(PARAM_BRANCH_UUID)
      .setDescription("Branch uuid the issues belong to. Recorded on the changelog rows, which is what "
        + "issues.project_uuid holds, and reindexed once the issues are written.")
      .setRequired(true);
    action.createParam(PARAM_CHANGE_AUTHOR)
      .setDescription("Uuid of the user the change is attributed to. Defaults to the caller.");
    action.createParam(PARAM_COMMENT)
      .setDescription("Comment to add to every resolved issue. Omitted means no comment is written.");
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

    List<String> issueKeys = request.mandatoryParamAsStrings(PARAM_ISSUES);
    checkArgument(issueKeys.size() <= MAX_ISSUES, "Number of issues is limited to %s", MAX_ISSUES);
    String transition = request.mandatoryParam(PARAM_TRANSITION);
    String resolution = TRANSITION_TO_RESOLUTION.get(transition);
    checkArgument(resolution != null, "Unsupported transition: %s", transition);
    String branchUuid = request.mandatoryParam(PARAM_BRANCH_UUID);
    String changeAuthor = request.param(PARAM_CHANGE_AUTHOR);
    String comment = request.param(PARAM_COMMENT);
    boolean refreshMeasures = request.mandatoryParamAsBoolean(PARAM_REFRESH_MEASURES);

    Result result = apply(issueKeys, resolution, branchUuid, changeAuthor, comment, refreshMeasures);

    try (JsonWriter json = response.newJsonWriter()) {
      json.beginObject()
        .prop("requested", issueKeys.size())
        .prop("transitioned", result.transitioned.size());
      json.name("notTransitioned").beginArray().values(result.notTransitioned).endArray();
      json.endObject();
    }
    response.stream().setMediaType(MediaTypes.JSON);
  }

  private Result apply(List<String> issueKeys, String resolution, String branchUuid,
    @Nullable String changeAuthor, @Nullable String comment, boolean refreshMeasures) {
    long now = system2.now();
    List<IssueKeyStatusDto> movable;
    // One session for the whole chunk: the issues and their changelog are written together or not at all. The read
    // runs first, under the same predicate the update uses, so it is exactly the set the update will move - which
    // is what makes a read-back afterwards unnecessary.
    long selectMs;
    long updateMs;
    long changelogMs;
    long commitMs;
    try (DbSession dbSession = dbClient.openSession(true)) {
      long t0 = system2.now();
      movable = dbClient.issueDao().selectTransitionable(dbSession, issueKeys);
      selectMs = system2.now() - t0;
      if (movable.isEmpty()) {
        return new Result(List.of(), issueKeys);
      }
      t0 = system2.now();
      dbClient.issueDao().bulkTransition(dbSession, movable.stream().map(IssueKeyStatusDto::getKee).toList(),
        resolution, now);
      updateMs = system2.now() - t0;
      t0 = system2.now();
      writeChangelog(dbSession, movable, resolution, branchUuid, changeAuthor, comment, now);
      changelogMs = system2.now() - t0;
      t0 = system2.now();
      dbSession.commit();
      commitMs = system2.now() - t0;
    }

    List<String> transitioned = movable.stream().map(IssueKeyStatusDto::getKee).toList();
    long t0 = system2.now();
    issueIndexer.indexOnAnalysis(branchUuid, transitioned);
    long indexMs = system2.now() - t0;
    long measuresMs = refreshMeasures
      ? refreshMeasures(movable.stream().map(IssueKeyStatusDto::getComponentUuid).collect(Collectors.toSet()))
      : 0;
    LOG.info("bulk_transition {} issues: select={}ms update={}ms changelog={}ms commit={}ms index={}ms measures={}ms",
      transitioned.size(), selectMs, updateMs, changelogMs, commitMs, indexMs, measuresMs);

    Set<String> moved = new HashSet<>(transitioned);
    List<String> notTransitioned = issueKeys.stream().filter(key -> !moved.contains(key)).toList();
    return new Result(transitioned, notTransitioned);
  }

  /**
   * One recomputation for the whole run, over the components the transition touched. {@link BulkChangeAction} pays
   * this on every call, which is 2,000 redundant recomputations of one project for a million-issue transfer.
   */
  private long refreshMeasures(Set<String> touchedComponentUuids) {
    long startedAt = system2.now();
    try (DbSession dbSession = dbClient.openSession(false)) {
      // The components the issues live on, not the branch root: the tree updater recomputes upwards from the
      // components it is given, so handing it only the root leaves every aggregate exactly as it was.
      List<ComponentDto> touched = dbClient.componentDao().selectByUuids(dbSession, touchedComponentUuids);
      if (touched.isEmpty()) {
        return 0;
      }
      issueChangePostProcessor.process(dbSession, List.of(), touched, false);
      dbSession.commit();
    }
    return system2.now() - startedAt;
  }

  /**
   * {@link IssueChangeDto#of(String, FieldDiffs, String)} builds the encoded change data, so a row written here is
   * indistinguishable from one a manual bulk change would have written. It leaves the uuid unset, as every other
   * caller does, so the uuid is assigned here.
   */
  private void writeChangelog(DbSession dbSession, List<IssueKeyStatusDto> movable, String resolution,
    String branchUuid, @Nullable String changeAuthor, @Nullable String comment, long now) {
    String issueStatus = RESOLUTION_TO_ISSUE_STATUS.get(resolution);
    Date changeDate = new Date(now);
    int sinceFlush = 0;
    for (IssueKeyStatusDto issue : movable) {
      FieldDiffs diffs = new FieldDiffs()
        .setUserUuid(changeAuthor)
        .setCreationDate(changeDate)
        .setIssueKey(issue.getKee())
        .setDiff(RESOLUTION, null, resolution)
        .setDiff(STATUS, issue.getStatus(), Issue.STATUS_RESOLVED)
        .setDiff(ISSUE_STATUS, statusOf(issue.getStatus()), issueStatus);
      IssueChangeDto diffRow = IssueChangeDto.of(issue.getKee(), diffs, branchUuid);
      diffRow.setUuid(uuidFactory.create());
      dbClient.issueChangeDao().insert(dbSession, diffRow);
      if (comment != null) {
        dbClient.issueChangeDao().insert(dbSession, commentRow(issue.getKee(), branchUuid, changeAuthor, comment, now));
      }
      // Send the accumulated batch to the driver periodically. Without this a 25,000-issue chunk holds 50,000
      // pending inserts in the web JVM before the commit. Flushing is not committing, so the chunk stays atomic.
      if (++sinceFlush >= CHANGELOG_FLUSH_EVERY) {
        dbSession.flushStatements();
        sinceFlush = 0;
      }
    }
  }

  private IssueChangeDto commentRow(String issueKey, String projectUuid, @Nullable String userUuid, String comment,
    long now) {
    return new IssueChangeDto()
      .setUuid(uuidFactory.create())
      .setKey(uuidFactory.create())
      .setIssueKey(issueKey)
      .setChangeType(IssueChangeDto.TYPE_COMMENT)
      .setChangeData(comment)
      .setUserUuid(userUuid)
      .setProjectUuid(projectUuid)
      .setIssueChangeCreationDate(now)
      .setCreatedAt(now)
      .setUpdatedAt(now);
  }

  /** An unresolved issue's issue-status is its status, which is what the changelog records as the old value. */
  private static String statusOf(String status) {
    return Issue.STATUS_REOPENED.equals(status) ? Issue.STATUS_OPEN : status;
  }

  private record Result(List<String> transitioned, List<String> notTransitioned) {
  }
}
