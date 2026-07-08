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
 * Code issue (non-hotspot) exception resolution expiry: manual YMD, or instance auto-expiry by severity.
 * Civil dates are always anchored to UTC start-of-day to align with security-hotspot exception expiry
 * (see {@code ChangeStatusAction} and {@code HotspotExceptionExpiryJob}).
 * Used by {@code api/issues/do_transition} and bulk transition ({@code TransitionAction}).
 */
@ServerSide
public class CodeIssueExceptionExpiryService {

  /** HTTP / bulk action map key; same as {@code api/issues/do_transition}. */
  public static final String PARAM_ISSUE_RESOLUTION_EXPIRY_DATE = "issueResolutionExpiryDate";

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
    boolean hasExpiryDateParam, @Nullable String expiryDateParam) {
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
      Long expiry = resolveCodeIssueExceptionExpiry(dtoBeforeTransition, hasExpiryDateParam, expiryDateParam);
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
  private Long resolveCodeIssueExceptionExpiry(IssueDto issueDto, boolean hasExpiryDateParam, @Nullable String expiryDateParam) {
    if (StringUtils.isNotBlank(expiryDateParam)) {
      return manualExpiryDateToEpochMillis(expiryDateParam.trim());
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
    return utcStartOfDayAfterDaysFromNowUtc(days);
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

  private long utcStartOfDayAfterDaysFromNowUtc(int days) {
    LocalDate d = Instant.ofEpochMilli(system2.now())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
      .plusDays(days);
    return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  private static long manualExpiryDateToEpochMillis(String ymd) {
    return LocalDate.parse(ymd).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
