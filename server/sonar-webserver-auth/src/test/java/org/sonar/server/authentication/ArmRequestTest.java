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

public class ArmRequestTest {

  private static final String KEY = "k".repeat(40);
  private static final String OTHER_KEY = "o".repeat(40);

  @Rule
  public LogTester logTester = new LogTester().setLevel(Level.DEBUG);

  private final HttpRequest request = mock(HttpRequest.class);

  private ArmRequest underTest(String keys) {
    Properties processProperties = new Properties();
    processProperties.setProperty(ArmRequest.KEYS_PROPERTY, keys);
    return new ArmRequest(processProperties);
  }

  private void presentKey(String key) {
    when(request.getHeader(ArmRequest.ARM_KEY_HEADER)).thenReturn(key);
  }

  @Test
  public void header_name_and_setting_match_what_arm_sends() {
    assertThat(ArmRequest.ARM_KEY_HEADER).isEqualTo("X-CodeScan-ARM-Key");
    assertThat(ArmRequest.KEYS_PROPERTY).isEqualTo("sonar.codescan.armKeys");
  }

  @Test
  public void accepts_a_configured_key_among_several() {
    presentKey(KEY);

    assertThat(underTest(OTHER_KEY + "," + KEY).isArm(request)).isTrue();
  }

  @Test
  public void accepts_each_configured_key_so_one_can_be_replaced_without_downtime() {
    ArmRequest check = underTest(OTHER_KEY + "," + KEY);

    presentKey(OTHER_KEY);
    assertThat(check.isArm(request)).isTrue();
    presentKey(KEY);
    assertThat(check.isArm(request)).isTrue();
  }

  @Test
  public void spaces_around_configured_keys_are_ignored() {
    presentKey(KEY);

    assertThat(underTest(" " + OTHER_KEY + " , " + KEY + " ").isArm(request)).isTrue();
  }

  @Test
  public void rejects_wrong_missing_and_blank_keys() {
    ArmRequest check = underTest(KEY);
    when(request.getHeader(ArmRequest.ARM_KEY_HEADER)).thenReturn("x".repeat(40), null, "");

    assertThat(check.isArm(request)).isFalse();
    assertThat(check.isArm(request)).isFalse();
    assertThat(check.isArm(request)).isFalse();
  }

  @Test
  public void rejects_a_longer_or_shorter_version_of_a_configured_key() {
    ArmRequest check = underTest(KEY);

    presentKey(KEY + "k");
    assertThat(check.isArm(request)).isFalse();
    presentKey(KEY.substring(1));
    assertThat(check.isArm(request)).isFalse();
  }

  @Test
  public void never_matches_without_keys_or_with_short_keys() {
    presentKey("short");

    assertThat(new ArmRequest(new Properties()).isArm(request)).isFalse();
    assertThat(underTest("short").isArm(request)).isFalse();
  }

  @Test
  public void key_one_character_shorter_than_the_minimum_is_ignored() {
    String almost = "a".repeat(ArmRequest.MIN_KEY_LENGTH - 1);
    String enough = "b".repeat(ArmRequest.MIN_KEY_LENGTH);
    ArmRequest check = underTest(almost + "," + enough);

    presentKey(almost);
    assertThat(check.isArm(request)).isFalse();
    presentKey(enough);
    assertThat(check.isArm(request)).isTrue();
  }

  @Test
  public void short_keys_get_one_warning_that_names_neither_key() {
    underTest("tiny-key-one," + KEY + ",tiny-key-two");

    assertThat(logTester.logs(Level.WARN)).containsExactly("Ignoring 2 ARM key(s) shorter than 32 characters");
    assertThat(logTester.logs()).noneMatch(line -> line.contains("tiny-key") || line.contains(KEY));
  }

  @Test
  public void startup_logs_how_many_keys_are_configured_and_no_key() {
    underTest(KEY + "," + OTHER_KEY);

    assertThat(logTester.logs(Level.INFO)).containsExactly("ARM keys configured: 2");
    assertThat(logTester.logs()).noneMatch(line -> line.contains(KEY) || line.contains(OTHER_KEY));
  }

  @Test
  public void startup_count_leaves_out_keys_that_are_ignored_and_says_zero_when_there_are_none() {
    underTest("tiny-key," + KEY);
    assertThat(logTester.logs(Level.INFO)).containsExactly("ARM keys configured: 1");

    logTester.clear();
    new ArmRequest(new Properties());
    assertThat(logTester.logs(Level.INFO)).containsExactly("ARM keys configured: 0");
  }

  @Test
  public void checking_a_request_logs_neither_the_presented_nor_the_configured_key() {
    ArmRequest check = underTest(KEY);
    logTester.clear();
    presentKey(OTHER_KEY);
    check.isArm(request);
    presentKey(KEY);
    check.isArm(request);

    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  public void arm_key_sent_in_the_mcp_header_does_not_count() {
    when(request.getHeader(HostedMcpRequest.MCP_KEY_HEADER)).thenReturn(KEY);

    assertThat(underTest(KEY).isArm(request)).isFalse();
  }

  @Test
  public void mcp_key_sent_in_the_arm_header_does_not_count() {
    Properties processProperties = new Properties();
    processProperties.setProperty(HostedMcpRequest.KEYS_PROPERTY, OTHER_KEY);
    presentKey(OTHER_KEY);

    assertThat(new ArmRequest(processProperties).isArm(request)).isFalse();
  }
}
