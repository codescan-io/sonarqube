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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.ce.task.CeTaskResult;
import org.sonar.ce.task.taskprocessor.CeTaskProcessor;
import org.sonar.api.Startable;
import org.sonar.db.DbClient;
import org.sonar.db.DbInputStream;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.IssueMarkingProliferationService;
import org.sonar.server.issue.IssueMarkingProliferationService.BranchOutcome;
import org.sonar.server.issue.IssueMarkingProliferationService.ProliferatedMarking;
import org.sonar.server.issue.IssueMarkingProliferationService.Signature;
import org.sonar.server.issue.IssueMarkingProliferationService.TargetIndex;
import org.sonar.server.issue.ProliferationTaskPayload;
import org.sonar.server.measure.live.LiveMeasureComputer;
import org.sonar.server.setting.ProjectConfigurationLoader;

import static org.sonar.core.issue.IssueChangeContext.issueChangeContextByUserBuilder;
import static org.sonar.server.issue.IssueMarkingProliferationService.CHARACTERISTIC_AUTHOR;
import static org.sonar.server.issue.IssueMarkingProliferationService.CHARACTERISTIC_MARKED_AT;
import static org.sonar.server.issue.IssueMarkingProliferationService.CHARACTERISTIC_SOURCE_BRANCH_LABEL;
import static org.sonar.server.issue.IssueMarkingProliferationService.CHARACTERISTIC_TRANSITION;


public class ResolutionProliferationTaskProcessor implements CeTaskProcessor, Startable {

  private static final Logger LOG = LoggerFactory.getLogger(ResolutionProliferationTaskProcessor.class);

  private static final Set<String> HANDLED_TYPES = Set.of(CeTaskTypes.RESOLUTION_PROLIFERATION);

  private static final ProliferationTaskPayload EMPTY_PAYLOAD =
    new ProliferationTaskPayload(List.of(), null, null);

  static final int MAX_THREADS = 8;

  /**
   * Upper bound on each parallel phase of one task (reading candidates; writing and refreshing branches). The
   * project's analyses wait behind the task (the queue holds tasks of the same entity), so a stuck job must not hold
   * them forever; jobs still running at the deadline are cancelled and counted as failures.
   */
  static final Duration JOBS_TIMEOUT = Duration.ofHours(1);


  private static final String LEGACY_CHARACTERISTIC_COMMENT = "proliferationComment";
  private static final String LEGACY_CHARACTERISTIC_EXCEPTION_REASON = "proliferationExceptionReason";

  private final DbClient dbClient;
  private final IssueMarkingProliferationService proliferationService;
  private final System2 system2;
  private final LiveMeasureComputer liveMeasureComputer;
  private final ProjectConfigurationLoader projectConfigurationLoader;

  private ExecutorService executor;

  public ResolutionProliferationTaskProcessor(DbClient dbClient,
    IssueMarkingProliferationService proliferationService, System2 system2,
    LiveMeasureComputer liveMeasureComputer, ProjectConfigurationLoader projectConfigurationLoader) {
    this.dbClient = dbClient;
    this.proliferationService = proliferationService;
    this.system2 = system2;
    this.liveMeasureComputer = liveMeasureComputer;
    this.projectConfigurationLoader = projectConfigurationLoader;
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
   * <p>
   * Every failure - a candidate group that could not be read, a slice or branch job that threw or ran out of time,
   * a match that could not be prepared, a branch that could not be indexed or refreshed - is counted, and the task
   * fails once everything else has been done. Nothing re-runs a marking that was lost, so the task status is the
   * only place it can show.
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
    ProliferationTaskPayload payload;
    TargetIndex targets;
    try (DbSession dbSession = dbClient.openSession(false)) {
      if (!isProliferationEnabled(dbSession, projectUuid)) {
        LOG.info("Proliferation is disabled for project {}, skipping task {}", projectUuid, task.getUuid());
        return;
      }
      payload = readPayload(dbSession, task.getUuid());
      if (payload.issueKeys().isEmpty()) {
        return;
      }
      sourceIssues = dbClient.issueDao().selectByKeys(dbSession, payload.issueKeys());
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


    long readStart = system2.now();
    Failures failures = new Failures();
    targets = readTargets(sourceIssues, targetBranchUuids, failures);

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
      // The characteristics are the fallback, not the source: a task queued before the text moved into the
      // payload still carries it there. Removable once no such task can still be pending.
      payload.comment() != null ? payload.comment() : characteristics.get(LEGACY_CHARACTERISTIC_COMMENT),
      payload.exceptionReason() != null ? payload.exceptionReason()
        : characteristics.get(LEGACY_CHARACTERISTIC_EXCEPTION_REASON),
      characteristics.getOrDefault(CHARACTERISTIC_SOURCE_BRANCH_LABEL, sourceBranchUuid));

    long writeStart = system2.now();
    long branchesWithMatches = targetBranchUuids.stream().filter(targets::hasMatches).count();
    runAcrossBranches(sourceIssues, targetBranchUuids, targets, marking, failures);
    LOG.info("Proliferation task {}: {} source issue(s), {} target branch(es), {} with matches; read {} ms, "
        + "write and measure refresh {} ms; failed: {} candidate group(s), {} branch(es)", task.getUuid(),
      sourceIssues.size(), targetBranchUuids.size(), branchesWithMatches, writeStart - readStart,
      system2.now() - writeStart, failures.reads.get(), failures.branches.get());
    if (failures.reads.get() > 0 || failures.branches.get() > 0) {
      throw new IllegalStateException(String.format("Resolution proliferation of task %s failed for %d candidate "
        + "group(s) and %d of %d target branch(es); the other branches were written. See the log for the causes.",
        task.getUuid(), failures.reads.get(), failures.branches.get(), branchesWithMatches));
    }
  }

  private boolean isProliferationEnabled(DbSession dbSession, String projectUuid) {
    return projectConfigurationLoader.loadProjectConfiguration(dbSession, projectUuid)
      .getBoolean(IssueMarkingProliferationService.KEY_RESOLUTION_PROLIFERATION_ENABLED)
      .orElse(false);
  }

  /**
   * Failure counts of one task. {@code reads} are (rule, file) candidate groups, or whole slices, that could not be
   * read; {@code branches} are branches where something failed: the job itself, a match, indexing or the refresh.
   */
  private static final class Failures {
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger branches = new AtomicInteger();
  }

  private TargetIndex readTargets(List<IssueDto> sourceIssues, List<String> targetBranchUuids, Failures failures) {
    int sliceCount = Math.min(MAX_THREADS, sourceIssues.size());
    if (sliceCount <= 1) {
      try (DbSession dbSession = dbClient.openSession(false)) {
        TargetIndex index = proliferationService.indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids);
        failures.reads.addAndGet(index.failedGroups());
        return index;
      }
    }

    List<Callable<TargetIndex>> jobs = new ArrayList<>(sliceCount);
    for (int slice = 0; slice < sliceCount; slice++) {
      int own = slice;
      jobs.add(() -> {
        try (DbSession dbSession = dbClient.openSession(false)) {
          return proliferationService.indexTargetsBySignature(dbSession, sourceIssues, targetBranchUuids, own,
            sliceCount);
        }
      });
    }

    List<TargetIndex> slices = new ArrayList<>(sliceCount);
    try {
      // A failed slice is counted, so the task fails; the markings of the slices that were read are still applied.
      int failedSlices = awaitAll(executor.invokeAll(jobs, JOBS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
        slices::add, "Reading one slice of proliferation candidates failed");
      failures.reads.addAndGet(failedSlices);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Proliferation interrupted while reading candidates", e);
    }
    TargetIndex merged = IssueMarkingProliferationService.mergeIndexes(slices);
    failures.reads.addAndGet(merged.failedGroups());
    return merged;
  }

  /**
   * Waits for every job and hands each result to {@code onResult}, logging the ones that failed or were cancelled
   * by the timeout instead of abandoning the rest, and returns how many did. Interruption is left to the caller,
   * which gives up on the whole pass.
   */
  private static <T> int awaitAll(Collection<Future<T>> futures, Consumer<T> onResult, String failureMessage)
    throws InterruptedException {

    int failed = 0;
    for (Future<T> future : futures) {
      try {
        onResult.accept(future.get());
      } catch (ExecutionException e) {
        failed++;
        LOG.error(failureMessage, e.getCause());
      } catch (CancellationException e) {
        failed++;
        LOG.error("{}: cancelled, not finished in time", failureMessage);
      }
    }
    return failed;
  }


  /**
   * One job per target branch with matches: write the marking, commit, then refresh the measures of the components
   * whose issues were written. Branches are independent (their own issues, components and measure rows), so the
   * refresh runs inside the job and branches proceed concurrently on the pool instead of refreshing one branch
   * after another on the CE worker once every write is done. The refresh is latency-bound (a few statements per
   * touched component), so running branches side by side is what bounds the task's duration.
   */
  private void runAcrossBranches(Collection<IssueDto> sourceIssues, List<String> targetBranchUuids,
    TargetIndex targets, ProliferatedMarking marking, Failures failures) {

    List<Callable<Boolean>> jobs = new ArrayList<>(targetBranchUuids.size());
    for (String targetBranchUuid : targetBranchUuids) {
      if (!targets.hasMatches(targetBranchUuid)) {
        continue;
      }
      Map<Signature, List<String>> branchTargets = targets.of(targetBranchUuid);
      jobs.add(() -> {
        try (DbSession own = dbClient.openSession(false)) {
          BranchOutcome outcome = proliferationService.proliferateToBranch(own, sourceIssues, branchTargets,
            targetBranchUuid, marking);
          own.commit();
          boolean refreshed = refreshBranchMeasures(own, targetBranchUuid, outcome.writtenComponents());
          return outcome.failures() == 0 && refreshed;
        }
      });
    }
    if (jobs.isEmpty()) {
      return;
    }

    try {
      int failedJobs = awaitAll(executor.invokeAll(jobs, JOBS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
        succeeded -> {
          if (!succeeded) {
            failures.branches.incrementAndGet();
          }
        }, "Proliferation to one branch failed");
      failures.branches.addAndGet(failedJobs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Proliferation interrupted", e);
    }
  }

  /** @return false when the refresh failed */
  private boolean refreshBranchMeasures(DbSession dbSession, String branchUuid, Set<String> writtenComponentUuids) {
    if (writtenComponentUuids.isEmpty()) {
      return true;
    }
    try {
      List<ComponentDto> touchedComponents = dbClient.componentDao().selectByUuids(dbSession, writtenComponentUuids);
      if (!touchedComponents.isEmpty()) {
        liveMeasureComputer.refresh(dbSession, touchedComponents);
      }
      return true;
    } catch (RuntimeException e) {
      LOG.error("Could not refresh measures of branch " + branchUuid + " after proliferation", e);
      return false;
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

  /**
   * The whole payload is read into memory before it is parsed, rather than streamed: the text in it is read by
   * byte count, and a {@link BufferedReader} over the same stream would have already pulled those bytes into
   * its own buffer. A payload is a list of keys and a comment, so there is nothing to gain from streaming it.
   */
  private ProliferationTaskPayload readPayload(DbSession dbSession, String taskUuid) {
    Optional<DbInputStream> data = dbClient.ceTaskInputDao().selectData(dbSession, taskUuid);
    if (data.isEmpty()) {
      LOG.warn("Proliferation task {} has no stored issue keys", taskUuid);
      return EMPTY_PAYLOAD;
    }
    try (DbInputStream stream = data.get()) {
      return ProliferationTaskPayload.parse(stream.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException("Could not read issue keys of proliferation task " + taskUuid, e);
    }
  }
}
