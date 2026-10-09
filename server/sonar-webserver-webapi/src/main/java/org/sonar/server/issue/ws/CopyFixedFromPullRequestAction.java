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

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentKeys;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.issue.IssueFieldsSetter;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.user.UserSession;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.sonarqube.ws.MediaTypes.JSON;

public class CopyFixedFromPullRequestAction implements IssuesWsAction {

  static final String ACTION = "copy_fixed_from_pull_request";
  static final String PARAM_PROJECT = "project";
  static final String PARAM_PULL_REQUEST = "pullRequest";
  static final String PARAM_BRANCH = "branch";
  static final String PARAM_TARGET_PROJECT = "targetProject";
  static final String PARAM_TARGET_PULL_REQUEST = "targetPullRequest";

  // Issue workflow values, as stored in the issues table.
  private static final String STATUS_OPEN = "OPEN";
  private static final String STATUS_CLOSED = "CLOSED";
  private static final String RESOLUTION_FIXED = "FIXED";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_RESOLUTION = "resolution";

  private static final Logger LOG = LoggerFactory.getLogger(CopyFixedFromPullRequestAction.class);

  private final DbClient dbClient;
  private final UserSession userSession;
  private final IssueIndexer issueIndexer;
  private final UuidFactory uuidFactory;
  private final System2 system2;

  public CopyFixedFromPullRequestAction(DbClient dbClient, UserSession userSession, IssueIndexer issueIndexer,
    UuidFactory uuidFactory, System2 system2) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.issueIndexer = issueIndexer;
    this.uuidFactory = uuidFactory;
    this.system2 = system2;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION)
      .setDescription("Copy the issues fixed in a merged pull request to the branch it was merged into, as fixed issues, " +
        "or to a pull request open from that branch. Internal, called by codescanng when the pull request is merged. " +
        "Requires 'Administer' permission on the project and on the target project.")
      .setSince("10.8")
      .setPost(true)
      .setInternal(true)
      .setHandler(this);

    action.createParam(PARAM_PROJECT).setDescription("Project key").setRequired(true);
    action.createParam(PARAM_PULL_REQUEST).setDescription("Key of the merged pull request").setRequired(true);
    action.createParam(PARAM_BRANCH).setDescription("Branch the pull request was merged into. Either this or '"
      + PARAM_TARGET_PULL_REQUEST + "' must be set");
    action.createParam(PARAM_TARGET_PROJECT).setDescription("Key of the project to copy to. Defaults to '" + PARAM_PROJECT + "'");
    action.createParam(PARAM_TARGET_PULL_REQUEST).setDescription("Key of the pull request to copy to, instead of a branch");
  }

  @Override
  public void handle(Request request, Response response) {
    userSession.checkLoggedIn();
    CopyRequest copyRequest = CopyRequest.from(request);

    Counts counts;
    try (DbSession dbSession = dbClient.openSession(false)) {
      CopyRun run = new CopyRun(dbSession, copyRequest, resolveTarget(dbSession, copyRequest));
      counts = run.copyAll();
    }

    if (LOG.isInfoEnabled()) {
      LOG.info("Copied {} fixed issue(s) of pull request {} of project {} to {} of project {} ({} already there, {} deleted file(s) added)",
        counts.copied, copyRequest.pullRequestKey(), copyRequest.projectKey(), copyRequest.targetLabel(), copyRequest.targetProjectKey(),
        counts.alreadyThere, counts.filesAdded);
    }
    writeResponse(response, counts);
  }

  private Target resolveTarget(DbSession dbSession, CopyRequest copyRequest) {
    ProjectDto project = selectProject(dbSession, copyRequest.projectKey());
    ProjectDto targetProject = copyRequest.isSameProject() ? project : selectProject(dbSession, copyRequest.targetProjectKey());
    BranchDto pullRequest = dbClient.branchDao().selectByPullRequestKey(dbSession, project.getUuid(), copyRequest.pullRequestKey())
      .orElseThrow(() -> new NotFoundException("Pull request '" + copyRequest.pullRequestKey() + "' not found"));
    String targetLabel = capitalize(copyRequest.targetLabel());
    BranchDto targetBranch = (copyRequest.branchKey() != null
      ? dbClient.branchDao().selectByBranchKey(dbSession, targetProject.getUuid(), copyRequest.branchKey())
      : dbClient.branchDao().selectByPullRequestKey(dbSession, targetProject.getUuid(), copyRequest.targetPullRequestKey()))
      .orElseThrow(() -> new NotFoundException(targetLabel + " not found"));
    ComponentDto targetRoot = dbClient.componentDao().selectByUuid(dbSession, targetBranch.getUuid())
      .orElseThrow(() -> new NotFoundException(targetLabel + " has no component"));
    return new Target(pullRequest, targetProject, project.getUuid().equals(targetProject.getUuid()), targetRoot);
  }

  private ProjectDto selectProject(DbSession dbSession, String key) {
    ProjectDto project = dbClient.projectDao().selectProjectByKey(dbSession, key)
      .orElseThrow(() -> new NotFoundException("Project '" + key + "' not found"));
    userSession.checkEntityPermission(UserRole.ADMIN, project);
    return project;
  }

  private static void writeResponse(Response response, Counts counts) {
    JsonObject json = new JsonObject();
    json.addProperty("copied", counts.copied);
    json.addProperty("alreadyOnBranch", counts.alreadyThere);
    json.addProperty("deletedFilesAdded", counts.filesAdded);
    response.stream().setMediaType(JSON);
    try (OutputStream output = response.stream().output()) {
      output.write(json.toString().getBytes(UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String capitalize(String label) {
    return Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  /** Same rule, same file and same line hash (or same message when there is no hash). */
  private static boolean isSameIssue(IssueDto branchIssue, IssueDto prIssue) {
    if (!Objects.equals(branchIssue.getRuleKey(), prIssue.getRuleKey())) {
      return false;
    }
    String prChecksum = prIssue.getChecksum();
    String branchChecksum = branchIssue.getChecksum();
    if (prChecksum != null && branchChecksum != null) {
      return prChecksum.equals(branchChecksum);
    }
    return Objects.equals(prIssue.getMessage(), branchIssue.getMessage());
  }

  /** Validated parameters of a call. */
  private record CopyRequest(String projectKey, String pullRequestKey, @Nullable String branchKey,
    @Nullable String targetPullRequestKey, String targetProjectKey) {

    static CopyRequest from(Request request) {
      String projectKey = request.mandatoryParam(PARAM_PROJECT);
      String pullRequestKey = request.mandatoryParam(PARAM_PULL_REQUEST);
      String branchKey = request.param(PARAM_BRANCH);
      String targetPullRequestKey = request.param(PARAM_TARGET_PULL_REQUEST);
      String targetProjectKey = Optional.ofNullable(request.param(PARAM_TARGET_PROJECT)).orElse(projectKey);
      if ((branchKey == null) == (targetPullRequestKey == null)) {
        throw BadRequestException.create("Exactly one of '" + PARAM_BRANCH + "' and '" + PARAM_TARGET_PULL_REQUEST + "' must be set");
      }
      if (targetProjectKey.equals(projectKey) && pullRequestKey.equals(targetPullRequestKey)) {
        throw BadRequestException.create("A pull request cannot be copied to itself");
      }
      return new CopyRequest(projectKey, pullRequestKey, branchKey, targetPullRequestKey, targetProjectKey);
    }

    boolean isSameProject() {
      return targetProjectKey.equals(projectKey);
    }

    /** "branch main" or "pull request 25", for messages. */
    String targetLabel() {
      return branchKey != null ? ("branch " + branchKey) : ("pull request " + targetPullRequestKey);
    }

    /** "main" or "#25", as SonarQube names branches and pull requests in issue changelogs. */
    String targetName() {
      return branchKey != null ? branchKey : ("#" + targetPullRequestKey);
    }
  }

  /** Where the issues are copied from and to. */
  private record Target(BranchDto pullRequest, ProjectDto targetProject, boolean sameProject, ComponentDto targetRoot) {
  }

  private static final class Counts {
    private int copied;
    private int alreadyThere;
    private int filesAdded;
  }

  /** One call: copies the fixed issues of the pull request, sharing lookups across its issues. */
  private final class CopyRun {
    private final DbSession dbSession;
    private final CopyRequest copyRequest;
    private final Target target;
    private final Counts counts = new Counts();
    private final Map<String, List<IssueDto>> targetIssuesByFile = new HashMap<>();
    private final Map<String, Optional<String>> prFilePaths = new HashMap<>();
    private final List<IssueDto> copies = new ArrayList<>();

    private CopyRun(DbSession dbSession, CopyRequest copyRequest, Target target) {
      this.dbSession = dbSession;
      this.copyRequest = copyRequest;
      this.target = target;
    }

    Counts copyAll() {
      List<IssueDto> fixedInPullRequest = dbClient.issueDao().selectByKeys(dbSession,
        dbClient.issueDao().selectFixedIssueKeysByBranchUuid(dbSession, target.pullRequest().getUuid()));
      fixedInPullRequest.forEach(this::copy);
      issueIndexer.commitAndIndexIssues(dbSession, copies);
      return counts;
    }

    private void copy(IssueDto prIssue) {
      targetFile(prIssue).ifPresent(file -> {
        List<IssueDto> targetIssues = targetIssuesByFile.computeIfAbsent(file.uuid(),
          uuid -> new ArrayList<>(dbClient.issueDao().selectByKeys(dbSession, dbClient.issueDao().selectIssueKeysByFileUuid(dbSession, uuid))));
        if (targetIssues.stream().anyMatch(i -> isSameIssue(i, prIssue))) {
          counts.alreadyThere++;
        } else {
          IssueDto copy = insertCopy(prIssue, file);
          targetIssues.add(copy);
          copies.add(copy);
          counts.copied++;
        }
      });
    }

    /**
     * The PR issue's file in the target. When it is missing (the file only ever existed in the PR: added then
     * deleted there), it is kept in the target as a disabled component, like a deleted file of any branch, so its
     * fixed issue can be listed.
     */
    private Optional<ComponentDto> targetFile(IssueDto prIssue) {
      String fileKey = target.sameProject() ? prIssue.getComponentKey() : targetFileKey(prIssue);
      if (fileKey == null) {
        return Optional.empty();
      }
      Optional<ComponentDto> file = copyRequest.branchKey() != null
        ? dbClient.componentDao().selectByKeyAndBranch(dbSession, fileKey, copyRequest.branchKey())
        : dbClient.componentDao().selectByKeyAndPullRequest(dbSession, fileKey, copyRequest.targetPullRequestKey());
      if (file.isPresent()) {
        return file;
      }
      String prFileUuid = prIssue.getComponentUuid();
      Optional<ComponentDto> added = prFileUuid == null ? Optional.empty() : insertDisabledFile(prFileUuid, fileKey);
      added.ifPresent(f -> counts.filesAdded++);
      return added;
    }

    /** Key of the PR issue's file in another project: same path, under that project's key. */
    @CheckForNull
    private String targetFileKey(IssueDto prIssue) {
      String prFileUuid = prIssue.getComponentUuid();
      if (prFileUuid == null) {
        return null;
      }
      Optional<String> path = prFilePaths.computeIfAbsent(prFileUuid,
        uuid -> dbClient.componentDao().selectByUuid(dbSession, uuid).map(ComponentDto::path));
      return path.map(p -> ComponentKeys.createEffectiveKey(target.targetProject().getKey(), p)).orElse(null);
    }

    private IssueDto insertCopy(IssueDto prIssue, ComponentDto file) {
      long now = system2.now();
      Date nowDate = new Date(now);
      DefaultIssue issue = prIssue.toDefaultIssue();
      issue.setKey(uuidFactory.create());
      issue.setLine(null);
      // Closed "now" rather than at the PR fix date, so the closed-issue housekeeping delay starts at the merge.
      issue.setCloseDate(nowDate);
      issue.setUpdateDate(nowDate);
      issue.setSelectedAt(now);
      issue.setIsNewCodeReferenceIssue(false);
      IssueDto copy = IssueDto.toDtoForServerInsert(issue, file, target.targetRoot(), prIssue.getRuleUuid(), now);
      dbClient.issueDao().insert(dbSession, copy);

      // System changelog entry, without author: where the issue comes from (shown as "The issue has been copied from
      // branch '#N' to branch 'X'", as for the statuses SonarQube copies from pull requests), and the same closing an
      // analysis writes, so the issue behaves like any fixed issue.
      FieldDiffs closing = new FieldDiffs()
        .setIssueKey(copy.getKey())
        .setCreationDate(nowDate)
        .setDiff(IssueFieldsSetter.FROM_BRANCH, "#" + copyRequest.pullRequestKey(), copyRequest.targetName())
        .setDiff(FIELD_RESOLUTION, null, RESOLUTION_FIXED)
        .setDiff(FIELD_STATUS, STATUS_OPEN, STATUS_CLOSED);
      IssueChangeDto change = IssueChangeDto.of(copy.getKey(), closing, target.targetRoot().uuid());
      change.setUuid(uuidFactory.create());
      dbClient.issueChangeDao().insert(dbSession, change);
      return copy;
    }

    /**
     * Adds the PR's file to the target as a disabled component, which is how SonarQube keeps a deleted file: hidden
     * from the code tree and measures, but still holding its closed issues. Housekeeping removes it once it has no
     * issue left, and an analysis that later finds a file with the same key re-enables it.
     */
    private Optional<ComponentDto> insertDisabledFile(String prFileUuid, String key) {
      ComponentDto targetRoot = target.targetRoot();
      return dbClient.componentDao().selectByUuid(dbSession, prFileUuid).map(source -> {
        ComponentDto file = new ComponentDto()
          .setUuid(uuidFactory.create())
          .setKey(key)
          .setOrganizationUuid(targetRoot.getOrganizationUuid())
          .setBranchUuid(targetRoot.uuid())
          .setUuidPath(ComponentDto.formatUuidPathFromParent(targetRoot))
          .setScope(source.scope())
          .setQualifier(source.qualifier())
          .setName(source.name())
          .setLongName(source.longName())
          .setPath(source.path())
          .setLanguage(source.language())
          .setPrivate(targetRoot.isPrivate())
          .setEnabled(false)
          .setCreatedAt(new Date(system2.now()));
        dbClient.componentDao().insert(dbSession, file, false);
        return file;
      });
    }
  }
}
