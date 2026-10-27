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
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.rule.RuleDto;

import static org.assertj.core.api.Assertions.assertThat;
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

  @Test
  public void exceptionStoresItsExpiryOnEveryIssueItMoves() {
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    int moved = underTest.bulkTransition(db.getSession(), List.of(first.getKey(), second.getKey()),
      RESOLUTION_EXCEPTION, EXPIRES_AT, NOW);
    db.getSession().commit();

    assertThat(moved).isEqualTo(2);
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

    underTest.bulkTransition(db.getSession(), List.of(neverExpires.getKey()), RESOLUTION_EXCEPTION, null, NOW);
    underTest.bulkTransition(db.getSession(), List.of(falsePositive.getKey()), RESOLUTION_FALSE_POSITIVE, null, NOW);
    db.getSession().commit();

    assertThat(reload(neverExpires).getIssueResolutionExpiresAt()).isNull();
    assertThat(reload(falsePositive).getResolution()).isEqualTo(RESOLUTION_FALSE_POSITIVE);
    assertThat(reload(falsePositive).getIssueResolutionExpiresAt()).isNull();
  }

  private IssueDto insertOpenIssue() {
    ComponentDto project = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    RuleDto rule = db.rules().insertIssueRule();
    return db.issues().insertIssue(rule, project, file, i -> i.setStatus(STATUS_OPEN).setResolution(null));
  }

  private IssueDto reload(IssueDto issue) {
    return underTest.selectOrFailByKey(db.getSession(), issue.getKey());
  }
}
