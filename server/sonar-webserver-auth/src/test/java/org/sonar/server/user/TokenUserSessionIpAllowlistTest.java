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
package org.sonar.server.user;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.TokenType;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTokenDto;
import org.sonar.server.organization.OrganizationIpAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A project analysis token keeps its own project without the organization membership check, so the IP allowlist
 * has to be applied there separately. TokenUserSessionIT does not compile, hence a separate class.
 */
public class TokenUserSessionIpAllowlistTest {

  private static final String ORG = "org-uuid";
  private static final String OUTSIDE_IP = "192.0.2.1";

  private final OrganizationIpAccess ipAccess = mock(OrganizationIpAccess.class);
  private final ProjectDto project = new ProjectDto().setUuid("project-uuid").setOrganizationUuid(ORG);

  @Test
  public void project_analysis_token_loses_its_project_when_the_organization_blocks_the_request() {
    when(ipAccess.isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class))).thenReturn(true);

    TokenUserSession session = projectAnalysisTokenSession();

    assertThat(session.keepAuthorizedEntities(UserRole.SCAN, List.of(project))).isEmpty();
    assertThat(session.keepAuthorizedEntities(UserRole.USER, List.of(project))).isEmpty();
  }

  @Test
  public void project_analysis_token_keeps_its_project_when_the_organization_lets_the_request_in() {
    when(ipAccess.isBlocked(eq(ORG), eq(OUTSIDE_IP), any(BooleanSupplier.class))).thenReturn(false);

    TokenUserSession session = projectAnalysisTokenSession();

    assertThat(session.keepAuthorizedEntities(UserRole.SCAN, List.of(project))).containsExactly(project);
  }

  private TokenUserSession projectAnalysisTokenSession() {
    UserDto user = new UserDto().setUuid("user-uuid").setLogin("analyst").setActive(true);
    UserTokenDto token = new UserTokenDto().setType(TokenType.PROJECT_ANALYSIS_TOKEN.name()).setProjectUuid(project.getUuid());
    TokenUserSession session = new TokenUserSession(mock(DbClient.class), user, token);
    session.withIpAllowlist(ipAccess, OUTSIDE_IP, null);
    return session;
  }
}
