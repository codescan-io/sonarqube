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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.config.Configuration;
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

  private static final long WALL_NOW = 1_700_000_000_000L;

  @Rule
  public LogTester logTester = new LogTester();

  private final DbClient dbClient = mock(DbClient.class);
  private final IpAllowlistDao dao = mock(IpAllowlistDao.class);
  /** Never goes backwards; every age and deadline is measured on it. */
  private final AtomicLong clock = new AtomicLong(1_000_000L);
  /** Can be set back or forward, as an administrator or NTP does; nothing may depend on it. */
  private final TestSystem2 wallClock = new TestSystem2().setNow(WALL_NOW);
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
    underTest = access(settings.asConfig());
  }

  @Test
  public void org_without_allowlist_is_never_blocked() {
    assertThat(underTest.isBlocked("org-other", "192.0.2.1", () -> false)).isFalse();
    assertThat(underTest.isBlocked("org-other", null, () -> false)).isFalse();
  }

  @Test
  public void ip_inside_list_is_allowed_and_outside_is_blocked() {
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", () -> false)).isTrue();
  }

  @Test
  public void missing_or_unparseable_client_ip_is_blocked() {
    assertThat(underTest.isBlocked("org-a", null, () -> false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "", () -> false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "example.com", () -> false)).isTrue();
    assertThat(underTest.isBlocked("org-a", "not an ip", () -> false)).isTrue();
  }

  @Test
  public void ipv4_mapped_ipv6_client_is_checked_as_ipv4() {
    assertThat(underTest.isBlocked("org-a", "::ffff:203.0.113.9", () -> false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "::ffff:192.0.2.1", () -> false)).isTrue();
  }

  @Test
  public void upper_case_ipv6_is_checked_like_lower_case() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-v6", false, "2001:db8::/32"),
      range("org-v6-upper", false, "2001:DB8::/32")));
    OrganizationIpAccess access = access(settings.asConfig());

    assertThat(access.isBlocked("org-v6", "2001:DB8::1", () -> false)).isFalse();
    assertThat(access.isBlocked("org-v6-upper", "2001:db8::1", () -> false)).isFalse();
    assertThat(access.isBlocked("org-v6", "2001:dba::1", () -> false)).isTrue();
  }

  @Test
  public void admins_are_exempt_unless_applies_to_admins() {
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", () -> true)).isFalse();
    assertThat(underTest.isBlocked("org-admins", "192.0.2.1", () -> true)).isTrue();
  }

  @Test
  public void enabled_org_with_no_range_blocks_everyone() {
    assertThat(underTest.isBlocked("org-empty", "203.0.113.9", () -> false)).isTrue();
  }

  @Test
  public void unparseable_stored_range_is_skipped_without_throwing() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-mixed", false, "not-an-ip"),
      range("org-mixed", false, "203.0.113.0/24"),
      range("org-mixed", false, "203.0.113.77/24"),
      range("org-only-bad", false, "example.com/24"),
      range("org-ok", false, "198.51.100.0/24")));
    OrganizationIpAccess access = access(settings.asConfig());

    assertThat(access.isBlocked("org-mixed", "203.0.113.9", () -> false)).isFalse();
    assertThat(access.isBlocked("org-mixed", "192.0.2.1", () -> false)).isTrue();
    // nothing valid is left, so the list lets nobody in: skipping never widens access
    assertThat(access.isBlocked("org-only-bad", "203.0.113.9", () -> false)).isTrue();
    assertThat(access.isBlocked("org-ok", "198.51.100.5", () -> false)).isFalse();
    assertThat(logTester.logs(Level.WARN)).anyMatch(l -> l.contains("not-an-ip") && l.contains("org-mixed"));
  }

  @Test
  public void unparseable_stored_range_is_logged_once_not_on_every_reload() {
    when(dao.selectEnabledRanges(any())).thenReturn(List.of(
      range("org-mixed", false, "not-an-ip"),
      range("org-mixed", false, "203.0.113.0/24")));
    OrganizationIpAccess access = access(settings.asConfig());
    logTester.clear();

    access.isBlocked("org-mixed", "203.0.113.9", () -> false);
    clock.set(1_000_000L + 30_000L);
    access.isBlocked("org-mixed", "203.0.113.9", () -> false);
    clock.set(1_000_000L + 60_000L);
    access.isBlocked("org-mixed", "203.0.113.9", () -> false);

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
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    clock.set(1_000_000L + 29_999L);
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    verify(dao, times(1)).selectEnabledRanges(any());

    clock.set(1_000_000L + 30_000L);
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    verify(dao, times(2)).selectEnabledRanges(any());
  }

  @Test
  public void failed_load_is_thrown_to_the_caller_and_not_cached() {
    when(dao.selectEnabledRanges(any())).thenThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isInstanceOf(OrganizationIpAccessUnavailableException.class);

    doReturn(List.of(range("org-a", false, "203.0.113.0/24"))).when(dao).selectEnabledRanges(any());
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", () -> false)).isTrue();
  }

  @Test
  public void database_session_is_always_closed() {
    DbSession session = mock(DbSession.class);
    when(dbClient.openSession(false)).thenReturn(session);

    underTest.isBlocked("org-a", "192.0.2.1", () -> false);

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
    OrganizationIpAccess noInternal = access(new MapSettings().asConfig());

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

    clock.set(1_000_000L + 59_999L);
    assertThat(underTest.shouldLogBlock("acme", "192.0.2.1")).isFalse();
    clock.set(1_000_000L + 60_000L);
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
    clock.set(1_000_000L + 60_000L);

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

  // ---- null organization, clock, and a slow or failing database ----

  @Test
  public void null_organization_uuid_is_never_blocked() {
    assertThat(underTest.isBlocked(null, "192.0.2.1", () -> false)).isFalse();
    assertThat(underTest.isBlocked(null, null, () -> true)).isFalse();
  }

  @Test
  public void logBlocked_accepts_a_null_organization_key() {
    logTester.clear();

    underTest.logBlocked(null, "192.0.2.1", null);

    assertThat(logTester.logs(Level.WARN)).hasSize(1);
  }

  @Test(timeout = 30_000)
  public void older_load_finishing_after_a_newer_one_never_replaces_its_list() throws Exception {
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch olderStarted = new CountDownLatch(1);
    CountDownLatch releaseOlder = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        olderStarted.countDown();
        assertThat(releaseOlder.await(10, TimeUnit.SECONDS)).isTrue();
        return List.of(range("org-a", false, "192.0.2.0/24"));
      }
      return List.of(range("org-a", false, "198.51.100.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      clock.set(1_031_000L);
      Future<Boolean> older = executor.submit(() -> underTest.isBlocked("org-a", "198.51.100.5", () -> false));
      assertThat(olderStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_031_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(underTest.isBlocked("org-a", "198.51.100.5", () -> false)).as("newer load reads the new list").isFalse();

      releaseOlder.countDown();
      older.get(10, TimeUnit.SECONDS);

      assertThat(underTest.isBlocked("org-a", "198.51.100.5", () -> false)).as("the older list does not come back").isFalse();
      verify(dao, times(3)).selectEnabledRanges(any());
    } finally {
      releaseOlder.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void older_load_never_replaces_a_newer_one_that_started_at_the_same_clock_reading() throws Exception {
    // no wait before a takeover, and the clock does not move: both loads start at the same reading
    OrganizationIpAccess access = access(settings.asConfig(), OrganizationIpAccess.LOAD_WAIT_TIMEOUT_MS, 0L);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch olderStarted = new CountDownLatch(1);
    CountDownLatch releaseOlder = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        olderStarted.countDown();
        assertThat(releaseOlder.await(10, TimeUnit.SECONDS)).isTrue();
        return List.of(range("org-a", false, "192.0.2.0/24"));
      }
      return List.of(range("org-a", false, "198.51.100.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> older = executor.submit(() -> access.isBlocked("org-a", "198.51.100.5", () -> false));
      assertThat(olderStarted.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(access.isBlocked("org-a", "198.51.100.5", () -> false)).as("newer load reads the new list").isFalse();

      releaseOlder.countDown();
      older.get(10, TimeUnit.SECONDS);

      assertThat(access.isBlocked("org-a", "198.51.100.5", () -> false)).as("the older list does not come back").isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());
    } finally {
      releaseOlder.countDown();
      executor.shutdownNow();
    }
  }

  // ---- the wall clock is set back or forward ----

  @Test
  public void wall_clock_going_back_or_forward_causes_no_database_read() {
    OrganizationIpAccess access = productionAccess();
    access.isBlocked("org-a", "192.0.2.1", () -> false);

    for (int i = 1; i <= 100; i++) {
      wallClock.setNow(WALL_NOW + (i % 2 == 0 ? 1 : -1) * (3_600_000L + i));
      assertThat(access.isBlocked("org-a", "192.0.2.1", () -> false)).isTrue();
    }

    verify(dao, times(1)).selectEnabledRanges(any());
  }

  @Test
  public void wall_clock_going_back_with_a_failing_database_keeps_answering_from_the_snapshot() {
    OrganizationIpAccess access = productionAccess();
    access.isBlocked("org-a", "192.0.2.1", () -> false);
    when(dao.selectEnabledRanges(any())).thenThrow(new IllegalStateException("db down"));

    wallClock.setNow(WALL_NOW - 3_600_000L);

    assertThat(access.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
    verify(dao, times(1)).selectEnabledRanges(any());
  }

  @Test(timeout = 30_000)
  public void wall_clock_going_back_does_not_make_a_running_load_look_stuck() throws Exception {
    OrganizationIpAccess access = productionAccess();
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      loadStarted.countDown();
      assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();

      wallClock.setNow(WALL_NOW - 500L);
      Future<Boolean> waiter = executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      awaitWaitingThreads(access, 1);
      finishLoad.countDown();

      assertThat(waiter.get(10, TimeUnit.SECONDS)).isFalse();
      verify(dao, times(1)).selectEnabledRanges(any());
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void old_snapshot_is_served_while_another_thread_reloads() throws Exception {
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    clock.set(1_000_000L + 30_000L);
    CountDownLatch reloadStarted = new CountDownLatch(1);
    CountDownLatch finishReload = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      reloadStarted.countDown();
      assertThat(finishReload.await(10, TimeUnit.SECONDS)).isTrue();
      return List.of(range("org-a", false, "198.51.100.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> leader = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(reloadStarted.await(10, TimeUnit.SECONDS)).isTrue();

      // answered at once from the old snapshot, without waiting for the reload or starting a second one
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());

      finishReload.countDown();
      assertThat(leader.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      finishReload.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void old_snapshot_is_served_for_up_to_twice_the_ttl_when_the_reload_fails() {
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    when(dao.selectEnabledRanges(any())).thenThrow(new IllegalStateException("db down"));

    clock.set(1_000_000L + 30_000L);
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", () -> false)).isTrue();
    clock.set(1_000_000L + 59_999L);
    assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();

    clock.set(1_000_000L + 60_000L);
    assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isInstanceOf(OrganizationIpAccessUnavailableException.class);
  }

  @Test
  public void snapshot_that_became_too_old_during_a_slow_failed_reload_is_not_served() {
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      clock.set(1_000_000L + 59_000L + 31_000L);
      throw new IllegalStateException("db timed out");
    });

    clock.set(1_000_000L + 59_000L);

    assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isInstanceOf(OrganizationIpAccessUnavailableException.class);
  }

  @Test
  public void failed_load_without_a_snapshot_throws_the_unavailable_exception_with_the_database_error_as_cause() {
    IllegalStateException dbError = new IllegalStateException("db down");
    when(dao.selectEnabledRanges(any())).thenThrow(dbError);

    assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false))
      .isInstanceOf(OrganizationIpAccessUnavailableException.class)
      .hasCause(dbError);
    assertThatThrownBy(() -> underTest.blockedOrganizationUuids("203.0.113.9"))
      .isInstanceOf(OrganizationIpAccessUnavailableException.class)
      .hasCause(dbError);
  }

  @Test
  public void failed_reload_is_logged_at_most_once_a_minute() {
    underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    when(dao.selectEnabledRanges(any())).thenThrow(new IllegalStateException("db down"));
    logTester.clear();

    clock.set(1_000_000L + 30_000L);
    for (int i = 0; i < 20; i++) {
      underTest.isBlocked("org-a", "192.0.2.1", () -> false);
    }

    assertThat(logTester.logs(Level.WARN)).singleElement().asString().contains("previous");
  }

  @Test(timeout = 30_000)
  public void threads_without_a_snapshot_wait_for_one_load_and_share_its_result() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      loadStarted.countDown();
      assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(3);
    try {
      Future<Boolean> first = executor.submit(() -> underTest.isBlocked("org-a", "192.0.2.1", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();
      Future<Boolean> second = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      Future<Boolean> third = executor.submit(() -> underTest.isBlocked("org-a", "192.0.2.1", () -> false));
      awaitWaitingThreads(underTest, 2);

      finishLoad.countDown();

      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
      assertThat(third.get(10, TimeUnit.SECONDS)).isTrue();
      verify(dao, times(1)).selectEnabledRanges(any());
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void threads_without_a_snapshot_all_fail_when_the_one_load_fails() throws Exception {
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      loadStarted.countDown();
      assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
      throw new IllegalStateException("db down");
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();
      Future<Boolean> second = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      awaitWaitingThreads(underTest, 1);

      finishLoad.countDown();

      assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasRootCauseMessage("db down");
      assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasRootCauseMessage("db down");
      verify(dao, times(1)).selectEnabledRanges(any());
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void error_during_the_load_releases_the_waiting_threads_at_once_and_does_not_block_the_next_load() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        loadStarted.countDown();
        assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
        throw new AssertionError("out of memory, say");
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> leader = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();
      Future<Boolean> waiter = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      awaitWaitingThreads(underTest, 1);

      finishLoad.countDown();

      assertThatThrownBy(() -> leader.get(10, TimeUnit.SECONDS)).hasRootCauseInstanceOf(AssertionError.class);
      assertThatThrownBy(() -> waiter.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasStackTraceContaining("load was aborted");
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      assertThat(calls).hasValue(2);
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void wall_clock_going_back_or_forward_does_not_repeat_the_block_log_line_within_the_minute() {
    OrganizationIpAccess access = productionAccess();
    assertThat(access.shouldLogBlock("org-a", "192.0.2.1")).isTrue();

    wallClock.setNow(WALL_NOW - 3_600_000L);
    assertThat(access.shouldLogBlock("org-a", "192.0.2.1")).isFalse();
    wallClock.setNow(WALL_NOW + 3_600_000L);
    assertThat(access.shouldLogBlock("org-a", "192.0.2.1")).isFalse();
  }

  @Test
  public void wall_clock_going_back_does_not_empty_a_full_tracker() {
    OrganizationIpAccess access = productionAccess();
    for (int i = 0; i <= OrganizationIpAccess.MAX_TRACKED_BLOCKS; i++) {
      access.shouldLogBlock("org-" + i, "192.0.2.1");
    }
    int full = access.trackedBlockCount();

    wallClock.setNow(WALL_NOW - 3_600_000L);

    assertThat(access.shouldLogBlock("new-org", "192.0.2.1")).isFalse();
    assertThat(access.trackedBlockCount()).isEqualTo(full);
  }

  // ---- a load that never returns ----

  @Test(timeout = 30_000)
  public void load_stuck_longer_than_the_takeover_deadline_is_taken_over_by_another_thread() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(10, TimeUnit.SECONDS)).isTrue();
        return List.of(range("org-a", false, "198.51.100.0/24"));
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> stuck = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();

      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());

      releaseStuck.countDown();
      assertThat(stuck.get(10, TimeUnit.SECONDS)).isTrue();
      // the late answer of the stuck load, which read the list earlier, does not replace the newer one
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());
    } finally {
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void load_past_the_wait_deadline_is_not_taken_over_before_the_takeover_deadline() throws Exception {
    OrganizationIpAccess access = access(settings.asConfig(), 50L, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        loadStarted.countDown();
        assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();

      // well past the 3 second wait, so the load would already be taken over if both used one deadline
      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS - 1L);
      assertThatThrownBy(() -> access.isBlocked("org-a", "203.0.113.9", () -> false))
        .isInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasMessageContaining("Timed out");
      verify(dao, times(1)).selectEnabledRanges(any());

      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(access.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void failed_takeover_load_throws_and_never_lets_the_request_in() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(10, TimeUnit.SECONDS)).isTrue();
        return List.of();
      }
      throw new IllegalStateException("db down");
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();

      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThatThrownBy(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false))
        .isInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasRootCauseMessage("db down");
    } finally {
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void finishing_the_stuck_load_does_not_release_the_load_that_took_over() throws Exception {
    long deadline = 45_000L;
    OrganizationIpAccess access = access(settings.asConfig(), OrganizationIpAccess.LOAD_WAIT_TIMEOUT_MS, deadline);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    CountDownLatch takeoverStarted = new CountDownLatch(1);
    CountDownLatch releaseTakeover = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      int call = calls.incrementAndGet();
      CountDownLatch started = call == 1 ? stuckStarted : takeoverStarted;
      CountDownLatch release = call == 1 ? releaseStuck : releaseTakeover;
      started.countDown();
      assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> stuck = executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_000_000L + deadline);
      executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(takeoverStarted.await(10, TimeUnit.SECONDS)).isTrue();

      releaseStuck.countDown();
      assertThat(stuck.get(10, TimeUnit.SECONDS)).isFalse();

      // the takeover load is still running: this call serves the old snapshot and starts no third load
      clock.set(1_000_000L + deadline + 1_000L);
      assertThat(access.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());
    } finally {
      releaseStuck.countDown();
      releaseTakeover.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void thread_waiting_on_a_stuck_load_that_then_fails_gets_the_list_of_the_load_that_took_over() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(10, TimeUnit.SECONDS)).isTrue();
        throw new IllegalStateException("db timed out");
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_000_000L + 1_000L);
      Future<Boolean> waiter = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      awaitWaitingThreads(underTest, 1);

      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      releaseStuck.countDown();

      assertThat(waiter.get(10, TimeUnit.SECONDS)).isFalse();
    } finally {
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void thread_whose_wait_on_a_stuck_load_runs_out_gets_the_list_of_the_load_that_took_over() throws Exception {
    long waitMs = 1_000L;
    OrganizationIpAccess access = access(settings.asConfig(), waitMs, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(20, TimeUnit.SECONDS)).isTrue();
        return List.of();
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_000_000L + 100L);
      Future<Boolean> waiter = executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      awaitWaitingThreads(access, 1);

      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(access.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();

      // the stuck load never finishes: the wait runs out, and the newer list answers
      assertThat(waiter.get(10, TimeUnit.SECONDS)).isFalse();
    } finally {
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void stuck_load_that_fails_after_a_newer_load_succeeded_logs_that_the_newer_list_is_in_use() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(10, TimeUnit.SECONDS)).isTrue();
        throw new IllegalStateException("db timed out");
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> stuck = executor.submit(() -> underTest.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      assertThat(underTest.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      logTester.clear();

      releaseStuck.countDown();

      assertThat(stuck.get(10, TimeUnit.SECONDS)).isFalse();
      assertThat(logTester.logs(Level.WARN)).singleElement().asString()
        .contains("newer")
        .doesNotContain("previous");
    } finally {
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void no_third_load_is_started_while_two_are_stuck_and_the_caller_is_not_let_in() throws Exception {
    OrganizationIpAccess access = access(settings.asConfig(), 200L, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    AtomicInteger calls = new AtomicInteger();
    List<CountDownLatch> started = List.of(new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1));
    List<CountDownLatch> release = List.of(new CountDownLatch(1), new CountDownLatch(1), new CountDownLatch(1));
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      int call = calls.getAndIncrement();
      started.get(call).countDown();
      assertThat(release.get(call).await(20, TimeUnit.SECONDS)).isTrue();
      if (call == 0) {
        throw new IllegalStateException("db timed out");
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      Future<Boolean> first = executor.submit(() -> access.isBlocked("org-a", "192.0.2.1", () -> false));
      assertThat(started.get(0).await(10, TimeUnit.SECONDS)).isTrue();
      clock.set(1_000_000L + OrganizationIpAccess.TAKEOVER_AFTER_MS);
      executor.submit(() -> access.isBlocked("org-a", "192.0.2.1", () -> false));
      assertThat(started.get(1).await(10, TimeUnit.SECONDS)).isTrue();

      // the second load is stuck as well, but two are already in the database
      clock.set(1_000_000L + 2 * OrganizationIpAccess.TAKEOVER_AFTER_MS);
      Future<Boolean> third = executor.submit(() -> access.isBlocked("org-a", "192.0.2.1", () -> false));
      assertThatThrownBy(() -> third.get(5, TimeUnit.SECONDS))
        .hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class);
      assertThat(calls).hasValue(2);

      // once the first stuck load ends, a new load may take over from the second
      release.get(0).countDown();
      assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
        .hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class);
      executor.submit(() -> access.isBlocked("org-a", "192.0.2.1", () -> false));
      assertThat(started.get(2).await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(calls).hasValue(3);
    } finally {
      release.forEach(CountDownLatch::countDown);
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void caller_that_starts_a_load_after_another_load_refreshed_the_list_does_not_read_the_database() throws Exception {
    AtomicReference<Thread> pauseOnNextRead = new AtomicReference<>();
    CountDownLatch paused = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    OrganizationIpAccess access = new OrganizationIpAccess(dbClient, settings.asConfig(), () -> {
      if (pauseOnNextRead.compareAndSet(Thread.currentThread(), null)) {
        paused.countDown();
        assertThat(await(resume)).isTrue();
      }
      return clock.get();
    }, OrganizationIpAccess.LOAD_WAIT_TIMEOUT_MS, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    access.isBlocked("org-a", "192.0.2.1", () -> false);
    when(dao.selectEnabledRanges(any())).thenReturn(
      List.of(range("org-a", false, "198.51.100.0/24")),
      List.of(range("org-a", false, "192.0.2.0/24")));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      clock.set(1_000_000L + OrganizationIpAccess.SNAPSHOT_TTL_MS);
      // this caller has seen the expired snapshot and stops before deciding to load
      Future<Boolean> late = executor.submit(() -> {
        pauseOnNextRead.set(Thread.currentThread());
        return access.isBlocked("org-a", "198.51.100.5", () -> false);
      });
      assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(access.isBlocked("org-a", "198.51.100.5", () -> false)).isFalse();

      resume.countDown();

      assertThat(late.get(10, TimeUnit.SECONDS)).isFalse();
      verify(dao, times(2)).selectEnabledRanges(any());
    } finally {
      resume.countDown();
      executor.shutdownNow();
    }
  }

  @Test(timeout = 30_000)
  public void caller_arriving_while_a_load_finishes_gets_an_answer_without_spinning() throws Exception {
    AtomicLong clockReads = new AtomicLong();
    AtomicReference<Thread> pauseOnNextRead = new AtomicReference<>();
    CountDownLatch finishing = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    OrganizationIpAccess access = new OrganizationIpAccess(dbClient, settings.asConfig(), () -> {
      clockReads.incrementAndGet();
      if (pauseOnNextRead.compareAndSet(Thread.currentThread(), null)) {
        finishing.countDown();
        assertThat(await(finish)).isTrue();
      }
      return clock.get();
    }, OrganizationIpAccess.LOAD_WAIT_TIMEOUT_MS, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    access.isBlocked("org-a", "192.0.2.1", () -> false);
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch stuckStarted = new CountDownLatch(1);
    CountDownLatch releaseStuck = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      int call = calls.incrementAndGet();
      if (call == 1) {
        stuckStarted.countDown();
        assertThat(releaseStuck.await(20, TimeUnit.SECONDS)).isTrue();
        return List.of();
      }
      if (call == 2) {
        // the load that took over fails, and stops at its next clock reading, on its way out
        pauseOnNextRead.set(Thread.currentThread());
        throw new IllegalStateException("db down");
      }
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      clock.set(1_000_000L + OrganizationIpAccess.MAX_STALE_MS);
      executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(stuckStarted.await(10, TimeUnit.SECONDS)).isTrue();
      clock.addAndGet(OrganizationIpAccess.TAKEOVER_AFTER_MS);
      Future<Boolean> takeover = executor.submit(() -> access.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(finishing.await(10, TimeUnit.SECONDS)).isTrue();

      long readsBefore = clockReads.get();
      assertThatThrownBy(() -> access.isBlocked("org-a", "203.0.113.9", () -> false))
        .isInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasRootCauseMessage("db down");
      assertThat(clockReads.get() - readsBefore).isLessThanOrEqualTo(3);

      finish.countDown();
      assertThatThrownBy(() -> takeover.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(OrganizationIpAccessUnavailableException.class);
      // the stuck load still runs, yet the next caller starts a load at once
      readsBefore = clockReads.get();
      assertThat(access.isBlocked("org-a", "203.0.113.9", () -> false)).isFalse();
      assertThat(clockReads.get() - readsBefore).isLessThanOrEqualTo(3);
      assertThat(calls).hasValue(3);
    } finally {
      finish.countDown();
      releaseStuck.countDown();
      executor.shutdownNow();
    }
  }

  // ---- the admin check is only made when it can change the answer ----

  @Test
  public void admin_check_is_not_made_for_an_organization_without_allowlist() {
    AtomicInteger calls = new AtomicInteger();

    assertThat(underTest.isBlocked("org-other", "192.0.2.1", countingAdminCheck(calls, true))).isFalse();
    assertThat(underTest.isBlocked(null, "192.0.2.1", countingAdminCheck(calls, true))).isFalse();

    assertThat(calls).hasValue(0);
  }

  @Test
  public void admin_check_is_not_made_when_the_ip_is_allowed() {
    AtomicInteger calls = new AtomicInteger();

    assertThat(underTest.isBlocked("org-a", "203.0.113.9", countingAdminCheck(calls, false))).isFalse();

    assertThat(calls).hasValue(0);
  }

  @Test
  public void admin_check_is_not_made_when_the_allowlist_applies_to_admins() {
    AtomicInteger calls = new AtomicInteger();

    assertThat(underTest.isBlocked("org-admins", "192.0.2.1", countingAdminCheck(calls, true))).isTrue();

    assertThat(calls).hasValue(0);
  }

  @Test
  public void admin_check_decides_when_the_ip_is_outside_and_admins_are_exempt() {
    AtomicInteger calls = new AtomicInteger();

    assertThat(underTest.isBlocked("org-a", "192.0.2.1", countingAdminCheck(calls, true))).isFalse();
    assertThat(underTest.isBlocked("org-a", "192.0.2.1", countingAdminCheck(calls, false))).isTrue();

    assertThat(calls).hasValue(2);
  }

  @Test
  public void admin_check_is_made_once_per_call() {
    AtomicInteger calls = new AtomicInteger();

    underTest.isBlocked("org-a", null, countingAdminCheck(calls, false));

    assertThat(calls).hasValue(1);
  }

  @Test
  public void failing_admin_check_is_thrown_not_treated_as_allowed() {
    assertThatThrownBy(() -> underTest.isBlocked("org-a", "192.0.2.1", () -> {
      throw new IllegalStateException("role lookup failed");
    })).isInstanceOf(IllegalStateException.class);
  }

  @Test(timeout = 30_000)
  public void thread_that_waits_too_long_for_the_one_load_throws() throws Exception {
    OrganizationIpAccess impatient = access(settings.asConfig(), 50L, OrganizationIpAccess.TAKEOVER_AFTER_MS);
    CountDownLatch loadStarted = new CountDownLatch(1);
    CountDownLatch finishLoad = new CountDownLatch(1);
    when(dao.selectEnabledRanges(any())).thenAnswer(invocation -> {
      loadStarted.countDown();
      assertThat(finishLoad.await(10, TimeUnit.SECONDS)).isTrue();
      return List.of(range("org-a", false, "203.0.113.0/24"));
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> leader = executor.submit(() -> impatient.isBlocked("org-a", "203.0.113.9", () -> false));
      assertThat(loadStarted.await(10, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> impatient.isBlocked("org-a", "203.0.113.9", () -> false))
        .isInstanceOf(OrganizationIpAccessUnavailableException.class)
        .hasMessageContaining("Timed out");

      finishLoad.countDown();
      assertThat(leader.get(10, TimeUnit.SECONDS)).isFalse();
    } finally {
      finishLoad.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void full_tracker_is_cleaned_at_most_once_per_interval() {
    for (int i = 0; i <= OrganizationIpAccess.MAX_TRACKED_BLOCKS; i++) {
      underTest.shouldLogBlock("org-" + i, "192.0.2.1");
    }
    int cleanupsBefore = underTest.trackedBlockCleanupCount();

    for (int i = 0; i < 100; i++) {
      assertThat(underTest.shouldLogBlock("flood-" + i, "192.0.2.1")).isFalse();
    }

    assertThat(underTest.trackedBlockCleanupCount() - cleanupsBefore).isEqualTo(1);
    assertThat(underTest.trackedBlockCount()).isEqualTo(OrganizationIpAccess.MAX_TRACKED_BLOCKS + 1);

    clock.set(1_000_000L + OrganizationIpAccess.BLOCK_LOG_CLEANUP_INTERVAL_MS);
    underTest.shouldLogBlock("flood-again", "192.0.2.1");
    assertThat(underTest.trackedBlockCleanupCount() - cleanupsBefore).isEqualTo(2);
  }

  private static void awaitWaitingThreads(OrganizationIpAccess access, int expected) throws InterruptedException {
    long giveUpAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (access.waitingThreadCount() < expected) {
      assertThat(System.nanoTime()).as("threads waiting for the load").isLessThan(giveUpAt);
      Thread.sleep(5);
    }
  }

  private static boolean await(CountDownLatch latch) {
    try {
      return latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static java.util.function.BooleanSupplier countingAdminCheck(AtomicInteger calls, boolean isAdmin) {
    return () -> {
      calls.incrementAndGet();
      return isAdmin;
    };
  }

  private OrganizationIpAccess accessWithInternal(String cidrs) {
    MapSettings s = new MapSettings();
    s.setProperty("sonar.codescan.internalCidr", cidrs);
    return access(s.asConfig());
  }

  private OrganizationIpAccess access(Configuration config) {
    return access(config, OrganizationIpAccess.LOAD_WAIT_TIMEOUT_MS, OrganizationIpAccess.TAKEOVER_AFTER_MS);
  }

  private OrganizationIpAccess access(Configuration config, long loadWaitTimeoutMs, long takeoverAfterMs) {
    return new OrganizationIpAccess(dbClient, config, clock::get, loadWaitTimeoutMs, takeoverAfterMs);
  }

  /** Built as in production, so its ages run on the real steady clock and the wall clock is only {@link #wallClock}. */
  private OrganizationIpAccess productionAccess() {
    return new OrganizationIpAccess(dbClient, wallClock, settings.asConfig());
  }

  private static IpAllowlistRangeDto range(String org, boolean appliesToAdmins, String cidr) {
    return new IpAllowlistRangeDto().setOrganizationUuid(org).setAppliesToAdmins(appliesToAdmins).setCidr(cidr);
  }
}
