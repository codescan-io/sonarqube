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

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.api.server.ServerSide;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.workflow.Transition;
import org.sonar.server.user.UserSession;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

@ServerSide
public class TransitionAction extends Action {

  public static final String DO_TRANSITION_KEY = "do_transition";
  public static final String TRANSITION_PARAMETER = "transition";

  private final TransitionService transitionService;
  private final IssueExceptionExpiryResolver exceptionExpiryResolver;
  private final DbClient dbClient;

  public TransitionAction(TransitionService transitionService, IssueExceptionExpiryResolver exceptionExpiryResolver, DbClient dbClient) {
    super(DO_TRANSITION_KEY);
    this.transitionService = transitionService;
    this.exceptionExpiryResolver = exceptionExpiryResolver;
    this.dbClient = dbClient;
  }

  @Override
  public boolean verify(Map<String, Object> properties, Collection<DefaultIssue> issues, UserSession userSession) {
    transition(properties);
    return true;
  }

  @Override
  public boolean execute(Map<String, Object> properties, Context context) {
    DefaultIssue issue = context.issue();
    String transition = transition(properties);
    if (!canExecuteTransition(issue, transition) || !transitionService.doTransition(issue, context.issueChangeContext(), transition)) {
      return false;
    }
    IssueDto issueDto = effectiveIssueDto(context.issueDto(), issue);
    @Nullable DbSession session = context.dbSession();
    if (session != null) {
      issue.setIssueResolutionExpiresAt(
        exceptionExpiryResolver.resolveIssueResolutionExpiresAt(session, issueDto, issue, transition));
    } else {
      try (DbSession dbSession = dbClient.openSession(false)) {
        issue.setIssueResolutionExpiresAt(
          exceptionExpiryResolver.resolveIssueResolutionExpiresAt(dbSession, issueDto, issue, transition));
      }
    }
    return true;
  }

  /**
   * Bulk change and other callers should pass the loaded {@link IssueDto}. If it is missing, derive severity and prior
   * expiry from the {@link DefaultIssue} (still holding DB-loaded expiry until {@link DefaultIssue#setIssueResolutionExpiresAt} runs).
   */
  private static IssueDto effectiveIssueDto(@Nullable IssueDto fromContext, DefaultIssue issueAfterTransition) {
    if (fromContext != null) {
      return fromContext;
    }
    return new IssueDto()
      .setKee(issueAfterTransition.key())
      .setSeverity(issueAfterTransition.severity())
      .setIssueResolutionExpiresAt(issueAfterTransition.issueResolutionExpiresAt());
  }

  @Override
  public boolean shouldRefreshMeasures() {
    return true;
  }

  private boolean canExecuteTransition(DefaultIssue issue, String transitionKey) {
    return transitionService.listTransitions(issue)
      .stream()
      .map(Transition::key)
      .collect(Collectors.toSet())
      .contains(transitionKey);
  }

  private static String transition(Map<String, Object> properties) {
    String param = (String) properties.get(TRANSITION_PARAMETER);
    checkArgument(!isNullOrEmpty(param), "Missing parameter : 'transition'");
    return param;
  }

}
