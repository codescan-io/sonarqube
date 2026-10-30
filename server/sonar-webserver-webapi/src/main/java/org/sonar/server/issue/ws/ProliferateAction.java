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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.issue.IssueProliferationSubmitter;
import org.sonar.server.user.UserSession;

import static org.sonar.core.issue.IssueChangeContext.issueChangeContextByUserBuilder;


public class ProliferateAction implements IssuesWsAction {

  private static final String ACTION_PROLIFERATE = "proliferate";
  private static final String PARAM_ISSUES = "issues";
  private static final String PARAM_COMMENT = "comment";
  private static final String PARAM_MARKED_AT = "markedAt";

  /** Caps one request so a caller cannot ask for an unbounded fan-out in a single call. */
  private static final int MAX_ISSUES = 100;

  private final DbClient dbClient;
  private final UserSession userSession;
  private final System2 system2;
  private final IssueProliferationSubmitter proliferationSubmitter;

  public ProliferateAction(DbClient dbClient, UserSession userSession, System2 system2,
    IssueProliferationSubmitter proliferationSubmitter) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.system2 = system2;
    this.proliferationSubmitter = proliferationSubmitter;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION_PROLIFERATE)
      .setDescription("Codescan customization: copy the marking already on the given issues to the matching issues "
        + "on the other branches of their project. Requires 'Administer Issues' on the project. "
        + "Does nothing for a project that has proliferation switched off.")
      .setSince("10.8")
      .setHandler(this)
      .setInternal(true)
      .setPost(true);

    action.createParam(PARAM_ISSUES)
      .setDescription("Comma-separated list of issue keys, at most " + MAX_ISSUES)
      .setRequired(true);
    action.createParam(PARAM_COMMENT)
      .setDescription("Comment to copy onto each proliferated issue")
      .setRequired(false);
    action.createParam(PARAM_MARKED_AT)
      .setDescription("Epoch millis to stamp the proliferated changes with, so they carry the time of the original "
        + "marking rather than the time of this call. Defaults to now.")
      .setRequired(false);
  }

  @Override
  public void handle(Request request, Response response) {
    userSession.checkLoggedIn();
    List<String> issueKeys = request.mandatoryParamAsStrings(PARAM_ISSUES);
    if (issueKeys.size() > MAX_ISSUES) {
      throw new IllegalArgumentException("Can proliferate at most " + MAX_ISSUES + " issues per request");
    }
    String comment = request.param(PARAM_COMMENT);
    Long markedAt = request.paramAsLong(PARAM_MARKED_AT);

    try (DbSession dbSession = dbClient.openSession(false)) {
      List<IssueDto> issues = dbClient.issueDao().selectByKeys(dbSession, issueKeys);
      IssueChangeContext context = issueChangeContextByUserBuilder(
        new Date(markedAt == null ? system2.now() : markedAt), userSession.getUuid()).build();

      // Grouped so one task covers every issue that shares a branch and a transition, instead of one task each.
      Map<String, List<String>> keysByBranchAndTransition = new LinkedHashMap<>();
      Map<String, String> transitionByGroup = new LinkedHashMap<>();
      Map<String, String> branchByGroup = new LinkedHashMap<>();
      for (IssueDto issueDto : issues) {
        String transition = transitionOf(issueDto);
        if (transition == null) {
          continue;
        }
        checkIssueAdminPermission(dbSession, issueDto);
        String group = issueDto.getProjectUuid() + "\u0000" + transition;
        keysByBranchAndTransition.computeIfAbsent(group, g -> new ArrayList<>()).add(issueDto.getKey());
        transitionByGroup.put(group, transition);
        branchByGroup.put(group, issueDto.getProjectUuid());
      }
      keysByBranchAndTransition.forEach((group, keys) -> proliferationSubmitter.submit(dbSession,
        branchByGroup.get(group), keys, context, transitionByGroup.get(group), comment, null));
    }
    response.noContent();
  }

  private void checkIssueAdminPermission(DbSession dbSession, IssueDto issueDto) {
    ProjectDto project = dbClient.projectDao().selectByBranchUuid(dbSession, issueDto.getProjectUuid())
      .orElseThrow(() -> new IllegalStateException("Project not found for issue " + issueDto.getKey()));
    userSession.checkEntityPermission(UserRole.ISSUE_ADMIN, project);
  }

  /**
   * The transition that produced the issue's current resolution. Returns null for anything that is not one of the
   * three markings this feature proliferates, which is how a fixed or removed issue becomes a silent skip.
   */
  @Nullable
  private static String transitionOf(IssueDto issueDto) {
    String resolution = issueDto.getResolution();
    if (resolution == null) {
      return null;
    }
    return switch (resolution) {
      case Issue.RESOLUTION_FALSE_POSITIVE -> DefaultTransitions.FALSE_POSITIVE;
      case Issue.RESOLUTION_WONT_FIX -> DefaultTransitions.ACCEPT;
      case Issue.RESOLUTION_EXCEPTION -> DefaultTransitions.EXCEPTION;
      default -> null;
    };
  }
}
