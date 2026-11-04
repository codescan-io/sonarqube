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
package org.sonar.db.issue;

import javax.annotation.CheckForNull;
import org.sonar.db.component.BranchType;

/**
 * A branch or pull request that holds retained issues, as listed for the CSV export. {@code active} is false once
 * the branch has been deleted: its uuid no longer has a 'project_branches' row.
 */
public class RetainedBranchDto {

  private String branchUuid;
  private String branchKey;
  private BranchType branchType;
  private String targetBranchKey;
  private boolean active;
  private long issueCount;

  public String getBranchUuid() {
    return branchUuid;
  }

  public String getBranchKey() {
    return branchKey;
  }

  public BranchType getBranchType() {
    return branchType;
  }

  @CheckForNull
  public String getTargetBranchKey() {
    return targetBranchKey;
  }

  public boolean isActive() {
    return active;
  }

  public long getIssueCount() {
    return issueCount;
  }
}
