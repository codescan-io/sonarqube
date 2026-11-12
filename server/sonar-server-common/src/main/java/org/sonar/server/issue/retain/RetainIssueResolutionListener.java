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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sonar.api.issue.IssueStatus;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.RetainBranchContextDto;
import org.sonar.db.issue.RetainedIssueDao;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * Keeps 'cs_issue_track_for_retain' in step with the issues: an issue that enters Fixed gets a row when its
 * organization retains Fixed issues, and an issue that leaves Fixed loses its row whatever the toggle says. Keys are
 * collected in the caller's buffer and written once a buffer holds {@link #WRITE_SIZE} of them, so an analysis writes
 * per Fixed change rather than per saved issue. Writes go to the caller's session, so they commit with the issues. See
 * CSN-9.
 */
public class RetainIssueResolutionListener implements IssueResolutionListener {

  static final int WRITE_SIZE = 500;

  private final RetainedIssueDao retainedIssueDao;

  public RetainIssueResolutionListener(DbClient dbClient) {
    this.retainedIssueDao = dbClient.retainedIssueDao();
  }

  @Override
  public Buffer newBuffer() {
    return new RetainBuffer();
  }

  @Override
  public void onIssuesPersisted(IssuesPersistedEvent event, Buffer buffer) {
    RetainBuffer retain = (RetainBuffer) buffer;
    DbSession dbSession = event.dbSession();
    retain.now = event.now();
    retain.statusMarkedByUuid = event.statusMarkedByUuid();
    for (DefaultIssue issue : event.issues()) {
      if (RetainedIssueStatus.isFixed(issue)) {
        if (isRetainEnabled(dbSession, retain, issue.projectUuid())) {
          retain.enteredFixed.add(new EnteredFixed(issue.projectUuid(), issue.key()));
        }
      } else {
        // only issues that left Fixed reach here, see RetainedIssueStatus.isCandidate
        retain.leftFixed.add(issue.key());
      }
    }
    if (retain.leftFixed.size() >= WRITE_SIZE) {
      writeLeftFixed(dbSession, retain);
    }
    if (retain.enteredFixed.size() >= WRITE_SIZE) {
      writeEnteredFixed(dbSession, retain);
    }
  }

  @Override
  public void flush(DbSession dbSession, Buffer buffer) {
    RetainBuffer retain = (RetainBuffer) buffer;
    writeLeftFixed(dbSession, retain);
    writeEnteredFixed(dbSession, retain);
  }

  /** The branch rows were written to the failed session, so they may be gone with it. */
  @Override
  public void forceFlush(DbSession dbSession, Buffer buffer) {
    RetainBuffer retain = (RetainBuffer) buffer;
    retain.retainEnabledByBranch.values().forEach(branch -> branch.ifPresent(context -> retainedIssueDao.writeBranch(dbSession, context)));
    flush(dbSession, retain);
  }

  /** Read once per branch, on its first Fixed issue; the branch row is written then, so later writes only insert. */
  private boolean isRetainEnabled(DbSession dbSession, RetainBuffer retain, String branchUuid) {
    return retain.retainEnabledByBranch.computeIfAbsent(branchUuid, b -> {
      Optional<RetainBranchContextDto> branch = retainedIssueDao.selectBranchContext(dbSession, b)
        .filter(RetainBranchContextDto::isRetainEnabled);
      branch.ifPresent(context -> retainedIssueDao.writeBranch(dbSession, context));
      return branch;
    }).isPresent();
  }

  private void writeLeftFixed(DbSession dbSession, RetainBuffer retain) {
    retainedIssueDao.deleteRetainedIssues(dbSession, IssueStatus.FIXED, retain.leftFixed);
    retain.leftFixed.clear();
  }

  /** One insert per branch: an analysis is one branch, a user change nearly always is. */
  private void writeEnteredFixed(DbSession dbSession, RetainBuffer retain) {
    retain.enteredFixed.stream()
      .collect(groupingBy(EnteredFixed::branchUuid, LinkedHashMap::new, mapping(EnteredFixed::issueKey, toList())))
      .forEach((branchUuid, keys) ->
        retainedIssueDao.insertIssues(dbSession, branchUuid, IssueStatus.FIXED, retain.statusMarkedByUuid, retain.now, keys));
    retain.enteredFixed.clear();
  }

  private record EnteredFixed(String branchUuid, String issueKey) {
  }

  /** One caller's Fixed changes: an analysis, or one user change, which may cover several branches and users. */
  private static final class RetainBuffer implements Buffer {
    private final List<EnteredFixed> enteredFixed = new ArrayList<>(WRITE_SIZE);
    private final List<String> leftFixed = new ArrayList<>(WRITE_SIZE);
    /** Empty for a branch whose organization does not retain. */
    private final Map<String, Optional<RetainBranchContextDto>> retainEnabledByBranch = new HashMap<>();
    /** The caller's user, null for the analysis. */
    private String statusMarkedByUuid;
    private long now;
  }
}
