/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonar.scanner.repository;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.batch.fs.internal.DefaultInputProject;
import org.sonar.api.config.Configuration;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.scanner.scan.ScanProperties;
import org.sonar.scanner.scan.branch.BranchConfiguration;
import org.sonar.scanner.scan.branch.ProjectBranches;
import org.sonar.scanner.scm.ScmConfiguration;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.NewCodePeriods;

import static java.lang.String.format;

public class ReferenceBranchSupplier {
  private static final Logger LOG = Loggers.get(ReferenceBranchSupplier.class);
  private static final String LOG_MSG_WS = "Load New Code definition";
  private static final String NEW_CODE_PARAM_KEY = "sonar.newCode.referenceBranch";

  private final Configuration configuration;
  private final NewCodePeriodLoader newCodePeriodLoader;
  private final BranchConfiguration branchConfiguration;
  private final DefaultInputProject project;
  private final ProjectBranches branches;
  private final MeasuresComponentLoader measuresComponentLoader;
  private final ScmConfiguration scmConfiguration;
  private final AnalysisWarnings analysisWarnings;
  private final ScanProperties properties;

  public ReferenceBranchSupplier(Configuration configuration, NewCodePeriodLoader newCodePeriodLoader, MeasuresComponentLoader measuresComponentLoader, BranchConfiguration branchConfiguration, DefaultInputProject project, ScmConfiguration scmConfiguration,ProjectBranches branches, AnalysisWarnings analysisWarnings, ScanProperties properties) {
    this.configuration = configuration;
    this.newCodePeriodLoader = newCodePeriodLoader;
    this.measuresComponentLoader = measuresComponentLoader;
    this.branchConfiguration = branchConfiguration;
    this.project = project;
    this.scmConfiguration = scmConfiguration;
    this.analysisWarnings = analysisWarnings;
    this.branches = branches;
    this.properties = properties;
  }

  @CheckForNull
  public String get() {
    // branches will be empty in CE
    if (branchConfiguration.isPullRequest() || branches.isEmpty()) {
      return null;
    }
    return Optional.ofNullable(getFromProperties()).orElseGet(this::loadWs);
  }

  private String loadWs() {
    String branchName = getBranchName();
    Profiler profiler = Profiler.create(LOG).startInfo(LOG_MSG_WS);
    NewCodePeriods.ShowWSResponse newCode = newCodePeriodLoader.load(project.key(), branchName);
    profiler.stopInfo();
    if (newCode.getType() != NewCodePeriods.NewCodePeriodType.REFERENCE_BRANCH) {
      return null;
    }

    String referenceBranchName = newCode.getValue();
    if (branchName.equals(referenceBranchName)) {
      LOG.warn("New Code reference branch is set to the branch being analyzed. Skipping the computation of New Code");
      return null;
    }
    return referenceBranchName;
  }

  @CheckForNull
  public String getFromProperties() {
    // branches will be empty in CE
    if (branchConfiguration.isPullRequest() || branches.isEmpty()) {
      return null;
    }

    Optional<String> value = configuration.get(NEW_CODE_PARAM_KEY);
    if (value.isPresent()) {
      String referenceBranchName = value.get();
      String branchName = getBranchName();
      if (referenceBranchName.equals(branchName)) {
        throw new IllegalStateException(format("Reference branch set with '%s' points to the current branch '%s'", NEW_CODE_PARAM_KEY, referenceBranchName));
      }
      //ForkData Implementation
      Instant scmDate = getForkDateFromScmProvider(referenceBranchName);

      if (scmDate == null) {
        scmDate = properties.get("sonar.branch.forkDate")
                .map(val -> {
                  LOG.info("Parsing sonar.branch.forkDate={} property value to use in New Code.", val);
                  java.util.Date parsedDate = org.sonar.api.utils.DateUtils.parseDateTimeQuietly(val);
                  return parsedDate != null ? parsedDate.toInstant() : null;
                })
                .orElse(null);
      }

      if (scmDate == null
              && StringUtils.isNotEmpty(referenceBranchName)
              && properties.get("sonar.newCode.referenceBranch.useNewCodePeriodStartDate").map(Boolean::parseBoolean).orElse(false)) {
        Measures.ComponentWsResponse componentMeasures = measuresComponentLoader.load(project.key(), referenceBranchName);

        LOG.info("Getting period start date for reference branch '{}'", referenceBranchName);
        scmDate = StringUtils.isNotEmpty(componentMeasures.getPeriod().getDate())
                ? org.sonar.api.utils.DateUtils.parseDateTime(componentMeasures.getPeriod().getDate()).toInstant() : null;
      }

      if (scmDate == null
              && StringUtils.isNotEmpty(referenceBranchName)
              && properties.get("sonar.newCode.referenceBranch.useLastAnalysisDate").map(Boolean::parseBoolean).orElse(false)) {
        Measures.ComponentWsResponse componentMeasures = measuresComponentLoader.load(project.key(), referenceBranchName);

        LOG.info("Getting last analysis date for reference branch '{}'", referenceBranchName);
        scmDate = StringUtils.isNotEmpty(componentMeasures.getPeriod().getBuildDate())
                ? org.sonar.api.utils.DateUtils.parseDateTime(componentMeasures.getPeriod().getBuildDate()).toInstant() : null;
      }

      if (scmDate == null) {
        Measures.ComponentWsResponse branchMeasures = measuresComponentLoader.load(project.key(), branchName);
        if (branchMeasures != null) {
          LOG.info("The branch {} was already analysed before. Using the period date from first run: {}",
                  branchName, branchMeasures.getPeriod().getDate());
          scmDate = StringUtils.isNotEmpty(branchMeasures.getPeriod().getDate())
                  ? org.sonar.api.utils.DateUtils.parseDateTime(branchMeasures.getPeriod().getDate()).toInstant() : null;
        }
      }

      if (scmDate == null && StringUtils.isNotEmpty(referenceBranchName)) {
        Measures.ComponentWsResponse componentMeasures = measuresComponentLoader.load(project.key(), referenceBranchName);

        LOG.info("Getting last analysis date for reference branch '{}'", referenceBranchName);
        scmDate = StringUtils.isNotEmpty(componentMeasures.getPeriod().getBuildDate())
                ? org.sonar.api.utils.DateUtils.parseDateTime(componentMeasures.getPeriod().getBuildDate()).toInstant() : null;
      }

      LOG.info("Using the value '{}' as a branch fork date", scmDate);
      //return scmDate.
    }
    return null;
  }

  private String getBranchName() {
    return branchConfiguration.branchName() != null ? branchConfiguration.branchName() : branches.defaultBranchName();
  }

  private Instant getForkDateFromScmProvider(String referenceBranchName) {
    if (scmConfiguration.isDisabled() || scmConfiguration.provider() == null) {
      LOG.warn("SCM provider is disabled. No New Code will be computed.");
      analysisWarnings.addUnique("The scanner failed to compute New Code because no SCM provider was found. Please check your scanner logs.");
      return null;
    }

    Instant forkdate = scmConfiguration.provider().forkDate(referenceBranchName, project.getBaseDir());
    if (forkdate != null) {
      LOG.debug("Fork detected at '{}'", referenceBranchName, forkdate);
    } else {
      analysisWarnings.addUnique("The scanner failed to compute New Code. Please check your scanner logs.");
      LOG.warn("Failed to detect fork date. No New Code will be computed.", referenceBranchName);
    }

    return forkdate;
  }
}
