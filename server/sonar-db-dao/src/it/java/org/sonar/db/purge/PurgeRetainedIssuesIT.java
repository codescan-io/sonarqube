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
import static org.sonar.db.issue.RetainFixtures.retainedCommentUuids;
import static org.sonar.db.issue.RetainFixtures.retainedIssueKeys;

/** How the purge treats retained issues: captured when it closes them, kept past the closed-issue purge, see CSN-9. */
class PurgeRetainedIssuesIT {

  private static final int CODE_SMELL = 1;
  private static final int SECURITY_HOTSPOT = 4;

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
