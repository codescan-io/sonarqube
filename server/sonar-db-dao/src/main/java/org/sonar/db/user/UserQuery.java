/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UserQuery {
  private final String searchText;
  private final Boolean isActive;
  private final List<String> organizationUuids;

  public UserQuery(@Nullable String searchText, @Nullable Boolean isActive, @Nullable List<String> organizationUuids) {
    this.searchText = searchTextToSearchTextSql(searchText);
    this.isActive = isActive;
    this.organizationUuids = organizationUuids != null ? ImmutableList.copyOf(organizationUuids) : null;
  }

  private static String searchTextToSearchTextSql(@Nullable String text) {
    String sql = null;
    if (text != null) {
      sql = StringUtils.replace(text, "%", "/%");
      sql = StringUtils.replace(sql, "_", "/_");
      sql = "%" + sql + "%";
    }
    return sql;
  }

  @CheckForNull
  private String getSearchText() {
    return searchText;
  }

  @CheckForNull
  private Boolean isActive() {
    return isActive;
  }

  @CheckForNull
  private List<String> organizationUuids() {
    return organizationUuids;
  }

  public static UserQueryBuilder builder() {
    return new UserQueryBuilder();
  }

  public static final class UserQueryBuilder {
    private String searchText;
    private Boolean isActive;
    private final List<String> organizationUuids = new ArrayList<>();

    private UserQueryBuilder() {
    }

    public UserQueryBuilder searchText(@Nullable String searchText) {
      this.searchText = searchText;
      return this;
    }

    public UserQueryBuilder isActive(@Nullable Boolean isActive) {
      this.isActive = isActive;
      return this;
    }

    /**
     * Include only users that are members of at least one of the OrganizationUuids
     */
    public UserQueryBuilder addOrganizationUuids(List<String> organizationUuids) {
      this.organizationUuids.addAll(organizationUuids);
      return this;
    }

    public UserQuery build() {
      return new UserQuery(searchText, isActive, organizationUuids);
    }
  }
}
