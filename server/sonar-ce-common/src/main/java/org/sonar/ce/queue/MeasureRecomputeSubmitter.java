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
package org.sonar.ce.queue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.ce.task.CeTask;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskCharacteristicDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * CodeScan: queues the recomputation of a whole branch's live measures and quality gate as a
 * {@link CeTaskTypes#MEASURE_RECOMPUTE} Compute Engine task, for callers that change many issues without an analysis
 * and cannot wait for a recomputation that runs for minutes on a large branch.
 *
 * <p>A branch has at most one such task pending: a request while one is pending returns that task, because it reads
 * the branch afresh when it starts. A request while one is running queues another, because the running one may
 * already have read the issues the request is about. The pending task is looked up in the database, so this holds
 * across web nodes; two requests for the same branch that reach two nodes at the same instant can each queue a task,
 * and the second then recomputes once more. The Compute Engine runs one task of a project at a time, so the
 * recomputation never overlaps an analysis of the same project or another recomputation of it.
 */
@ServerSide
public class MeasureRecomputeSubmitter {

  private static final Logger LOG = LoggerFactory.getLogger(MeasureRecomputeSubmitter.class);

  /** An issue the caller resolved, carried to the quality-gate listeners, which drop a broadcast that names none. */
  public static final String CHARACTERISTIC_RESOLVED_ISSUE = "measureRecomputeIssue";
  /** The resolution {@link #CHARACTERISTIC_RESOLVED_ISSUE} was moved to. */
  public static final String CHARACTERISTIC_RESOLUTION = "measureRecomputeResolution";

  private final DbClient dbClient;
  private final CeQueue ceQueue;

  public MeasureRecomputeSubmitter(DbClient dbClient, CeQueue ceQueue) {
    this.dbClient = dbClient;
    this.ceQueue = ceQueue;
  }

  /**
   * @param resolvedIssueKey an issue the caller resolved on the branch, or null when it names none, in which case the
   *                         quality-gate listeners are not reached
   * @return the uuid of the pending task that will recompute the branch: the one this call queued, or the one it
   * found already pending
   */
  public String submit(String branchUuid, @Nullable String resolvedIssueKey, @Nullable String resolution,
    @Nullable String submitterUuid) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      BranchDto branch = dbClient.branchDao().selectByUuid(dbSession, branchUuid).orElse(null);
      checkArgument(branch != null, "Branch '%s' not found", branchUuid);
      boolean broadcast = resolvedIssueKey != null && resolution != null;
      Optional<String> pending = pendingTaskOf(dbSession, branch, broadcast);
      if (pending.isPresent()) {
        LOG.info("Measure recomputation of branch {} is already queued as task {}", branchUuid, pending.get());
        return pending.get();
      }
      Map<String, String> characteristics = new HashMap<>();
      if (broadcast) {
        characteristics.put(CHARACTERISTIC_RESOLVED_ISSUE, resolvedIssueKey);
        characteristics.put(CHARACTERISTIC_RESOLUTION, resolution);
      }
      CeTask task = ceQueue.submit(dbSession, ceQueue.prepareSubmit()
        .setType(CeTaskTypes.MEASURE_RECOMPUTE)
        .setComponent(CeTaskSubmit.Component.fromDto(branchUuid, branch.getProjectUuid()))
        .setCharacteristics(characteristics)
        .setSubmitterUuid(submitterUuid)
        .build())
        .orElseThrow();
      dbSession.commit();
      LOG.info("Queued measure recomputation of branch {} as task {}", branchUuid, task.getUuid());
      return task.getUuid();
    }
  }

  /**
   * A pending recomputation of the branch that does at least what this request asks: one that names no issue
   * recomputes but does not broadcast, so it does not stand in for a request that needs the broadcast.
   */
  private Optional<String> pendingTaskOf(DbSession dbSession, BranchDto branch, boolean broadcast) {
    List<String> pending = dbClient.ceQueueDao().selectByEntityUuid(dbSession, branch.getProjectUuid()).stream()
      .filter(task -> task.getStatus() == CeQueueDto.Status.PENDING)
      .filter(task -> CeTaskTypes.MEASURE_RECOMPUTE.equals(task.getTaskType()))
      .filter(task -> branch.getUuid().equals(task.getComponentUuid()))
      .map(CeQueueDto::getUuid)
      .toList();
    if (!broadcast || pending.isEmpty()) {
      return pending.stream().findFirst();
    }
    return dbClient.ceTaskCharacteristicsDao().selectByTaskUuids(dbSession, pending).stream()
      .filter(characteristic -> CHARACTERISTIC_RESOLVED_ISSUE.equals(characteristic.getKey()))
      .map(CeTaskCharacteristicDto::getTaskUuid)
      .findFirst();
  }
}
