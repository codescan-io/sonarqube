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
package org.sonar.db.issue;

import java.util.Date;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.issue.Issue.STATUS_CONFIRMED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_REOPENED;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.issue.IssueChangeDto.TYPE_COMMENT;
import static org.sonar.db.issue.IssueChangeDto.TYPE_FIELD_CHANGE;

/**
 * CodeScan: covers the set-based changelog of a proliferated marking, the counterpart of
 * {@link IssueChangeDaoTransitionIT} for the two statements proliferation adds.
 * <p>
 * The statements bind their parameters off a {@link ProliferationChangeQuery} rather than off a MyBatis parameter
 * map, so these tests are what proves the SQL still receives every value: a mocked DAO cannot catch a property the
 * XML asks for and the bean does not expose. As in the transition test, the change data is asserted against
 * {@link FieldDiffs#toEncodedString()} so a proliferated row stays indistinguishable from a hand-made one.
 */
public class IssueChangeDaoProliferationIT {

  private static final long NOW = 2_000_000_000_000L;
  private static final long MARKED_AT = 1_700_000_000_000L;
  private static final String AUTHOR = "author-uuid";
  private static final String COMMENT = "Agreed with security";
  private static final List<String> CANDIDATE_STATUSES = List.of(STATUS_OPEN, STATUS_REOPENED);

  @Rule
  public DbTester db = DbTester.create();

  private final IssueChangeDao underTest = db.getDbClient().issueChangeDao();
  private final UuidFactory uuidFactory = new SequenceUuidFactory();
  private ComponentDto project;
  private ComponentDto file;
  private RuleDto rule;

  private void givenAProject() {
    project = db.components().insertPrivateProject().getMainBranchComponent();
    file = db.components().insertComponent(newFileDto(project));
    rule = db.rules().insertIssueRule();
  }

  @Test
  public void diffRowEncodesExactlyAsFieldDiffsDoes() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);

    underTest.insertProliferationDiffs(db.getSession(), marking(List.of(issue.getKey())), uuidFactory);
    db.getSession().commit();

    assertThat(changeOf(issue, TYPE_FIELD_CHANGE).getChangeData())
      .isEqualTo(equivalentFieldDiffs(issue.getKey(), STATUS_OPEN, STATUS_OPEN).toEncodedString());
  }

  @Test
  public void reopenedIssuePresentsItsIssueStatusAsOpen() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_REOPENED);

    underTest.insertProliferationDiffs(db.getSession(), marking(List.of(issue.getKey())), uuidFactory);
    db.getSession().commit();

    assertThat(changeOf(issue, TYPE_FIELD_CHANGE).getChangeData())
      .isEqualTo(equivalentFieldDiffs(issue.getKey(), STATUS_REOPENED, STATUS_OPEN).toEncodedString())
      .isEqualTo("resolution=WONTFIX,status=REOPENED|RESOLVED,issueStatus=OPEN|ACCEPTED");
  }

  /**
   * The candidate statuses are the caller's, not a constant in the SQL: a confirmed issue is written by a bulk
   * transition but is not a proliferation candidate, and the only thing standing between the two is this parameter.
   */
  @Test
  public void writesOnlyTheIssuesInTheCandidateStatuses() {
    givenAProject();
    IssueDto open = insertIssue(STATUS_OPEN);
    IssueDto reopened = insertIssue(STATUS_REOPENED);
    IssueDto confirmed = insertIssue(STATUS_CONFIRMED);
    IssueDto alreadyResolved = insertIssue(STATUS_RESOLVED);

    List<String> requested = List.of(open.getKey(), reopened.getKey(), confirmed.getKey(),
      alreadyResolved.getKey());
    underTest.insertProliferationDiffs(db.getSession(), marking(requested), uuidFactory);
    underTest.insertProliferationComments(db.getSession(), comment(requested, COMMENT, TYPE_COMMENT), uuidFactory);
    db.getSession().commit();

    assertThat(underTest.selectByIssueKeys(db.getSession(), requested))
      .extracting(IssueChangeDto::getIssueKey, IssueChangeDto::getChangeType)
      .containsExactlyInAnyOrder(
        tuple(open.getKey(), TYPE_FIELD_CHANGE),
        tuple(open.getKey(), TYPE_COMMENT),
        tuple(reopened.getKey(), TYPE_FIELD_CHANGE),
        tuple(reopened.getKey(), TYPE_COMMENT));
  }

  /** The point of the separate statement: a copy is dated with the marking, not with the run that copied it. */
  @Test
  public void rowsAreDatedWhenTheUserMarkedTheIssueNotWhenTheyWereWritten() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);

    underTest.insertProliferationDiffs(db.getSession(), marking(List.of(issue.getKey())), uuidFactory);
    underTest.insertProliferationComments(db.getSession(),
      comment(List.of(issue.getKey()), COMMENT, TYPE_COMMENT), uuidFactory);
    db.getSession().commit();

    assertThat(underTest.selectByIssueKeys(db.getSession(), List.of(issue.getKey())))
      .extracting(IssueChangeDto::getIssueChangeCreationDate, IssueChangeDto::getCreatedAt)
      .containsOnly(tuple(MARKED_AT, NOW));
  }

  @Test
  public void commentRowCarriesTheAuthorTheTextAndTheBranchItWasWrittenFor() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);

    underTest.insertProliferationComments(db.getSession(),
      comment(List.of(issue.getKey()), COMMENT, TYPE_COMMENT), uuidFactory);
    db.getSession().commit();

    IssueChangeDto written = changeOf(issue, TYPE_COMMENT);
    assertThat(written.getChangeData()).isEqualTo(COMMENT);
    assertThat(written.getUserUuid()).isEqualTo(AUTHOR);
    assertThat(written.getProjectUuid()).isEqualTo(project.uuid());
  }

  /** The change type is a parameter so one statement writes both plain comments and Exception reasons. */
  @Test
  public void theSameStatementWritesAnExceptionReason() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);

    underTest.insertProliferationComments(db.getSession(),
      comment(List.of(issue.getKey()), "approved by the architecture board", "exception_reason"), uuidFactory);
    db.getSession().commit();

    assertThat(underTest.selectByIssueKeys(db.getSession(), List.of(issue.getKey())))
      .extracting(IssueChangeDto::getChangeType, IssueChangeDto::getChangeData)
      .containsExactly(tuple("exception_reason", "approved by the architecture board"));
  }

  @Test
  public void everyRowGetsItsOwnUuidWithinTheUuidColumnWidth() {
    givenAProject();
    List<String> keys = List.of(insertIssue(STATUS_OPEN).getKey(), insertIssue(STATUS_OPEN).getKey(),
      insertIssue(STATUS_OPEN).getKey());

    underTest.insertProliferationDiffs(db.getSession(), marking(keys), uuidFactory);
    underTest.insertProliferationComments(db.getSession(), comment(keys, COMMENT, TYPE_COMMENT), uuidFactory);
    db.getSession().commit();

    List<IssueChangeDto> written = underTest.selectByIssueKeys(db.getSession(), keys);
    assertThat(written).hasSize(6);
    assertThat(written).extracting(IssueChangeDto::getUuid).doesNotHaveDuplicates();
    assertThat(written).allMatch(change -> change.getUuid().length() <= 40);
    assertThat(written).filteredOn(change -> TYPE_COMMENT.equals(change.getChangeType()))
      .extracting(IssueChangeDto::getKey).doesNotContainNull().doesNotHaveDuplicates();
  }

  /** One shared query copied per statement: neither copy may see the other's payload. */
  @Test
  public void copyingTheSharedQueryDoesNotLeakOnePayloadIntoTheOther() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);
    List<String> keys = List.of(issue.getKey());

    ProliferationChangeQuery shared = new ProliferationChangeQuery()
      .setBranchUuid(project.uuid())
      .setChangeAuthor(AUTHOR)
      .setNow(NOW)
      .setMarkedAt(MARKED_AT)
      .setStatuses(CANDIDATE_STATUSES);

    underTest.insertProliferationDiffs(db.getSession(), shared.copyFor(keys)
      .setResolution(RESOLUTION_WONT_FIX)
      .setIssueStatus("ACCEPTED"), uuidFactory);
    underTest.insertProliferationComments(db.getSession(), shared.copyFor(keys)
      .setComment(COMMENT)
      .setChangeType(TYPE_COMMENT), uuidFactory);
    db.getSession().commit();

    assertThat(shared.getResolution()).isNull();
    assertThat(shared.getComment()).isNull();
    assertThat(changeOf(issue, TYPE_COMMENT).getChangeData()).isEqualTo(COMMENT);
    assertThat(changeOf(issue, TYPE_FIELD_CHANGE).getChangeData())
      .isEqualTo(equivalentFieldDiffs(issue.getKey(), STATUS_OPEN, STATUS_OPEN).toEncodedString());
  }

  private ProliferationChangeQuery marking(List<String> keys) {
    return shared(keys)
      .setResolution(RESOLUTION_WONT_FIX)
      .setIssueStatus("ACCEPTED");
  }

  private ProliferationChangeQuery comment(List<String> keys, String text, String changeType) {
    return shared(keys)
      .setComment(text)
      .setChangeType(changeType);
  }

  private ProliferationChangeQuery shared(List<String> keys) {
    return new ProliferationChangeQuery()
      .setKeys(keys)
      .setBranchUuid(project.uuid())
      .setChangeAuthor(AUTHOR)
      .setNow(NOW)
      .setMarkedAt(MARKED_AT)
      .setStatuses(CANDIDATE_STATUSES);
  }

  /** What the per-row writer builds for the same issue, which the set-based SQL has to reproduce byte for byte. */
  private static FieldDiffs equivalentFieldDiffs(String issueKey, String oldStatus, String oldIssueStatus) {
    return new FieldDiffs()
      .setUserUuid(AUTHOR)
      .setCreationDate(new Date(MARKED_AT))
      .setIssueKey(issueKey)
      .setDiff("resolution", null, RESOLUTION_WONT_FIX)
      .setDiff("status", oldStatus, STATUS_RESOLVED)
      .setDiff("issueStatus", oldIssueStatus, "ACCEPTED");
  }

  private IssueDto insertIssue(String status) {
    return db.issues().insertIssue(rule, project, file,
      i -> i.setStatus(status).setResolution(STATUS_RESOLVED.equals(status) ? RESOLUTION_WONT_FIX : null));
  }

  private IssueChangeDto changeOf(IssueDto issue, String changeType) {
    List<IssueChangeDto> changes = underTest.selectByTypeAndIssueKeys(db.getSession(), List.of(issue.getKey()),
      changeType);
    assertThat(changes).hasSize(1);
    return changes.get(0);
  }
}
