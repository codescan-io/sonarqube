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
package org.sonar.server.issue.ws;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.core.util.SequenceUuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.issue.TestIssueChangePostProcessor;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.issue.index.IssueIteratorFactory;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.api.issue.Issue.RESOLUTION_EXCEPTION;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.STATUS_OPEN;
import static org.sonar.api.issue.Issue.STATUS_RESOLVED;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.issue.IssueChangeDto.TYPE_COMMENT;
import static org.sonar.db.issue.IssueChangeDto.TYPE_EXCEPTION_REASON;
import static org.sonar.db.issue.IssueChangeDto.TYPE_FIELD_CHANGE;

/** CSN-82: the exception transition of {@code api/issues/bulk_transition}, with its reason and expiry date. */
public class BulkTransitionActionIT {

  private static final long NOW = 2_000_000_000_000L;
  private static final String REASON = "Approved until the vendor patch";
  private static final String COMMENT = "Resolution transferred from src (branch- main)";

  @Rule
  public DbTester db = DbTester.create();
  @Rule
  public EsTester es = EsTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private final DbClient dbClient = db.getDbClient();
  private final IssueIndexer issueIndexer = new IssueIndexer(es.client(), dbClient, new IssueIteratorFactory(dbClient),
    null);
  private final WsActionTester ws = new WsActionTester(new BulkTransitionAction(dbClient, userSession, issueIndexer,
    new SequenceUuidFactory(), new TestSystem2().setNow(NOW), new TestIssueChangePostProcessor()));

  private ComponentDto project;
  private ComponentDto file;
  private RuleDto rule;

  @Test
  public void exceptionRecordsItsReasonExpiryAndCommentOnEveryIssue() {
    givenAProject();
    IssueDto first = insertOpenIssue();
    IssueDto second = insertOpenIssue();

    String json = exceptionRequest(List.of(first, second), "2026-12-31").execute().getInput();

    assertThat(json).contains("\"transitioned\":2");
    for (IssueDto issue : List.of(first, second)) {
      IssueDto reloaded = reload(issue);
      assertThat(reloaded.getStatus()).isEqualTo(STATUS_RESOLVED);
      assertThat(reloaded.getResolution()).isEqualTo(RESOLUTION_EXCEPTION);
      assertThat(reloaded.getIssueResolutionExpiresAt()).isEqualTo(utcMidnight("2026-12-31"));
      assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).containsExactly(REASON);
      assertThat(changeData(issue, TYPE_COMMENT)).containsExactly(COMMENT);
      assertThat(changeData(issue, TYPE_FIELD_CHANGE))
        .containsExactly("resolution=EXCEPTION,status=OPEN|RESOLVED,issueStatus=OPEN|EXCEPTION");
    }
  }

  @Test
  public void emptyExpiryDateRecordsAnExceptionThatNeverExpires() {
    givenAProject();
    IssueDto issue = insertOpenIssue();

    exceptionRequest(List.of(issue), "").execute();

    assertThat(reload(issue).getResolution()).isEqualTo(RESOLUTION_EXCEPTION);
    assertThat(reload(issue).getIssueResolutionExpiresAt()).isNull();
    assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).containsExactly(REASON);
  }

  @Test
  public void perRowChangelogWritesTheSameExceptionRows() {
    givenAProject();
    IssueDto issue = insertOpenIssue();

    exceptionRequest(List.of(issue), "2027-03-31").setParam("setBasedChangelog", "false").execute();

    assertThat(reload(issue).getIssueResolutionExpiresAt()).isEqualTo(utcMidnight("2027-03-31"));
    assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).containsExactly(REASON);
    assertThat(changeData(issue, TYPE_COMMENT)).containsExactly(COMMENT);
    assertThat(changeData(issue, TYPE_FIELD_CHANGE))
      .containsExactly("resolution=EXCEPTION,status=OPEN|RESOLVED,issueStatus=OPEN|EXCEPTION");
  }

  @Test
  public void exceptionWithoutAReasonIsRejected() {
    givenAProject();
    TestRequest request = exceptionRequest(List.of(insertOpenIssue()), "2026-12-31").setParam("exceptionReason", " ");

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'exceptionReason' is required with the 'exception' transition");
  }

  @Test
  public void exceptionWithoutAnExpiryDateIsRejectedRatherThanAutoExpired() {
    givenAProject();
    TestRequest request = baseRequest(List.of(insertOpenIssue()), "exception").setParam("exceptionReason", REASON);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("'issueResolutionExpiryDate' is required with the 'exception' transition");
  }

  @Test
  public void malformedExpiryDateIsRejected() {
    givenAProject();
    IssueDto issue = insertOpenIssue();
    TestRequest request = exceptionRequest(List.of(issue), "31/12/2026");

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'issueResolutionExpiryDate' must be a date in the yyyy-MM-dd format: 31/12/2026");
    assertThat(reload(issue).getStatus()).isEqualTo(STATUS_OPEN);
  }

  @Test
  public void exceptionParametersAreRejectedWithAnotherTransition() {
    givenAProject();
    TestRequest request = baseRequest(List.of(insertOpenIssue()), "falsepositive").setParam("exceptionReason", REASON);

    assertThatThrownBy(request::execute)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("'exceptionReason' and 'issueResolutionExpiryDate' apply only to the 'exception' transition");
  }

  @Test
  public void falsePositiveLeavesNoExpiryAndNoReason() {
    givenAProject();
    IssueDto issue = insertOpenIssue();

    baseRequest(List.of(issue), "falsepositive").execute();

    assertThat(reload(issue).getResolution()).isEqualTo(RESOLUTION_FALSE_POSITIVE);
    assertThat(reload(issue).getIssueResolutionExpiresAt()).isNull();
    assertThat(changeData(issue, TYPE_EXCEPTION_REASON)).isEmpty();
  }

  private void givenAProject() {
    userSession.logIn().setSystemAdministrator();
    project = db.components().insertPrivateProject().getMainBranchComponent();
    file = db.components().insertComponent(newFileDto(project));
    rule = db.rules().insertIssueRule();
  }

  private IssueDto insertOpenIssue() {
    return db.issues().insertIssue(rule, project, file, i -> i.setStatus(STATUS_OPEN).setResolution(null));
  }

  private TestRequest exceptionRequest(List<IssueDto> issues, String expiryDate) {
    return baseRequest(issues, "exception")
      .setParam("exceptionReason", REASON)
      .setParam("issueResolutionExpiryDate", expiryDate);
  }

  private TestRequest baseRequest(List<IssueDto> issues, String transition) {
    return ws.newRequest()
      .setMethod("POST")
      .setParam("issues", String.join(",", issues.stream().map(IssueDto::getKey).toList()))
      .setParam("transition", transition)
      .setParam("branchUuid", project.uuid())
      .setParam("comment", COMMENT)
      .setParam("refreshMeasures", "false");
  }

  private IssueDto reload(IssueDto issue) {
    return dbClient.issueDao().selectOrFailByKey(db.getSession(), issue.getKey());
  }

  private List<String> changeData(IssueDto issue, String changeType) {
    return dbClient.issueChangeDao().selectByTypeAndIssueKeys(db.getSession(), List.of(issue.getKey()), changeType)
      .stream().map(IssueChangeDto::getChangeData).toList();
  }

  private static long utcMidnight(String date) {
    return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }
}
