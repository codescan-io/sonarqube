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
package org.sonar.server.analytics.ws;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.measure.index.ProjectMeasuresDoc;
import org.sonar.server.measure.index.ProjectMeasuresIndex;
import org.sonar.server.measure.index.ProjectMeasuresIndexer;
import org.sonar.server.permission.index.IndexPermissions;
import org.sonar.server.permission.index.PermissionIndexerTester;
import org.sonar.server.permission.index.WebAuthorizationTypeSupport;
import org.sonar.server.user.UserSession;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.component.ComponentQualifiers.PROJECT;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.measure.index.ProjectMeasuresIndexDefinition.TYPE_PROJECT_MEASURES;
import static org.sonar.test.JsonAssert.assertJson;

class ProjectQualityGatesActionIT {

  private static final String USER_UUID = "analytics-admin-uuid";

  @RegisterExtension
  public final DbTester db = DbTester.create();
  @RegisterExtension
  public final EsTester es = EsTester.create();

  private final UserSession userSession = mock(UserSession.class);
  private final AnalyticsWsTesting testing = new AnalyticsWsTesting(db);
  private final PermissionIndexerTester permissionIndexer = new PermissionIndexerTester(es, new ProjectMeasuresIndexer(null, es.client()));
  private final ProjectMeasuresIndex index = new ProjectMeasuresIndex(es.client(), new WebAuthorizationTypeSupport(userSession), System2.INSTANCE);
  private final WsActionTester ws = new WsActionTester(
    new ProjectQualityGatesAction(db.getDbClient(), index, new AnalyticsWsSupport(db.getDbClient(), userSession)));

  private OrganizationDto organization;

  @BeforeEach
  void setUp() {
    organization = testing.insertOrganization("analytics-org");
    when(userSession.getUuid()).thenReturn(USER_UUID);
    when(userSession.getGroups()).thenReturn(List.of());
  }

  @Test
  void definition() {
    WebService.Action def = ws.getDef();

    assertThat(def.key()).isEqualTo("project_quality_gates");
    assertThat(def.isInternal()).isTrue();
    assertThat(def.responseExampleAsString()).isNotEmpty();
    assertThat(def.params()).extracting(WebService.Param::key).containsOnly("organization");
  }

  @Test
  void quality_gate_status_of_every_browsable_project_of_the_organization() {
    index(doc(organization, "passing", "OK"), true);
    index(doc(organization, "failing", "ERROR"), true);
    index(doc(organization, "never-analyzed", null), true);
    index(doc(organization, "not-browsable", "ERROR"), false);
    index(doc(testing.insertOrganization("analytics-other-org"), "other-organization", "OK"), true);

    String json = call().execute().getInput();

    assertJson(json).isSimilarTo("""
      {"projects": [{"key": "failing", "status": "ERROR"}, {"key": "passing", "status": "OK"}]}
      """);
    assertThat(json).doesNotContain("never-analyzed", "not-browsable", "other-organization");
  }

  @Test
  void fail_when_not_organization_administrator() {
    doThrow(new ForbiddenException("Insufficient privileges")).when(userSession).checkPermission(eq(ADMINISTER), any(OrganizationDto.class));
    TestRequest request = call();

    assertThatThrownBy(request::execute).isInstanceOf(ForbiddenException.class);
  }

  private static ProjectMeasuresDoc doc(OrganizationDto organization, String key, String qualityGateStatus) {
    return new ProjectMeasuresDoc()
      .setId(Uuids.createFast())
      .setOrganizationUuid(organization.getUuid())
      .setKey(key)
      .setName(key)
      .setQualifier(PROJECT)
      .setQualityGateStatus(qualityGateStatus);
  }

  private void index(ProjectMeasuresDoc doc, boolean browsable) {
    es.putDocuments(TYPE_PROJECT_MEASURES, doc);
    IndexPermissions permissions = new IndexPermissions(doc.getId(), PROJECT);
    if (browsable) {
      permissions.addUserUuid(USER_UUID);
    }
    permissionIndexer.allow(permissions);
  }

  private TestRequest call() {
    return ws.newRequest().setParam("organization", organization.getKey());
  }
}
