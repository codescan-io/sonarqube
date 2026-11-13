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
package org.sonar.server.authentication;

import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.event.Level;
import org.sonar.api.server.http.HttpRequest;
import org.sonar.api.testfixtures.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HostedMcpRequestTest {

  private static final String KEY = "k".repeat(40);
  private static final String OTHER_KEY = "o".repeat(40);

  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.DEBUG);

  private final HttpRequest request = mock(HttpRequest.class);

  private HostedMcpRequest underTest(String keys) {
    Properties processProperties = new Properties();
    processProperties.setProperty(HostedMcpRequest.KEYS_PROPERTY, keys);
    return new HostedMcpRequest(processProperties);
  }

  private void presentKey(String key) {
    when(request.getHeader(HostedMcpRequest.MCP_KEY_HEADER)).thenReturn(key);
  }

  @Test
  public void header_name_and_setting_match_the_hosted_mcp_server() {
    assertThat(HostedMcpRequest.MCP_KEY_HEADER).isEqualTo("X-CodeScan-MCP-Key");
    assertThat(HostedMcpRequest.KEYS_PROPERTY).isEqualTo("sonar.codescan.mcpKeys");
  }

  @Test
  public void accepts_a_configured_key_among_several() {
    presentKey(KEY);

    assertThat(underTest(OTHER_KEY + "," + KEY).isHostedMcp(request)).isTrue();
  }

  @Test
  public void accepts_each_configured_key_so_one_can_be_replaced_without_downtime() {
    HostedMcpRequest check = underTest(OTHER_KEY + "," + KEY);

    presentKey(OTHER_KEY);
    assertThat(check.isHostedMcp(request)).isTrue();
    presentKey(KEY);
    assertThat(check.isHostedMcp(request)).isTrue();
  }

  @Test
  public void spaces_around_configured_keys_are_ignored() {
    presentKey(KEY);

    assertThat(underTest(" " + OTHER_KEY + " , " + KEY + " ").isHostedMcp(request)).isTrue();
  }

  @Test
  public void rejects_wrong_missing_and_blank_keys() {
    HostedMcpRequest check = underTest(KEY);
    when(request.getHeader(HostedMcpRequest.MCP_KEY_HEADER)).thenReturn("x".repeat(40), null, "");

    assertThat(check.isHostedMcp(request)).isFalse();
    assertThat(check.isHostedMcp(request)).isFalse();
    assertThat(check.isHostedMcp(request)).isFalse();
  }

  @Test
  public void rejects_a_longer_or_shorter_version_of_a_configured_key() {
    HostedMcpRequest check = underTest(KEY);

    presentKey(KEY + "k");
    assertThat(check.isHostedMcp(request)).isFalse();
    presentKey(KEY.substring(1));
    assertThat(check.isHostedMcp(request)).isFalse();
  }

  @Test
  public void never_matches_without_keys_or_with_short_keys() {
    presentKey("short");

    assertThat(new HostedMcpRequest(new Properties()).isHostedMcp(request)).isFalse();
    assertThat(underTest("short").isHostedMcp(request)).isFalse();
  }

  @Test
  public void key_one_character_shorter_than_the_minimum_is_ignored() {
    String almost = "a".repeat(HostedMcpRequest.MIN_KEY_LENGTH - 1);
    String enough = "b".repeat(HostedMcpRequest.MIN_KEY_LENGTH);
    HostedMcpRequest check = underTest(almost + "," + enough);

    presentKey(almost);
    assertThat(check.isHostedMcp(request)).isFalse();
    presentKey(enough);
    assertThat(check.isHostedMcp(request)).isTrue();
  }

  @Test
  public void short_keys_get_one_warning_that_names_neither_key() {
    underTest("tiny-key-one," + KEY + ",tiny-key-two");

    assertThat(logTester.logs(Level.WARN)).containsExactly("Ignoring 2 hosted MCP key(s) shorter than 32 characters");
    assertThat(logTester.logs()).noneMatch(line -> line.contains("tiny-key") || line.contains(KEY));
  }

  @Test
  public void checking_a_request_logs_neither_the_presented_nor_the_configured_key() {
    HostedMcpRequest check = underTest(KEY);
    presentKey(OTHER_KEY);
    check.isHostedMcp(request);
    presentKey(KEY);
    check.isHostedMcp(request);

    assertThat(logTester.logs()).isEmpty();
  }
}
