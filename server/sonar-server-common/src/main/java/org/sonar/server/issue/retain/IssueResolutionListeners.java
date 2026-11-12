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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.db.DbSession;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Calls every {@link IssueResolutionListener} with the issues of a save whose Fixed state may have changed. Nothing is
 * called when no issue qualifies, so ordinary saves cost one pass over the batch in memory.
 */
public class IssueResolutionListeners {

  private final IssueResolutionListener[] listeners;

  /**
   * Used by the container when there is no IssueResolutionListener implementation in container.
   */
  @Autowired(required = false)
  public IssueResolutionListeners() {
    this.listeners = new IssueResolutionListener[0];
  }

  /**
   * Used by the container when there is at least one IssueResolutionListener implementation in container.
   */
  @Autowired(required = false)
  public IssueResolutionListeners(IssueResolutionListener[] listeners) {
    this.listeners = listeners;
  }

  /**
   * For the analysis, which saves issues in several batches: lets the listeners collect across the batches. Its changes
   * are made by no user. The caller keeps it local and calls {@link Batch#flush()} before committing {@code dbSession}.
   */
  public Batch newBatch(DbSession dbSession) {
    return new Batch(dbSession, null);
  }

  /**
   * For a user change, saved once. Every issue of it was changed by the same user, read from the issues' change. Call
   * after the issues are written to {@code dbSession} and before it is committed.
   */
  public void onIssuesPersisted(DbSession dbSession, Collection<DefaultIssue> savedIssues, long now) {
    if (listeners.length == 0 || savedIssues.isEmpty()) {
      return;
    }
    Batch batch = new Batch(dbSession, userOfTheChange(savedIssues));
    batch.onIssuesPersisted(savedIssues, now);
    batch.flush();
  }

  @CheckForNull
  private static String userOfTheChange(Collection<DefaultIssue> issues) {
    return issues.stream()
      .map(DefaultIssue::currentChange)
      .filter(Objects::nonNull)
      .map(change -> change.userUuid().orElse(null))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  public final class Batch {
    private final DbSession dbSession;
    private final String statusMarkedByUuid;
    private final List<BufferedListener> bufferedListeners;

    private Batch(DbSession dbSession, @Nullable String statusMarkedByUuid) {
      this.dbSession = dbSession;
      this.statusMarkedByUuid = statusMarkedByUuid;
      this.bufferedListeners = Arrays.stream(listeners)
        .map(listener -> new BufferedListener(listener, listener.newBuffer()))
        .toList();
    }

    /** Call after the issues are written to the session. */
    public void onIssuesPersisted(Collection<DefaultIssue> savedIssues, long now) {
      if (bufferedListeners.isEmpty() || savedIssues.isEmpty()) {
        return;
      }
      List<DefaultIssue> candidates = savedIssues.stream().filter(RetainedIssueStatus::isCandidate).toList();
      if (candidates.isEmpty()) {
        return;
      }
      IssuesPersistedEvent event = new IssuesPersistedEvent(dbSession, candidates, now, statusMarkedByUuid);
      bufferedListeners.forEach(bufferedListener -> bufferedListener.onIssuesPersisted(event));
    }

    /** Writes what the listeners still hold. Required before the session is committed, or those writes are lost. */
    public void flush() {
      bufferedListeners.forEach(bufferedListener -> bufferedListener.flush(dbSession));
    }

    /** Writes what the listeners still hold to {@code flushSession}, for when the batch's own session failed. */
    public void forceFlush(DbSession flushSession) {
      bufferedListeners.forEach(bufferedListener -> bufferedListener.forceFlush(flushSession));
    }
  }

  /** A listener with what it collected for one batch. */
  private record BufferedListener(IssueResolutionListener listener, IssueResolutionListener.Buffer buffer) {
    void onIssuesPersisted(IssuesPersistedEvent event) {
      listener.onIssuesPersisted(event, buffer);
    }

    void flush(DbSession dbSession) {
      listener.flush(dbSession, buffer);
    }

    void forceFlush(DbSession dbSession) {
      listener.forceFlush(dbSession, buffer);
    }
  }
}
