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

import com.google.common.collect.Lists;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.server.ServerSide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.ce.queue.CeQueue;
import org.sonar.ce.queue.CeTaskSubmit;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.server.setting.ProjectConfigurationLoader;


@ServerSide
public class IssueProliferationSubmitter {

  private static final Logger LOG = LoggerFactory.getLogger(IssueProliferationSubmitter.class);


  static final int ISSUES_PER_TASK = 500;

  static final String CHARACTERISTIC_TRANSITION = "proliferationTransition";
  static final String CHARACTERISTIC_MARKED_AT = "proliferationMarkedAt";
  static final String CHARACTERISTIC_AUTHOR = "proliferationAuthor";
  static final String CHARACTERISTIC_COMMENT = "proliferationComment";
  static final String CHARACTERISTIC_EXCEPTION_REASON = "proliferationExceptionReason";
  static final String CHARACTERISTIC_SOURCE_BRANCH_LABEL = "proliferationSourceBranch";

  private final DbClient dbClient;
  private final CeQueue ceQueue;
  private final IssueMarkingProliferationService proliferationService;
  private final ProjectConfigurationLoader projectConfigurationLoader;

  public IssueProliferationSubmitter(DbClient dbClient, CeQueue ceQueue,
    IssueMarkingProliferationService proliferationService,
    ProjectConfigurationLoader projectConfigurationLoader) {
    this.dbClient = dbClient;
    this.ceQueue = ceQueue;
    this.proliferationService = proliferationService;
    this.projectConfigurationLoader = projectConfigurationLoader;
  }


  public void submit(DbSession dbSession, String sourceBranchUuid, Collection<String> issueKeys,
    IssueChangeContext context, String transitionKey, @Nullable String comment, @Nullable String exceptionReason) {
    try {
      if (issueKeys.isEmpty() || !proliferationService.isProliferable(transitionKey)) {
        return;
      }
      Optional<BranchDto> sourceBranch = dbClient.branchDao().selectByUuid(dbSession, sourceBranchUuid);
      if (sourceBranch.isEmpty()) {
        return;
      }
      String projectUuid = sourceBranch.get().getProjectUuid();
      if (!isEnabled(dbSession, projectUuid)) {
        return;
      }

      Map<String, String> characteristics = characteristicsOf(context, transitionKey, comment, exceptionReason,
        IssueMarkingProliferationService.branchLabel(sourceBranch.get()));

      List<CeTaskSubmit> submissions = new ArrayList<>();
      for (List<String> chunk : Lists.partition(new ArrayList<>(issueKeys), ISSUES_PER_TASK)) {
        CeTaskSubmit.Builder builder = ceQueue.prepareSubmit()
          .setType(CeTaskTypes.RESOLUTION_PROLIFERATION)
          .setComponent(new CeTaskSubmit.Component(sourceBranchUuid, projectUuid))
          .setCharacteristics(characteristics);
        // The keys must be readable before the task row exists, or a worker could pick the task up and find
        // nothing to do. Same ordering as ReportSubmitter.
        dbClient.ceTaskInputDao().insert(dbSession, builder.getUuid(), serialize(chunk));
        submissions.add(builder.build());
      }
      dbSession.commit();
      ceQueue.massSubmit(submissions);

      LOG.debug("Queued {} proliferation task(s) for {} issue(s) on branch {}", submissions.size(), issueKeys.size(),
        sourceBranchUuid);
    } catch (RuntimeException e) {
      LOG.error("Failed to queue proliferation for branch " + sourceBranchUuid, e);
    }
  }

  private boolean isEnabled(DbSession dbSession, String projectUuid) {
    return projectConfigurationLoader.loadProjectConfiguration(dbSession, projectUuid)
      .getBoolean(IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED)
      .orElse(false);
  }

  private static Map<String, String> characteristicsOf(IssueChangeContext context, String transitionKey,
    @Nullable String comment, @Nullable String exceptionReason, String sourceBranchLabel) {
    // A HashMap rather than Map.of: the optional values are null more often than not, and Map.of rejects nulls.
    Map<String, String> characteristics = new HashMap<>();
    characteristics.put(CHARACTERISTIC_TRANSITION, transitionKey);
    characteristics.put(CHARACTERISTIC_MARKED_AT, String.valueOf(context.date().getTime()));
    characteristics.put(CHARACTERISTIC_SOURCE_BRANCH_LABEL, sourceBranchLabel);
    if (context.userUuid() != null) {
      characteristics.put(CHARACTERISTIC_AUTHOR, context.userUuid());
    }
    if (comment != null) {
      characteristics.put(CHARACTERISTIC_COMMENT, comment);
    }
    if (exceptionReason != null) {
      characteristics.put(CHARACTERISTIC_EXCEPTION_REASON, exceptionReason);
    }
    return characteristics;
  }

  private static ByteArrayInputStream serialize(List<String> issueKeys) {
    return new ByteArrayInputStream(String.join("\n", issueKeys).getBytes(StandardCharsets.UTF_8));
  }
}
