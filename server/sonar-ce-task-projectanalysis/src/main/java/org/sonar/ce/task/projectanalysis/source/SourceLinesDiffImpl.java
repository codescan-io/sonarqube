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
package org.sonar.ce.task.projectanalysis.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;
import org.sonar.ce.task.projectanalysis.analysis.AnalysisMetadataHolder;
import org.sonar.ce.task.projectanalysis.component.Component;
import org.sonar.ce.task.projectanalysis.component.ConfigurationRepository;
import org.sonar.ce.task.projectanalysis.period.NewCodeReferenceBranchComponentUuids;
import org.sonar.ce.task.projectanalysis.component.ReferenceBranchComponentUuids;
import org.sonar.ce.task.projectanalysis.filemove.MovedFilesRepository;
import org.sonar.ce.task.projectanalysis.period.PeriodHolder;
import org.sonar.core.util.CloseableIterator;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.newcodeperiod.NewCodePeriodType;
import org.sonar.db.protobuf.DbFileSources;
import org.sonar.db.source.FileSourceDao;
import org.sonar.db.source.FileSourceDto;

public class SourceLinesDiffImpl implements SourceLinesDiff {

  private static final Logger LOG = LoggerFactory.getLogger(SourceLinesDiffImpl.class);
  private static final String KEY_HISTOGRAM_DIFF_ENABLED = "codescan.ce.histogramDiff.enabled";

  private final DbClient dbClient;
  private final FileSourceDao fileSourceDao;
  private final SourceLinesHashRepository sourceLinesHash;
  private final SourceLinesRepository sourceLinesRepository;
  private final ReferenceBranchComponentUuids referenceBranchComponentUuids;
  private final MovedFilesRepository movedFilesRepository;
  private final AnalysisMetadataHolder analysisMetadataHolder;
  private final PeriodHolder periodHolder;
  private final NewCodeReferenceBranchComponentUuids newCodeReferenceBranchComponentUuids;
  private final ConfigurationRepository configurationRepository;

  public SourceLinesDiffImpl(DbClient dbClient, FileSourceDao fileSourceDao, SourceLinesHashRepository sourceLinesHash,
    SourceLinesRepository sourceLinesRepository, ReferenceBranchComponentUuids referenceBranchComponentUuids,
    MovedFilesRepository movedFilesRepository, AnalysisMetadataHolder analysisMetadataHolder, PeriodHolder periodHolder,
    NewCodeReferenceBranchComponentUuids newCodeReferenceBranchComponentUuids, ConfigurationRepository configurationRepository) {
    this.dbClient = dbClient;
    this.fileSourceDao = fileSourceDao;
    this.sourceLinesHash = sourceLinesHash;
    this.sourceLinesRepository = sourceLinesRepository;
    this.referenceBranchComponentUuids = referenceBranchComponentUuids;
    this.movedFilesRepository = movedFilesRepository;
    this.analysisMetadataHolder = analysisMetadataHolder;
    this.periodHolder = periodHolder;
    this.newCodeReferenceBranchComponentUuids = newCodeReferenceBranchComponentUuids;
    this.configurationRepository = configurationRepository;
  }

  @Override
  public int[] computeMatchingLines(Component component) {
    boolean useHistogram = useHistogramDiff() && isXmlFile(component);
    LOG.info("[SOURCELINES-DIFF] computeMatchingLines called for component={}, useHistogramDiff={}", component.getKey(), useHistogram);
    if (useHistogram) {
      return computeWithHistogramDiff(component);
    }
    return computeWithMyersDiff(component);
  }

  private static boolean isXmlFile(Component component) {
    String key = component.getKey().toLowerCase();
    return key.endsWith(".xml") || key.endsWith(".permissionset") || key.endsWith(".profile")
      || key.endsWith(".object") || key.endsWith(".layout") || key.endsWith(".flow")
      || key.endsWith(".flexipage") || key.endsWith(".labels");
  }

  private int[] computeWithMyersDiff(Component component) {
    List<String> database = getDBLineHashes(component);
    List<String> report = getReportLineHashes(component);
    return new SourceLinesDiffFinder().findMatchingLines(database, report);
  }

  private int[] computeWithHistogramDiff(Component component) {
    try {
      List<String> dbSourceLines = getDBSourceContent(component);
      List<String> reportSourceLines = getReportSourceContent(component);

      LOG.info("[HISTOGRAM-DIFF] component={}, dbLines={}, reportLines={}",
        component.getKey(), dbSourceLines.size(), reportSourceLines.size());

      if (dbSourceLines.isEmpty() && reportSourceLines.isEmpty()) {
        return new int[0];
      }

      if (dbSourceLines.isEmpty()) {
        return new int[reportSourceLines.size()];
      }

      int[] result = new GitHistogramDiffFinder().findMatchingLines(dbSourceLines, reportSourceLines);

      StringBuilder newLines = new StringBuilder();
      for (int i = 0; i < result.length; i++) {
        if (result[i] == 0) {
          if (newLines.length() > 0) newLines.append(",");
          newLines.append(i + 1);
        }
      }
      LOG.info("[HISTOGRAM-DIFF] SUCCESS for {}. New lines (match=0): [{}]", component.getKey(), newLines);

      return result;
    } catch (Exception e) {
      LOG.warn("[HISTOGRAM-DIFF] FAILED for {}, falling back to Myers. Error: {}", component.getKey(), e.getMessage(), e);
      return computeWithMyersDiff(component);
    }
  }

  private boolean useHistogramDiff() {
    Configuration config = configurationRepository.getConfiguration();
    Optional<Boolean> configValue = config.getBoolean(KEY_HISTOGRAM_DIFF_ENABLED);
    boolean result = configValue.orElse(true);
    LOG.info("[HISTOGRAM-DIFF] Toggle check: key={}, configValue={}, resolved={}", KEY_HISTOGRAM_DIFF_ENABLED, configValue, result);
    return result;
  }

  private List<String> getDBSourceContent(Component component) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      String uuid = resolveDbFileUuid(component);
      if (uuid == null) {
        return Collections.emptyList();
      }

      FileSourceDto dto = fileSourceDao.selectByFileUuid(dbSession, uuid);
      if (dto == null) {
        return Collections.emptyList();
      }

      DbFileSources.Data sourceData = dto.getSourceData();
      if (sourceData == null) {
        return Collections.emptyList();
      }

      List<DbFileSources.Line> lines = sourceData.getLinesList();
      List<String> result = new ArrayList<>(lines.size());
      for (DbFileSources.Line line : lines) {
        result.add(line.hasSource() ? line.getSource() : "");
      }
      return result;
    }
  }

  private List<String> getReportSourceContent(Component component) {
    List<String> lines = new ArrayList<>();
    try (CloseableIterator<String> iterator = sourceLinesRepository.readLines(component)) {
      while (iterator.hasNext()) {
        lines.add(iterator.next());
      }
    }
    return lines;
  }

  private String resolveDbFileUuid(Component component) {
    if (analysisMetadataHolder.isPullRequest()) {
      return referenceBranchComponentUuids.getComponentUuid(component.getKey());
    } else if (periodHolder.hasPeriod() && periodHolder.getPeriod().getMode().equals(NewCodePeriodType.REFERENCE_BRANCH.name())) {
      return newCodeReferenceBranchComponentUuids.getComponentUuid(component.getKey());
    } else {
      Optional<MovedFilesRepository.OriginalFile> originalFile = movedFilesRepository.getOriginalFile(component);
      return originalFile.map(MovedFilesRepository.OriginalFile::uuid).orElse(component.getUuid());
    }
  }

  private List<String> getDBLineHashes(Component component) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      String uuid = resolveDbFileUuid(component);
      if (uuid == null) {
        return Collections.emptyList();
      }

      List<String> database = fileSourceDao.selectLineHashes(dbSession, uuid);
      if (database == null) {
        return Collections.emptyList();
      }
      return database;
    }
  }

  private List<String> getReportLineHashes(Component component) {
    return sourceLinesHash.getLineHashesMatchingDBVersion(component);
  }
}
