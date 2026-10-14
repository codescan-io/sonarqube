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

import javax.annotation.CheckForNull;

/** One measure of one analysis of a project's main branch. */
public class MainBranchMeasureHistoryDto {
  // set by reflection by MyBatis
  private String projectUuid;
  private long analysisDate;
  private String metricKey;
  private Double value;
  private String textValue;

  public String getProjectUuid() {
    return projectUuid;
  }

  public long getAnalysisDate() {
    return analysisDate;
  }

  public String getMetricKey() {
    return metricKey;
  }

  @CheckForNull
  public Double getValue() {
    return value;
  }

  @CheckForNull
  public String getTextValue() {
    return textValue;
  }

  public MainBranchMeasureHistoryDto setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  public MainBranchMeasureHistoryDto setAnalysisDate(long analysisDate) {
    this.analysisDate = analysisDate;
    return this;
  }

  public MainBranchMeasureHistoryDto setMetricKey(String metricKey) {
    this.metricKey = metricKey;
    return this;
  }

  public MainBranchMeasureHistoryDto setValue(@CheckForNull Double value) {
    this.value = value;
    return this;
  }

  public MainBranchMeasureHistoryDto setTextValue(@CheckForNull String textValue) {
    this.textValue = textValue;
    return this;
  }
}
