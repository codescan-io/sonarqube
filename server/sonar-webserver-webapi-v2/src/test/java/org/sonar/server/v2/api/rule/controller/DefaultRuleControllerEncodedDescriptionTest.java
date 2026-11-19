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
package org.sonar.server.v2.api.rule.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.resources.Languages;
import org.sonar.api.rule.RuleKey;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.rule.RuleDescriptionSectionDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.common.organization.OrganizationService;
import org.sonar.server.common.rule.service.NewCustomRule;
import org.sonar.server.common.rule.service.RuleInformation;
import org.sonar.server.common.rule.service.RuleService;
import org.sonar.server.common.text.MacroInterpreter;
import org.sonar.server.language.LanguageTesting;
import org.sonar.server.rule.RuleDescriptionFormatter;
import org.sonar.server.user.UserSession;
import org.sonar.server.v2.api.ControllerTester;
import org.sonar.server.v2.api.rule.converter.RuleRestResponseGenerator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.server.v2.WebApiEndpoints.RULES_ENDPOINT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DefaultRuleControllerEncodedDescriptionTest {

  private static final String DESCRIPTION = "Flag ../../classes/Foo.cls where Name = 'a' OR 1=1 -- é";

  private final UserSession userSession = mock(UserSession.class, RETURNS_SELF);
  private final RuleService ruleService = mock(RuleService.class);
  private final OrganizationService organizationService = mock(OrganizationService.class);
  private final Languages languages = mock(Languages.class);
  private final MacroInterpreter macroInterpreter = mock(MacroInterpreter.class);
  private final RuleDescriptionFormatter ruleDescriptionFormatter = mock(RuleDescriptionFormatter.class);
  private final RuleRestResponseGenerator ruleRestResponseGenerator = new RuleRestResponseGenerator(languages, macroInterpreter,
    ruleDescriptionFormatter);
  private final MockMvc mockMvc = ControllerTester.getMockMvc(new DefaultRuleController(userSession, ruleService,
    ruleRestResponseGenerator, organizationService));

  @BeforeEach
  void setUp() {
    when(organizationService.getOrganizationByKey("my-org")).thenReturn(new OrganizationDto().setUuid("org-uuid").setKey("my-org"));
    when(macroInterpreter.interpret(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    when(ruleDescriptionFormatter.toHtml(any(), any())).thenAnswer(invocation -> ((RuleDescriptionSectionDto) invocation.getArgument(1)).getContent());
    when(languages.get(anyString())).thenAnswer(invocation -> LanguageTesting.newLanguage(invocation.getArgument(0, String.class), "languageName"));
    RuleDto ruleDto = RuleTesting.newCustomRule(RuleTesting.newTemplateRule(RuleKey.parse("sf:XPathRule")));
    when(ruleService.createCustomRule(any())).thenReturn(new RuleInformation(ruleDto, List.of()));
  }

  @Test
  void create_whenDescriptionIsPlain_passesItThrough() throws Exception {
    postCreate("\"markdownDescription\":\"plain desc\"").andExpect(status().isOk());

    assertThat(createdRule().markdownDescription()).isEqualTo("plain desc");
  }

  @Test
  void create_whenDescriptionIsBase64UrlEncoded_decodesIt() throws Exception {
    postCreate("\"encodedMarkdownDescription\":\"" + base64Url(DESCRIPTION) + "\"").andExpect(status().isOk());

    assertThat(createdRule().markdownDescription()).isEqualTo(DESCRIPTION);
  }

  @Test
  void create_whenBothAreProvided_usesEncodedDescription() throws Exception {
    postCreate("\"markdownDescription\":\"plain\",\"encodedMarkdownDescription\":\"" + base64Url(DESCRIPTION) + "\"")
      .andExpect(status().isOk());

    assertThat(createdRule().markdownDescription()).isEqualTo(DESCRIPTION);
  }

  @Test
  void create_whenEncodedDescriptionIsEmpty_usesDescription() throws Exception {
    postCreate("\"markdownDescription\":\"plain\",\"encodedMarkdownDescription\":\"\"").andExpect(status().isOk());

    assertThat(createdRule().markdownDescription()).isEqualTo("plain");
  }

  @Test
  void create_whenNoDescriptionIsProvided_shouldFailWithBadRequest() throws Exception {
    postCreate("\"type\":\"CODE_SMELL\"")
      .andExpectAll(status().isBadRequest(),
        content().json("{message:\"Either 'markdownDescription' or 'encodedMarkdownDescription' must be provided\"}"));

    verify(ruleService, never()).createCustomRule(any());
  }

  @Test
  void create_whenEncodedDescriptionIsMalformed_shouldFailWithBadRequest() throws Exception {
    postCreate("\"encodedMarkdownDescription\":\"not+base64/\"")
      .andExpectAll(status().isBadRequest(),
        content().json("{message:\"Parameter 'encodedMarkdownDescription' is not valid base64url\"}"));

    verify(ruleService, never()).createCustomRule(any());
  }

  private ResultActions postCreate(String descriptionJson) throws Exception {
    String body = "{\"organization\":\"my-org\",\"key\":\"sf:my_xpath\",\"templateKey\":\"sf:XPathRule\",\"name\":\"My XPath\","
      + "\"impacts\":[]," + descriptionJson + "}";
    return mockMvc.perform(post(RULES_ENDPOINT).contentType(MediaType.APPLICATION_JSON_VALUE).content(body));
  }

  private NewCustomRule createdRule() {
    ArgumentCaptor<NewCustomRule> captor = ArgumentCaptor.forClass(NewCustomRule.class);
    verify(ruleService).createCustomRule(captor.capture());
    return captor.getValue();
  }

  private static String base64Url(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
