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
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.IpAllowlistRangeDto;

import static java.util.stream.Collectors.toSet;

/**
 * Decides whether a client address may reach an organization under its IP allowlist (CSN-74), with the same rules
 * as codescanng's {@code OrgIpAccessService}: only addresses inside the ranges get in, a missing or unparseable
 * address is blocked, and a range that cannot be parsed is skipped, which can only narrow access.
 *
 * <p>While the global setting {@value #FEATURE_FLAG_PROPERTY} is not {@code true}, the feature is off: no organization is
 * blocked and the stored ranges are not read. The setting is read in the same load as the allowlists, so a change takes
 * effect on the next reload. When the setting cannot be read and was off when last read, the feature stays off, so a
 * database problem does not block organizations while the feature is off. Before the first successful read, or when
 * it was last read as on, a failed read is handled like a failed allowlist read.
 *
 * <p>All enabled allowlists are read in one query and kept for {@link #SNAPSHOT_TTL_MS}. One caller reloads an
 * expired snapshot while the others keep using it until it is {@link #MAX_STALE_MS} old, also when the reload
 * fails. With no usable snapshot, callers wait up to {@link #LOAD_WAIT_TIMEOUT_MS} for the running load. A load
 * running longer than {@link #TAKEOVER_AFTER_MS} can be taken over, at most {@link #MAX_LOADS_IN_FLIGHT} loads
 * run, and the load started last wins. Ages are counted from the start of the read, on a clock that never goes
 * backwards. A failed read is never kept. When no usable list can be had, the call throws
 * {@link OrganizationIpAccessUnavailableException}: callers must treat it as a failed request, never as allowed.
 *
 * <p>The client address can only be trusted when {@value #TRUSTED_PROXIES_ENV_VAR} names the load balancers. Without
 * it Tomcat takes X-Forwarded-For from any private address, so anyone inside the network can choose the address seen
 * here. Then every address is treated as unknown: each organization with an allowlist blocks every request it applies
 * to, and no address is internal.
 */
@ServerSide
public class OrganizationIpAccess {

  public static final String INTERNAL_CIDR_PROPERTY = "sonar.codescan.internalCidr";
  public static final String TRUSTED_PROXIES_PROPERTY = "sonar.web.trustedProxiesRegex";
  /** The environment variable the deployment sets; the application launcher maps it to {@value #TRUSTED_PROXIES_PROPERTY}. */
  public static final String TRUSTED_PROXIES_ENV_VAR = "CODESCAN_TRUSTED_PROXIES";
  public static final String FEATURE_FLAG_PROPERTY = "codescan.cloud.ipAllowlist.enabled";
  /** Read only from the web server's startup properties, so a value saved in the database would be ignored. */
  public static final Set<String> STARTUP_ONLY_PROPERTIES = Set.of(TRUSTED_PROXIES_PROPERTY, INTERNAL_CIDR_PROPERTY);
  static final long SNAPSHOT_TTL_MS = 30_000L;
  /** Oldest a snapshot may be and still answer while a reload is running or after a reload failed. */
  static final long MAX_STALE_MS = 2 * SNAPSHOT_TTL_MS;
  static final long LOAD_WAIT_TIMEOUT_MS = 3_000L;
  /** Longer than the two 5 second statement timeouts of a load, so a load that the database will still end is left to end. */
  static final long TAKEOVER_AFTER_MS = 11_000L;
  /** Loads that hang in the database each hold a request thread, so their number is capped. */
  static final int MAX_LOADS_IN_FLIGHT = 2;
  static final long BLOCK_LOG_INTERVAL_MS = 60_000L;
  static final long BLOCK_LOG_CLEANUP_INTERVAL_MS = 10_000L;
  static final int MAX_TRACKED_BLOCKS = 10_000;
  static final int MAX_REPORTED_BAD_RANGES = 1_000;
  private static final int MAX_LOGGED_VALUE_LENGTH = 100;

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationIpAccess.class);

  private final DbClient dbClient;
  private final LongSupplier clock;
  private final List<IpRangeMatcher> internalRanges;
  private final Map<String, Long> lastBlockLogByOrgAndIp = new ConcurrentHashMap<>();
  private final Set<String> reportedBadRanges = ConcurrentHashMap.newKeySet();
  private final AtomicLong lastBlockLogCleanupAt;
  private final AtomicInteger blockLogCleanups = new AtomicInteger();

  private final AtomicReference<Loads> loads = new AtomicReference<>(new Loads(null, 0));
  private final AtomicLong lastLoadFailureLogAt;
  private final AtomicLong loadNumbers = new AtomicLong();
  private final long loadWaitTimeoutMs;
  private final long takeoverAfterMs;
  private final boolean clientAddressTrusted;
  private final AtomicLong lastUntrustedAddressLogAt;

  private volatile Snapshot snapshot;
  /** The setting {@value #FEATURE_FLAG_PROPERTY} as last read, by the load started last; null until a read succeeds. */
  private final AtomicReference<FlagReading> lastFlagReading = new AtomicReference<>();

  /**
   * {@code system2} is not read: the wall clock can be set back or forward, so every age and deadline here is
   * measured on {@link System#nanoTime()}, which never goes backwards.
   *
   * @param processProperties the web server's startup properties, the ones Tomcat's X-Forwarded-For handling is
   *        configured with. Not {@code Configuration}: it also returns values saved in the database, which Tomcat
   *        never reads, so a saved value could make a forgeable client address look trusted or internal.
   */
  @Inject
  public OrganizationIpAccess(DbClient dbClient, System2 system2, Properties processProperties) {
    this(dbClient, processProperties, () -> System.nanoTime() / 1_000_000L, LOAD_WAIT_TIMEOUT_MS, TAKEOVER_AFTER_MS);
  }

  /** @param clock milliseconds on a clock that never goes backwards */
  OrganizationIpAccess(DbClient dbClient, Properties processProperties, LongSupplier clock, long loadWaitTimeoutMs, long takeoverAfterMs) {
    this.dbClient = dbClient;
    this.clock = clock;
    this.loadWaitTimeoutMs = loadWaitTimeoutMs;
    this.takeoverAfterMs = takeoverAfterMs;
    String internalCidrs = processProperties.getProperty(INTERNAL_CIDR_PROPERTY);
    this.internalRanges = parseInternalRanges(internalCidrs == null ? new String[0] : internalCidrs.split(","));
    this.clientAddressTrusted = isTrustedProxiesSet(processProperties.getProperty(TRUSTED_PROXIES_PROPERTY));
    long now = clock.getAsLong();
    this.lastBlockLogCleanupAt = new AtomicLong(now - BLOCK_LOG_CLEANUP_INTERVAL_MS);
    this.lastLoadFailureLogAt = new AtomicLong(now - BLOCK_LOG_INTERVAL_MS);
    this.lastUntrustedAddressLogAt = new AtomicLong(now - BLOCK_LOG_INTERVAL_MS);
  }

  /**
   * Whether {@value #TRUSTED_PROXIES_PROPERTY} is set in the web server's startup properties, the value Tomcat's
   * X-Forwarded-For handling uses, so the client address can be trusted.
   */
  public boolean isTrustedProxiesSet() {
    return clientAddressTrusted;
  }

  /** The rule both this class and the web server's X-Forwarded-For handling apply to {@value #TRUSTED_PROXIES_PROPERTY}. */
  public static boolean isTrustedProxiesSet(@Nullable String trustedProxiesRegex) {
    return trustedProxiesRegex != null && !trustedProxiesRegex.isBlank();
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
    if (policy == null || policy.allows(trusted(clientIp))) {
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
    Map<String, Policy> policies = policies();
    String address = policies.isEmpty() ? clientIp : trusted(clientIp);
    return policies.entrySet().stream()
      .filter(e -> !e.getValue().allows(address))
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
    if (!clientAddressTrusted || clientIp == null || clientIp.indexOf('%') >= 0) {
      return false;
    }
    return internalRanges.stream().anyMatch(r -> r.containsLiteral(clientIp));
  }

  /** Called only when an organization has an allowlist, so the error is logged only while it blocks requests. */
  @Nullable
  private String trusted(@Nullable String clientIp) {
    if (clientAddressTrusted) {
      return clientIp;
    }
    long now = clock.getAsLong();
    long last = lastUntrustedAddressLogAt.get();
    if (now - last >= BLOCK_LOG_INTERVAL_MS && lastUntrustedAddressLogAt.compareAndSet(last, now)) {
      LOG.error("IP allowlist cannot be enforced: {} is not set, so the client address can be forged through X-Forwarded-For. "
        + "Every organization with an IP allowlist blocks every request it applies to until it is set to the addresses of the load balancer",
        TRUSTED_PROXIES_ENV_VAR);
    }
    return null;
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
    long now = clock.getAsLong();
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

  private static boolean isExpired(long loggedAt, long now) {
    return now - loggedAt >= BLOCK_LOG_INTERVAL_MS;
  }

  /** Scanning the whole map for every blocked request while it is full would cost more than the requests. */
  private void cleanUpBlockLog(long now) {
    long last = lastBlockLogCleanupAt.get();
    if (now - last >= BLOCK_LOG_CLEANUP_INTERVAL_MS && lastBlockLogCleanupAt.compareAndSet(last, now)) {
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
      Snapshot current = snapshot;
      long now = clock.getAsLong();
      if (current != null && current.isFresh(now)) {
        return current.policies();
      }
      Loads state = loads.get();
      Load running = state.current();
      if (running == null || (running.isStuck(now, takeoverAfterMs) && state.inFlight() < MAX_LOADS_IN_FLIGHT)) {
        Load mine = new Load(newLoadResult(), loadNumbers.incrementAndGet(), now);
        if (loads.compareAndSet(state, new Loads(mine, state.inFlight() + 1))) {
          return reload(mine);
        }
        // another thread started or finished a load first: look again
        continue;
      }
      if (current != null && current.isUsableWhenStale(now)) {
        return current.policies();
      }
      return awaitLoad(running.result());
    }
  }

  /** Runs the one load. Every other thread that has no usable snapshot is waiting on {@code mine}. */
  private Map<String, Policy> reload(Load mine) {
    try {
      // another load may have finished since this thread saw the snapshot expire
      Snapshot current = snapshot;
      if (current != null && current.isFresh(clock.getAsLong())) {
        mine.result().complete(current);
        return current.policies();
      }
      Snapshot loaded = new Snapshot(load(mine.number()), mine.number(), mine.startedAt());
      publish(loaded);
      mine.result().complete(loaded);
      return loaded.policies();
    } catch (RuntimeException e) {
      mine.result().completeExceptionally(e);
      Snapshot last = snapshot;
      if (last != null && last.isUsableWhenStale(clock.getAsLong())) {
        logLoadFailure(e);
        return last.policies();
      }
      throw new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", e);
    } finally {
      // the count is released even when ending the waits throws an Error: each leak lowers MAX_LOADS_IN_FLIGHT until restart
      try {
        if (!mine.result().isDone()) {
          // an Error, not an exception: do not leave the waiting threads until their deadline
          mine.result().completeExceptionally(new IllegalStateException("The IP allowlist load was aborted"));
        }
      } finally {
        // another thread may have taken over from this load while it was stuck: that one stays current
        loads.updateAndGet(s -> new Loads(s.current() == mine ? null : s.current(), s.inFlight() - 1));
      }
    }
  }

  /** The future that threads waiting on a load block on; tests replace it to make ending the waits fail. */
  <T> CompletableFuture<T> newLoadResult() {
    return new CompletableFuture<>();
  }

  /** A load that was taken over can finish after the one that took over from it: the load started last wins. */
  private synchronized void publish(Snapshot loaded) {
    Snapshot current = snapshot;
    if (current == null || loaded.loadNumber() > current.loadNumber()) {
      snapshot = loaded;
    }
  }

  private Map<String, Policy> awaitLoad(CompletableFuture<Snapshot> running) {
    try {
      return running.get(loadWaitTimeoutMs, TimeUnit.MILLISECONDS).policies();
    } catch (ExecutionException e) {
      return usableSnapshotOrThrow("Cannot read the IP allowlist", e.getCause());
    } catch (TimeoutException e) {
      return usableSnapshotOrThrow("Timed out waiting for the IP allowlist to load", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OrganizationIpAccessUnavailableException("Interrupted while waiting for the IP allowlist to load", e);
    }
  }

  /** The awaited load may have been stuck while another load, which took over from it, refreshed the snapshot. */
  private Map<String, Policy> usableSnapshotOrThrow(String message, Throwable cause) {
    Snapshot current = snapshot;
    if (current != null && current.isUsableWhenStale(clock.getAsLong())) {
      return current.policies();
    }
    if (wasFlagLastReadAsOff()) {
      return Map.of();
    }
    throw new OrganizationIpAccessUnavailableException(message, cause);
  }

  /** While the database is down every request would otherwise log. */
  private void logLoadFailure(RuntimeException e) {
    long now = clock.getAsLong();
    long last = lastLoadFailureLogAt.get();
    if (now - last >= BLOCK_LOG_INTERVAL_MS && lastLoadFailureLogAt.compareAndSet(last, now)) {
      LOG.warn("Cannot reload the IP allowlist, using the last list read at most until it is {} seconds old", MAX_STALE_MS / 1000, e);
    }
  }

  private Map<String, Policy> load(long loadNumber) {
    List<IpAllowlistRangeDto> rows;
    Boolean featureOn = null;
    try (DbSession dbSession = dbClient.openSession(false)) {
      featureOn = dbClient.ipAllowlistDao().selectGlobalPropertyValues(dbSession, FEATURE_FLAG_PROPERTY).stream().anyMatch(OrganizationIpAccess::isFeatureOn);
      rememberFlag(new FlagReading(loadNumber, featureOn));
      if (!featureOn) {
        return Map.of();
      }
      rows = dbClient.ipAllowlistDao().selectEnabledRanges(dbSession);
    } catch (RuntimeException e) {
      if (!Boolean.TRUE.equals(featureOn) && wasFlagLastReadAsOff()) {
        logFlagReadFailure(e);
        return Map.of();
      }
      throw e;
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

  /** A load that was taken over can finish after the one that took over from it: the load started last wins. */
  private void rememberFlag(FlagReading reading) {
    lastFlagReading.accumulateAndGet(reading, (last, next) -> last == null || next.loadNumber() > last.loadNumber() ? next : last);
  }

  private boolean wasFlagLastReadAsOff() {
    FlagReading last = lastFlagReading.get();
    return last != null && !last.on();
  }

  /** Logged at most once a minute, as a failed allowlist read is. */
  private void logFlagReadFailure(RuntimeException e) {
    long now = clock.getAsLong();
    long last = lastLoadFailureLogAt.get();
    if (now - last >= BLOCK_LOG_INTERVAL_MS && lastLoadFailureLogAt.compareAndSet(last, now)) {
      LOG.warn("Cannot read {}, so the IP allowlist stays off as it was when last read", FEATURE_FLAG_PROPERTY, e);
    }
  }

  private record FlagReading(long loadNumber, boolean on) {
  }

  /** The snapshot is reloaded every {@link #SNAPSHOT_TTL_MS}, so a range that stays bad is reported once. */
  private void reportBadRange(String organizationUuid, String cidr) {
    if (reportedBadRanges.size() < MAX_REPORTED_BAD_RANGES && reportedBadRanges.add(organizationUuid + '|' + cidr)) {
      LOG.warn("Ignoring unparseable IP allowlist range '{}' of organization {}", printable(cidr), printable(organizationUuid));
    }
  }

  /** True only for the exact value {@code true}: an absent or any other value leaves the feature off. */
  public static boolean isFeatureOn(@Nullable String featureFlagValue) {
    return "true".equals(featureFlagValue);
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

  /**
   * {@code number} orders loads by when they started. {@code startedAt} is when the database read began, so a
   * snapshot is never presented as newer than its data.
   */
  private record Load(CompletableFuture<Snapshot> result, long number, long startedAt) {
    boolean isStuck(long now, long deadlineMs) {
      return now - startedAt >= deadlineMs;
    }
  }

  /**
   * The current load and the number of loads not yet ended, changed together. Only the current load can be taken
   * over, and only while fewer than {@link #MAX_LOADS_IN_FLIGHT} run, so when there is no current load fewer than
   * that many run and a caller can always start one.
   */
  private record Loads(@Nullable Load current, int inFlight) {
  }

  private record Snapshot(Map<String, Policy> policies, long loadNumber, long loadedAt) {
    boolean isFresh(long now) {
      return now - loadedAt < SNAPSHOT_TTL_MS;
    }

    boolean isUsableWhenStale(long now) {
      return now - loadedAt < MAX_STALE_MS;
    }
  }

  private record Policy(boolean appliesToAdmins, List<IpRangeMatcher> ranges) {
    boolean allows(@Nullable String clientIp) {
      return ranges.stream().anyMatch(r -> r.contains(clientIp));
    }
  }
}
