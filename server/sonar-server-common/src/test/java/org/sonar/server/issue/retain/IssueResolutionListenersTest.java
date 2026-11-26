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
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonar.api.rules.RuleType;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_FIXED;
import static org.sonar.api.issue.Issue.RESOLUTION_REMOVED;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.issue.Issue.STATUS_CLOSED;
import static org.sonar.api.issue.Issue.STATUS_CONFIRMED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_REOPENED;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;

class IssueResolutionListenersTest {

  private final IssueChangeContext context = IssueChangeContext.issueChangeContextByScanBuilder(new Date()).build();
  private final RecordingListener listener = new RecordingListener();
  private final List<IssuesPersistedEvent> events = listener.events;
  private final IssueResolutionListeners underTest = new IssueResolutionListeners(new IssueResolutionListener[] {listener});

  @Test
  void isFixed_coversEveryStateTheUiShowsAsFixed() {
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_CLOSED, RESOLUTION_FIXED))).isTrue();
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_CLOSED, RESOLUTION_REMOVED))).isTrue();
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_RESOLVED, RESOLUTION_FIXED))).isTrue();
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_RESOLVED, RESOLUTION_FALSE_POSITIVE))).isFalse();
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_OPEN, null))).isFalse();
    assertThat(RetainedIssueStatus.isFixed(issue(STATUS_CLOSED, RESOLUTION_FIXED).setType(RuleType.SECURITY_HOTSPOT))).isFalse();
  }

  @Test
  void onIssuesPersisted_passesOnlyIssuesWhoseStatusOrResolutionChanged() {
    DefaultIssue closedByAnalysis = issue(STATUS_CLOSED, RESOLUTION_FIXED).setKey("closed");
    closedByAnalysis.setFieldChange(context, "status", STATUS_OPEN, STATUS_CLOSED);
    DefaultIssue reopened = issue(STATUS_REOPENED, null).setKey("reopened");
    reopened.setFieldChange(context, "status", STATUS_RESOLVED, STATUS_REOPENED);
    reopened.setFieldChange(context, "resolution", RESOLUTION_FIXED, null);
    DefaultIssue fixedButOnlyReassigned = issue(STATUS_RESOLVED, RESOLUTION_FIXED).setKey("reassigned");
    fixedButOnlyReassigned.setFieldChange(context, "assignee", "a", "b");
    DefaultIssue copiedAsFixed = issue(STATUS_RESOLVED, RESOLUTION_FIXED).setKey("copied").setCopied(true);
    DefaultIssue newOpen = issue(STATUS_OPEN, null).setKey("new-open").setNew(true);

    underTest.onIssuesPersisted(mock(DbSession.class), List.of(closedByAnalysis, reopened, fixedButOnlyReassigned, copiedAsFixed, newOpen), 1L);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).issues()).extracting(DefaultIssue::key).containsExactly("closed", "reopened", "copied");
  }

  @Test
  void onIssuesPersisted_skipsChangesThatStayInsideOrOutsideFixed() {
    DefaultIssue resolvedThenClosed = issue(STATUS_CLOSED, RESOLUTION_FIXED).setKey("fixed-to-fixed");
    resolvedThenClosed.setFieldChange(context, "status", STATUS_RESOLVED, STATUS_CLOSED);
    DefaultIssue fixedThenRemoved = issue(STATUS_CLOSED, RESOLUTION_REMOVED).setKey("fixed-to-removed");
    fixedThenRemoved.setFieldChange(context, "resolution", RESOLUTION_FIXED, RESOLUTION_REMOVED);
    DefaultIssue accepted = issue(STATUS_RESOLVED, RESOLUTION_WONT_FIX).setKey("open-to-accepted");
    accepted.setFieldChange(context, "status", STATUS_OPEN, STATUS_RESOLVED);
    accepted.setFieldChange(context, "resolution", null, RESOLUTION_WONT_FIX);
    DefaultIssue confirmed = issue(STATUS_CONFIRMED, null).setKey("open-to-confirmed");
    confirmed.setFieldChange(context, "status", STATUS_OPEN, STATUS_CONFIRMED);
    DefaultIssue acceptedThenClosed = issue(STATUS_CLOSED, RESOLUTION_FIXED).setKey("accepted-to-fixed");
    acceptedThenClosed.setFieldChange(context, "status", STATUS_RESOLVED, STATUS_CLOSED);
    acceptedThenClosed.setFieldChange(context, "resolution", RESOLUTION_WONT_FIX, RESOLUTION_FIXED);

    underTest.onIssuesPersisted(mock(DbSession.class),
      List.of(resolvedThenClosed, fixedThenRemoved, accepted, confirmed, acceptedThenClosed), 1L);

    assertThat(events).hasSize(1);
    assertThat(events.get(0).issues()).extracting(DefaultIssue::key).containsExactly("accepted-to-fixed");
  }

  @Test
  void onIssuesPersisted_publishesNothingWhenNoIssueQualifies() {
    DefaultIssue reassigned = issue(STATUS_OPEN, null).setKey("reassigned");
    reassigned.setFieldChange(context, "assignee", "a", "b");

    underTest.onIssuesPersisted(mock(DbSession.class), List.of(reassigned), 1L);

    assertThat(events).isEmpty();
  }

  /** One buffer per batch, handed to every call of that batch and to its flush. */
  @Test
  void batch_passesItsOwnBufferToEveryCallAndToTheFlush() {
    DefaultIssue closed = issue(STATUS_CLOSED, RESOLUTION_FIXED).setKey("closed");
    closed.setFieldChange(context, "status", STATUS_OPEN, STATUS_CLOSED);
    IssueResolutionListeners.Batch first = underTest.newBatch(mock(DbSession.class));
    IssueResolutionListeners.Batch second = underTest.newBatch(mock(DbSession.class));

    first.onIssuesPersisted(List.of(closed), 1L);
    first.onIssuesPersisted(List.of(closed), 2L);
    second.onIssuesPersisted(List.of(closed), 3L);
    first.flush();

    assertThat(listener.buffersCalled).hasSize(3);
    assertThat(listener.buffersCalled.get(0)).isSameAs(listener.buffersCalled.get(1)).isNotSameAs(listener.buffersCalled.get(2));
    assertThat(listener.buffersFlushed).containsExactly(listener.buffersCalled.get(0));
  }

  @Test
  void onIssuesPersisted_flushesOnceForACallerThatSavesOnce() {
    DefaultIssue closed = issue(STATUS_CLOSED, RESOLUTION_FIXED).setKey("closed");
    closed.setFieldChange(context, "status", STATUS_OPEN, STATUS_CLOSED);

    underTest.onIssuesPersisted(mock(DbSession.class), List.of(closed), 1L);

    assertThat(listener.buffersFlushed).containsExactly(listener.buffersCalled.get(0));
  }

  private static final class RecordingListener implements IssueResolutionListener {
    private final List<IssuesPersistedEvent> events = new ArrayList<>();
    private final List<Buffer> buffersCalled = new ArrayList<>();
    private final List<Buffer> buffersFlushed = new ArrayList<>();

    @Override
    public Buffer newBuffer() {
      return new Buffer() {
      };
    }

    @Override
    public void onIssuesPersisted(IssuesPersistedEvent event, Buffer buffer) {
      events.add(event);
      buffersCalled.add(buffer);
    }

    @Override
    public void flush(DbSession dbSession, Buffer buffer) {
      buffersFlushed.add(buffer);
    }
  }

  private static DefaultIssue issue(String status, String resolution) {
    // an issue that is already in the database, as the analysis and the web hand them over
    return new DefaultIssue().setNew(false).setStatus(status).setResolution(resolution).setType(RuleType.CODE_SMELL);
  }
}
