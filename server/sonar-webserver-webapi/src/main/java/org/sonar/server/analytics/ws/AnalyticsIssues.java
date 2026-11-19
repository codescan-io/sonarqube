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
package org.sonar.server.analytics.ws;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.AnalyticsIssueDto;
import org.sonar.db.issue.IssueGroupCountDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.NotFoundException;

import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.utils.DateUtils.formatDateTime;

/** Issues, users and counts as the governance and audit actions write them. */
class AnalyticsIssues {

  private AnalyticsIssues() {
    // static only
  }

  /** The issue status the UI shows for an accepted or false-positive resolution, the resolution itself otherwise. */
  static String exceptionStatus(@Nullable String resolution) {
    if (RESOLUTION_WONT_FIX.equals(resolution)) {
      return "ACCEPTED";
    }
    return RESOLUTION_FALSE_POSITIVE.equals(resolution) ? "FALSE_POSITIVE" : resolution;
  }

  @CheckForNull
  static String typeName(@Nullable Integer dbConstant) {
    RuleType type = dbConstant == null ? null : RuleType.valueOfNullable(dbConstant);
    return type == null ? null : type.name();
  }

  static ProjectDto checkProjectOfOrganization(DbClient dbClient, DbSession dbSession, OrganizationDto organization, String projectKey) {
    return dbClient.projectDao().selectProjectByKey(dbSession, projectKey)
      .filter(p -> organization.getUuid().equals(p.getOrganizationUuid()))
      .orElseThrow(() -> new NotFoundException(String.format("Project '%s' not found", projectKey)));
  }

  static Map<String, UserDto> usersByUuid(DbClient dbClient, DbSession dbSession, Collection<String> uuids) {
    if (uuids.isEmpty()) {
      return Map.of();
    }
    return dbClient.userDao().selectByUuids(dbSession, uuids).stream()
      .collect(Collectors.toMap(UserDto::getUuid, Function.identity()));
  }

  /** Writes the user as a {@code name} property, or nothing when there is no user (a change made by the analysis). */
  static void writeUser(JsonWriter json, String name, @Nullable String userUuid, Map<String, UserDto> users) {
    if (userUuid == null) {
      return;
    }
    UserDto user = users.get(userUuid);
    json.name(name).beginObject();
    if (user == null) {
      // the user was deleted since
      json.prop("login", userUuid);
    } else {
      json.prop("login", user.getLogin())
        .prop("name", user.getName())
        .prop("active", user.isActive());
    }
    json.endObject();
  }

  /** The properties of an issue, inside an object the caller opens and closes. */
  static void writeIssueProps(JsonWriter json, AnalyticsIssueDto issue) {
    json.prop("issueKey", issue.getIssueKey())
      .prop("type", typeName(issue.getIssueType()))
      .prop("severity", issue.getSeverity())
      .prop("status", issue.getStatus())
      .prop("resolution", issue.getResolution())
      .prop("rule", issue.getRuleRepository() + ":" + issue.getRuleKey())
      .prop("ruleName", issue.getRuleName())
      .prop("projectKey", issue.getProjectKey())
      .prop("projectName", issue.getProjectName())
      .prop("filePath", issue.getFilePath());
    if (issue.getLine() != null) {
      json.prop("line", issue.getLine());
    }
    writeDate(json, "createdAt", issue.getCreationDate());
    writeDate(json, "closedAt", issue.getCloseDate());
  }

  static void writeDate(JsonWriter json, String name, @Nullable Long date) {
    if (date != null) {
      json.prop(name, formatDateTime(date));
    }
  }

  /** Counts by type then severity, both sorted. */
  static Map<String, Map<String, Long>> byTypeAndSeverity(List<IssueGroupCountDto> counts) {
    Map<String, Map<String, Long>> byType = new TreeMap<>();
    for (IssueGroupCountDto count : counts) {
      String type = typeName(count.getIssueType());
      if (type != null && count.getSeverity() != null) {
        byType.computeIfAbsent(type, k -> new TreeMap<>()).merge(count.getSeverity(), count.getCount(), Long::sum);
      }
    }
    return byType;
  }

  static void writeByTypeAndSeverity(JsonWriter json, String name, Map<String, Map<String, Long>> byType) {
    json.name(name).beginObject();
    byType.forEach((type, bySeverity) -> {
      json.name(type).beginObject();
      bySeverity.forEach((severity, count) -> json.prop(severity, count.longValue()));
      json.endObject();
    });
    json.endObject();
  }
}
