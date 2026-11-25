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
package org.sonar.application;

import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.application.command.CommandFactoryImpl;
import org.sonar.application.command.JavaCommand;
import org.sonar.core.extension.ServiceLoaderWrapper;
import org.sonar.process.ProcessProperties;
import org.sonar.process.Props;
import org.sonar.process.System2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AppTest {

  private static final String ENV_VAR = "CODESCAN_TRUSTED_PROXIES";
  private static final String PROPERTY = "sonar.web.trustedProxiesRegex";
  private static final String LOAD_BALANCER = "10\\.0\\.0\\.2";
  private static final String MCP_ENV_VAR = "MCP_KEYS";
  private static final String MCP_PROPERTY = "sonar.codescan.mcpKeys";
  private static final String MCP_KEYS = "first-key-0123456789abcdefghijklmnop,second-key-0123456789abcdefghijklmno";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void shared_trusted_proxies_variable_sets_the_web_server_property() throws IOException {
    Properties props = App.buildDefaultProperties(env(ENV_VAR, LOAD_BALANCER), new Properties());

    assertThat(props.getProperty(PROPERTY)).isEqualTo(LOAD_BALANCER);
  }

  @Test
  public void shared_trusted_proxies_variable_is_trimmed() throws IOException {
    Properties props = App.buildDefaultProperties(env(ENV_VAR, "  " + LOAD_BALANCER + " \n"), new Properties());

    assertThat(props.getProperty(PROPERTY)).isEqualTo(LOAD_BALANCER);
  }

  @Test
  public void property_is_left_out_when_the_variable_is_missing_or_blank() throws IOException {
    assertThat(App.buildDefaultProperties(new HashMap<>(), new Properties()).getProperty(PROPERTY)).isNull();
    assertThat(App.buildDefaultProperties(env(ENV_VAR, "   "), new Properties()).getProperty(PROPERTY)).isNull();
    assertThat(App.buildDefaultProperties(env(ENV_VAR, ""), new Properties()).getProperty(PROPERTY)).isNull();
  }

  @Test
  public void explicit_prop_environment_variable_wins_over_the_shared_variable() throws IOException {
    Map<String, String> env = env(ENV_VAR, LOAD_BALANCER);
    env.put("prop." + PROPERTY, "192\\.168\\.0\\.1");

    assertThat(App.buildDefaultProperties(env, new Properties()).getProperty(PROPERTY)).isEqualTo("192\\.168\\.0\\.1");
  }

  @Test
  public void explicit_prop_system_property_wins_over_the_shared_variable() throws IOException {
    Properties systemProperties = new Properties();
    systemProperties.setProperty("prop." + PROPERTY, "192\\.168\\.0\\.1");

    assertThat(App.buildDefaultProperties(env(ENV_VAR, LOAD_BALANCER), systemProperties).getProperty(PROPERTY)).isEqualTo("192\\.168\\.0\\.1");
  }

  @Test
  public void sonarqube_properties_entry_wins_over_the_shared_variable() throws IOException {
    Map<String, String> env = env(ENV_VAR, LOAD_BALANCER);
    env.put("SONARQUBE_PROPERTIES", "sonar.web.javaOpts=-Xmx1G;" + PROPERTY + "=192\\.168\\.0\\.1");

    Properties props = App.buildDefaultProperties(env, new Properties());

    assertThat(props.getProperty(PROPERTY)).isEqualTo("192\\.168\\.0\\.1");
    assertThat(props.getProperty("sonar.web.javaOpts")).isEqualTo("-Xmx1G");
  }

  @Test
  public void shared_variable_reaches_the_arguments_of_the_web_server_process() throws IOException {
    Properties generated = App.buildDefaultProperties(env(ENV_VAR, LOAD_BALANCER), new Properties());
    // conf/sonar.properties is written with Properties.store and read back with Properties.load
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    generated.store(file, "conf/sonar.properties");
    Properties loaded = new Properties();
    loaded.load(new ByteArrayInputStream(file.toByteArray()));
    File homeDir = temp.newFolder();
    File tempDir = temp.newFolder();
    loaded.setProperty("sonar.path.home", homeDir.getAbsolutePath());
    loaded.setProperty("sonar.path.temp", tempDir.getAbsolutePath());
    loaded.setProperty("sonar.path.logs", temp.newFolder().getAbsolutePath());
    Props props = new Props(loaded);
    ServiceLoaderWrapper serviceLoaderWrapper = mock(ServiceLoaderWrapper.class);
    when(serviceLoaderWrapper.load()).thenReturn(ImmutableSet.of());
    new ProcessProperties(serviceLoaderWrapper).completeDefaults(props);

    JavaCommand<?> web = new CommandFactoryImpl(props, tempDir, System2.INSTANCE).createWebCommand(true);

    assertThat(web.getReadsArgumentsFromFile()).isTrue();
    assertThat(web.getArguments()).containsEntry(PROPERTY, LOAD_BALANCER);
  }

  @Test
  public void shared_mcp_keys_variable_sets_the_web_server_property() throws IOException {
    Properties props = App.buildDefaultProperties(env(MCP_ENV_VAR, "  " + MCP_KEYS + " \n"), new Properties());

    assertThat(props.getProperty(MCP_PROPERTY)).isEqualTo(MCP_KEYS);
  }

  @Test
  public void mcp_keys_property_is_left_out_when_the_variable_is_missing_or_blank() throws IOException {
    assertThat(App.buildDefaultProperties(new HashMap<>(), new Properties()).getProperty(MCP_PROPERTY)).isNull();
    assertThat(App.buildDefaultProperties(env(MCP_ENV_VAR, "   "), new Properties()).getProperty(MCP_PROPERTY)).isNull();
  }

  @Test
  public void explicit_prop_environment_variable_wins_over_the_shared_mcp_keys_variable() throws IOException {
    Map<String, String> env = env(MCP_ENV_VAR, MCP_KEYS);
    env.put("prop." + MCP_PROPERTY, "explicit-key-0123456789abcdefghijklmnop");

    assertThat(App.buildDefaultProperties(env, new Properties()).getProperty(MCP_PROPERTY))
      .isEqualTo("explicit-key-0123456789abcdefghijklmnop");
  }

  @Test
  public void sonarqube_properties_entry_wins_over_the_shared_mcp_keys_variable() throws IOException {
    Map<String, String> env = env(MCP_ENV_VAR, MCP_KEYS);
    env.put("SONARQUBE_PROPERTIES", MCP_PROPERTY + "=explicit-key-0123456789abcdefghijklmnop");

    assertThat(App.buildDefaultProperties(env, new Properties()).getProperty(MCP_PROPERTY))
      .isEqualTo("explicit-key-0123456789abcdefghijklmnop");
  }

  @Test
  public void shared_default_ranges_variable_sets_the_web_server_property() throws IOException {
    Properties props = App.buildDefaultProperties(env("IP_ALLOWLIST_DEFAULT_RANGES", " 192.0.2.0/24,198.51.100.7 "), new Properties());

    assertThat(props.getProperty("sonar.codescan.ipAllowlistDefaultRanges")).isEqualTo("192.0.2.0/24,198.51.100.7");
  }

  @Test
  public void explicit_prop_environment_variable_wins_over_the_shared_default_ranges_variable() throws IOException {
    Map<String, String> env = env("IP_ALLOWLIST_DEFAULT_RANGES", "192.0.2.0/24");
    env.put("prop.sonar.codescan.ipAllowlistDefaultRanges", "198.51.100.0/24");

    assertThat(App.buildDefaultProperties(env, new Properties()).getProperty("sonar.codescan.ipAllowlistDefaultRanges"))
      .isEqualTo("198.51.100.0/24");
  }

  private static Map<String, String> env(String key, String value) {
    Map<String, String> env = new HashMap<>();
    env.put(key, value);
    return env;
  }
}
