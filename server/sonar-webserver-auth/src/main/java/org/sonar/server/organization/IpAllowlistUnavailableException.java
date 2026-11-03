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
package org.sonar.server.organization;

import org.sonar.server.exceptions.ServerException;

/**
 * The request fails with HTTP 503 because the IP allowlist cannot be read. It is never let through, and it is not
 * a login failure: the user stays logged in. The message is safe to show; the database error is kept as the cause.
 */
public class IpAllowlistUnavailableException extends ServerException {

  public static final int HTTP_SERVICE_UNAVAILABLE = 503;
  static final String MESSAGE = "Access to this organization cannot be checked because its IP allowlist cannot be read right now. "
    + "Try again in a few minutes.";

  public IpAllowlistUnavailableException(OrganizationIpAccessUnavailableException cause) {
    super(HTTP_SERVICE_UNAVAILABLE, MESSAGE);
    initCause(cause);
  }
}
