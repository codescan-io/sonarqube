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
package org.sonar.db.organization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class IpAllowlistDaoIT {

  @RegisterExtension
  private final DbTester db = DbTester.create(System2.INSTANCE);

  private final IpAllowlistDao underTest = new IpAllowlistDao();

  @Test
  void selectEnabledRanges_returns_ranges_of_enabled_orgs_only() {
    insertOrg("org-on", true, false);
    insertOrg("org-admins", true, true);
    insertOrg("org-off", false, false);
    insertRange("org-on", "203.0.113.0/24");
    insertRange("org-on", "2001:db8::/32");
    insertRange("org-admins", "198.51.100.7/32");
    insertRange("org-off", "192.0.2.0/24");

    assertThat(underTest.selectEnabledRanges(db.getSession()))
      .extracting(IpAllowlistRangeDto::getOrganizationUuid, IpAllowlistRangeDto::isAppliesToAdmins, IpAllowlistRangeDto::getCidr)
      .containsExactlyInAnyOrder(
        tuple("org-on", false, "203.0.113.0/24"),
        tuple("org-on", false, "2001:db8::/32"),
        tuple("org-admins", true, "198.51.100.7/32"));
  }

  @Test
  void selectEnabledRanges_returns_enabled_org_without_ranges_with_null_cidr() {
    insertOrg("org-empty", true, false);

    assertThat(underTest.selectEnabledRanges(db.getSession()))
      .extracting(IpAllowlistRangeDto::getOrganizationUuid, IpAllowlistRangeDto::getCidr)
      .containsExactly(tuple("org-empty", null));
  }

  @Test
  void selectEnabledRanges_returns_nothing_when_no_org_has_the_allowlist_on() {
    insertOrg("org-off", false, false);
    insertRange("org-off", "192.0.2.0/24");

    assertThat(underTest.selectEnabledRanges(db.getSession())).isEmpty();
  }

  @Test
  void selectCidrsByOrganizationUuid_returns_only_that_org() {
    insertOrg("org-a", true, false);
    insertOrg("org-b", true, false);
    insertRange("org-a", "203.0.113.0/24");
    insertRange("org-b", "192.0.2.0/24");

    assertThat(underTest.selectCidrsByOrganizationUuid(db.getSession(), "org-a")).containsExactly("203.0.113.0/24");
  }

  @Test
  void organization_dto_exposes_allowlist_flags() {
    insertOrg("org-on", true, true);

    OrganizationDto org = db.getDbClient().organizationDao().selectByUuid(db.getSession(), "org-on").orElseThrow();

    assertThat(org.isIpAllowlistEnabled()).isTrue();
    assertThat(org.isIpAllowlistAppliesToAdmins()).isTrue();
  }

  @Test
  void organization_dto_flags_default_to_false() {
    db.executeInsert("organizations",
      "uuid", "org-plain", "kee", "org-plain", "name", "org-plain", "default_quality_gate_uuid", "qg",
      "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L);

    OrganizationDto org = db.getDbClient().organizationDao().selectByUuid(db.getSession(), "org-plain").orElseThrow();

    assertThat(org.isIpAllowlistEnabled()).isFalse();
    assertThat(org.isIpAllowlistAppliesToAdmins()).isFalse();
  }

  @Test
  void organization_members_table_has_the_type_column_the_mapper_reads() {
    db.executeInsert("organization_members", "organization_uuid", "org-a", "user_uuid", "user-1", "type", "STANDARD");

    assertThat(db.getDbClient().organizationMemberDao().selectOrganizationUuidsByUserUuidAndType(db.getSession(), "user-1", "STANDARD"))
      .containsExactly("org-a");
  }

  private void insertOrg(String uuid, boolean enabled, boolean appliesToAdmins) {
    db.executeInsert("organizations",
      "uuid", uuid, "kee", uuid, "name", uuid, "default_quality_gate_uuid", "qg",
      "new_project_private", false, "subscription", "FREE", "created_at", 1L, "updated_at", 1L,
      "ip_allowlist_enabled", enabled, "ip_allowlist_applies_to_admins", appliesToAdmins);
  }

  private void insertRange(String orgUuid, String cidr) {
    db.executeInsert("cs_ip_allowlist_range",
      "uuid", orgUuid + cidr, "organization_uuid", orgUuid, "cidr", cidr, "description", "test range", "created_at", 1L);
  }
}
