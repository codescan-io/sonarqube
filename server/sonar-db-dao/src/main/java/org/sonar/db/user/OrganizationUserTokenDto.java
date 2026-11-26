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
package org.sonar.db.user;

/**
 * A user token together with the login and name of the user who owns it.
 * Used by the organization token administration screen.
 */
public class OrganizationUserTokenDto extends UserTokenDto {

  private String login;
  private String userName;

  public String getLogin() {
    return login;
  }

  public OrganizationUserTokenDto setLogin(String login) {
    this.login = login;
    return this;
  }

  public String getUserName() {
    return userName;
  }

  public OrganizationUserTokenDto setUserName(String userName) {
    this.userName = userName;
    return this;
  }
}
