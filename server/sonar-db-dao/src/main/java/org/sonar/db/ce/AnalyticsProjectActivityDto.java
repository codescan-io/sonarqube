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

import javax.annotation.CheckForNull;

/** Analyses of one project over a period. */
public class AnalyticsProjectActivityDto {
  // set by reflection by MyBatis
  private String projectKey;
  private String projectName;
  private int analyses;
  private int success;
  private int failed;
  private int canceled;
  private Long lastSubmittedAt;
  private Double avgExecutionTimeMs;

  public String getProjectKey() {
    return projectKey;
  }

  @CheckForNull
  public String getProjectName() {
    return projectName;
  }

  public int getAnalyses() {
    return analyses;
  }

  public int getSuccess() {
    return success;
  }

  public int getFailed() {
    return failed;
  }

  public int getCanceled() {
    return canceled;
  }

  /**
   * Submission date of the project's latest analysis: inside the period for analyzed projects, before it for the others
   * (null when CE activity has no trace of one).
   */
  @CheckForNull
  public Long getLastSubmittedAt() {
    return lastSubmittedAt;
  }

  /** Average execution time of the successful analyses of the period. */
  @CheckForNull
  public Long getAvgExecutionTimeMs() {
    return avgExecutionTimeMs == null ? null : Math.round(avgExecutionTimeMs);
  }
}
