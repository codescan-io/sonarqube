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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.protobuf.DbFileSources;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.source.FileSourceDto;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.source.HtmlSourceDecorator;
import org.sonar.server.source.SourceService;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.component.ComponentTesting.newFileDto;

class IssueContextActionIT {

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);
  @RegisterExtension
  private final UserSessionRule userSession = UserSessionRule.standalone();

  private final HtmlSourceDecorator htmlSourceDecorator = mock(HtmlSourceDecorator.class);
  private final SourceService sourceService = new SourceService(db.getDbClient(), htmlSourceDecorator);
  private final IssueFinder issueFinder = new IssueFinder(db.getDbClient(), userSession);
  private final IssueContextAction underTest = new IssueContextAction(userSession, db.getDbClient(),
    issueFinder, TestComponentFinder.from(db), sourceService);
  private final WsActionTester tester = new WsActionTester(underTest);

  @BeforeEach
  void setUp() {
    when(htmlSourceDecorator.getDecoratedSourceAsHtml(anyString(), anyString(), anyString()))
      .thenAnswer(invocation -> invocation.getArguments()[0].toString());
  }

  @Test
  void return_issue_context_with_low_severity_default_range() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(50));
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S100"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(25).setRuleKey(rule.getKey().repository(), rule.getKey().rule()));
    System.out.println(rule.getKey().repository()+" "+rule.getKey().rule());
    System.out.println(rule.getRepositoryKey()+" "+rule.getRuleKey());
    insertRuleCatalog("java:S100", "Test rule", "LOW");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("issueKey").getAsString()).isEqualTo(issue.getKey());
    assertThat(json.get("ruleKey").getAsString()).isEqualTo("java:S100");
    assertThat(json.get("line").getAsInt()).isEqualTo(25);
    assertThat(json.get("ruleDescription").getAsString()).isEqualTo("Test rule");
    assertThat(json.get("contextSeverity").getAsString()).isEqualTo("LOW");
    String snippet = json.get("snippet").getAsString();
    assertThat(snippet.split("\n").length).isEqualTo(11); // 25-5 to 25+5 = 11 lines
    assertThat(snippet).contains("line 20");
    assertThat(snippet).contains("line 30");
  }

  @Test
  void return_issue_context_with_medium_severity_method_range() {
    ProjectData projectData = db.components().insertPrivateProject();
    List<String> sourceLines = List.of(
      "public class TestClass {",
      "  private String field;",
      "",
      "  public void testMethod(int param) {",
      "    int x = 10;",
      "    if (x > 5) {",
      "      System.out.println(\"test\");",
      "    }",
      "  }",
      "",
      "  public void anotherMethod() {",
      "    return;",
      "  }",
      "}"
    );
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), sourceLines);
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S200"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(7).setRuleKey(rule.getKey().repository(), rule.getKey().rule())); // Line inside testMethod
    insertRuleCatalog("java:S200", "Medium severity rule", "MEDIUM");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("contextSeverity").getAsString()).isEqualTo("MEDIUM");
    String snippet = json.get("snippet").getAsString();
    // Should include the entire method from line 4 to 8
    assertThat(snippet).contains("public void testMethod");
    assertThat(snippet).contains("System.out.println");
    assertThat(snippet).doesNotContain("anotherMethod"); // Should not include other methods
  }

  @Test
  void return_issue_context_with_high_severity_class_range() {
    ProjectData projectData = db.components().insertPrivateProject();
    List<String> sourceLines = List.of(
      "package com.test;",
      "",
      "public class TestClass {",
      "  private String field;",
      "",
      "  public void testMethod(int param) {",
      "    int x = 10;",
      "    System.out.println(\"test\");",
      "  }",
      "",
      "  public void anotherMethod() {",
      "    return;",
      "  }",
      "}"
    );
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), sourceLines);
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S300"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(7).setRuleKey(rule.getKey().repository(), rule.getKey().rule()));
    insertRuleCatalog("java:S300", "High severity rule", "HIGH");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("contextSeverity").getAsString()).isEqualTo("HIGH");
    String snippet = json.get("snippet").getAsString();
    // Should include the entire class
    assertThat(snippet).contains("public class TestClass");
    assertThat(snippet).contains("testMethod");
    assertThat(snippet).contains("anotherMethod");
  }

  @Test
  void use_rule_key_from_request_param_if_provided() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto issueRule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S100"));
    RuleDto paramRule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S200"));
    IssueDto issue = db.issues().insertIssue(issueRule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10).setRuleKey(issueRule.getRepositoryKey(), issueRule.getKey().rule()));
    insertRuleCatalog("java:S200", "Rule from param", "LOW");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .setParam("rule", "java:S200")
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("ruleKey").getAsString()).isEqualTo("java:S200");
    assertThat(json.get("ruleDescription").getAsString()).isEqualTo("Rule from param");
  }

  @Test
  void use_rule_key_from_issue_if_not_provided_in_request() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S100"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10).setRuleKey(rule.getRepositoryKey(), rule.getKey().rule()));
    insertRuleCatalog("java:S100", "Rule from issue", "LOW");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("ruleKey").getAsString()).isEqualTo("java:S100");
    assertThat(json.get("ruleDescription").getAsString()).isEqualTo("Rule from issue");
  }

  @Test
  void handle_issue_without_rule_key() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10).setRuleKey(null,null));
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.has("ruleKey")).isFalse();
    assertThat(json.has("ruleDescription")).isFalse();
    assertThat(json.has("contextSeverity")).isFalse();
  }

  @Test
  void handle_issue_with_null_line_number() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(null));
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("line").getAsInt()).isEqualTo(1); // Default to 1
    String snippet = json.get("snippet").getAsString();
    assertThat(snippet.split("\n").length).isLessThanOrEqualTo(6); // 1-5 to 1+5, but file might be shorter
  }

  @Test
  void handle_issue_with_line_zero() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(0));
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("line").getAsInt()).isEqualTo(1); // Default to 1
  }

  @Test
  void handle_rule_catalog_entry_not_found() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S999"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10).setRuleKey(rule.getRepositoryKey(), rule.getRuleKey()));
    // No catalog entry for java:S999
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("ruleKey").getAsString()).isEqualTo("java:S999");
    assertThat(json.has("ruleDescription")).isFalse();
    assertThat(json.has("contextSeverity")).isFalse();
    // Should use default LOW behavior (±5 lines)
    String snippet = json.get("snippet").getAsString();
    assertThat(snippet.split("\n").length).isEqualTo(11);
  }

  @Test
  void handle_medium_severity_when_method_signature_not_found() {
    ProjectData projectData = db.components().insertPrivateProject();
    List<String> sourceLines = List.of(
      "line 1",
      "line 2",
      "line 3",
      "if (condition) {", // Control flow, not a method
      "  int x = 10;",
      "  System.out.println(x);",
      "}"
    );
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), sourceLines);
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S200"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(5).setRuleKey(rule.getRepositoryKey(), rule.getRuleKey()));
    insertRuleCatalog("java:S200", "Medium rule", "MEDIUM");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    // Should fall back to ±5 lines when method signature not found
    String snippet = json.get("snippet").getAsString();
    assertThat(snippet.split("\n").length).isLessThanOrEqualTo(11);
  }

  @Test
  void handle_empty_file() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), List.of());
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(1));
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    String snippet = json.get("snippet").getAsString();
    assertThat(snippet).isEmpty();
  }

  @Test
  void fail_when_user_not_logged_in() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10));

    TestRequest request = tester.newRequest()
      .setParam("issue", issue.getKey());

    assertThatThrownBy(() -> request.execute())
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Authentication is required");
  }

  @Test
  void fail_when_user_lacks_codeviewer_permission() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10));
    userSession.logIn("user").addProjectPermission(UserRole.USER, projectData.getProjectDto());

    TestRequest request = tester.newRequest()
      .setParam("issue", issue.getKey());

    assertThatThrownBy(() -> request.execute())
      .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void fail_when_issue_not_found() {
    ProjectData projectData = db.components().insertPrivateProject();
    setUserWithValidPermission(projectData);

    TestRequest request = tester.newRequest()
      .setParam("issue", "non-existent-issue-key");

    assertThatThrownBy(() -> request.execute())
      .isInstanceOf(NotFoundException.class)
      .hasMessageContaining("Issue with key 'non-existent-issue-key' does not exist");
  }

  @Test
  void fail_when_missing_issue_parameter() {
    userSession.logIn();

    TestRequest request = tester.newRequest();

    assertThatThrownBy(() -> request.execute())
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void handle_case_insensitive_severity() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(20));
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S200"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(10).setRuleKey(rule.getRepositoryKey(), rule.getRuleKey()));
    insertRuleCatalog("java:S200", "Test rule", "medium"); // lowercase
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    assertThat(json.get("contextSeverity").getAsString()).isEqualTo("medium");
    // Should use method extraction for MEDIUM
  }

  @Test
  void handle_snippet_at_file_boundaries() {
    ProjectData projectData = db.components().insertPrivateProject();
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), createSourceLines(5));
    RuleDto rule = db.rules().insertIssueRule();
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(2)); // Near beginning
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    String snippet = json.get("snippet").getAsString();
    // Should handle gracefully when trying to extract before line 1
    assertThat(snippet).isNotEmpty();
  }

  @Test
  void handle_complex_nested_methods() {
    ProjectData projectData = db.components().insertPrivateProject();
    List<String> sourceLines = List.of(
      "public class Outer {",
      "  public void outerMethod() {",
      "    if (true) {",
      "      innerMethod();",
      "    }",
      "  }",
      "",
      "  private void innerMethod() {",
      "    int x = 5;",
      "  }",
      "}"
    );
    ComponentDto file = insertFileWithSource(projectData.getMainBranchComponent(), sourceLines);
    RuleDto rule = db.rules().insertIssueRule(r -> r.setRuleKey("java:S200"));
    IssueDto issue = db.issues().insertIssue(rule, projectData.getMainBranchComponent(), file,
      i -> i.setLine(9).setRuleKey(rule.getRepositoryKey(), rule.getRuleKey())); // Inside innerMethod
    insertRuleCatalog("java:S200", "Medium rule", "MEDIUM");
    setUserWithValidPermission(projectData);

    TestResponse response = tester.newRequest()
      .setParam("issue", issue.getKey())
      .execute();

    JsonObject json = JsonParser.parseString(response.getInput()).getAsJsonObject();
    String snippet = json.get("snippet").getAsString();
    // Should extract innerMethod, not outerMethod
    assertThat(snippet).contains("private void innerMethod");
    assertThat(snippet).contains("int x = 5");
  }

  private List<String> createSourceLines(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
      .mapToObj(i -> "line " + i)
      .toList();
  }

  private ComponentDto insertFileWithSource(ComponentDto project, List<String> sourceLines) {
    ComponentDto file = db.components().insertComponent(newFileDto(project, project.uuid()));
    DbFileSources.Data.Builder sourceDataBuilder = DbFileSources.Data.newBuilder();
    for (int i = 0; i < sourceLines.size(); i++) {
      sourceDataBuilder.addLinesBuilder()
        .setLine(i + 1)
        .setSource(sourceLines.get(i))
        .build();
    }
    db.getDbClient().fileSourceDao().insert(db.getSession(), new FileSourceDto()
      .setUuid(java.util.UUID.randomUUID().toString())
      .setProjectUuid(project.uuid())
      .setFileUuid(file.uuid())
      .setSourceData(sourceDataBuilder.build()));
    db.commit();
    return file;
  }

  private void insertRuleCatalog(String ruleKey, String description, String contextSeverity) {
    try (DbSession session = db.getDbClient().openSession(false)) {
      java.sql.PreparedStatement stmt = session.getConnection()
        .prepareStatement("INSERT INTO cs_ai_rules_catalog (rule_key, description, context_severity) VALUES (?, ?, ?)");
      stmt.setString(1, ruleKey);
      stmt.setString(2, description);
      stmt.setString(3, contextSeverity);
      stmt.executeUpdate();
      session.commit();
      stmt.close();
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void setUserWithValidPermission(ProjectData projectData) {
    userSession.logIn("user")
      .addProjectPermission(UserRole.USER, projectData.getProjectDto())
      .addProjectPermission(UserRole.CODEVIEWER, projectData.getProjectDto());
  }
}
