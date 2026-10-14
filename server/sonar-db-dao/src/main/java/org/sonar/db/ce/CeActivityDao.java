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
package org.sonar.db.ce;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.utils.System2;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;

import static org.sonar.db.DatabaseUtils.executeLargeUpdates;

public class CeActivityDao implements Dao {

  private final System2 system2;

  public CeActivityDao(System2 system2) {
    this.system2 = system2;
  }

  public Optional<CeActivityDto> selectByUuid(DbSession dbSession, String uuid) {
    return Optional.ofNullable(mapper(dbSession).selectByUuid(uuid));
  }

  public void insert(DbSession dbSession, CeActivityDto dto) {
    dto.setCreatedAt(system2.now());
    dto.setUpdatedAt(system2.now());
    boolean isLast = dto.getStatus() != CeActivityDto.Status.CANCELED;
    dto.setIsLast(isLast);

    CeActivityMapper ceActivityMapper = mapper(dbSession);
    if (isLast) {
      ceActivityMapper.clearIsLast(dto.getIsLastKey(), dto.getUpdatedAt());
      ceActivityMapper.clearMainIsLast(dto.getMainIsLastKey(), dto.getUpdatedAt());
    }
    ceActivityMapper.insert(dto);
  }

  public List<CeActivityDto> selectOlderThan(DbSession dbSession, long beforeDate) {
    return mapper(dbSession).selectOlderThan(beforeDate);
  }

  public List<CeActivityDto> selectNewerThan(DbSession dbSession, long beforeDate) {
    return mapper(dbSession).selectNewerThan(beforeDate);
  }

  public List<CeActivityDto> selectByTaskType(DbSession dbSession, String taskType) {
    return mapper(dbSession).selectByTaskType(taskType);
  }

  /**
   * Streams the tasks of every project of the organization submitted in [{@code fromSubmittedAt}, {@code toSubmittedAt}),
   * so callers can aggregate them without loading the whole period in memory.
   */
  public void scrollForOrganizationAnalytics(DbSession dbSession, String organizationUuid, long fromSubmittedAt, long toSubmittedAt,
    ResultHandler<CeActivityAnalyticsDto> handler) {
    mapper(dbSession).scrollForOrganizationAnalytics(organizationUuid, fromSubmittedAt, toSubmittedAt, handler);
  }

  public List<AnalyticsTaskDto> selectForOrganizationAnalytics(DbSession dbSession, AnalyticsTaskQuery query, Pagination pagination) {
    return mapper(dbSession).selectForOrganizationAnalytics(query, pagination);
  }

  public int countForOrganizationAnalytics(DbSession dbSession, AnalyticsTaskQuery query) {
    return mapper(dbSession).countForOrganizationAnalytics(query);
  }

  /**
   * The latest finished task of every task type of the organization's projects, whatever its branch: the tasks
   * {@code api/ce/component} picks its current one from.
   */
  public List<AnalyticsProjectTaskStatusDto> selectCurrentStatusesForOrganization(DbSession dbSession, String organizationUuid) {
    return mapper(dbSession).selectCurrentStatusesForOrganization(organizationUuid);
  }

  /** Projects of the organization with at least one analysis submitted in the period, most analyzed first. */
  public List<AnalyticsProjectActivityDto> selectAnalyzedProjects(DbSession dbSession, String organizationUuid, long fromSubmittedAt,
    long toSubmittedAt, Pagination pagination) {
    return mapper(dbSession).selectAnalyzedProjects(organizationUuid, fromSubmittedAt, toSubmittedAt, pagination);
  }

  public int countAnalyzedProjects(DbSession dbSession, String organizationUuid, long fromSubmittedAt, long toSubmittedAt) {
    return mapper(dbSession).countAnalyzedProjects(organizationUuid, fromSubmittedAt, toSubmittedAt);
  }

  /** Projects of the organization with no analysis submitted in the period, by key. */
  public List<AnalyticsProjectActivityDto> selectNotAnalyzedProjects(DbSession dbSession, String organizationUuid, long fromSubmittedAt,
    long toSubmittedAt, Pagination pagination) {
    return mapper(dbSession).selectNotAnalyzedProjects(organizationUuid, fromSubmittedAt, toSubmittedAt, pagination);
  }

  public int countNotAnalyzedProjects(DbSession dbSession, String organizationUuid, long fromSubmittedAt, long toSubmittedAt) {
    return mapper(dbSession).countNotAnalyzedProjects(organizationUuid, fromSubmittedAt, toSubmittedAt);
  }

  public void deleteByUuids(DbSession dbSession, Set<String> uuids) {
    executeLargeUpdates(uuids, mapper(dbSession)::deleteByUuids);
  }

  /**
   * Ordered by id desc -> newest to oldest
   */
  public List<CeActivityDto> selectByQuery(DbSession dbSession, CeTaskQuery query, Pagination pagination) {
    if (query.isShortCircuitedByEntityUuids()) {
      return Collections.emptyList();
    }

    return mapper(dbSession).selectByQuery(query, pagination);
  }

  public int countByQuery(DbSession dbSession, CeTaskQuery query) {
    return mapper(dbSession).countByQuery(query);
  }

  public int countLastByStatusAndEntityUuid(DbSession dbSession, CeActivityDto.Status status, @Nullable String entityUuid) {
    return mapper(dbSession).countLastByStatusAndEntityUuid(status, entityUuid);
  }

  public Optional<CeActivityDto> selectLastByComponentUuidAndTaskType(DbSession dbSession, String componentUuid, String taskType) {
    return Optional.ofNullable(mapper(dbSession).selectLastByComponentUuidAndTaskType(componentUuid, taskType));
  }

  public boolean hasAnyFailedOrCancelledIssueSyncTask(DbSession dbSession) {
    return mapper(dbSession).hasAnyFailedOrCancelledIssueSyncTask() > 0;
  }

  private static CeActivityMapper mapper(DbSession dbSession) {
    return dbSession.getMapper(CeActivityMapper.class);
  }
}
