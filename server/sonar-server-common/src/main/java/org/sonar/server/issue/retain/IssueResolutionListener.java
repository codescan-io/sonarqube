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

import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.server.ServerSide;
import org.sonar.db.DbSession;

/**
 * Notified synchronously after issues are saved. An exception fails the save, so the issue change and the listener's
 * writes stay consistent. A listener keeps no state of its own: what it collects across the save batches of one caller
 * lives in that caller's {@link Buffer}.
 */
@ServerSide
@ComputeEngineSide
public interface IssueResolutionListener {

  /** What a listener collects for one caller, such as one analysis or one request. Never shared between threads. */
  interface Buffer {
  }

  Buffer newBuffer();

  /** One save batch. May write to the event's session, for example when the buffer is full. */
  void onIssuesPersisted(IssuesPersistedEvent event, Buffer buffer);

  /** Writes what {@code buffer} still holds. Called once, after the last batch and before the session is committed. */
  void flush(DbSession dbSession, Buffer buffer);

  /**
   * Like {@link #flush}, but to a fresh session after the caller's own one failed and was rolled back: also writes again
   * what earlier batches wrote to that session and the buffer depends on.
   */
  default void forceFlush(DbSession dbSession, Buffer buffer) {
    flush(dbSession, buffer);
  }
}
