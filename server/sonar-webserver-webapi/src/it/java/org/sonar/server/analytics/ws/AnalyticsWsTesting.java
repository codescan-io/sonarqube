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
package org.sonar.server.analytics.ws;

import org.sonar.core.util.Uuids;
import org.sonar.db.DbTester;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.ce.CeQueueDto;
import org.sonar.db.ce.CeTaskTypes;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.organization.OrganizationDto;

/** Organizations, projects and CE tasks for the analytics web service tests. */
class AnalyticsWsTesting {

  private final DbTester db;

  AnalyticsWsTesting(DbTester db) {
    this.db = db;
  }

  /** DbTester does not clean the organizations table between tests, so every key gets a unique suffix. */
  OrganizationDto insertOrganization(String keyPrefix) {
    String uuid = Uuids.createFast();
    OrganizationDto organization = new OrganizationDto()
      .setUuid(uuid)
      .setKey(keyPrefix + "-" + uuid)
      .setName(keyPrefix)
      .setDefaultQualityGateUuid(Uuids.createFast())
      .setSubscription(OrganizationDto.Subscription.PAID);
    db.getDbClient().organizationDao().insert(db.getSession(), organization, true);
    db.commit();
    return organization;
  }

  ProjectData insertProject(OrganizationDto organization) {
    return db.components().insertPrivateProject(b -> {
    }, c -> c.setOrganizationUuid(organization.getUuid()), p -> p.setOrganizationUuid(organization.getUuid()));
  }

  CeQueueDto newTask(ProjectData project, BranchDto branch, long submittedAt) {
    return new CeQueueDto()
      .setUuid(Uuids.createFast())
      .setTaskType(CeTaskTypes.REPORT)
      .setEntityUuid(project.projectUuid())
      .setComponentUuid(branch.getUuid())
      .setCreatedAt(submittedAt)
      .setUpdatedAt(submittedAt);
  }

  void insertActivity(CeQueueDto task, CeActivityDto.Status status, Long startedAt, Long executionTimeMs) {
    insertActivity(task, status, startedAt, executionTimeMs, null);
  }

  void insertActivity(CeQueueDto task, CeActivityDto.Status status, Long startedAt, Long executionTimeMs, String errorMessage) {
    task.setStartedAt(startedAt);
    CeActivityDto activity = new CeActivityDto(task)
      .setStatus(status)
      .setExecutionTimeMs(executionTimeMs)
      .setExecutedAt(startedAt == null || executionTimeMs == null ? null : startedAt + executionTimeMs)
      .setErrorMessage(errorMessage);
    db.getDbClient().ceActivityDao().insert(db.getSession(), activity);
    db.commit();
  }

  void insertQueued(CeQueueDto task, CeQueueDto.Status status) {
    task.setStatus(status);
    db.getDbClient().ceQueueDao().insert(db.getSession(), task);
    db.commit();
  }
}
