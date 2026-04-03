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

import static org.sonar.server.v2.WebApiEndpoints.INTERNAL;
import static org.sonar.server.v2.WebApiEndpoints.ISSUE_EXCEPTION_EXPIRE_ENDPOINT;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.sonar.server.v2.api.issue.response.ExpireIssueExceptionsRestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping(ISSUE_EXCEPTION_EXPIRE_ENDPOINT)
@RestController
public interface IssueExceptionExpiryController {

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Expire standard issue exceptions", description = """
      Reopens non-security-hotspot issues in Exception resolution whose exception expiry time has passed
      (workflow status REOPENED, resolution cleared). Does not change security hotspots. Requires 'Administer System' permission.
      """, extensions = @Extension(properties = {@ExtensionProperty(name = INTERNAL, value = "true")}))
  ExpireIssueExceptionsRestResponse expireExpiredIssueExceptions();
}
