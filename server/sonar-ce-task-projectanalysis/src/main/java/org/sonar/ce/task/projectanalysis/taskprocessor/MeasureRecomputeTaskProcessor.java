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

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.System2;
import org.sonar.ce.task.CeTask;
import org.sonar.ce.task.CeTaskResult;
import org.sonar.ce.task.taskprocessor.CeTaskProcessor;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.measure.live.LiveMeasureComputer;
import org.sonar.server.qualitygate.changeevent.QGChangeEvent;
import org.sonar.server.qualitygate.changeevent.QGChangeEventListeners;

import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLUTION;
import static org.sonar.ce.queue.MeasureRecomputeSubmitter.CHARACTERISTIC_RESOLVED_ISSUE;

/**
 * CodeScan: recomputes every component of a branch, as the web server does after a manual transition, then hands the
 * quality-gate change events to the same listeners (project activity event, webhooks). Queued by
 * {@link org.sonar.ce.queue.MeasureRecomputeSubmitter}.
 *
 * <p>A failure fails the task, which then shows as failed in the project's background tasks; the branch keeps the
 * measures it had until the next recomputation or analysis of it.
 */
public class MeasureRecomputeTaskProcessor implements CeTaskProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(MeasureRecomputeTaskProcessor.class);

  private final DbClient dbClient;
  private final LiveMeasureComputer liveMeasureComputer;
  private final QGChangeEventListeners qualityGateListeners;
  private final System2 system2;

  public MeasureRecomputeTaskProcessor(DbClient dbClient, LiveMeasureComputer liveMeasureComputer,
    QGChangeEventListeners qualityGateListeners, System2 system2) {
    this.dbClient = dbClient;
    this.liveMeasureComputer = liveMeasureComputer;
    this.qualityGateListeners = qualityGateListeners;
    this.system2 = system2;
  }

  @Override
  public Set<String> getHandledCeTaskTypes() {
    return Set.of(CeTaskTypes.MEASURE_RECOMPUTE);
  }

  @CheckForNull
  @Override
  public CeTaskResult process(CeTask task) {
    String branchUuid = task.getComponent().map(CeTask.Component::getUuid)
      .orElseThrow(() -> new IllegalStateException("Measure recomputation task " + task.getUuid() + " has no branch"));
    String projectKey = task.getEntity().flatMap(CeTask.Component::getKey).orElse(null);
    long startedAt = system2.now();
    try {
      int components = recompute(task, branchUuid);
      LOG.info("Recomputed measures of project {} branch {}: {} component(s) in {} ms", projectKey,
        branchName(branchUuid), components, system2.now() - startedAt);
    } catch (RuntimeException e) {
      // The worker logs the stack trace when it marks the task failed; this line names what was being recomputed.
      LOG.error("Could not recompute measures of project {} branch {} ({}): {}", projectKey, branchName(branchUuid),
        branchUuid, e.getMessage());
      throw e;
    }
    return null;
  }

  /**
   * A batch session: the refresh writes a measure row for nearly every component, and only a batch session sends
   * those writes as JDBC batches. The listeners run once the measures are committed, so neither a webhook nor a
   * project event announces a quality gate that a failed commit did not keep.
   */
  private int recompute(CeTask task, String branchUuid) {
    List<QGChangeEvent> gateChangeEvents;
    int componentCount;
    try (DbSession dbSession = dbClient.openSession(true)) {
      List<ComponentDto> components = dbClient.componentDao().selectByBranchUuid(branchUuid, dbSession);
      componentCount = components.size();
      if (components.isEmpty()) {
        return 0;
      }
      gateChangeEvents = liveMeasureComputer.refresh(dbSession, components);
      dbSession.commit();
    }
    Map<String, String> characteristics = task.getCharacteristics();
    qualityGateListeners.broadcastOnIssueChange(changedIssuesOf(branchUuid,
      characteristics.get(CHARACTERISTIC_RESOLVED_ISSUE), characteristics.get(CHARACTERISTIC_RESOLUTION)),
      gateChangeEvents, false);
    return componentCount;
  }

  private String branchName(String branchUuid) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      return dbClient.branchDao().selectByUuid(dbSession, branchUuid).map(BranchDto::getKey).orElse(branchUuid);
    } catch (RuntimeException e) {
      return branchUuid;
    }
  }

  /**
   * One issue, not every issue the caller resolved. QGChangeEventListenersImpl drops the whole broadcast when this
   * list is empty, then indexes it by branch and hands the listeners a set neither of them reads - both work from
   * the QGChangeEvent alone. A task covers one branch, so one entry reaches every listener that a thousand would.
   *
   * <p>It carries the four fields anything on that path looks at: the branch it belongs to, a key the caller really
   * resolved, and the status and resolution it was moved to. Type, severity and impacts are left unset because
   * nothing reads them; a listener that starts to would need the issues loaded properly rather than this.
   */
  private static List<DefaultIssue> changedIssuesOf(String branchUuid, @Nullable String resolvedKey,
    @Nullable String resolution) {
    if (resolvedKey == null || resolution == null) {
      return List.of();
    }
    DefaultIssue issue = new DefaultIssue();
    issue.setKey(resolvedKey);
    issue.setProjectUuid(branchUuid);
    issue.setStatus(STATUS_RESOLVED);
    issue.setResolution(resolution);
    return List.of(issue);
  }
}
