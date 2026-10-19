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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogAndArguments;
import org.sonar.api.testfixtures.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.server.exceptions.ServerException;

import static org.assertj.core.api.Assertions.assertThat;

public class IpAllowlistUnavailableLogTest {

  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.DEBUG);

  private long now = 1_000_000L;
  private final IpAllowlistUnavailableLog underTest = new IpAllowlistUnavailableLog(() -> now);

  @Test
  public void failure_becomes_a_503_that_keeps_the_cause_and_hides_its_details() {
    OrganizationIpAccessUnavailableException failure = failure();

    ServerException result = underTest.unavailable(failure);

    assertThat(result.httpCode()).isEqualTo(503);
    assertThat(result.getCause()).isSameAs(failure);
    assertThat(result.getMessage()).contains("IP allowlist").doesNotContain("db-host");
  }

  @Test
  public void first_failure_is_logged_as_a_warning_with_its_cause() {
    OrganizationIpAccessUnavailableException failure = failure();

    underTest.unavailable(failure);

    List<LogAndArguments> warnings = logTester.getLogs(LoggerLevel.WARN);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).getFormattedMsg()).contains("IP allowlist").contains("503");
    assertThat(warnings.get(0).getThrowable()).isSameAs(failure);
  }

  @Test
  public void failures_within_the_interval_are_logged_only_at_debug() {
    underTest.unavailable(failure());
    now += IpAllowlistUnavailableLog.INTERVAL_MS - 1;
    underTest.unavailable(failure());
    underTest.unavailable(failure());

    assertThat(logTester.logs(Level.WARN)).hasSize(1);
    assertThat(logTester.logs(Level.DEBUG)).hasSize(2).allMatch(line -> line.contains("IP allowlist"));
  }

  @Test
  public void failure_after_the_interval_is_warned_again_with_the_number_left_out() {
    underTest.unavailable(failure());
    underTest.unavailable(failure());
    underTest.unavailable(failure());
    now += IpAllowlistUnavailableLog.INTERVAL_MS;

    underTest.unavailable(failure());

    List<String> warnings = logTester.logs(Level.WARN);
    assertThat(warnings).hasSize(2);
    assertThat(warnings.get(1)).contains("2 more");
  }

  @Test(timeout = 20_000)
  public void many_threads_failing_at_once_give_one_warning() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<?>> done = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        done.add(pool.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          for (int j = 0; j < 50; j++) {
            underTest.unavailable(failure());
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> f : done) {
        f.get(10, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(logTester.logs(Level.WARN)).hasSize(1);
  }

  private static OrganizationIpAccessUnavailableException failure() {
    return new OrganizationIpAccessUnavailableException("Cannot read the IP allowlist", new IllegalStateException("connection refused to db-host:5432"));
  }
}
