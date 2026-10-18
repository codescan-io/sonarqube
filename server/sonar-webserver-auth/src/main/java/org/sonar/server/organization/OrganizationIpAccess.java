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
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>All organizations with the allowlist on are read in one query and kept for {@link #SNAPSHOT_TTL_MS}. A failed
 * read is thrown to the caller and not kept. Callers must treat it as a failed request, never as allowed.
 */
@ServerSide
public class OrganizationIpAccess {

  public static final String INTERNAL_CIDR_PROPERTY = "sonar.codescan.internalCidr";
  static final long SNAPSHOT_TTL_MS = 30_000L;
  static final long BLOCK_LOG_INTERVAL_MS = 60_000L;
  static final int MAX_TRACKED_BLOCKS = 10_000;
  static final int MAX_REPORTED_BAD_RANGES = 1_000;
  private static final int MAX_LOGGED_VALUE_LENGTH = 100;

  private static final Logger LOG = LoggerFactory.getLogger(OrganizationIpAccess.class);

  private final DbClient dbClient;
  private final System2 system2;
  private final List<IpRangeMatcher> internalRanges;
  private final Map<String, Long> lastBlockLogByOrgAndIp = new ConcurrentHashMap<>();
  private final Set<String> reportedBadRanges = ConcurrentHashMap.newKeySet();

  private volatile Snapshot snapshot;

  public OrganizationIpAccess(DbClient dbClient, System2 system2, Configuration config) {
    this.dbClient = dbClient;
    this.system2 = system2;
    this.internalRanges = parseInternalRanges(config.getStringArray(INTERNAL_CIDR_PROPERTY));
  }

  /**
   * @throws RuntimeException when the allowlist cannot be read, for example when the database is unavailable
   */
  public boolean isBlocked(String organizationUuid, @Nullable String clientIp, boolean isOrgAdmin) {
    Policy policy = policies().get(organizationUuid);
    if (policy == null) {
      return false;
    }
    if (isOrgAdmin && !policy.appliesToAdmins()) {
      return false;
    }
    return !policy.allows(clientIp);
  }

  /**
   * Organizations whose allowlist is on and does not let {@code clientIp} in. Whether the caller is an admin
   * is not taken into account.
   *
   * @throws RuntimeException when the allowlist cannot be read, for example when the database is unavailable
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
  public void logBlocked(String organizationKey, @Nullable String clientIp, @Nullable String login) {
    if (shouldLogBlock(organizationKey, clientIp)) {
      LOG.warn("Request blocked by IP allowlist: organization={}, ip={}, user={}", printable(organizationKey), printable(clientIp),
        login != null ? printable(login) : "anonymous");
    }
  }

  boolean shouldLogBlock(String organizationKey, @Nullable String clientIp) {
    long now = system2.now();
    if (lastBlockLogByOrgAndIp.size() > MAX_TRACKED_BLOCKS) {
      lastBlockLogByOrgAndIp.values().removeIf(loggedAt -> now - loggedAt >= BLOCK_LOG_INTERVAL_MS);
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
    return now - previous >= BLOCK_LOG_INTERVAL_MS && lastBlockLogByOrgAndIp.replace(key, previous, now);
  }

  int trackedBlockCount() {
    return lastBlockLogByOrgAndIp.size();
  }

  private Map<String, Policy> policies() {
    Snapshot current = snapshot;
    if (current != null && !current.isOlderThanTtl(system2.now())) {
      return current.policies();
    }
    synchronized (this) {
      // One thread reloads; the others wait for it instead of each querying the database.
      current = snapshot;
      long now = system2.now();
      if (current == null || current.isOlderThanTtl(now)) {
        current = new Snapshot(load(), now);
        snapshot = current;
      }
      return current.policies();
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

  private record Snapshot(Map<String, Policy> policies, long loadedAt) {
    boolean isOlderThanTtl(long now) {
      return now - loadedAt >= SNAPSHOT_TTL_MS;
    }
  }

  private record Policy(boolean appliesToAdmins, List<IpRangeMatcher> ranges) {
    boolean allows(@Nullable String clientIp) {
      return ranges.stream().anyMatch(r -> r.contains(clientIp));
    }
  }
}
