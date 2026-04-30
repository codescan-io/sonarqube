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
package org.sonar.server.rule.ws;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonarqube.ws.MediaTypes;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.user.UserSession;

import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Generate XPath from natural language description without creating a rule.
 * Used for initial generation and regeneration in the AI custom rule wizard.
 */
public class GenerateAIXPathAction implements RulesWsAction {

  static final String PARAM_DESCRIPTION = "description";
  static final String PARAM_ORGANIZATION = "organization";
  private static final int DESCRIPTION_MAX_LENGTH = 5000;

  private final DbClient dbClient;
  private final RuleWsSupport ruleWsSupport;
  private final AIRuleGenerator aiRuleGenerator;
  private final UserSession userSession;

  public GenerateAIXPathAction(DbClient dbClient, RuleWsSupport ruleWsSupport, AIRuleGenerator aiRuleGenerator, UserSession userSession) {
    this.dbClient = dbClient;
    this.ruleWsSupport = ruleWsSupport;
    this.aiRuleGenerator = aiRuleGenerator;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller
      .createAction("generate_ai_xpath")
      .setPost(true)
      .setInternal(true)
      .setDescription("Generate an XPath expression from a natural language description using AI.<br>" +
        "Does not create a rule. Use this for preview and regeneration.<br>" +
        "Requires the 'Administer Quality Profiles' permission")
      .setSince("10.7")
      .setHandler(this);

    action.createParam(PARAM_DESCRIPTION)
      .setRequired(true)
      .setMaximumLength(DESCRIPTION_MAX_LENGTH)
      .setDescription("Natural language description of the rule")
      .setExampleValue("Find all flows that are not active");

    action.createParam(PARAM_ORGANIZATION)
      .setRequired(true)
      .setDescription("Organization key")
      .setExampleValue("my-org");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    String description = request.mandatoryParam(PARAM_DESCRIPTION).trim();
    String organizationKey = request.mandatoryParam(PARAM_ORGANIZATION).trim();

    if ("null".equals(organizationKey)) {
      throw new IllegalArgumentException("Organization key is required and cannot be null");
    }

    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = ruleWsSupport.getOrganizationByKey(dbSession, organizationKey);
      ruleWsSupport.checkQProfileAdminPermission(organization);

      String userUuid = userSession.getUuid();
      String generatedXPath = aiRuleGenerator.generateXPath(description, organization.getUuid(), userUuid);

      String aiSummary = "This rule detects: " + description;
      writeResponse(response, new GenerateXPathResult(generatedXPath, description, "COMPLETED", aiSummary));
    }
  }

  private static void writeResponse(Response response, GenerateXPathResult result) throws IOException {
    String json = new GsonBuilder().create().toJson(result);
    response.stream().setStatus(HTTP_OK);
    response.stream().setMediaType(MediaTypes.JSON);
    response.stream().output().write(json.getBytes(StandardCharsets.UTF_8));
    response.stream().output().flush();
  }

  private record GenerateXPathResult(
    @SerializedName("generatedXPath") String generatedXPath,
    @SerializedName("description") String description,
    @SerializedName("status") String status,
    @SerializedName("aiSummary") String aiSummary) {
  }
}
