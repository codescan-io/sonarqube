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

import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.issue.Issue.RESOLUTION_EXCEPTION;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;

/** Covers the exception expiry the set-based bulk transition writes alongside the resolution. */
public class IssueDaoBulkTransitionIT {

  private static final long NOW = 2_000_000_000_000L;
  private static final long EXPIRES_AT = 1_798_675_200_000L;

  @Rule
  public DbTester db = DbTester.create();

  private final IssueDao underTest = db.getDbClient().issueDao();
  private ComponentDto project;
  private ComponentDto file;
  private RuleDto rule;

  @Test
  public void exceptionStoresItsExpiryOnEveryIssueItMoves() {
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    underTest.bulkTransition(db.getSession(), List.of(first.getKey(), second.getKey()), project.uuid(),
      RESOLUTION_EXCEPTION, EXPIRES_AT, NOW);
    db.getSession().commit();

    assertThat(List.of(reload(first), reload(second))).allSatisfy(issue -> {
      assertThat(issue.getStatus()).isEqualTo(STATUS_RESOLVED);
      assertThat(issue.getResolution()).isEqualTo(RESOLUTION_EXCEPTION);
      assertThat(issue.getIssueResolutionExpiresAt()).isEqualTo(EXPIRES_AT);
    });
  }

  @Test
  public void anExceptionThatNeverExpiresAndAnyOtherResolutionLeaveNoExpiry() {
    IssueDto neverExpires = insertOpenIssue();
    IssueDto falsePositive = insertOpenIssue();

    underTest.bulkTransition(db.getSession(), List.of(neverExpires.getKey()), project.uuid(),
      RESOLUTION_EXCEPTION, null, NOW);
    underTest.bulkTransition(db.getSession(), List.of(falsePositive.getKey()), project.uuid(),
      RESOLUTION_FALSE_POSITIVE, null, NOW);
    db.getSession().commit();

    assertThat(reload(neverExpires).getIssueResolutionExpiresAt()).isNull();
    assertThat(reload(falsePositive).getResolution()).isEqualTo(RESOLUTION_FALSE_POSITIVE);
    assertThat(reload(falsePositive).getIssueResolutionExpiresAt()).isNull();
  }

  @Test
  public void selectExceptionSourcesOfTransferReturnsEachPairedSourceThatIsStillAnException() {
    givenTheTransferReportTable();
    IssueDto expiring = insertOpenIssue();
    IssueDto neverExpires = insertOpenIssue();
    IssueDto falsePositive = insertOpenIssue();
    underTest.bulkTransition(db.getSession(), List.of(expiring.getKey()), project.uuid(),
      RESOLUTION_EXCEPTION, EXPIRES_AT, NOW);
    underTest.bulkTransition(db.getSession(), List.of(neverExpires.getKey()), project.uuid(),
      RESOLUTION_EXCEPTION, null, NOW);
    underTest.bulkTransition(db.getSession(), List.of(falsePositive.getKey()), project.uuid(),
      RESOLUTION_FALSE_POSITIVE, null, NOW);
    db.getSession().commit();
    String run = UUID.randomUUID().toString();
    pair(run, expiring, "D1");
    pair(run, neverExpires, "D1");
    pair(run, falsePositive, "D2");
    pair(run, expiring, "D3");
    pair(UUID.randomUUID().toString(), neverExpires, "D3");

    List<IssueExceptionSourceDto> sources = underTest.selectExceptionSourcesOfTransfer(db.getSession(), run,
      List.of("D1", "D2", "D3", "D4"));

    assertThat(sources)
      .extracting(IssueExceptionSourceDto::getIssueKey, IssueExceptionSourceDto::getSourceKey,
        IssueExceptionSourceDto::getExpiresAt)
      .containsExactlyInAnyOrder(
        tuple("D1", expiring.getKey(), EXPIRES_AT),
        tuple("D1", neverExpires.getKey(), null),
        tuple("D3", expiring.getKey(), EXPIRES_AT));
  }

  /** The resolution transfer report lives in CodeScan's schema, so the test database only gets the columns read. */
  private void givenTheTransferReportTable() {
    db.executeDdl("create table if not exists cs_resolution_transfer_issue (uuid varchar(40) not null primary key, "
      + "transfer_uuid varchar(40) not null, issue_key varchar(50) not null, destination_issue_key varchar(50))");
  }

  private void pair(String transferUuid, IssueDto source, String destinationKey) {
    db.executeUpdateSql("insert into cs_resolution_transfer_issue (uuid, transfer_uuid, issue_key, "
      + "destination_issue_key) values (?, ?, ?, ?)", UUID.randomUUID().toString(), transferUuid, source.getKey(),
      destinationKey);
  }

  private IssueDto insertOpenIssue() {
    if (project == null) {
      project = db.components().insertPrivateProject().getMainBranchComponent();
      file = db.components().insertComponent(newFileDto(project));
      rule = db.rules().insertIssueRule();
    }
    return db.issues().insertIssue(rule, project, file, i -> i.setStatus(STATUS_OPEN).setResolution(null));
  }

  private IssueDto reload(IssueDto issue) {
    return underTest.selectOrFailByKey(db.getSession(), issue.getKey());
  }
}
