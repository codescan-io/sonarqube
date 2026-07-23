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
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.sonar.db.DatabaseUtils;
import org.sonar.db.DbClient;

import static java.util.Optional.ofNullable;

public class IssueIteratorForMultipleChunks implements IssueIterator {

  private final DbClient dbClient;
  private final Iterator<List<String>> iteratorOverChunks;
  private IssueIteratorForSingleChunk currentChunk;

  public IssueIteratorForMultipleChunks(DbClient dbClient, Collection<String> issueKeys) {
    this.dbClient = dbClient;
    iteratorOverChunks = DatabaseUtils.toUniqueAndSortedPartitions(issueKeys).iterator();
  }

  @Override
  public boolean hasNext() {
    // Advance past chunks that yield no rows. A chunk's keys can resolve to zero indexable issues — e.g. the issues
    // were deleted since the keys were selected, or they no longer satisfy scrollIssuesForIndexation's inner joins
    // (missing component/branch). The previous implementation returned true whenever another key-chunk existed and
    // then called next() on an empty chunk, throwing NoSuchElementException.
    while (currentChunk == null || !currentChunk.hasNext()) {
      if (!iteratorOverChunks.hasNext()) {
        return false;
      }
      if (currentChunk != null) {
        currentChunk.close();
      }
      currentChunk = nextChunk();
    }
    return true;
  }

  @Override
  public IssueDoc next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return currentChunk.next();
  }

  private IssueIteratorForSingleChunk nextChunk() {
    List<String> nextInput = iteratorOverChunks.next();
    return new IssueIteratorForSingleChunk(dbClient, null, nextInput);
  }

  @Override
  public void close() {
    ofNullable(currentChunk).ifPresent(IssueIterator::close);
  }
}
