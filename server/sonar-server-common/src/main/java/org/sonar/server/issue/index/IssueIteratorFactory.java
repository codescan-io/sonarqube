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
package org.sonar.server.issue.index;

import java.util.Collection;
import javax.annotation.Nullable;
import org.sonar.db.DbClient;

public class IssueIteratorFactory {

  private final DbClient dbClient;

  public IssueIteratorFactory(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  public IssueIterator createForAll() {
    return createForBranch(null);
  }

  public IssueIterator createForBranch(@Nullable String branchUuid) {
    return new IssueIteratorForSingleChunk(dbClient, branchUuid, null);
  }

  public IssueIterator createForIssueKeys(Collection<String> issueKeys) {
    return new IssueIteratorForMultipleChunks(dbClient, issueKeys);
  }

  /**
   * Streams every issue of the given rule uuids in a single server-side scroll cursor. Used by the codefixStatus
   * backfill migration to reindex the whole in-scope set in one pass (no key pagination / per-chunk re-query).
   */
  public IssueIterator createForRuleUuids(Collection<String> ruleUuids) {
    return new IssueIteratorForSingleChunk(dbClient, ruleUuids);
  }
}
