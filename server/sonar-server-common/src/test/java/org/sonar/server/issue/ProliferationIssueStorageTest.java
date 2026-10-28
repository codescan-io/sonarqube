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
package org.sonar.server.issue;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.issue.Issue;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.DefaultIssueExceptionReason;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueChangeDao;
import org.sonar.db.issue.IssueChangeMapper;
import org.sonar.db.issue.IssueDao;
import org.sonar.db.issue.ProliferationChangeQuery;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.rule.ServerRuleFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ProliferationIssueStorageTest {

  private static final String BRANCH_UUID = "branch-release";
  private static final long MARKED_AT = 1_700_000_000_000L;
  private static final Set<String> STATUSES = Set.of(Issue.STATUS_OPEN, Issue.STATUS_REOPENED);

  private final System2 system2 = mock(System2.class);
  private final DbClient dbClient = mock(DbClient.class);
  private final DbSession dbSession = mock(DbSession.class);
  private final IssueDao issueDao = mock(IssueDao.class);
  private final IssueChangeDao issueChangeDao = mock(IssueChangeDao.class);
  private final ServerRuleFinder ruleFinder = mock(ServerRuleFinder.class);
  private final IssueIndexer indexer = mock(IssueIndexer.class);

  private ProliferationIssueStorage underTest;

  @Before
  public void setUp() {
    when(dbClient.issueDao()).thenReturn(issueDao);
    when(dbClient.issueChangeDao()).thenReturn(issueChangeDao);
    when(dbSession.getMapper(IssueChangeMapper.class)).thenReturn(mock(IssueChangeMapper.class));
    when(system2.now()).thenReturn(MARKED_AT);
    underTest = new ProliferationIssueStorage(system2, dbClient, ruleFinder, indexer, new SequenceUuidFactory());
  }

  /** An issue as the workflow leaves it after a resolving transition. */
  private static DefaultIssue resolved(String key, String resolution) {
    DefaultIssue issue = new DefaultIssue();
    issue.setKey(key);
    issue.setProjectUuid(BRANCH_UUID);
    issue.setStatus(Issue.STATUS_RESOLVED);
    issue.setResolution(resolution);
    issue.setRuleKey(org.sonar.api.rule.RuleKey.of("apex", "SomeRule"));
    issue.setType(org.sonar.api.rules.RuleType.CODE_SMELL);
    issue.setCurrentChange(new FieldDiffs()
      .setDiff("resolution", null, resolution)
      .setDiff("status", "OPEN", Issue.STATUS_RESOLVED)
      .setDiff("issueStatus", "OPEN", "ACCEPTED")
      .setCreationDate(new Date(MARKED_AT)));
    return issue;
  }

  @Test
  public void writesFalsePositiveAndAcceptedWithOneStatementPerGroup() {
    List<DefaultIssue> issues = List.of(
      resolved("AAA-1", Issue.RESOLUTION_WONT_FIX),
      resolved("AAA-2", Issue.RESOLUTION_WONT_FIX));

    List<String> written = underTest.save(dbSession, issues, BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    assertThat(written).containsExactly("AAA-1", "AAA-2");
    // One update and one diff statement for both issues, not one each.
    verify(issueDao, times(1)).bulkTransitionWithExpiry(eq(dbSession), eq(List.of("AAA-1", "AAA-2")),
      eq(Issue.RESOLUTION_WONT_FIX), any(), anyLong(), eq(STATUSES));
    ArgumentCaptor<ProliferationChangeQuery> diffs = ArgumentCaptor.forClass(ProliferationChangeQuery.class);
    verify(issueChangeDao, times(1)).insertProliferationDiffs(eq(dbSession), diffs.capture(), any());
    assertThat(diffs.getValue().getKeys()).containsExactly("AAA-1", "AAA-2");
    assertThat(diffs.getValue().getResolution()).isEqualTo(Issue.RESOLUTION_WONT_FIX);
    assertThat(diffs.getValue().getIssueStatus()).isEqualTo("ACCEPTED");
    assertThat(diffs.getValue().getBranchUuid()).isEqualTo(BRANCH_UUID);
    assertThat(diffs.getValue().getChangeAuthor()).isEqualTo("user-uuid");
    assertThat(diffs.getValue().getMarkedAt()).isEqualTo(MARKED_AT);
    assertThat(diffs.getValue().getStatuses()).isEqualTo(STATUSES);
    // Nothing went down the per-issue path.
    verify(issueDao, never()).updateWithoutIssueImpacts(any(), any());
  }

  @Test
  public void splitsGroupsByResolution() {
    List<DefaultIssue> issues = List.of(
      resolved("AAA-1", Issue.RESOLUTION_WONT_FIX),
      resolved("AAA-2", Issue.RESOLUTION_FALSE_POSITIVE));

    underTest.save(dbSession, issues, BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    // Two resolutions cannot share one update statement.
    verify(issueDao, times(2)).bulkTransitionWithExpiry(any(), any(), any(), any(), anyLong(), any());
  }

  @Test
  public void splitsGroupsByExpiry() {
    DefaultIssue early = resolved("AAA-1", Issue.RESOLUTION_WONT_FIX);
    early.setIssueResolutionExpiresAt(111L);
    DefaultIssue late = resolved("AAA-2", Issue.RESOLUTION_WONT_FIX);
    late.setIssueResolutionExpiresAt(222L);

    underTest.save(dbSession, List.of(early, late), BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    // A severity-based expiry differs between issues, and one update statement carries one value.
    verify(issueDao, times(1)).bulkTransitionWithExpiry(any(), eq(List.of("AAA-1")), any(), eq(111L), anyLong(), any());
    verify(issueDao, times(1)).bulkTransitionWithExpiry(any(), eq(List.of("AAA-2")), any(), eq(222L), anyLong(), any());
  }

  @Test
  public void sendsAnExceptionMarkingDownThePerIssuePath() {
    DefaultIssue exception = resolved("AAA-1", Issue.RESOLUTION_WONT_FIX);
    exception.addExceptionReason(DefaultIssueExceptionReason.create("AAA-1", "user-uuid", "agreed with security"));

    underTest.save(dbSession, List.of(exception), BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    // The set-based statements have no column for an Exception reason, so they must not be used.
    verify(issueDao, never()).bulkTransitionWithExpiry(any(), any(), any(), any(), anyLong(), any());
    verify(issueDao, times(1)).updateWithoutIssueImpacts(eq(dbSession), any());
  }

  @Test
  public void sendsAnIssueWithAnUnmodelledFieldChangeDownThePerIssuePath() {
    DefaultIssue reassigned = resolved("AAA-1", Issue.RESOLUTION_WONT_FIX);
    reassigned.currentChange().setDiff("assignee", "someone", null);

    underTest.save(dbSession, List.of(reassigned), BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    // The update writes status, resolution and expiry only - an assignee change would be silently dropped.
    verify(issueDao, never()).bulkTransitionWithExpiry(any(), any(), any(), any(), anyLong(), any());
    verify(issueDao, times(1)).updateWithoutIssueImpacts(eq(dbSession), any());
  }

  @Test
  public void neverRewritesImpactsOnThePerIssuePath() {
    DefaultIssue exception = resolved("AAA-1", Issue.RESOLUTION_WONT_FIX);
    exception.addExceptionReason(DefaultIssueExceptionReason.create("AAA-1", "u", "r"));

    underTest.save(dbSession, List.of(exception), BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES);

    // Proliferation never changes impacts; issueDao.update() would delete and re-insert every impact row.
    verify(issueDao, never()).update(any(), any());
    verify(issueDao, times(1)).updateWithoutIssueImpacts(any(), any());
  }

  @Test
  public void indexesTheBranchOnceRatherThanPerBatch() {
    underTest.indexBranch(BRANCH_UUID, List.of("AAA-1", "AAA-2"));

    verify(indexer, times(1)).indexOnAnalysis(BRANCH_UUID, List.of("AAA-1", "AAA-2"));
    // commitAndIndexIssues writes an es_queue row per issue and deletes it again; that is what it replaces.
    verify(indexer, never()).commitAndIndexIssues(any(), any());
  }

  @Test
  public void indexesNothingForAnEmptyBranch() {
    underTest.indexBranch(BRANCH_UUID, List.of());

    verify(indexer, never()).indexOnAnalysis(any(), any());
  }

  @Test
  public void writesNothingForAnEmptyBatch() {
    assertThat(underTest.save(dbSession, List.of(), BRANCH_UUID, MARKED_AT, "user-uuid", STATUSES)).isEmpty();

    verify(dbSession, never()).commit();
  }
}
