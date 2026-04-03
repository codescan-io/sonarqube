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
package org.sonar.server.v2.api.issue.controller;

import org.sonar.server.user.UserSession;
import org.sonar.server.v2.api.issue.response.ExpireIssueExceptionsRestResponse;
import org.sonar.server.v2.api.issue.service.StandardIssueExceptionExpiryService;

public class DefaultIssueExceptionExpiryController implements IssueExceptionExpiryController {

  private final UserSession userSession;
  private final StandardIssueExceptionExpiryService standardIssueExceptionExpiryService;

  public DefaultIssueExceptionExpiryController(UserSession userSession,
    StandardIssueExceptionExpiryService standardIssueExceptionExpiryService) {
    this.userSession = userSession;
    this.standardIssueExceptionExpiryService = standardIssueExceptionExpiryService;
  }

  @Override
  public ExpireIssueExceptionsRestResponse expireExpiredIssueExceptions() {
    userSession.checkIsSystemAdministrator();
    int expiredCount = standardIssueExceptionExpiryService.expireExpiredExceptions();
    return new ExpireIssueExceptionsRestResponse(expiredCount);
  }
}
