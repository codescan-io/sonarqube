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
package org.sonar.server.permission.index;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.GroupTesting;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserTesting;
import org.sonar.server.organization.IpAllowlistUnavailableException;
import org.sonar.server.organization.OrganizationIpAccessUnavailableException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.user.UserSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.test.JsonAssert.assertJson;

public class WebAuthorizationTypeSupportTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private WebAuthorizationTypeSupport underTest = new WebAuthorizationTypeSupport(userSession);

  @Test
  public void createQueryFilter_sets_filter_on_anyone_group_if_user_is_anonymous() {
    userSession.anonymous();

    HasParentQueryBuilder filter = (HasParentQueryBuilder) underTest.createQueryFilter("organization");

    assertJson(filter.toString()).isSimilarTo("{" +
      "  \"has_parent\" : {" +
      "    \"query\" : {" +
      "      \"bool\" : {" +
      "        \"filter\" : [{" +
      "          \"bool\" : {" +
      "            \"should\" : [{" +
      "              \"term\" : {" +
      "                \"auth_allowAnyone\" : {\"value\": true}" +
      "              }" +
      "            }]" +
      "          }" +
      "        }]" +
      "      }" +
      "    }," +
      "    \"parent_type\" : \"auth\"" +
      "  }" +
      "}");
  }

  @Test
  public void createQueryFilter_sets_filter_on_anyone_and_user_id_if_user_is_logged_in_but_has_no_groups() {
    UserDto userDto = UserTesting.newUserDto();
    userSession.logIn(userDto);

    HasParentQueryBuilder filter = (HasParentQueryBuilder) underTest.createQueryFilter("organization");

    assertJson(filter.toString()).isSimilarTo("{" +
      "  \"has_parent\": {" +
      "    \"query\": {" +
      "      \"bool\": {" +
      "        \"filter\": [{" +
      "          \"bool\": {" +
      "            \"should\": [" +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_allowAnyone\": {\"value\": true}" +
      "                }" +
      "              }," +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_userIds\": {\"value\": \"" + userDto.getUuid() + "\"}" +
      "                }" +
      "              }" +
      "            ]" +
      "          }" +
      "        }]" +
      "      }" +
      "    }," +
      "    \"parent_type\": \"auth\"" +
      "  }" +
      "}");
  }

  @Test
  public void createQueryFilter_sets_filter_on_anyone_and_user_id_and_group_ids_if_user_is_logged_in_and_has_groups() {
    GroupDto group1 = GroupTesting.newGroupDto().setUuid("10");
    GroupDto group2 = GroupTesting.newGroupDto().setUuid("11");
    UserDto userDto = UserTesting.newUserDto();
    userSession.logIn(userDto).setGroups(group1, group2);

    HasParentQueryBuilder filter = (HasParentQueryBuilder) underTest.createQueryFilter("organization");

    assertJson(filter.toString()).isSimilarTo("{" +
      "  \"has_parent\": {" +
      "    \"query\": {" +
      "      \"bool\": {" +
      "        \"filter\": [{" +
      "          \"bool\": {" +
      "            \"should\": [" +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_allowAnyone\": {\"value\": true}" +
      "                }" +
      "              }," +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_userIds\": {\"value\": \"" + userDto.getUuid() + "\"}" +
      "                }" +
      "              }," +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_groupIds\": {\"value\": \"10\"}" +
      "                }" +
      "              }," +
      "              {" +
      "                \"term\": {" +
      "                  \"auth_groupIds\": {\"value\": \"11\"}" +
      "                }" +
      "              }" +
      "            ]" +
      "          }" +
      "        }]" +
      "      }" +
      "    }," +
      "    \"parent_type\": \"auth\"" +
      "  }" +
      "}");
  }

  @Test
  public void createQueryFilter_leaves_out_organizations_blocked_by_their_ip_allowlist() {
    UserDto userDto = UserTesting.newUserDto();
    userSession.logIn(userDto).setIpBlockedOrganizationUuids("org-blocked-1", "org-blocked-2");

    QueryBuilder filter = underTest.createQueryFilter("organization");

    assertJson(filter.toString()).isSimilarTo("{" +
      "  \"bool\": {" +
      "    \"filter\": [{" +
      "      \"has_parent\": {" +
      "        \"query\": {\"bool\": {\"filter\": [{\"bool\": {\"should\": [" +
      "          {\"term\": {\"auth_allowAnyone\": {\"value\": true}}}," +
      "          {\"term\": {\"auth_userIds\": {\"value\": \"" + userDto.getUuid() + "\"}}}" +
      "        ]}}]}}," +
      "        \"parent_type\": \"auth\"" +
      "      }" +
      "    }]," +
      "    \"must_not\": [{\"terms\": {\"organization\": [\"org-blocked-1\", \"org-blocked-2\"]}}]" +
      "  }" +
      "}");
  }

  @Test
  public void createQueryFilter_leaves_out_blocked_organizations_on_the_given_field_for_anonymous() {
    userSession.anonymous().setIpBlockedOrganizationUuids("org-blocked");

    QueryBuilder filter = underTest.createQueryFilter("organizationUuid");

    assertJson(filter.toString()).isSimilarTo("{" +
      "  \"bool\": {" +
      "    \"filter\": [{\"has_parent\": {\"parent_type\": \"auth\"}}]," +
      "    \"must_not\": [{\"terms\": {\"organizationUuid\": [\"org-blocked\"]}}]" +
      "  }" +
      "}");
    assertThat(filter.toString()).doesNotContain("\"organization\"");
  }

  @Test
  public void createQueryFilter_is_only_the_authorization_filter_when_no_organization_is_blocked() {
    userSession.logIn(UserTesting.newUserDto());

    assertThat(underTest.createQueryFilter("organization")).isInstanceOf(HasParentQueryBuilder.class);
  }

  @Test
  public void createQueryFilter_fails_when_the_ip_allowlist_cannot_be_read() {
    UserSession session = mock(UserSession.class);
    IpAllowlistUnavailableException unavailable = new IpAllowlistUnavailableException(
      new OrganizationIpAccessUnavailableException("database down", null));
    when(session.getIpBlockedOrganizationUuids()).thenThrow(unavailable);
    WebAuthorizationTypeSupport underTestWithFailingAllowlist = new WebAuthorizationTypeSupport(session);

    assertThatThrownBy(() -> underTestWithFailingAllowlist.createQueryFilter("organization"))
      .isSameAs(unavailable);
  }
}
