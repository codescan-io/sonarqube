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
package org.sonar.db.measure;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.utils.System2;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;

import static java.util.Collections.singletonList;
import static org.sonar.api.measures.CoreMetrics.NCLOC_KEY;
import static org.sonar.db.DatabaseUtils.PARTITION_SIZE_FOR_ORACLE;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;

public class MeasureDao implements Dao {

  private final System2 system2;

  public MeasureDao(System2 system2) {
    this.system2 = system2;
  }

  public int insert(DbSession dbSession, MeasureDto dto) {
    dto.computeJsonValueHash();
    return mapper(dbSession).insert(dto, system2.now());
  }

  /**
   * Update a measure. The measure json value will be overwritten.
   */
  public int update(DbSession dbSession, MeasureDto dto) {
    return mapper(dbSession).update(dto, system2.now());
  }

  /**
   * Unlike {@link #update(DbSession, MeasureDto)}, this method will not overwrite the entire json value,
   * but will update the measures inside the json.
   */
  public int insertOrUpdate(DbSession dbSession, MeasureDto dto) {
    long now = system2.now();
    Optional<MeasureDto> existingMeasureOpt = selectByComponentUuid(dbSession, dto.getComponentUuid());
    if (existingMeasureOpt.isPresent()) {
      MeasureDto existingDto = existingMeasureOpt.get();
      existingDto.getMetricValues().putAll(dto.getMetricValues());
      dto.getMetricValues().putAll(existingDto.getMetricValues());
      dto.computeJsonValueHash();
      return mapper(dbSession).update(dto, now);
    } else {
      dto.computeJsonValueHash();
      return mapper(dbSession).insert(dto, now);
    }
  }

  /**
   * {@link #insertOrUpdate(DbSession, MeasureDto)} for many components: the existing rows are read in one statement
   * per partition instead of one per component, and that partition's writes follow back to back, so a batch session
   * sends them as JDBC batches. A refresh of a whole branch writes a row for nearly every component, and one read
   * plus one write each is two round trips per file.
   *
   * <p>Each partition is read and then written before the next is read. Only one partition of existing rows, each
   * holding every metric of its component, is on the heap at a time, and a row read here is written back within the
   * same partition rather than after the whole tree has been read, which keeps short the window in which another
   * writer's change to it would be overwritten by the merge.
   */
  public void insertOrUpdate(DbSession dbSession, Collection<MeasureDto> dtos) {
    if (dtos.isEmpty()) {
      return;
    }
    long now = system2.now();
    MeasureMapper mapper = mapper(dbSession);
    for (List<MeasureDto> partition : Lists.partition(List.copyOf(dtos), PARTITION_SIZE_FOR_ORACLE)) {
      Map<String, MeasureDto> existingByComponent = mapper
        .selectByComponentUuids(partition.stream().map(MeasureDto::getComponentUuid).toList()).stream()
        .collect(Collectors.toMap(MeasureDto::getComponentUuid, Function.identity()));
      for (MeasureDto dto : partition) {
        MeasureDto existingDto = existingByComponent.get(dto.getComponentUuid());
        if (existingDto == null) {
          dto.computeJsonValueHash();
          mapper.insert(dto, now);
        } else {
          existingDto.getMetricValues().putAll(dto.getMetricValues());
          dto.getMetricValues().putAll(existingDto.getMetricValues());
          dto.computeJsonValueHash();
          mapper.update(dto, now);
        }
      }
    }
  }

  public Optional<MeasureDto> selectByComponentUuid(DbSession dbSession, String componentUuid) {
    List<MeasureDto> measures = mapper(dbSession).selectByComponentUuids(singletonList(componentUuid));
    if (!measures.isEmpty()) {
      // component_uuid column is unique. List can't have more than 1 item.
      return Optional.of(measures.get(0));
    }
    return Optional.empty();
  }

  public Optional<MeasureDto> selectByComponentUuidAndMetricKeys(DbSession dbSession, String componentUuid, Collection<String> metricKeys) {
    List<MeasureDto> measures = selectByComponentUuidsAndMetricKeys(dbSession, singletonList(componentUuid), metricKeys);
    // component_uuid column is unique. List can't have more than 1 item.
    if (measures.size() == 1) {
      return Optional.of(measures.get(0));
    }
    return Optional.empty();
  }

  public List<MeasureDto> selectByComponentUuidsAndMetricKeys(DbSession dbSession, Collection<String> largeComponentUuids,
    Collection<String> metricKeys) {
    if (largeComponentUuids.isEmpty() || metricKeys.isEmpty()) {
      return Collections.emptyList();
    }

    return executeLargeInputs(
      largeComponentUuids,
      componentUuids -> mapper(dbSession).selectByComponentUuids(componentUuids)).stream()
      .map(measureDto -> {
        measureDto.getMetricValues().entrySet().removeIf(entry -> !metricKeys.contains(entry.getKey()));
        return measureDto;
      })
      .filter(measureDto -> !measureDto.getMetricValues().isEmpty())
      .toList();
  }

  public void scrollSelectByComponentUuid(DbSession dbSession, String componentUuid, ResultHandler<MeasureDto> handler) {
    mapper(dbSession).scrollSelectByComponentUuid(componentUuid, handler);
  }

  public Set<MeasureHash> selectMeasureHashesForBranch(DbSession dbSession, String branchUuid) {
    return mapper(dbSession).selectMeasureHashesForBranch(branchUuid);
  }

  public List<MeasureDto> selectBranchMeasuresForProject(DbSession dbSession, String projectUuid) {
    return mapper(dbSession).selectBranchMeasuresForProject(projectUuid);
  }

  public void selectTreeByQuery(DbSession dbSession, ComponentDto baseComponent, MeasureTreeQuery query,
    ResultHandler<MeasureDto> resultHandler) {
    if (query.returnsEmpty()) {
      return;
    }
    mapper(dbSession).selectTreeByQuery(query, baseComponent.uuid(), query.getUuidPath(baseComponent), resultHandler);
  }

  public List<ProjectMainBranchMeasureDto> selectAllForProjectMainBranches(DbSession dbSession) {
    return mapper(dbSession).selectAllForProjectMainBranches();
  }

  public List<ProjectMainBranchMeasureDto> selectAllForProjectMainBranchesAssociatedToDefaultQualityProfile(DbSession dbSession) {
    return mapper(dbSession).selectAllForProjectMainBranchesAssociatedToDefaultQualityProfile();
  }

  public List<MeasureDto> selectAllForMainBranches(DbSession dbSession) {
    return mapper(dbSession).selectAllForMainBranches();
  }

  public long findNclocOfBiggestBranchForProject(DbSession dbSession, String projectUuid) {
    List<MeasureDto> branchMeasures = mapper(dbSession).selectBranchMeasuresForProject(projectUuid);

    long maxncloc = 0;
    for (MeasureDto measure : branchMeasures) {
      Long branchNcloc = measure.getLong(NCLOC_KEY);
      if (branchNcloc != null && branchNcloc > maxncloc) {
        maxncloc = branchNcloc;
      }
    }

    return maxncloc;
  }

  private static MeasureMapper mapper(DbSession dbSession) {
    return dbSession.getMapper(MeasureMapper.class);
  }

}
