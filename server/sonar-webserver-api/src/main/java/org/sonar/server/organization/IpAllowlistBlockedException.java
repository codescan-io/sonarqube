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

import javax.annotation.Nullable;
import org.sonar.server.exceptions.ForbiddenException;

/**
 * HTTP 403 for a request that the organization's IP allowlist blocks. Never a 401, which would log the user out.
 */
public class IpAllowlistBlockedException extends ForbiddenException {

  /** The web app recognises this answer by this text in the message. */
  public static final String MESSAGE_MARKER = "is blocked by its IP allowlist";

  /**
   * @param clientIp shown as "unknown" when missing or blank; such a request is blocked by every allowlist
   */
  public IpAllowlistBlockedException(String organizationKey, @Nullable String clientIp) {
    super("Access to organization '" + organizationKey + "' " + MESSAGE_MARKER + ". Your IP address is "
      + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp) + ".");
  }
}
