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
  private static final String KEY_CODESCAN_GITCLI_ENABLED = "codescan.gitcli.enabled";
  private static final String KEY_SFMETA_FILE_SUFFIXES = "sonar.sfmeta.file.suffixes";

  private final DbClient dbClient;
  private final FileSourceDao fileSourceDao;
  private final SourceLinesHashRepository sourceLinesHashRepository;
  private final SourceLinesRepository sourceLinesRepository;
  private final ReferenceBranchComponentUuids referenceBranchComponentUuids;
  private final MovedFilesRepository movedFilesRepository;
  private final AnalysisMetadataHolder analysisMetadataHolder;
  private final PeriodHolder periodHolder;
  private final NewCodeReferenceBranchComponentUuids newCodeReferenceBranchComponentUuids;
  private final ConfigurationRepository configurationRepository;

  public SourceLinesDiffImpl(DbClient dbClient, FileSourceDao fileSourceDao, SourceLinesHashRepository sourceLinesHashRepository,
    SourceLinesRepository sourceLinesRepository, ReferenceBranchComponentUuids referenceBranchComponentUuids,
    MovedFilesRepository movedFilesRepository, AnalysisMetadataHolder analysisMetadataHolder, PeriodHolder periodHolder,
    NewCodeReferenceBranchComponentUuids newCodeReferenceBranchComponentUuids, ConfigurationRepository configurationRepository) {
    this.dbClient = dbClient;
    this.fileSourceDao = fileSourceDao;
    this.sourceLinesHashRepository = sourceLinesHashRepository;
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
    if (isGitCliEnabled() && isSalesforceMetadataFile(component)) {
      return computeWithHistogramDiff(component);
    }
    return computeWithMyersDiff(component);
  }

  private int[] computeWithMyersDiff(Component component) {
    List<String> database = getPreviousVersionLineHashes(component);
    List<String> report = getCurrentVersionLineHashes(component);
    return new SourceLinesDiffFinder().findMatchingLines(database, report);
  }

  private int[] computeWithHistogramDiff(Component component) {
    try {
      List<String> previousVersionSourceLines = getPreviousVersionSourceContent(component);
      List<String> currentVersionSourceLines = getCurrentVersionSourceContent(component);

      if (previousVersionSourceLines.isEmpty() && currentVersionSourceLines.isEmpty()) {
        return new int[0];
      }

      if (previousVersionSourceLines.isEmpty()) {
        return new int[currentVersionSourceLines.size()];
      }

      return new GitDiffFinder().findMatchingLines(previousVersionSourceLines, currentVersionSourceLines);

    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      LOG.warn("[HISTOGRAM-DIFF] Interrupted for {}, falling back to Myers", component.getKey());
      return computeWithMyersDiff(component);
    } catch (Exception histogramDiffException) {
      LOG.warn("[HISTOGRAM-DIFF] Failed for {}, falling back to Myers. Error: {}",
        component.getKey(), histogramDiffException.getMessage(), histogramDiffException);
      return computeWithMyersDiff(component);
    }
  }

  private boolean isGitCliEnabled() {
    Configuration projectConfiguration = configurationRepository.getConfiguration();
    return projectConfiguration.getBoolean(KEY_CODESCAN_GITCLI_ENABLED).orElse(false);
  }

  private boolean isSalesforceMetadataFile(Component component) {
    String fileName = component.getName();
    if (fileName == null || fileName.isEmpty()) {
      return false;
    }
    String[] suffixes = configurationRepository.getConfiguration().getStringArray(KEY_SFMETA_FILE_SUFFIXES);
    for (String suffix : suffixes) {
      if (suffix == null || suffix.isEmpty()) {
        continue;
      }
      String normalized = suffix.startsWith(".") ? suffix : "." + suffix;
      if (fileName.endsWith(normalized)) {
        return true;
      }
    }
    return false;
  }

  private List<String> getPreviousVersionSourceContent(Component component) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      String previousFileUuid = resolveDbFileUuid(component);
      if (previousFileUuid == null) {
        return Collections.emptyList();
      }

      FileSourceDto fileSourceDto = fileSourceDao.selectByFileUuid(dbSession, previousFileUuid);
      if (fileSourceDto == null) {
        return Collections.emptyList();
      }

      DbFileSources.Data protobufSourceData = fileSourceDto.getSourceData();
      if (protobufSourceData == null) {
        return Collections.emptyList();
      }

      List<DbFileSources.Line> protobufLines = protobufSourceData.getLinesList();
      List<String> sourceLines = new ArrayList<>(protobufLines.size());
      for (DbFileSources.Line protobufLine : protobufLines) {
        sourceLines.add(protobufLine.hasSource() ? protobufLine.getSource() : "");
      }
      return sourceLines;
    }
  }

  private List<String> getCurrentVersionSourceContent(Component component) {
    List<String> sourceLines = new ArrayList<>();
    try (CloseableIterator<String> lineIterator = sourceLinesRepository.readLines(component)) {
      while (lineIterator.hasNext()) {
        sourceLines.add(lineIterator.next());
      }
    }
    return sourceLines;
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

  private List<String> getPreviousVersionLineHashes(Component component) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      String previousFileUuid = resolveDbFileUuid(component);
      if (previousFileUuid == null) {
        return Collections.emptyList();
      }

      List<String> lineHashes = fileSourceDao.selectLineHashes(dbSession, previousFileUuid);
      if (lineHashes == null) {
        return Collections.emptyList();
      }
      return lineHashes;
    }
  }

  private List<String> getCurrentVersionLineHashes(Component component) {
    return sourceLinesHashRepository.getLineHashesMatchingDBVersion(component);
  }
}
