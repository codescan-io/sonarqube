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
package org.sonar.db.issue;

import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Which changes of the issues and hotspots of an organization's main branches the audit trail lists, made in
 * [{@code fromDate}, {@code toDate}).
 */
public class IssueAuditQuery {

  /** Changes of the status or resolution: accepted, false positive, reopened, reviewed... */
  public static final String CATEGORY_STATUS = "STATUS";
  public static final String CATEGORY_SEVERITY = "SEVERITY";
  public static final String CATEGORY_COMMENT = "COMMENT";
  public static final String CATEGORY_EXCEPTION_REASON = "EXCEPTION_REASON";
  public static final Set<String> CATEGORIES = Set.of(CATEGORY_STATUS, CATEGORY_SEVERITY, CATEGORY_COMMENT, CATEGORY_EXCEPTION_REASON);

  /** A change row is never created before the change it records; the margin covers a scanner clock ahead of the server's. */
  private static final long CREATED_AT_MARGIN_MS = 24L * 60 * 60 * 1000;

  private final String organizationUuid;
  private final long fromDate;
  private final long toDate;
  private final List<String> categories;
  private final String userUuid;
  private final String projectUuid;
  private final boolean includeAnalysisChanges;

  public IssueAuditQuery(String organizationUuid, long fromDate, long toDate, List<String> categories, @Nullable String userUuid,
    @Nullable String projectUuid, boolean includeAnalysisChanges) {
    checkArgument(!categories.isEmpty() && CATEGORIES.containsAll(categories), "Categories must be some of %s", CATEGORIES);
    this.organizationUuid = organizationUuid;
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.categories = List.copyOf(categories);
    this.userUuid = userUuid;
    this.projectUuid = projectUuid;
    this.includeAnalysisChanges = includeAnalysisChanges;
  }

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  public long getFromDate() {
    return fromDate;
  }

  public long getToDate() {
    return toDate;
  }

  /**
   * The date of a change cannot be indexed, as it falls back to the row creation date, so the rows are first narrowed down by
   * creation date, which is indexed.
   */
  public long getMinCreatedAt() {
    return fromDate - CREATED_AT_MARGIN_MS;
  }

  public List<String> getCategories() {
    return categories;
  }

  @CheckForNull
  public String getUserUuid() {
    return userUuid;
  }

  @CheckForNull
  public String getProjectUuid() {
    return projectUuid;
  }

  /** Whether changes made by the analysis, such as closing an issue gone from the code, are listed too. */
  public boolean isIncludeAnalysisChanges() {
    return includeAnalysisChanges;
  }
}
