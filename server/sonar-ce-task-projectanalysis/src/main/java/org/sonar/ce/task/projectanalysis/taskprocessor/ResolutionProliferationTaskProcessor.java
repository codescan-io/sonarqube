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
package org.sonar.ce.task.projectanalysis.taskprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.ce.task.CeTaskResult;
import org.sonar.ce.task.taskprocessor.CeTaskProcessor;
import org.sonar.api.Startable;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbInputStream;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueMarkingProliferationService;
import org.sonar.server.issue.IssueMarkingProliferationService.ProliferatedMarking;
import org.sonar.server.issue.IssueMarkingProliferationService.Signature;

import static org.sonar.core.issue.IssueChangeContext.issueChangeContextByUserBuilder;


public class ResolutionProliferationTaskProcessor implements CeTaskProcessor, Startable {

  private static final Logger LOG = LoggerFactory.getLogger(ResolutionProliferationTaskProcessor.class);

  private static final Set<String> HANDLED_TYPES = Set.of(CeTaskTypes.RESOLUTION_PROLIFERATION);

  private static final String CHARACTERISTIC_TRANSITION = "proliferationTransition";
  private static final String CHARACTERISTIC_MARKED_AT = "proliferationMarkedAt";
  private static final String CHARACTERISTIC_AUTHOR = "proliferationAuthor";
  private static final String CHARACTERISTIC_COMMENT = "proliferationComment";
  private static final String CHARACTERISTIC_EXCEPTION_REASON = "proliferationExceptionReason";
  private static final String CHARACTERISTIC_SOURCE_BRANCH_LABEL = "proliferationSourceBranch";

  static final int MAX_THREADS = 8;

  private final DbClient dbClient;
  private final IssueMarkingProliferationService proliferationService;
  private final System2 system2;

  private ExecutorService executor;

  public ResolutionProliferationTaskProcessor(DbClient dbClient,
    IssueMarkingProliferationService proliferationService, System2 system2) {
    this.dbClient = dbClient;
    this.proliferationService = proliferationService;
    this.system2 = system2;
  }

  @Override
  public void start() {
    executor = Executors.newFixedThreadPool(MAX_THREADS, r -> {
      Thread t = new Thread(r, "resolution-proliferation");
      t.setDaemon(true);
      return t;
    });
  }

  @Override
  public void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public Set<String> getHandledCeTaskTypes() {
    return HANDLED_TYPES;
  }

  /**
   * Proliferation produces no task result: what it did is in the issues it marked and in the log, and
   * {@code ce_activity} records only that the task ran. The work is a void method of its own so this one has a
   * single exit instead of eight identical ones.
   */
  @CheckForNull
  @Override
  public CeTaskResult process(CeTask task) {
    proliferate(task);
    return null;
  }

  private void proliferate(CeTask task) {
    String sourceBranchUuid = task.getComponent().map(CeTask.Component::getUuid).orElse(null);
    String projectUuid = task.getEntity().map(CeTask.Component::getUuid).orElse(null);
    if (sourceBranchUuid == null || projectUuid == null) {
      LOG.warn("Proliferation task {} has no component or entity, skipping", task.getUuid());
      return;
    }

    List<IssueDto> sourceIssues;
    List<String> targetBranchUuids;
    Map<String, Map<Signature, List<IssueDto>>> targetsByBranch;
    try (DbSession dbSession = dbClient.openSession(false)) {
      List<String> issueKeys = readIssueKeys(dbSession, task.getUuid());
      if (issueKeys.isEmpty()) {
        return;
      }
      sourceIssues = dbClient.issueDao().selectByKeys(dbSession, issueKeys);
      if (sourceIssues.isEmpty()) {
        return;
      }

      targetBranchUuids = dbClient.branchDao().selectByProjectUuid(dbSession, projectUuid).stream()
        .map(BranchDto::getUuid)
        .filter(uuid -> !uuid.equals(sourceBranchUuid))
        .toList();
      if (targetBranchUuids.isEmpty()) {
        return;
      }
    }


    targetsByBranch = readTargets(sourceIssues, targetBranchUuids);
    if (targetsByBranch.isEmpty()) {
      return;
    }

    Map<String, String> characteristics = task.getCharacteristics();
    String transitionKey = characteristics.get(CHARACTERISTIC_TRANSITION);
    if (transitionKey == null) {
      LOG.warn("Proliferation task {} has no transition, skipping", task.getUuid());
      return;
    }
    ProliferatedMarking marking = new ProliferatedMarking(
      issueChangeContextByUserBuilder(markedAt(characteristics),
        characteristics.get(CHARACTERISTIC_AUTHOR)).build(),
      transitionKey,
      characteristics.get(CHARACTERISTIC_COMMENT),
      characteristics.get(CHARACTERISTIC_EXCEPTION_REASON),
      characteristics.getOrDefault(CHARACTERISTIC_SOURCE_BRANCH_LABEL, sourceBranchUuid));

    runAcrossBranches(sourceIssues, targetBranchUuids, targetsByBranch, marking);
  }

  private Map<String, Map<Signature, List<IssueDto>>> readTargets(List<IssueDto> sourceIssues,
    List<String> targetBranchUuids) {

    int sliceCount = Math.min(MAX_THREADS, sourceIssues.size());
    if (sliceCount <= 1) {
      try (DbSession dbSession = dbClient.openSession(false)) {
        return proliferationService.indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids);
      }
    }

    List<Callable<Map<String, Map<Signature, List<IssueDto>>>>> jobs = new ArrayList<>(sliceCount);
    for (int slice = 0; slice < sliceCount; slice++) {
      int own = slice;
      jobs.add(() -> {
        try (DbSession dbSession = dbClient.openSession(false)) {
          return proliferationService.indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids, own,
            sliceCount);
        }
      });
    }

    List<Map<String, Map<Signature, List<IssueDto>>>> slices = new ArrayList<>(sliceCount);
    try {
      // The markings a failed slice would have carried are simply not applied; the rest still are, and the next
      // run picks them up because an unmarked issue is still a candidate.
      awaitAll(executor.invokeAll(jobs), slices::add, "Reading one slice of proliferation candidates failed");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Proliferation interrupted while reading candidates", e);
      return Map.of();
    }
    return IssueMarkingProliferationService.mergeIndexes(slices);
  }

  /**
   * Waits for every job and hands each result to {@code onResult}, logging the ones that failed instead of
   * abandoning the rest. Interruption is left to the caller, which gives up on the whole pass.
   */
  private static <T> void awaitAll(Collection<Future<T>> futures, Consumer<T> onResult, String failureMessage)
    throws InterruptedException {

    for (Future<T> future : futures) {
      try {
        onResult.accept(future.get());
      } catch (ExecutionException e) {
        LOG.error(failureMessage, e.getCause());
      }
    }
  }


  private void runAcrossBranches(Collection<IssueDto> sourceIssues, List<String> targetBranchUuids,
    Map<String, Map<Signature, List<IssueDto>>> targetsByBranch, ProliferatedMarking marking) {

    List<Callable<Void>> jobs = new ArrayList<>(targetBranchUuids.size());
    for (String targetBranchUuid : targetBranchUuids) {
      Map<Signature, List<IssueDto>> targets = targetsByBranch.getOrDefault(targetBranchUuid, Map.of());
      if (targets.isEmpty()) {
        continue;
      }
      jobs.add(() -> {
        try (DbSession own = dbClient.openSession(false)) {
          proliferationService.proliferateToBranch(own, sourceIssues, targets, targetBranchUuid, marking);
          own.commit();
        }
        return null;
      });
    }
    if (jobs.isEmpty()) {
      return;
    }

    try {
      awaitAll(executor.invokeAll(jobs), ignored -> {
        // Nothing to collect: each job writes its own branch and reports its own count.
      }, "Proliferation to one branch failed");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Proliferation interrupted", e);
    }
  }

  private Date markedAt(Map<String, String> characteristics) {
    String raw = characteristics.get(CHARACTERISTIC_MARKED_AT);
    if (raw == null) {
      return new Date(system2.now());
    }
    try {
      return new Date(Long.parseLong(raw));
    } catch (NumberFormatException e) {
      return new Date(system2.now());
    }
  }

  private List<String> readIssueKeys(DbSession dbSession, String taskUuid) {
    Optional<DbInputStream> data = dbClient.ceTaskInputDao().selectData(dbSession, taskUuid);
    if (data.isEmpty()) {
      LOG.warn("Proliferation task {} has no stored issue keys", taskUuid);
      return List.of();
    }
    try (DbInputStream stream = data.get();
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader.lines().filter(line -> !line.isBlank()).toList();
    } catch (IOException e) {
      LOG.error("Could not read issue keys of proliferation task " + taskUuid, e);
      return List.of();
    }
  }
}
