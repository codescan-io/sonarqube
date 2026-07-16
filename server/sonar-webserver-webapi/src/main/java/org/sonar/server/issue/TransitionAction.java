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
import org.sonar.api.server.ServerSide;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.workflow.Transition;
import org.sonar.server.user.UserSession;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import org.sonar.api.issue.DefaultTransitions;

@ServerSide
public class TransitionAction extends Action {

  public static final String DO_TRANSITION_KEY = "do_transition";
  public static final String TRANSITION_PARAMETER = "transition";
    private static final String PARAM_EXCEPTION_REASON = "exceptionReason";
    private static final String PARAM_COMMENT = "comment";

  private final TransitionService transitionService;
  private final CodeIssueExceptionExpiryService codeIssueExceptionExpiryService;
  private final IssueFieldsSetter issueFieldsSetter;

    public TransitionAction(TransitionService transitionService, CodeIssueExceptionExpiryService codeIssueExceptionExpiryService, IssueFieldsSetter issueFieldsSetter) {
    super(DO_TRANSITION_KEY);
    this.transitionService = transitionService;
    this.codeIssueExceptionExpiryService = codeIssueExceptionExpiryService;
    this.issueFieldsSetter = issueFieldsSetter;
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
    if (!canExecuteTransition(issue, transition)) {
      return false;
    }
    IssueDto dtoBefore = context.issueDto();
    String previousStatus = dtoBefore.getStatus();
    if (!transitionService.doTransition(context.issue(), context.issueChangeContext(), transition)) {
      return false;
    }
    boolean hasExpiryDateParam = properties.containsKey(CodeIssueExceptionExpiryService.PARAM_ISSUE_RESOLUTION_EXPIRY_DATE);
    String expiryDateParam = (String) properties.get(CodeIssueExceptionExpiryService.PARAM_ISSUE_RESOLUTION_EXPIRY_DATE);
    codeIssueExceptionExpiryService.applyAfterTransition(issue, dtoBefore, transition, previousStatus, hasExpiryDateParam, expiryDateParam);
    String exceptionReason = trimToNull((String) properties.get(PARAM_EXCEPTION_REASON));
    String comment = trimToNull((String) properties.get(PARAM_COMMENT));
    if (DefaultTransitions.EXCEPTION.equals(transition)) {
          String reasonToSave = exceptionReason != null ? exceptionReason : comment;
          checkArgument(reasonToSave != null,
                  "Parameter '%s' or '%s' must be specified when transition is '%s'",
                  PARAM_EXCEPTION_REASON, PARAM_COMMENT, DefaultTransitions.EXCEPTION);
          issueFieldsSetter.addExceptionReason(issue, reasonToSave, context.issueChangeContext());
          if (exceptionReason == null) {
              comment = null;
          }
    }
    if (comment != null) {
          issueFieldsSetter.addComment(issue, comment, context.issueChangeContext());
    }
    return true;
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
