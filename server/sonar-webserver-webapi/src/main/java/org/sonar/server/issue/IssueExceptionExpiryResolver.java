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

import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.property.PropertyDto;

import static org.sonar.api.issue.Issue.RESOLUTION_EXCEPTION;

/**
 * Resolves {@link IssueDto#getIssueResolutionExpiresAt() issue_resolution_expires_at} after a workflow transition,
 * for {@code EXCEPTION} resolution and related cases. Used by {@code do_transition} and bulk {@code do_transition}.
 * <p>
 * Global keys must stay identical to {@code io.codescan.cloud.DeveloperPlugin#KEY_AUTO_ASSIGN_EXPIRY_*} in codescanng.
 */
@ServerSide
public class IssueExceptionExpiryResolver {

  private static final String AUTO_ASSIGN_EXPIRY_BLOCKER = "codescan.cloud.autoAssignExpiry.blocker";
  private static final String AUTO_ASSIGN_EXPIRY_CRITICAL = "codescan.cloud.autoAssignExpiry.critical";
  private static final String AUTO_ASSIGN_EXPIRY_MAJOR = "codescan.cloud.autoAssignExpiry.major";
  private static final String AUTO_ASSIGN_EXPIRY_MINOR = "codescan.cloud.autoAssignExpiry.minor";
  private static final String AUTO_ASSIGN_EXPIRY_INFO = "codescan.cloud.autoAssignExpiry.info";

  private final DbClient dbClient;
  private final System2 system2;
  private final Configuration configuration;

  public IssueExceptionExpiryResolver(DbClient dbClient, System2 system2, Configuration configuration) {
    this.dbClient = dbClient;
    this.system2 = system2;
    this.configuration = configuration;
  }

  /**
   * @param issueDto      state loaded before the transition (used for severity and prior expiry)
   * @param issueAfter    issue after the workflow transition
   * @param transitionKey transition that was applied
   * @return millis since epoch for {@code issue_resolution_expires_at}, or {@code null} to clear / leave unset
   */
  @CheckForNull
  public Long resolveIssueResolutionExpiresAt(DbSession session, IssueDto issueDto, DefaultIssue issueAfter, String transitionKey) {
    if (!RESOLUTION_EXCEPTION.equals(issueAfter.resolution())) {
      return null;
    }
    if (DefaultTransitions.EXCEPTION.equals(transitionKey)) {
      OptionalInt expiryDays = resolveAutoAssignExpiryDays(session, mapSeverityToPropertyKey(issueDto.getSeverity()));
      if (expiryDays.isEmpty()) {
        return null;
      }
      return system2.now() + TimeUnit.DAYS.toMillis(expiryDays.getAsInt());
    }
    return issueDto.getIssueResolutionExpiresAt();
  }

  private static String mapSeverityToPropertyKey(String severity) {
    if (severity == null) {
      return AUTO_ASSIGN_EXPIRY_MAJOR;
    }
    return switch (severity) {
      case "BLOCKER" -> AUTO_ASSIGN_EXPIRY_BLOCKER;
      case "CRITICAL" -> AUTO_ASSIGN_EXPIRY_CRITICAL;
      case "MAJOR" -> AUTO_ASSIGN_EXPIRY_MAJOR;
      case "MINOR" -> AUTO_ASSIGN_EXPIRY_MINOR;
      case "INFO" -> AUTO_ASSIGN_EXPIRY_INFO;
      default -> AUTO_ASSIGN_EXPIRY_MAJOR;
    };
  }

  private OptionalInt resolveAutoAssignExpiryDays(DbSession session, String propertyKey) {
    PropertyDto property = dbClient.propertiesDao().selectGlobalProperty(session, propertyKey);
    if (property != null && property.getValue() != null) {
      OptionalInt fromDb = parsePositiveDays(property.getValue());
      if (fromDb.isPresent()) {
        return fromDb;
      }
    }

    OptionalInt fromConfig = parsePositiveDays(configuration.get(propertyKey).orElse(null));
    if (fromConfig.isPresent()) {
      return fromConfig;
    }

    if (!AUTO_ASSIGN_EXPIRY_MAJOR.equals(propertyKey)) {
      OptionalInt fromMajorConfig = parsePositiveDays(configuration.get(AUTO_ASSIGN_EXPIRY_MAJOR).orElse(null));
      if (fromMajorConfig.isPresent()) {
        return fromMajorConfig;
      }
      PropertyDto majorProperty = dbClient.propertiesDao().selectGlobalProperty(session, AUTO_ASSIGN_EXPIRY_MAJOR);
      if (majorProperty != null && majorProperty.getValue() != null) {
        OptionalInt fromDbMajor = parsePositiveDays(majorProperty.getValue());
        if (fromDbMajor.isPresent()) {
          return fromDbMajor;
        }
      }
    }

    return OptionalInt.empty();
  }

  private static OptionalInt parsePositiveDays(String raw) {
    if (raw == null || raw.isBlank()) {
      return OptionalInt.empty();
    }
    try {
      int days = Integer.parseInt(raw.trim());
      return days > 0 ? OptionalInt.of(days) : OptionalInt.empty();
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
