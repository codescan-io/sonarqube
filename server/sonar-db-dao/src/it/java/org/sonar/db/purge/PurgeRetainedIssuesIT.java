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
package org.sonar.db.purge;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.issue.IssueStatus;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.issue.RetainFixtures;
import org.sonar.db.issue.RetainedIssueDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.issue.RetainFixtures.retainedCommentUuids;
import static org.sonar.db.issue.RetainFixtures.retainedIssueKeys;
import static org.sonar.db.issue.RetainFixtures.retainedIssues;

/** How the purge treats retained issues: captured when it closes them, kept past the closed-issue purge, see CSN-9. */
class PurgeRetainedIssuesIT {

  private static final int CODE_SMELL = 1;
  private static final int SECURITY_HOTSPOT = 4;
  private static final long PURGE_TIME = 5_000_000L;

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final RetainedIssueDao retainedIssueDao = new RetainedIssueDao();

  @Test
  void purgeDisabledComponents_retainsTheIssuesItClosesAsRemoved() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "ISSUE-1", "branch-1", "removed-file", CODE_SMELL);
    RetainFixtures.issue(db, "HOTSPOT-1", "branch-1", "removed-file", SECURITY_HOTSPOT);

    new PurgeCommands(db.getSession(), new PurgeProfiler(), System2.INSTANCE, retainedIssueDao)
      .purgeDisabledComponents("branch-1", List.of("removed-file"), PurgeListener.EMPTY);

    assertThat(retainedIssueKeys(db)).containsExactly("ISSUE-1");
  }

  @Test
  void purgeDisabledComponents_stillClosesTheIssues_whenTheOrganizationDoesNotRetain() {
    RetainFixtures.project(db, false, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "ISSUE-1", "branch-1", "removed-file", CODE_SMELL);

    new PurgeCommands(db.getSession(), new PurgeProfiler(), System2.INSTANCE, retainedIssueDao)
      .purgeDisabledComponents("branch-1", List.of("removed-file"), PurgeListener.EMPTY);

    assertThat(db.select("select status, resolution from issues where kee = 'ISSUE-1'"))
      .extracting(row -> row.get("STATUS"), row -> row.get("RESOLUTION"))
      .containsExactly(tuple("CLOSED", "REMOVED"));
    assertThat(db.countRowsOfTable("cs_issue_track_for_retain")).isZero();
  }

  /** A purge that closes no issue reads and writes nothing for the branch, not even its retained row. */
  @Test
  void purgeDisabledComponents_writesNothing_whenItClosesNoIssue() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);

    purgeAt(PURGE_TIME, "branch-1", "removed-file");

    assertThat(db.countRowsOfTable("cs_retain_details")).isZero();
    assertThat(db.countRowsOfTable("cs_issue_track_for_retain")).isZero();
  }

  /** The retained row is copied from the closed issue row: Fixed, closed by the purge, no user, file key kept. */
  @Test
  void purgeDisabledComponents_copiesTheClosedIssueRowAndWritesTheBranch() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "ISSUE-1", "branch-1", "removed-file", CODE_SMELL);

    purgeAt(PURGE_TIME, "branch-1", "removed-file");

    Map<String, Object> retained = retainedIssues(db).get(0);
    assertThat(retained)
      .containsEntry("ISSUE_KEE", "ISSUE-1")
      .containsEntry("BRANCH_UUID", "branch-1")
      .containsEntry("RETAINED_STATUS", "FIXED")
      .containsEntry("COMPONENT_KEY", "project-1:src/Gone.cls")
      .containsEntry("STATUS_CHANGED_AT", PURGE_TIME);
    assertThat(retained.get("STATUS_MARKED_BY_UUID")).isNull();
    assertThat(db.countRowsOfTable("cs_retain_details")).isOne();
  }

  /**
   * Issues of the removed file that were closed or resolved before the purge keep their own close date: the purge
   * neither closes them nor retains them, and a row retained earlier keeps the time it first became Fixed.
   */
  @Test
  void purgeDisabledComponents_retainsOnlyTheIssuesItClosesItself() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "OPEN", "branch-1", "removed-file", CODE_SMELL);
    RetainFixtures.closedIssue(db, "FIXED-WHILE-OFF", "branch-1", "removed-file", CODE_SMELL, 1_000L);
    RetainFixtures.closedIssue(db, "FIXED-AND-RETAINED", "branch-1", "removed-file", CODE_SMELL, 2_000L);
    RetainFixtures.retainedIssue(db, "branch-1", "FIXED-AND-RETAINED", "FIXED", 2_000L);
    RetainFixtures.issue(db, "ACCEPTED", "branch-1", "removed-file", CODE_SMELL, "RESOLVED", "WONTFIX", null);

    purgeAt(PURGE_TIME, "branch-1", "removed-file");

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("STATUS_CHANGED_AT"))
      .containsExactlyInAnyOrder(tuple("OPEN", PURGE_TIME), tuple("FIXED-AND-RETAINED", 2_000L));
    assertThat(db.select("select status, resolution from issues where kee = 'ACCEPTED'"))
      .extracting(row -> row.get("STATUS"), row -> row.get("RESOLUTION"))
      .containsExactly(tuple("RESOLVED", "WONTFIX"));
  }

  /** Only disabled files of the purged branch count, even for an issue of another branch closed at the same time. */
  @Test
  void purgeDisabledComponents_retainsOnlyIssuesOfDisabledFilesOfThePurgedBranch() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.branch(db, "branch-2", "project-1", "feature", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.file(db, "kept-file", "branch-1", "project-1:src/Kept.cls", true);
    RetainFixtures.file(db, "other-branch-file", "branch-2", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "REMOVED-FILE-ISSUE", "branch-1", "removed-file", CODE_SMELL);
    RetainFixtures.issue(db, "KEPT-FILE-ISSUE", "branch-1", "kept-file", CODE_SMELL);
    RetainFixtures.closedIssue(db, "OTHER-BRANCH-ISSUE", "branch-2", "other-branch-file", CODE_SMELL, PURGE_TIME);

    purgeAt(PURGE_TIME, "branch-1", "removed-file");

    assertThat(retainedIssueKeys(db)).containsExactly("REMOVED-FILE-ISSUE");
    assertThat(db.select("select status from issues where kee = 'KEPT-FILE-ISSUE'"))
      .extracting(row -> row.get("STATUS"))
      .containsExactly("OPEN");
  }

  /** Purging again later closes nothing new, so nothing more is retained and the first row is kept. */
  @Test
  void purgeDisabledComponents_twiceRetainsTheIssueOnce() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "removed-file", "branch-1", "project-1:src/Gone.cls", false);
    RetainFixtures.issue(db, "ISSUE-1", "branch-1", "removed-file", CODE_SMELL);

    purgeAt(PURGE_TIME, "branch-1", "removed-file");
    purgeAt(PURGE_TIME + 60_000L, "branch-1", "removed-file");

    assertThat(retainedIssues(db))
      .extracting(row -> row.get("ISSUE_KEE"), row -> row.get("STATUS_CHANGED_AT"))
      .containsExactly(tuple("ISSUE-1", PURGE_TIME));
  }

  @Test
  void selectOldClosedIssueKeys_skipsRetainedIssues() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "file-1", "branch-1", "project-1:src/Foo.cls");
    RetainFixtures.issue(db, "RETAINED", "branch-1", "file-1", CODE_SMELL);
    RetainFixtures.issue(db, "NOT-RETAINED", "branch-1", "file-1", CODE_SMELL);
    db.executeUpdateSql("update issues set status = 'CLOSED', resolution = 'FIXED', issue_close_date = 10");
    retain("branch-1", "RETAINED");

    assertThat(db.getSession().getMapper(PurgeMapper.class).selectOldClosedIssueKeys("branch-1", null))
      .containsExactly("NOT-RETAINED");
  }

  /** An issue whose file row is gone stays while retained; the branch deletion removes it and copies its comments. */
  @Test
  void selectBranchOrphanIssues_skipsRetainedIssues() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "file-1", "branch-1", "project-1:src/Foo.cls");
    RetainFixtures.issue(db, "RETAINED", "branch-1", "file-1", CODE_SMELL);
    RetainFixtures.issue(db, "NOT-RETAINED", "branch-1", "file-1", CODE_SMELL);
    retain("branch-1", "RETAINED");
    db.executeUpdateSql("delete from components where uuid = 'file-1'");

    assertThat(db.getSession().getMapper(PurgeMapper.class).selectBranchOrphanIssues("branch-1"))
      .containsExactly("NOT-RETAINED");
  }

  @Test
  void deleteBranch_copiesTheCommentsOfRetainedIssuesAndKeepsTheirRows() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "feature", "BRANCH", null);
    RetainFixtures.file(db, "file-1", "branch-1", "project-1:src/Foo.cls");
    RetainFixtures.issue(db, "RETAINED", "branch-1", "file-1", CODE_SMELL);
    RetainFixtures.issue(db, "NOT-RETAINED", "branch-1", "file-1", CODE_SMELL);
    retain("branch-1", "RETAINED");
    comment("c1", "RETAINED", "branch-1");
    comment("c2", "NOT-RETAINED", "branch-1");

    db.getDbClient().purgeDao().deleteBranch(db.getSession(), "branch-1");
    db.getSession().commit();

    assertThat(db.countRowsOfTable("issues")).isZero();
    assertThat(retainedIssueKeys(db)).containsExactly("RETAINED");
    assertThat(retainedCommentUuids(db)).containsExactly("c1");
  }

  @Test
  void deleteProject_removesItsRetainedIssues() {
    RetainFixtures.project(db, true, "project-1");
    RetainFixtures.branch(db, "branch-1", "project-1", "main", "BRANCH", null);
    RetainFixtures.file(db, "file-1", "branch-1", "project-1:src/Foo.cls");
    RetainFixtures.issue(db, "RETAINED", "branch-1", "file-1", CODE_SMELL);
    retain("branch-1", "RETAINED");
    comment("c1", "RETAINED", "branch-1");

    db.getDbClient().purgeDao().deleteProject(db.getSession(), "project-1", "TRK", "name", "key-project-1");
    db.getSession().commit();

    assertThat(db.countRowsOfTable("cs_issue_track_for_retain")).isZero();
    assertThat(db.countRowsOfTable("cs_retain_details")).isZero();
    assertThat(db.countRowsOfTable("cs_retain_issue_comments")).isZero();
  }

  private void purgeAt(long now, String branchUuid, String disabledFileUuid) {
    System2 system2 = mock(System2.class);
    when(system2.now()).thenReturn(now);
    new PurgeCommands(db.getSession(), new PurgeProfiler(), system2, retainedIssueDao)
      .purgeDisabledComponents(branchUuid, List.of(disabledFileUuid), PurgeListener.EMPTY);
    db.getSession().commit();
  }

  /** Retains an issue as the analysis would: its row is Fixed first. */
  private void retain(String branchUuid, String issueKey) {
    db.executeUpdateSql("update issues set status = 'CLOSED', resolution = 'FIXED', issue_close_date = 1 where kee = '" + issueKey + "'");
    DbSession session = db.getSession();
    retainedIssueDao.insertIssuesOfBranch(session, branchUuid, IssueStatus.FIXED, null, 1L, List.of(issueKey));
    session.commit();
  }

  private void comment(String uuid, String issueKey, String branchUuid) {
    db.executeInsert("issue_changes",
      "uuid", uuid,
      "kee", "kee-" + uuid,
      "issue_key", issueKey,
      "user_login", "user-1",
      "change_type", "comment",
      "change_data", "text of " + uuid,
      "created_at", 1L,
      "updated_at", 1L,
      "issue_change_creation_date", 1L,
      "project_uuid", branchUuid);
  }
}
