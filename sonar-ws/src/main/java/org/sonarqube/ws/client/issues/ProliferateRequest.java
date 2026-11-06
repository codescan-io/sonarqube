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
package org.sonarqube.ws.client.issues;

import java.util.List;
import jakarta.annotation.Generated;

/**
 * Codescan customization.
 *
 * This is part of the internal API.
 * This is a POST request.
 * @see <a href="https://next.sonarqube.com/sonarqube/web_api/api/issues/proliferate">Further information about this action online</a>
 */
@Generated("sonar-ws-generator")
public class ProliferateRequest {

  private List<String> issues;
  private String comment;
  private String markedAt;

  /**
   * Issue keys whose marking should be copied to the other branches of their project.
   */
  public ProliferateRequest setIssues(List<String> issues) {
    this.issues = issues;
    return this;
  }

  public List<String> getIssues() {
    return issues;
  }

  /**
   * Comment to copy onto each proliferated issue.
   */
  public ProliferateRequest setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public String getComment() {
    return comment;
  }

  /**
   * Epoch millis to stamp the proliferated changes with, so they carry the time of the original marking.
   */
  public ProliferateRequest setMarkedAt(String markedAt) {
    this.markedAt = markedAt;
    return this;
  }

  public String getMarkedAt() {
    return markedAt;
  }
}
