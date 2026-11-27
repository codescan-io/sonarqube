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
package org.sonar.server.issue.retain;

import java.util.List;
import javax.annotation.Nullable;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbSession;

/**
 * Issues whose Fixed state may have changed, just saved in {@link #dbSession()}: one save batch of an analysis, which
 * covers one branch, or one user change, which may cover several. Listeners write in the same session so the issue and
 * its retained row are committed together. See CSN-253 and CSN-254.
 *
 * @param statusMarkedByUuid the user who made the change, the same for every issue; null when the analysis made it
 */
public record IssuesPersistedEvent(DbSession dbSession, List<DefaultIssue> issues, long now, @Nullable String statusMarkedByUuid) {
}
