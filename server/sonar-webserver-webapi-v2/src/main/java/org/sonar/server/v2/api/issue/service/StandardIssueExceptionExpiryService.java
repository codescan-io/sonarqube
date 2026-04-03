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
package org.sonar.server.v2.api.issue.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.Issue;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueChangePostProcessor;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.issue.WebIssueStorage;
import org.sonar.server.user.UserSession;

/**
 * Reopens standard (non-security-hotspot) issues that are in Exception resolution and whose
 * {@code issue_resolution_expires_at} is in the past. Persists {@code REOPENED} status, {@code null}
 * resolution, {@code null} expiry, {@code issue_update_date} from the change context, and
 * {@code updated_at} from {@link WebIssueStorage#save}.
 */
@ServerSide
public class StandardIssueExceptionExpiryService {

  private static final Logger LOGGER = LoggerFactory.getLogger(StandardIssueExceptionExpiryService.class);
  private static final int BATCH_SIZE = 500;

  private final DbClient dbClient;
  private final UserSession userSession;
  private final TransitionService transitionService;
  private final WebIssueStorage issueStorage;
  private final IssueChangePostProcessor issueChangePostProcessor;
  private final System2 system2;

  public StandardIssueExceptionExpiryService(DbClient dbClient, UserSession userSession,
    TransitionService transitionService, WebIssueStorage issueStorage,
    IssueChangePostProcessor issueChangePostProcessor, System2 system2) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.transitionService = transitionService;
    this.issueStorage = issueStorage;
    this.issueChangePostProcessor = issueChangePostProcessor;
    this.system2 = system2;
  }

  public int expireExpiredExceptions() {
    long currentTime = system2.now();

    try (DbSession dbSession = dbClient.openSession(false)) {
      List<String> expiredKeys = dbClient.issueDao()
        .selectExpiredStandardIssueExceptionKeys(dbSession, currentTime);

      if (expiredKeys.isEmpty()) {
        LOGGER.debug("No expired standard-issue exceptions found at timestamp {}", currentTime);
        return 0;
      }

      LOGGER.info("Found {} expired standard-issue exception keys to process", expiredKeys.size());

      int totalExpired = 0;
      int totalBatches = (expiredKeys.size() + BATCH_SIZE - 1) / BATCH_SIZE;

      for (int i = 0; i < expiredKeys.size(); i += BATCH_SIZE) {
        int batchNumber = (i / BATCH_SIZE) + 1;
        int endIndex = Math.min(i + BATCH_SIZE, expiredKeys.size());
        List<String> batchKeys = expiredKeys.subList(i, endIndex);

        try {
          int batchExpired = processBatch(dbSession, batchKeys, currentTime);
          totalExpired += batchExpired;
          LOGGER.debug("Processed batch {}/{}: {} issues reopened", batchNumber, totalBatches, batchExpired);
        } catch (Exception e) {
          LOGGER.error("Failed to process batch {}/{} with {} keys: {}",
            batchNumber, totalBatches, batchKeys.size(), e.getMessage(), e);
        }
      }

      LOGGER.info("Successfully reopened {} out of {} expired exception issues", totalExpired, expiredKeys.size());
      return totalExpired;
    } catch (Exception e) {
      LOGGER.error("Failed to expire exception issues: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to expire exception issues", e);
    }
  }

  private int processBatch(DbSession dbSession, List<String> issueKeys, long operationTimeMillis) {
    if (issueKeys == null || issueKeys.isEmpty()) {
      return 0;
    }

    List<IssueDto> issues = dbClient.issueDao().selectByKeys(dbSession, issueKeys);
    if (issues.isEmpty()) {
      LOGGER.warn("No issues found for {} keys - may have been deleted", issueKeys.size());
      return 0;
    }

    if (issues.size() < issueKeys.size()) {
      LOGGER.warn("Found only {} issues for {} keys - some may have been deleted",
        issues.size(), issueKeys.size());
    }

    Set<String> componentUuids = issues.stream()
      .map(IssueDto::getComponentUuid)
      .filter(java.util.Objects::nonNull)
      .collect(Collectors.toSet());

    if (componentUuids.isEmpty()) {
      LOGGER.warn("No valid component UUIDs found for batch");
      return 0;
    }

    List<ComponentDto> components = dbClient.componentDao().selectByUuids(dbSession, componentUuids);
    Map<String, ComponentDto> componentsByUuid = components.stream()
      .collect(Collectors.toMap(ComponentDto::uuid, c -> c));

    Date changeDate = new Date(operationTimeMillis);
    String systemAdminUuid = userSession.getUuid();
    IssueChangeContext context = IssueChangeContext.newBuilder()
      .setDate(changeDate)
      .setUserUuid(systemAdminUuid)
      .withRefreshMeasures()
      .build();

    String transitionKey = DefaultTransitions.REOPEN;
    List<DefaultIssue> modifiedIssues = new ArrayList<>();
    List<String> successfullyTransitionedKeys = new ArrayList<>();
    Set<String> touchedComponentUuids = new java.util.HashSet<>();

    for (IssueDto issueDto : issues) {
      try {
        if (issueDto.getKey() == null) {
          LOGGER.warn("Skipping issue with null key");
          continue;
        }

        DefaultIssue defaultIssue = issueDto.toDefaultIssue();

        boolean reopened = transitionService.doTransition(defaultIssue, context, transitionKey);
        if (!reopened && Issue.STATUS_RESOLVED.equals(issueDto.getStatus())
          && Issue.RESOLUTION_EXCEPTION.equals(issueDto.getResolution())) {
          LOGGER.warn(
            "Workflow reopen did not apply for issue {} (type={}); forcing REOPENED after expired exception",
            issueDto.getKey(), issueDto.getType());
          reopened = true;
        }

        if (reopened) {
          applyExpiredStandardExceptionReopenState(defaultIssue, changeDate);
          modifiedIssues.add(defaultIssue);
          successfullyTransitionedKeys.add(issueDto.getKey());

          if (defaultIssue.componentUuid() != null) {
            touchedComponentUuids.add(defaultIssue.componentUuid());
          }
        } else {
          LOGGER.debug("Issue {} was not reopened (current status: {}, resolution: {})",
            issueDto.getKey(), issueDto.getStatus(), issueDto.getResolution());
        }
      } catch (Exception e) {
        LOGGER.error("Failed to transition issue {} after expired exception: {}",
          issueDto.getKey(), e.getMessage(), e);
      }
    }

    if (modifiedIssues.isEmpty()) {
      LOGGER.debug("No issues were modified in this batch");
      return 0;
    }

    try {
      issueStorage.save(dbSession, modifiedIssues);
      LOGGER.debug("Saved {} modified issues to database", modifiedIssues.size());
    } catch (Exception e) {
      LOGGER.error("Failed to save modified issues: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to save reopened issues", e);
    }

    List<ComponentDto> touchedComponents = touchedComponentUuids.stream()
      .map(componentsByUuid::get)
      .filter(java.util.Objects::nonNull)
      .toList();

    if (!touchedComponents.isEmpty()) {
      try {
        issueChangePostProcessor.process(dbSession, modifiedIssues, touchedComponents, false);
        LOGGER.debug("Post-processed {} components", touchedComponents.size());
      } catch (Exception e) {
        LOGGER.error("Failed to post-process issue changes: {}", e.getMessage(), e);
      }
    }

    return successfullyTransitionedKeys.size();
  }

  /**
   * Final persisted shape after cron expiry: aligns DB with {@code REOPENED}, cleared resolution and
   * {@code issue_resolution_expires_at}, and {@code issue_update_date}. {@code updated_at} is set in
   * {@link WebIssueStorage#save} via {@link org.sonar.db.issue.IssueDto#toDtoForUpdate}.
   */
  private static void applyExpiredStandardExceptionReopenState(DefaultIssue issue, Date changeDate) {
    issue.setStatus(Issue.STATUS_REOPENED);
    issue.setResolution(null);
    issue.setIssueResolutionExpiresAt(null);
    issue.setUpdateDate(changeDate);
    issue.setChanged(true);
  }
}
