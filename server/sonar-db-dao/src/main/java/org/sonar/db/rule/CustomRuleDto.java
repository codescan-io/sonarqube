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
package org.sonar.db.rule;

import javax.annotation.CheckForNull;

/** A rule created from a rule template, with the template it was created from. */
public class CustomRuleDto {
  // set by reflection by MyBatis
  private String uuid;
  private String repository;
  private String ruleKey;
  private String name;
  private String language;
  private Integer priority;
  private Integer ruleType;
  private String status;
  private Long createdAt;
  private String templateRepository;
  private String templateKey;

  public String getUuid() {
    return uuid;
  }

  public String getRepository() {
    return repository;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  @CheckForNull
  public String getName() {
    return name;
  }

  @CheckForNull
  public String getLanguage() {
    return language;
  }

  /** Default severity, as its ordinal: 0 info to 4 blocker. */
  @CheckForNull
  public Integer getPriority() {
    return priority;
  }

  /** The {@code RuleType} database constant: 1 code smell, 2 bug, 3 vulnerability, 4 security hotspot. */
  @CheckForNull
  public Integer getRuleType() {
    return ruleType;
  }

  @CheckForNull
  public String getStatus() {
    return status;
  }

  @CheckForNull
  public Long getCreatedAt() {
    return createdAt;
  }

  public String getTemplateRepository() {
    return templateRepository;
  }

  public String getTemplateKey() {
    return templateKey;
  }
}
