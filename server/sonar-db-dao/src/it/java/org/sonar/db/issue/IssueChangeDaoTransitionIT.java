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
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.STATUS_CONFIRMED;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_REOPENED;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.issue.IssueChangeDto.TYPE_COMMENT;
import static org.sonar.db.issue.IssueChangeDto.TYPE_FIELD_CHANGE;

/**
 * Covers the set-based changelog of a bulk transition, which builds its rows in the database rather than sending
 * one insert per row. The rows have to be indistinguishable from the ones a per-row writer produces, so the
 * change data is asserted against {@link FieldDiffs#toEncodedString()} rather than against a transcription of it.
 */
public class IssueChangeDaoTransitionIT {

  private static final long NOW = 2_000_000_000_000L;
  private static final String AUTHOR = "author-uuid";
  private static final String COMMENT = "transferred by CD-9256";

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

    underTest.insertTransitionDiffs(db.getSession(), List.of(issue.getKey()), RESOLUTION_FALSE_POSITIVE,
      "FALSE_POSITIVE", project.uuid(), AUTHOR, NOW, uuidFactory);
    db.getSession().commit();

    assertThat(changeOf(issue, TYPE_FIELD_CHANGE).getChangeData())
      .isEqualTo(equivalentFieldDiffs(issue.getKey(), STATUS_OPEN, STATUS_OPEN).toEncodedString());
  }

  @Test
  public void reopenedIssuePresentsItsIssueStatusAsOpen() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_REOPENED);

    underTest.insertTransitionDiffs(db.getSession(), List.of(issue.getKey()), RESOLUTION_FALSE_POSITIVE,
      "FALSE_POSITIVE", project.uuid(), AUTHOR, NOW, uuidFactory);
    db.getSession().commit();

    assertThat(changeOf(issue, TYPE_FIELD_CHANGE).getChangeData())
      .isEqualTo(equivalentFieldDiffs(issue.getKey(), STATUS_REOPENED, STATUS_OPEN).toEncodedString())
      .isEqualTo("resolution=FALSE-POSITIVE,status=REOPENED|RESOLVED,issueStatus=OPEN|FALSE_POSITIVE");
  }

  @Test
  public void writesOneRowPerTransitionableIssueAndSkipsTheRest() {
    givenAProject();
    IssueDto open = insertIssue(STATUS_OPEN);
    IssueDto confirmed = insertIssue(STATUS_CONFIRMED);
    IssueDto alreadyResolved = insertIssue(STATUS_RESOLVED);

    List<String> requested = List.of(open.getKey(), confirmed.getKey(), alreadyResolved.getKey());
    underTest.insertTransitionDiffs(db.getSession(), requested, RESOLUTION_FALSE_POSITIVE, "FALSE_POSITIVE",
      project.uuid(), AUTHOR, NOW, uuidFactory);
    underTest.insertTransitionComments(db.getSession(), requested, COMMENT, project.uuid(), AUTHOR, NOW,
      uuidFactory);
    db.getSession().commit();

    assertThat(underTest.selectByIssueKeys(db.getSession(), requested))
      .extracting(IssueChangeDto::getIssueKey, IssueChangeDto::getChangeType)
      .containsExactlyInAnyOrder(
        org.assertj.core.api.Assertions.tuple(open.getKey(), TYPE_FIELD_CHANGE),
        org.assertj.core.api.Assertions.tuple(open.getKey(), TYPE_COMMENT),
        org.assertj.core.api.Assertions.tuple(confirmed.getKey(), TYPE_FIELD_CHANGE),
        org.assertj.core.api.Assertions.tuple(confirmed.getKey(), TYPE_COMMENT));
  }

  @Test
  public void everyRowGetsItsOwnUuidWithinTheUuidColumnWidth() {
    givenAProject();
    List<String> keys = List.of(insertIssue(STATUS_OPEN).getKey(), insertIssue(STATUS_OPEN).getKey(),
      insertIssue(STATUS_OPEN).getKey());

    underTest.insertTransitionDiffs(db.getSession(), keys, RESOLUTION_FALSE_POSITIVE, "FALSE_POSITIVE",
      project.uuid(), AUTHOR, NOW, uuidFactory);
    underTest.insertTransitionComments(db.getSession(), keys, COMMENT, project.uuid(), AUTHOR, NOW, uuidFactory);
    db.getSession().commit();

    List<IssueChangeDto> written = underTest.selectByIssueKeys(db.getSession(), keys);
    assertThat(written).hasSize(6);
    assertThat(written).extracting(IssueChangeDto::getUuid).doesNotHaveDuplicates();
    assertThat(written).allMatch(change -> change.getUuid().length() <= 40);
    assertThat(written).filteredOn(change -> TYPE_COMMENT.equals(change.getChangeType()))
      .extracting(IssueChangeDto::getKey).doesNotContainNull().doesNotHaveDuplicates();
  }

  @Test
  public void commentRowCarriesTheAuthorAndTheBranchItWasWrittenFor() {
    givenAProject();
    IssueDto issue = insertIssue(STATUS_OPEN);

    underTest.insertTransitionComments(db.getSession(), List.of(issue.getKey()), COMMENT, project.uuid(), AUTHOR,
      NOW, uuidFactory);
    db.getSession().commit();

    IssueChangeDto written = changeOf(issue, TYPE_COMMENT);
    assertThat(written.getChangeData()).isEqualTo(COMMENT);
    assertThat(written.getUserUuid()).isEqualTo(AUTHOR);
    assertThat(written.getProjectUuid()).isEqualTo(project.uuid());
    assertThat(written.getIssueChangeCreationDate()).isEqualTo(NOW);
  }

  /** What the per-row writer builds for the same issue, which the set-based SQL has to reproduce byte for byte. */
  private static FieldDiffs equivalentFieldDiffs(String issueKey, String oldStatus, String oldIssueStatus) {
    return new FieldDiffs()
      .setUserUuid(AUTHOR)
      .setCreationDate(new Date(NOW))
      .setIssueKey(issueKey)
      .setDiff("resolution", null, RESOLUTION_FALSE_POSITIVE)
      .setDiff("status", oldStatus, STATUS_RESOLVED)
      .setDiff("issueStatus", oldIssueStatus, "FALSE_POSITIVE");
  }

  private IssueDto insertIssue(String status) {
    return db.issues().insertIssue(rule, project, file,
      i -> i.setStatus(status).setResolution(STATUS_RESOLVED.equals(status) ? RESOLUTION_FALSE_POSITIVE : null));
  }

  private IssueChangeDto changeOf(IssueDto issue, String changeType) {
    List<IssueChangeDto> changes = underTest.selectByTypeAndIssueKeys(db.getSession(), List.of(issue.getKey()),
      changeType);
    assertThat(changes).hasSize(1);
    return changes.get(0);
  }
}
