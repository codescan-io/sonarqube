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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.server.exceptions.ServerException;

/**
 * Turns "the IP allowlist cannot be read" into the HTTP 503 the request fails with, and logs it. The web service
 * layer does not log a 503, so this is the only place the cause is written down. While the database is down every
 * request fails, so the cause is logged as a warning at most once a minute; the others get one DEBUG line each.
 * One instance is shared by all requests and is safe to use from many threads.
 */
public class IpAllowlistUnavailableLog {

  private static final Logger LOG = LoggerFactory.getLogger(IpAllowlistUnavailableLog.class);

  static final long INTERVAL_MS = 60_000L;
  static final String MESSAGE = "Access to this organization cannot be checked because its IP allowlist cannot be read right now. "
    + "Try again in a few minutes.";

  private final LongSupplier clock;
  private final AtomicLong nextWarningAt = new AtomicLong(Long.MIN_VALUE);
  private final AtomicLong leftOutSinceLastWarning = new AtomicLong();

  public IpAllowlistUnavailableLog(LongSupplier clock) {
    this.clock = clock;
  }

  /** Logs the failure and returns the exception to throw. Never returns a result that lets the request through. */
  public ServerException unavailable(OrganizationIpAccessUnavailableException failure) {
    log(failure);
    ServerException result = new ServerException(503, MESSAGE);
    result.initCause(failure);
    return result;
  }

  private void log(OrganizationIpAccessUnavailableException failure) {
    long now = clock.getAsLong();
    long next = nextWarningAt.get();
    // only the one thread that moves the deadline on writes the warning
    if (now >= next && nextWarningAt.compareAndSet(next, now + INTERVAL_MS)) {
      LOG.warn("Requests that need an organization's IP allowlist are failing with HTTP 503 because it cannot be read. "
        + "{} more such failures since the last warning were logged at DEBUG only.", leftOutSinceLastWarning.getAndSet(0), failure);
    } else {
      leftOutSinceLastWarning.incrementAndGet();
      LOG.debug("Request failed with HTTP 503: the IP allowlist cannot be read ({})", failure.getMessage());
    }
  }
}
