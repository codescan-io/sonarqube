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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.issue.IssueDto;

/**
 * Code issue (non-hotspot) exception resolution expiry: manual YMD + optional offset, or instance auto-expiry by severity.
 * Used by {@code api/issues/do_transition} and bulk transition ({@code TransitionAction}).
 */
@ServerSide
public class CodeIssueExceptionExpiryService {

  /** HTTP / bulk action map key; same as {@code api/issues/do_transition}. */
  public static final String PARAM_ISSUE_RESOLUTION_EXPIRY_DATE = "issueResolutionExpiryDate";
  /** JS {@code Date.getTimezoneOffset()} when anchoring civil dates (manual and auto-expiry). */
  public static final String PARAM_ISSUE_RESOLUTION_EXPIRY_OFFSET_MINUTES = "issueResolutionExpiryOffsetMinutes";

  /** @see io.codescan.cloud.DeveloperPlugin#KEY_ISSUE_EXCEPTION_AUTO_EXPIRY_ENABLED */
  private static final String KEY_ISSUE_EXCEPTION_AUTO_EXPIRY_ENABLED = "codescan.cloud.issue.exception.autoAssignExpiry.enabled";
  private static final String KEY_DAYS_BLOCKER = "codescan.cloud.issue.exception.autoAssignExpiry.blocker";
  private static final String KEY_DAYS_CRITICAL = "codescan.cloud.issue.exception.autoAssignExpiry.critical";
  private static final String KEY_DAYS_MAJOR = "codescan.cloud.issue.exception.autoAssignExpiry.major";
  private static final String KEY_DAYS_MINOR = "codescan.cloud.issue.exception.autoAssignExpiry.minor";
  private static final String KEY_DAYS_INFO = "codescan.cloud.issue.exception.autoAssignExpiry.info";

  private static final Set<String> STATUSES_ELIGIBLE_FOR_EXCEPTION_EXPIRY = Set.of(
    Issue.STATUS_OPEN, Issue.STATUS_REOPENED, Issue.STATUS_CONFIRMED);

  private final System2 system2;
  private final Configuration configuration;

  public CodeIssueExceptionExpiryService(System2 system2, Configuration configuration) {
    this.system2 = system2;
    this.configuration = configuration;
  }

  public void applyAfterTransition(DefaultIssue issue, IssueDto dtoBeforeTransition, String transitionKey, String previousStatus,
    boolean hasExpiryDateParam, @Nullable String expiryDateParam, @Nullable String expiryOffsetMinutesParam) {
    if (dtoBeforeTransition.getType() == RuleType.SECURITY_HOTSPOT.getDbConstant()) {
      return;
    }
    // Reopen always drops exception resolution in workflow; clear expiry even if in-memory resolution were stale.
    if (DefaultTransitions.REOPEN.equals(transitionKey)) {
      clearIssueResolutionExpiresAtIfPresent(issue, dtoBeforeTransition);
      return;
    }
    if (Issue.RESOLUTION_EXCEPTION.equals(issue.resolution())
      && DefaultTransitions.EXCEPTION.equals(transitionKey)
      && STATUSES_ELIGIBLE_FOR_EXCEPTION_EXPIRY.contains(previousStatus)) {
      Long expiry = resolveCodeIssueExceptionExpiry(dtoBeforeTransition, hasExpiryDateParam, expiryDateParam, expiryOffsetMinutesParam);
      issue.setIssueResolutionExpiresAt(expiry);
      issue.setChanged(true);
      return;
    }
    if (!Issue.RESOLUTION_EXCEPTION.equals(issue.resolution())
      && (issue.issueResolutionExpiresAt() != null || dtoBeforeTransition.getIssueResolutionExpiresAt() != null)) {
      issue.setIssueResolutionExpiresAt(null);
      issue.setChanged(true);
    }
  }

  private static void clearIssueResolutionExpiresAtIfPresent(DefaultIssue issue, IssueDto dtoBeforeTransition) {
    if (issue.issueResolutionExpiresAt() != null || dtoBeforeTransition.getIssueResolutionExpiresAt() != null) {
      issue.setIssueResolutionExpiresAt(null);
      issue.setChanged(true);
    }
  }

  @Nullable
  private Long resolveCodeIssueExceptionExpiry(IssueDto issueDto, boolean hasExpiryDateParam, @Nullable String expiryDateParam,
    @Nullable String expiryOffsetMinutesParam) {
    if (StringUtils.isNotBlank(expiryDateParam)) {
      return manualExpiryDateToEpochMillis(expiryDateParam.trim(), expiryOffsetMinutesParam);
    }
    if (hasExpiryDateParam) {
      return null;
    }
    if (!isIssueExceptionAutoExpiryEnabled()) {
      return null;
    }
    int days = getAutoExpiryDaysForSeverity(issueDto);
    if (days <= 0) {
      return null;
    }
    return autoAssignExpiryAtStartOfUserLocalDay(days, expiryOffsetMinutesParam);
  }

  private boolean isIssueExceptionAutoExpiryEnabled() {
    return configuration.getBoolean(KEY_ISSUE_EXCEPTION_AUTO_EXPIRY_ENABLED).orElse(false);
  }

  private int getAutoExpiryDaysForSeverity(IssueDto issueDto) {
    String severity = issueDto.getSeverity();
    if (severity == null || severity.isBlank()) {
      severity = "INFO";
    }
    String normalized = severity.toUpperCase(Locale.ROOT);
    String primaryKey = propertyKeyForCodescanAutoAssignExpiryDays(normalized);
    return configuration.getInt(primaryKey).orElse(0);
  }

  /** @see io.codescan.cloud.DeveloperPlugin per-severity keys */
  private static String propertyKeyForCodescanAutoAssignExpiryDays(String severityUpperCase) {
    return switch (severityUpperCase) {
      case "BLOCKER" -> KEY_DAYS_BLOCKER;
      case "CRITICAL", "HIGH" -> KEY_DAYS_CRITICAL;
      case "MAJOR", "MEDIUM" -> KEY_DAYS_MAJOR;
      case "MINOR", "LOW" -> KEY_DAYS_MINOR;
      case "INFO" -> KEY_DAYS_INFO;
      default -> KEY_DAYS_INFO;
    };
  }

  private long autoAssignExpiryAtStartOfUserLocalDay(int days, @Nullable String offsetMinutesRaw) {
    Instant now = Instant.ofEpochMilli(system2.now());
    if (StringUtils.isBlank(offsetMinutesRaw)) {
      return utcStartOfDayAfterDaysFromNowUtc(days);
    }
    try {
      int offsetMinutes = Integer.parseInt(offsetMinutesRaw.trim());
      ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(Math.negateExact(offsetMinutes) * 60);
      LocalDate userToday = now.atZone(zoneOffset).toLocalDate();
      LocalDate expiryDate = userToday.plusDays(days);
      return expiryDate.atTime(LocalTime.MIDNIGHT).atOffset(zoneOffset).toInstant().toEpochMilli();
    } catch (RuntimeException ignored) {
      return utcStartOfDayAfterDaysFromNowUtc(days);
    }
  }

  private long utcStartOfDayAfterDaysFromNowUtc(int days) {
    LocalDate d = Instant.ofEpochMilli(system2.now())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
      .plusDays(days);
    return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  private static long manualExpiryDateToEpochMillis(String ymd, @Nullable String offsetMinutesRaw) {
    LocalDate d = LocalDate.parse(ymd);
    if (StringUtils.isBlank(offsetMinutesRaw)) {
      return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
    try {
      int offsetMinutes = Integer.parseInt(offsetMinutesRaw.trim());
      ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(Math.negateExact(offsetMinutes) * 60);
      return d.atTime(LocalTime.MIDNIGHT).atOffset(zoneOffset).toInstant().toEpochMilli();
    } catch (RuntimeException ignored) {
      return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
  }
}
