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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.ce.task.CeTask;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;

/**
 * Queue of pending Compute Engine tasks. Both producer and consumer actions
 * are implemented.
 * <p>
 * This class is decoupled from the regular task type {@link org.sonar.db.ce.CeTaskTypes#REPORT}.
 * </p>
 */
public interface CeQueue {
  /**
   * Build an instance of {@link CeTaskSubmit} required for {@link #submit(CeTaskSubmit, SubmitOption...)}. It allows
   * to enforce that task ids are generated by the queue. It's used also for having access
   * to the id before submitting the task to the queue.
   */
  CeTaskSubmit.Builder prepareSubmit();

  /**
   * Submits a task to the queue. The task is processed asynchronously.
   * <p>
   * Convenience method for calling {@link #submit(CeTaskSubmit, SubmitOption...)} without any {@link SubmitOption}
   * and which does not returning an {@link Optional}.
   * <p>
   * This method is equivalent to calling {@link #massSubmit(Collection, SubmitOption...)} with a singleton list and no
   * option.
   */
  CeTask submit(CeTaskSubmit submission);

  /**
   * Submits a task to the queue. The task is processed asynchronously.
   * <p>
   * This method is equivalent to calling {@code massSubmit(Collections.singletonList(submission))}.
   *
   * @return empty if {@code options} contains {@link SubmitOption#UNIQUE_QUEUE_PER_ENTITY UNIQUE_QUEUE_PER_MAIN_COMPONENT}
   * and there's already a queued task, otherwise the created task.
   */
  Optional<CeTask> submit(CeTaskSubmit submission, SubmitOption... options);

  /**
   * Submits a task to the queue. The task is processed asynchronously.
   * <p>
   * This method is equivalent to calling {@code massSubmit(Collections.singletonList(submission))}.
   *
   * @return empty if {@code options} contains {@link SubmitOption#UNIQUE_QUEUE_PER_ENTITY UNIQUE_QUEUE_PER_MAIN_COMPONENT}
   * and there's already a queued task, otherwise the created task.
   */
  Optional<CeTask> submit(DbSession dbSession, CeTaskSubmit submission, SubmitOption... options);

  /**
   * Submits multiple tasks to the queue at once. All tasks are processed asynchronously.
   * <p>
   * This method will perform significantly better that calling {@link #submit(CeTaskSubmit, SubmitOption...)} in a loop.
   * </p>
   */
  List<CeTask> massSubmit(Collection<CeTaskSubmit> submissions, SubmitOption... options);

  /**
   * Cancels a task in compute engine queue.
   */
  void cancel(DbSession dbSession, CeQueueDto ceQueueDto);

  /**
   * Removes all the tasks from the queue. They are marked
   * as {@link org.sonar.db.ce.CeActivityDto.Status#CANCELED} in past activity. The tasks with
   * status {@link org.sonar.db.ce.CeQueueDto.Status#IN_PROGRESS} are removed only if explicitly mentioned.
   * This method can be called at runtime, even if workers are being executed.
   *
   * @return the number of canceled tasks
   */
  int cancelAll(boolean includeInProgress);

  /**
   * Mark a task in status {@link org.sonar.db.ce.CeQueueDto.Status#IN_PROGRESS} as failed. An unchecked
   * exception is thrown if the status is not {@link org.sonar.db.ce.CeQueueDto.Status#IN_PROGRESS}.
   * <p>
   * The {@code dbSession} is committed.

   * @throws RuntimeException if the task is concurrently removed from the queue
   */
  void fail(DbSession dbSession, CeQueueDto ceQueueDto, @Nullable String errorType, @Nullable String errorMessage);

  /**
   * Requests workers to stop peeking tasks from queue. Does nothing if workers are already paused or being paused.
   * The workers that are already processing tasks are not interrupted.
   * This method is not restricted to the local workers. All the Compute Engine nodes are paused.
   */
  void pauseWorkers();

  /**
   * Resumes workers so that they can peek tasks from queue.
   * This method is not restricted to the local workers. All the Compute Engine nodes are paused.
   */
  void resumeWorkers();

  WorkersPauseStatus getWorkersPauseStatus();

  /**
   * Removes all the tasks from the queue, whatever their status. They are marked
   * as {@link CeActivityDto.Status#CANCELED} in past activity.
   * This method can NOT be called when  workers are being executed, as in progress
   * tasks can't be killed.
   *
   * @return the number of canceled tasks
   */
  int clear();

  enum SubmitOption {
    UNIQUE_QUEUE_PER_ENTITY,
    UNIQUE_QUEUE_PER_TASK_TYPE
  }

  enum WorkersPauseStatus {
    /**
     * Pause triggered but at least one task is still in-progress
     */
    PAUSING,

    /**
     * Paused, no tasks are in-progress. Tasks are pending.
     */
    PAUSED,

    /**
     * Not paused nor pausing
     */
    RESUMED
  }

}
