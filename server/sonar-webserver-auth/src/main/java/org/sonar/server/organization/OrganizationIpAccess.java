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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.IpAllowlistRangeDto;

import static java.util.stream.Collectors.toSet;

/**
 * Decides whether a client address may reach an organization under its IP allowlist (CSN-74). The
 * decision agrees with codescanng's {@code OrgIpAccessService}: an organization with the allowlist on lets in
 * only the addresses inside its ranges, a missing or unparseable address is blocked, and a range that cannot be
 * parsed is skipped, which can only narrow access.
 *
 * <p>All organizations with the allowlist on are read in one query and kept for {@link #SNAPSHOT_TTL_MS}.
 * When the snapshot is older, one thread reloads it. The others keep answering from the old snapshot, so a slow
 * database does not stall every request. A snapshot that can no longer be trusted is never served: it is dropped
 * once it is {@link #MAX_STALE_MS} old (twice the cache time, counted from the start of the read and checked
 * again after a failed reload), or if the clock went backwards since it was read.
 * The old snapshot is also used when a reload fails, within the same limit. When there is no usable snapshot,
 * callers wait (at most {@link #LOAD_WAIT_TIMEOUT_MS}) for the one load in progress and share its outcome. A load
 * that has run longer than {@link #TAKEOVER_AFTER_MS} is considered stuck: the next caller starts a new load, and
 * the stuck one can neither release the new one nor replace its result with an older list. At most
 * {@link #MAX_LOADS_IN_FLIGHT} loads run at once: when that many are stuck, a caller waits on the newest instead of
 * starting another. If a load fails, or the wait runs out,
 * the call throws {@link OrganizationIpAccessUnavailableException}, unless another load has left a usable
 * snapshot in the meantime. A failed read is never kept. Callers must treat an exception as a failed request,
 * never as allowed.
 */
@ServerSide
public class OrganizationIpAccess {

  public static final String INTERNAL_CIDR_PROPERTY = "sonar.codescan.internalCidr";
  static final long SNAPSHOT_TTL_MS = 30_000L;
  /** Oldest a snapshot may be and still answer while a reload is running or after a reload failed. */
  static final long MAX_STALE_MS = 2 * SNAPSHOT_TTL_MS;
  static final long LOAD_WAIT_TIMEOUT_MS = 3_000L;
  /** Longer than the 5 second statement timeout of the read, so a load that the database will still end is left to end. */
  static final long TAKEOVER_AFTER_MS = 8_000L;
  /** Loads that hang in the database each hold a request thread, so their number is capped. */
  static final int MAX_LOADS_IN_FLIGHT = 2;
  static final long BLOCK_LOG_INTERVAL_MS = 60_000L;
  static final long BLOCK_LOG_CLEANUP_INTERVAL_MS = 10_000L;
  static final int MAX_TRACKED_BLOCKS = 10_000;
  static final int MAX_REPORTED_BAD_RANGES = 1_000;
  private static final int MAX_LOGGED_VALUE_LENGTH = 100;

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationIpAccess.class);

  private final DbClient dbClient;
  private final System2 system2;
  private final List<IpRangeMatcher> internalRanges;
  private final Map<String, Long> lastBlockLogByOrgAndIp = new ConcurrentHashMap<>();
  private final Set<String> reportedBadRanges = ConcurrentHashMap.newKeySet();
  private final AtomicLong lastBlockLogCleanupAt = new AtomicLong();
  private final AtomicInteger blockLogCleanups = new AtomicInteger();
  private final AtomicInteger waitingThreads = new AtomicInteger();

  private final AtomicReference<Load> loadInProgress = new AtomicReference<>();
  private final Semaphore loadSlots = new Semaphore(MAX_LOADS_IN_FLIGHT);
  private final AtomicLong lastLoadFailureLogAt = new AtomicLong();
  private final long loadWaitTimeoutMs;
  private final long takeoverAfterMs;

  private volatile Snapshot snapshot;

  public OrganizationIpAccess(DbClient dbClient, System2 system2, Configuration config) {
    this(dbClient, system2, config, LOAD_WAIT_TIMEOUT_MS, TAKEOVER_AFTER_MS);
  }

  OrganizationIpAccess(DbClient dbClient, System2 system2, Configuration config, long loadWaitTimeoutMs, long takeoverAfterMs) {
    this.dbClient = dbClient;
    this.system2 = system2;
    this.loadWaitTimeoutMs = loadWaitTimeoutMs;
    this.takeoverAfterMs = takeoverAfterMs;
    this.internalRanges = parseInternalRanges(config.getStringArray(INTERNAL_CIDR_PROPERTY));
  }

  /**
   * @param isOrgAdmin whether the caller administers the organization. It is asked only when the answer depends on
   *        it: the organization has the allowlist on, the address is not allowed, and the allowlist does not apply to
   *        admins. So no permission lookup is made for an organization without an allowlist. Whatever it throws is
   *        passed on, never taken as "allowed".
   * @return false for a null organization, as for one without an allowlist
   * @throws OrganizationIpAccessUnavailableException when the allowlist cannot be read, for example when the
   *         database is unavailable
   */
  public boolean isBlocked(@Nullable String organizationUuid, @Nullable String clientIp, BooleanSupplier isOrgAdmin) {
    if (organizationUuid == null) {
      return false;
    }
    Policy policy = policies().get(organizationUuid);
    if (policy == null || policy.allows(clientIp)) {
      return false;
    }
    return policy.appliesToAdmins() || !isOrgAdmin.getAsBoolean();
  }

  /**
   * Organizations whose allowlist is on and does not let {@code clientIp} in. Whether the caller is an admin
   * is not taken into account.
   *
   * @throws OrganizationIpAccessUnavailableException when the allowlist cannot be read, for example when the
   *         database is unavailable
   */
  public Set<String> blockedOrganizationUuids(@Nullable String clientIp) {
    return policies().entrySet().stream()
      .filter(e -> !e.getValue().allows(clientIp))
      .map(Map.Entry::getKey)
      .collect(toSet());
  }

  /**
   * True when {@code clientIp} is a literal address inside {@value #INTERNAL_CIDR_PROPERTY}. Same rules as
   * codescanng's {@code ServiceLoginDetector}: a host name is never resolved, an address with an IPv6 zone
   * ({@code %}) is not internal, an IPv6 address is never inside an IPv4 range and the reverse, and an address
   * that cannot be parsed is not internal.
   */
  public boolean isInternalServiceAddress(@Nullable String clientIp) {
    if (clientIp == null || clientIp.indexOf('%') >= 0) {
      return false;
    }
    return internalRanges.stream().anyMatch(r -> r.containsLiteral(clientIp));
  }

  /**
   * Same line as codescanng's IpAllowlistGuard, so one log search finds blocks from both apps.
   */
  public void logBlocked(@Nullable String organizationKey, @Nullable String clientIp, @Nullable String login) {
    if (shouldLogBlock(organizationKey, clientIp)) {
      LOG.warn("Request blocked by IP allowlist: organization={}, ip={}, user={}", printable(organizationKey), printable(clientIp),
        login != null ? printable(login) : "anonymous");
    }
  }

  boolean shouldLogBlock(@Nullable String organizationKey, @Nullable String clientIp) {
    long now = system2.now();
    if (lastBlockLogByOrgAndIp.size() > MAX_TRACKED_BLOCKS) {
      cleanUpBlockLog(now);
      if (lastBlockLogByOrgAndIp.size() > MAX_TRACKED_BLOCKS) {
        // Full of entries from the last minute, as when addresses are scanned: skip this line rather than grow.
        return false;
      }
    }
    String key = organizationKey + '|' + clientIp;
    Long previous = lastBlockLogByOrgAndIp.get(key);
    if (previous == null) {
      return lastBlockLogByOrgAndIp.putIfAbsent(key, now) == null;
    }
    // replace() loses to a concurrent writer, so only one thread logs per window
    return isExpired(previous, now) && lastBlockLogByOrgAndIp.replace(key, previous, now);
  }

  /** A stamp ahead of the clock (the clock was set back) counts as expired, or the line would stay silent until the clock caught up. */
  private static boolean isExpired(long loggedAt, long now) {
    return now < loggedAt || now - loggedAt >= BLOCK_LOG_INTERVAL_MS;
  }

  /** Scanning the whole map for every blocked request while it is full would cost more than the requests. */
  private void cleanUpBlockLog(long now) {
    long last = lastBlockLogCleanupAt.get();
    boolean due = last == 0 || now < last || now - last >= BLOCK_LOG_CLEANUP_INTERVAL_MS;
    if (due && lastBlockLogCleanupAt.compareAndSet(last, now)) {
      blockLogCleanups.incrementAndGet();
      lastBlockLogByOrgAndIp.values().removeIf(loggedAt -> isExpired(loggedAt, now));
    }
  }

  int trackedBlockCleanupCount() {
    return blockLogCleanups.get();
  }

  int trackedBlockCount() {
    return lastBlockLogByOrgAndIp.size();
  }

  private Map<String, Policy> policies() {
    while (true) {
      long now = system2.now();
      Snapshot current = snapshot;
      if (current != null && current.isFresh(now)) {
        return current.policies();
      }
      Load running = loadInProgress.get();
      if (running == null || running.isStuck(now, takeoverAfterMs)) {
        if (loadSlots.tryAcquire()) {
          try {
            Load mine = new Load(new CompletableFuture<>(), now);
            if (loadInProgress.compareAndSet(running, mine)) {
              return reload(mine);
            }
          } finally {
            loadSlots.release();
          }
          // another thread started or finished a load first: look again
          continue;
        }
        if (running == null) {
          // cannot last: with no load in progress, at most one stuck load holds a slot
          Thread.onSpinWait();
          continue;
        }
      }
      if (current != null && current.isUsableWhenStale(now)) {
        return current.policies();
      }
      return awaitLoad(running.result());
    }
  }

  /** Runs the one load. Every other thread that has no usable snapshot is waiting on {@code mine}. */
  private Map<String, Policy> reload(Load mine) {
    Snapshot before = snapshot;
    try {
      // A load that finished just before this thread took over has already refreshed the snapshot.
      if (before != null && before.isFresh(system2.now())) {
        mine.result().complete(before);
        return before.policies();
      }
      Snapshot loaded = new Snapshot(load(), mine.startedAt());
      publish(loaded);
      mine.result().complete(loaded);
      return loaded.policies();
    } catch (RuntimeException e) {
      mine.result().completeExceptionally(e);
      Snapshot old = snapshot;
      if (old != null && old.isUsableWhenStale(system2.now())) {
        // a different snapshot means a load that took over from this one has refreshed the list meanwhile
        logLoadFailure(e, old != before);
        return old.policies();
      }
      throw new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", e);
    } finally {
      if (!mine.result().isDone()) {
        // an Error, not an exception: do not leave the waiting threads until their deadline
        mine.result().completeExceptionally(new IllegalStateException("The IP allowlist load was aborted"));
      }
      // not set(null): another thread may have taken over from this load while it was stuck
      loadInProgress.compareAndSet(mine, null);
    }
  }

  /**
   * A load that was taken over from can finish late with a list read earlier than the current one. A snapshot
   * stamped after the clock (the clock went back) is never served: such a load is not kept, and a current
   * snapshot like that is replaced by any new load.
   */
  private synchronized void publish(Snapshot loaded) {
    long now = system2.now();
    if (loaded.loadedAt() > now) {
      return;
    }
    Snapshot current = snapshot;
    if (current == null || loaded.loadedAt() >= current.loadedAt() || current.loadedAt() > now) {
      snapshot = loaded;
    }
  }

  int waitingThreadCount() {
    return waitingThreads.get();
  }

  private Map<String, Policy> awaitLoad(CompletableFuture<Snapshot> running) {
    waitingThreads.incrementAndGet();
    try {
      return running.get(loadWaitTimeoutMs, TimeUnit.MILLISECONDS).policies();
    } catch (ExecutionException e) {
      return usableSnapshotOrThrow("Cannot read the IP allowlist", e.getCause());
    } catch (TimeoutException e) {
      return usableSnapshotOrThrow("Timed out waiting for the IP allowlist to load", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OrganizationIpAccessUnavailableException("Interrupted while waiting for the IP allowlist to load", e);
    } finally {
      waitingThreads.decrementAndGet();
    }
  }

  /** The awaited load may have been stuck while another load, which took over from it, refreshed the snapshot. */
  private Map<String, Policy> usableSnapshotOrThrow(String message, Throwable cause) {
    Snapshot current = snapshot;
    if (current != null && current.isUsableWhenStale(system2.now())) {
      return current.policies();
    }
    throw new OrganizationIpAccessUnavailableException(message, cause);
  }

  /** While the database is down every request would otherwise log. */
  private void logLoadFailure(RuntimeException e, boolean newerListInUse) {
    long now = system2.now();
    long last = lastLoadFailureLogAt.get();
    if ((last == 0 || now < last || now - last >= BLOCK_LOG_INTERVAL_MS) && lastLoadFailureLogAt.compareAndSet(last, now)) {
      if (newerListInUse) {
        LOG.warn("Cannot reload the IP allowlist, using the newer one read by a later reload", e);
      } else {
        LOG.warn("Cannot reload the IP allowlist, using the previous one for at most {} seconds", MAX_STALE_MS / 1000, e);
      }
    }
  }

  private Map<String, Policy> load() {
    List<IpAllowlistRangeDto> rows;
    try (DbSession dbSession = dbClient.openSession(false)) {
      rows = dbClient.ipAllowlistDao().selectEnabledRanges(dbSession);
    }
    Map<String, Boolean> appliesToAdmins = new HashMap<>();
    Map<String, List<IpRangeMatcher>> ranges = new HashMap<>();
    for (IpAllowlistRangeDto row : rows) {
      appliesToAdmins.put(row.getOrganizationUuid(), row.isAppliesToAdmins());
      List<IpRangeMatcher> orgRanges = ranges.computeIfAbsent(row.getOrganizationUuid(), k -> new ArrayList<>());
      String cidr = row.getCidr();
      if (cidr != null) {
        try {
          orgRanges.add(IpRangeMatcher.parse(cidr));
        } catch (IllegalArgumentException e) {
          // Skipping keeps the list narrower than intended; it can never let extra addresses in.
          reportBadRange(row.getOrganizationUuid(), cidr);
        }
      }
    }
    Map<String, Policy> result = new HashMap<>();
    appliesToAdmins.forEach((org, applies) -> result.put(org, new Policy(applies, List.copyOf(ranges.get(org)))));
    return Map.copyOf(result);
  }

  /** The snapshot is reloaded every {@link #SNAPSHOT_TTL_MS}, so a range that stays bad is reported once. */
  private void reportBadRange(String organizationUuid, String cidr) {
    if (reportedBadRanges.size() < MAX_REPORTED_BAD_RANGES && reportedBadRanges.add(organizationUuid + '|' + cidr)) {
      LOG.warn("Ignoring unparseable IP allowlist range '{}' of organization {}", printable(cidr), printable(organizationUuid));
    }
  }

  /**
   * A bad entry empties the whole setting, so nothing is internal, rather than keeping the good entries of
   * a setting that is wrong. It is logged here once, not on every request.
   */
  private static List<IpRangeMatcher> parseInternalRanges(String[] configured) {
    List<String> entries = Arrays.stream(configured)
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .toList();
    if (entries.isEmpty()) {
      LOG.info("{} is not set: no request is treated as internal service traffic", INTERNAL_CIDR_PROPERTY);
      return List.of();
    }
    List<IpRangeMatcher> ranges = new ArrayList<>();
    for (String entry : entries) {
      try {
        if (entry.indexOf('/') < 0) {
          throw new IllegalArgumentException("not a CIDR");
        }
        ranges.add(IpRangeMatcher.parseLenient(entry));
      } catch (IllegalArgumentException e) {
        LOG.warn("{} contains '{}', which is not a CIDR range such as 10.0.0.0/16: no request is treated as internal service traffic",
          INTERNAL_CIDR_PROPERTY, printable(entry));
        return List.of();
      }
    }
    return List.copyOf(ranges);
  }

  /** Keeps a value that came from a request from adding lines to the log or filling it. */
  private static String printable(@Nullable String value) {
    if (value == null) {
      return "null";
    }
    String shown = value.length() > MAX_LOGGED_VALUE_LENGTH ? value.substring(0, MAX_LOGGED_VALUE_LENGTH) : value;
    StringBuilder out = new StringBuilder(shown.length());
    for (int i = 0; i < shown.length(); i++) {
      char c = shown.charAt(i);
      out.append(Character.isISOControl(c) ? '_' : c);
    }
    return out.toString();
  }

  /** {@code startedAt} is when the database read began, so a snapshot is never presented as newer than its data. */
  private record Load(CompletableFuture<Snapshot> result, long startedAt) {
    /** A load that has run longer than the takeover deadline, or whose start lies in the future of the clock. */
    boolean isStuck(long now, long deadlineMs) {
      return now < startedAt || now - startedAt >= deadlineMs;
    }
  }

  private record Snapshot(Map<String, Policy> policies, long loadedAt) {
    /** A clock that went backwards makes the age unknown, so the snapshot is not fresh and not usable when stale. */
    boolean isFresh(long now) {
      return now >= loadedAt && now - loadedAt < SNAPSHOT_TTL_MS;
    }

    boolean isUsableWhenStale(long now) {
      return now >= loadedAt && now - loadedAt < MAX_STALE_MS;
    }
  }

  private record Policy(boolean appliesToAdmins, List<IpRangeMatcher> ranges) {
    boolean allows(@Nullable String clientIp) {
      return ranges.stream().anyMatch(r -> r.contains(clientIp));
    }
  }
}
