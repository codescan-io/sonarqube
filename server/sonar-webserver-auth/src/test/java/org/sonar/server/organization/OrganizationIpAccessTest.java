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
package org.sonar.server.organization;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.impl.utils.TestSystem2;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.IpAllowlistDao;
import org.sonar.db.organization.IpAllowlistRangeDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrganizationIpAccessTest {

  @Rule
  public LogTester logTester = new LogTester();

  private final DbClient dbClient = mock(DbClient.class);
  private final IpAllowlistDao dao = mock(IpAllowlistDao.class);
  private final TestSystem2 system2 = new TestSystem2().setNow(1_000_000L);
  private final MapSettings settings = new MapSettings();
  private OrganizationIpAccess underTest;

  @Before
  public void setUp() {
    logTester.setLevel(Level.INFO);
    when(dbClient.openSession(false)).thenReturn(mock(DbSession.class));
    when(dbClient.ipAllowlistDao()).thenReturn(dao);
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-a", false, "203.0.113.0/24"),
      range("org-admins", true, "198.51.100.7/32"),
      range("org-empty", false, null)));
    settings.setProperty("sonar.codescan.internalCidr", "10.0.0.0/16,10.1.0.0/16");
    underTest = new OrganizationIpAccess(dbClient, system2, settings.asConfig());
  }

  @Test
  public void org_without_allowlist_is_never_blocked() {
    assertThat(underTest.isBlocked("org-other", "192.0.2.1", false)).isFalse();
    assertThat(underTest.isBlocked("org-other", null, false)).isFalse();
  }

  @Test
  public void ip_inside_list_is_allowed_and_outside_is_blocked() {
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", false)).isTrue();
  }

  @Test
  public void missing_or_unparseable_client_ip_is_blocked() {
    assertThat(underTest.isBlocked("org-a", null, false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "", false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "example.com", false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "not an ip", false)).isTrue();
  }

  @Test
  public void ipv4_mapped_ipv6_client_is_checked_as_ipv4() {
    assertThat(underTest.isBlocked("org-a", "::ffff:203.0.113.9", false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "::ffff:192.0.2.1", false)).isTrue();
  }

  @Test
  public void upper_case_ipv6_is_checked_like_lower_case() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-v6", false, "2001:db8::/32"),
      range("org-v6-upper", false, "2001:DB8::/32")));
    OrganizationIpAccess access = new OrganizationIpAccess(dbClient, system2, settings.asConfig());

    assertThat(access.isBlocked("org-v6", "2001:DB8::1", false)).isFalse();
    assertThat(access.isBlocked("org-v6-upper", "2001:db8::1", false)).isFalse();
    assertThat(access.isBlocked("org-v6", "2001:dba::1", false)).isTrue();
  }

  @Test
  public void admins_are_exempt_unless_applies_to_admins() {
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", true)).isFalse();
    assertThat(underTest.isBlocked("org-admins", "192.0.2.1", true)).isTrue();
  }

  @Test
  public void enabled_org_with_no_range_blocks_everyone() {
    assertThat(underTest.isBlocked("org-empty", "203.0.113.9", false)).isTrue();
  }

  @Test
  public void unparseable_stored_range_is_skipped_without_throwing() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-mixed", false, "not-an-ip"),
      range("org-mixed", false, "203.0.113.0/24"),
      range("org-mixed", false, "203.0.113.77/24"),
      range("org-only-bad", false, "example.com/24"),
      range("org-ok", false, "198.51.100.0/24")));
    OrganizationIpAccess access = new OrganizationIpAccess(dbClient, system2, settings.asConfig());

    assertThat(access.isBlocked("org-mixed", "203.0.113.9", false)).isFalse();
    assertThat(access.isBlocked("org-mixed", "192.0.2.1", false)).isTrue();
    // nothing valid is left, so the list lets nobody in: skipping never widens access
    assertThat(access.isBlocked("org-only-bad", "203.0.113.9", false)).isTrue();
    assertThat(access.isBlocked("org-ok", "198.51.100.5", false)).isFalse();
    assertThat(logTester.logs(Level.WARN)).anyMatch(l -> l.contains("not-an-ip") && l.contains("org-mixed"));
  }

  @Test
  public void unparseable_stored_range_is_logged_once_not_on_every_reload() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-mixed", false, "not-an-ip"),
      range("org-mixed", false, "203.0.113.0/24")));
    OrganizationIpAccess access = new OrganizationIpAccess(dbClient, system2, settings.asConfig());
    logTester.clear();

    access.isBlocked("org-mixed", "203.0.113.9", false);
    system2.setNow(1_000_000L + 30_000L);
    access.isBlocked("org-mixed", "203.0.113.9", false);
    system2.setNow(1_000_000L + 60_000L);
    access.isBlocked("org-mixed", "203.0.113.9", false);

    verify(dao, times(3)).selectEnabledRanges(any());
    assertThat(logTester.logs(Level.WARN)).filteredOn(l -> l.contains("not-an-ip")).hasSize(1);
  }

  @Test
  public void blockedOrganizationUuids_lists_orgs_whose_list_misses_the_ip() {
    assertThat(underTest.blockedOrganizationUuids("203.0.113.9")).containsExactlyInAnyOrder("org-admins", "org-empty");
    assertThat(underTest.blockedOrganizationUuids("198.51.100.7")).containsExactlyInAnyOrder("org-a", "org-empty");
  }

  @Test
  public void blockedOrganizationUuids_lists_every_enabled_org_when_the_ip_is_missing() {
    assertThat(underTest.blockedOrganizationUuids(null)).containsExactlyInAnyOrder("org-a", "org-admins", "org-empty");
    assertThat(underTest.blockedOrganizationUuids("garbage")).containsExactlyInAnyOrder("org-a", "org-admins", "org-empty");
  }

  @Test
  public void snapshot_is_reused_for_30_seconds_then_reloaded() {
    underTest.isBlocked("org-a", "192.0.2.1", false);
    system2.setNow(1_000_000L + 29_999L);
    underTest.isBlocked("org-a", "192.0.2.1", false);
    verify(dao, times(1)).selectEnabledRanges(any());

    system2.setNow(1_000_000L + 30_000L);
    underTest.isBlocked("org-a", "192.0.2.1", false);
    verify(dao, times(2)).selectEnabledRanges(any());
  }

  @Test
  public void failed_load_is_thrown_to_the_caller_and_not_cached() {
    when(dao.selectEnabledRanges(any())).thenThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", false)).isInstanceOf(IllegalStateException.class);

    doReturn(List.of(range("org-a", false, "203.0.113.0/24"))).when(dao).selectEnabledRanges(any());
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", false)).isTrue();
  }

  @Test
  public void database_session_is_always_closed() {
    DbSession session = mock(DbSession.class);
    when(dbClient.openSession(false)).thenReturn(session);

    underTest.isBlocked("org-a", "192.0.2.1", false);

    verify(session).close();
  }

  // ---- internal service address (same rules as codescanng's ServiceLoginDetector) ----

  @Test
  public void internal_service_address_uses_configured_ranges() {
    assertThat(underTest.isInternalServiceAddress("10.1.4.5")).isTrue();
    assertThat(underTest.isInternalServiceAddress("10.0.255.255")).isTrue();
    assertThat(underTest.isInternalServiceAddress("10.2.0.1")).isFalse();
    assertThat(underTest.isInternalServiceAddress(null)).isFalse();
    assertThat(underTest.isInternalServiceAddress("")).isFalse();
  }

  @Test
  public void internal_range_with_host_bits_is_accepted_like_codescanng_does() {
    OrganizationIpAccess access = accessWithInternal("172.31.5.9/16");

    assertThat(access.isInternalServiceAddress("172.31.200.1")).isTrue();
    assertThat(access.isInternalServiceAddress("172.32.0.1")).isFalse();
  }

  @Test
  public void ipv6_address_is_never_inside_an_ipv4_internal_range() {
    OrganizationIpAccess access = accessWithInternal("172.31.0.0/16");

    // same number as 172.31.4.5, but it is an IPv6 address
    assertThat(access.isInternalServiceAddress("::ac1f:405")).isFalse();
    assertThat(access.isInternalServiceAddress("172.31.4.5")).isTrue();
  }

  @Test
  public void ipv4_address_is_never_inside_an_ipv6_internal_range() {
    OrganizationIpAccess access = accessWithInternal("fd00::/8");

    assertThat(access.isInternalServiceAddress("fd00::5")).isTrue();
    assertThat(access.isInternalServiceAddress("0.0.0.5")).isFalse();
    assertThat(access.isInternalServiceAddress("10.0.0.5")).isFalse();
  }

  @Test
  public void ipv4_mapped_ipv6_address_is_internal_when_its_ipv4_form_is() {
    OrganizationIpAccess access = accessWithInternal("172.31.0.0/16");

    assertThat(access.isInternalServiceAddress("::ffff:172.31.4.5")).isTrue();
    assertThat(access.isInternalServiceAddress("::ffff:203.0.113.9")).isFalse();
  }

  @Test
  public void address_with_an_ipv6_zone_is_not_internal() {
    OrganizationIpAccess access = accessWithInternal("fe80::/10");

    assertThat(access.isInternalServiceAddress("fe80::1")).isTrue();
    assertThat(access.isInternalServiceAddress("fe80::1%eth0")).isFalse();
    assertThat(access.isInternalServiceAddress("fe80::1%1")).isFalse();
  }

  @Test
  public void host_name_is_never_resolved_to_decide_internal() {
    // "localhost" resolves to 127.0.0.1, which this range holds. A lookup would make this true.
    OrganizationIpAccess access = accessWithInternal("127.0.0.0/8");

    assertThat(access.isInternalServiceAddress("localhost")).isFalse();
    assertThat(access.isInternalServiceAddress("127.0.0.1")).isTrue();
  }

  @Test
  public void unparseable_address_is_not_internal() {
    OrganizationIpAccess access = accessWithInternal("0.0.0.0/0");

    for (String bad : new String[] {"garbage", "256.0.0.1", "1.2.3", "010.0.0.1", " 10.0.0.1", "10.0.0.1 ", "10.0.0.1,10.0.0.2"}) {
      assertThat(access.isInternalServiceAddress(bad)).as(bad).isFalse();
    }
  }

  @Test
  public void no_internal_ranges_configured_means_nothing_is_internal() {
    OrganizationIpAccess noInternal = new OrganizationIpAccess(dbClient, system2, new MapSettings().asConfig());

    assertThat(noInternal.isInternalServiceAddress("10.0.0.1")).isFalse();
  }

  @Test
  public void empty_internal_setting_means_nothing_is_internal_and_is_logged_once() {
    logTester.clear();
    OrganizationIpAccess access = accessWithInternal("  ");

    assertThat(access.isInternalServiceAddress("10.0.0.1")).isFalse();
    assertThat(access.isInternalServiceAddress("10.0.0.2")).isFalse();
    assertThat(access.isInternalServiceAddress("10.0.0.3")).isFalse();

    assertThat(logTester.logs()).filteredOn(l -> l.contains(OrganizationIpAccess.INTERNAL_CIDR_PROPERTY)).hasSize(1);
  }

  @Test
  public void bad_internal_setting_means_nothing_is_internal_and_is_logged_once() {
    for (String bad : new String[] {"garbage", "10.0.0.0/33", "localhost/8", "10.0.0.1", "10.0.0.0/16,garbage", "fe80::1%eth0/64"}) {
      logTester.clear();
      OrganizationIpAccess access = accessWithInternal(bad);

      assertThat(access.isInternalServiceAddress("10.0.0.1")).as(bad).isFalse();
      assertThat(access.isInternalServiceAddress("10.0.5.5")).as(bad).isFalse();
      assertThat(access.isInternalServiceAddress("10.0.0.1")).as(bad).isFalse();

      assertThat(logTester.logs(Level.WARN)).as(bad)
        .filteredOn(l -> l.contains(OrganizationIpAccess.INTERNAL_CIDR_PROPERTY)).hasSize(1);
    }
  }

  @Test
  public void bad_internal_setting_does_not_leak_into_the_log_per_request() {
    logTester.clear();
    OrganizationIpAccess access = accessWithInternal("garbage");
    int logsAfterStartup = logTester.logs().size();

    for (int i = 0; i < 50; i++) {
      access.isInternalServiceAddress("10.0.0." + i);
    }

    assertThat(logTester.logs()).hasSize(logsAfterStartup);
  }

  // ---- blocked log line ----

  @Test
  public void blocked_log_line_is_written_at_most_once_per_org_and_ip_per_minute() {
    assertThat(underTest.shouldLogBlock("acme", "192.0.2.1")).isTrue();
    assertThat(underTest.shouldLogBlock("acme", "192.0.2.1")).isFalse();
    assertThat(underTest.shouldLogBlock("acme", "198.51.100.1")).isTrue();
    assertThat(underTest.shouldLogBlock("other", "192.0.2.1")).isTrue();

    system2.setNow(1_000_000L + 59_999L);
    assertThat(underTest.shouldLogBlock("acme", "192.0.2.1")).isFalse();
    system2.setNow(1_000_000L + 60_000L);
    assertThat(underTest.shouldLogBlock("acme", "192.0.2.1")).isTrue();
  }

  @Test
  public void logBlocked_writes_the_same_line_as_codescanng() {
    logTester.clear();

    underTest.logBlocked("acme", "192.0.2.1", "jdoe");
    underTest.logBlocked("acme", "192.0.2.1", "jdoe");
    underTest.logBlocked("acme", "198.51.100.1", null);

    assertThat(logTester.logs(Level.WARN)).containsExactly(
      "Request blocked by IP allowlist: organization=acme, ip=192.0.2.1, user=jdoe",
      "Request blocked by IP allowlist: organization=acme, ip=198.51.100.1, user=anonymous");
  }

  @Test
  public void logBlocked_cannot_be_used_to_forge_log_lines() {
    logTester.clear();

    underTest.logBlocked("acme", "1.2.3.4\nERROR forged", "jdoe");

    assertThat(logTester.logs(Level.WARN)).hasSize(1);
    assertThat(logTester.logs(Level.WARN).get(0)).doesNotContain("\n");
  }

  @Test
  public void old_log_entries_are_dropped_once_the_tracker_is_full() {
    for (int i = 0; i <= OrganizationIpAccess.MAX_TRACKED_BLOCKS; i++) {
      underTest.shouldLogBlock("org-" + i, "192.0.2.1");
    }
    system2.setNow(1_000_000L + 60_000L);

    underTest.shouldLogBlock("acme", "192.0.2.1");

    assertThat(underTest.trackedBlockCount()).isEqualTo(1);
  }

  @Test
  public void tracker_stops_growing_when_it_is_full_of_recent_entries() {
    for (int i = 0; i <= OrganizationIpAccess.MAX_TRACKED_BLOCKS; i++) {
      underTest.shouldLogBlock("org-" + i, "192.0.2.1");
    }
    int full = underTest.trackedBlockCount();

    assertThat(underTest.shouldLogBlock("new-org", "192.0.2.1")).isFalse();
    assertThat(underTest.trackedBlockCount()).isEqualTo(full);
  }

  private OrganizationIpAccess accessWithInternal(String cidrs) {
    MapSettings s = new MapSettings();
    s.setProperty("sonar.codescan.internalCidr", cidrs);
    return new OrganizationIpAccess(dbClient, system2, s.asConfig());
  }

  private static IpAllowlistRangeDto range(String org, boolean appliesToAdmins, String cidr) {
    return new IpAllowlistRangeDto().setOrganizationUuid(org).setAppliesToAdmins(appliesToAdmins).setCidr(cidr);
  }
}
