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
package org.sonar.db.measure;

public class ProjectMeasureDto {

  private String projectUuid;
  private Long lastAnalysis;
  private long loc;
  private String textValue;

  public String getProjectUuid() {
    return projectUuid;
  }

  public ProjectMeasureDto setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  public String getTextValue() {
    return textValue;
  }

  public ProjectMeasureDto setTextValue(String textValue) {
    this.textValue = textValue;
    return this;
  }

  public long getLoc() {
    return loc;
  }

  public ProjectMeasureDto setLoc(long loc) {
    this.loc = loc;
    return this;
  }

  public Long getLastAnalysis() {
    return lastAnalysis;
  }

  public ProjectMeasureDto setLastAnalysis(Long lastAnalysis) {
    this.lastAnalysis = lastAnalysis;
    return this;
  }

}
