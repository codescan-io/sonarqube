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
package org.sonar.server.app;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.process.NetworkUtilsImpl;
import org.sonar.process.Props;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TomcatRemoteIpTest {

  private static final String PROPERTY = "sonar.web.trustedProxiesRegex";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final Tomcat mockTomcat = mock(Tomcat.class, Mockito.RETURNS_DEEP_STUBS);
  private Tomcat realTomcat;

  @After
  public void stopTomcat() throws Exception {
    if (realTomcat != null) {
      realTomcat.stop();
      realTomcat.destroy();
    }
  }

  @Test
  public void configure_adds_remote_ip_valve_on_engine_without_protocol_header() {
    new TomcatRemoteIp().configure(mockTomcat, new Props(new Properties()));

    RemoteIpValve valve = captureValve();
    assertThat(valve.getProtocolHeader()).isNull();
    assertThat(valve.getInternalProxies()).isEqualTo(new RemoteIpValve().getInternalProxies());
  }

  @Test
  public void configure_keeps_default_internal_proxies_when_property_is_blank() {
    new TomcatRemoteIp().configure(mockTomcat, props("   "));

    assertThat(captureValve().getInternalProxies()).isEqualTo(new RemoteIpValve().getInternalProxies());
  }

  @Test
  public void configure_uses_trusted_proxies_property_when_set() {
    new TomcatRemoteIp().configure(mockTomcat, props("10\\.1\\.\\d{1,3}\\.\\d{1,3}"));

    assertThat(captureValve().getInternalProxies()).isEqualTo("10\\.1\\.\\d{1,3}\\.\\d{1,3}");
  }

  @Test
  public void configure_trims_trusted_proxies_property_when_valve_is_built_from_untrimmed_value() {
    Props untrimmed = mock(Props.class);
    Mockito.when(untrimmed.value(PROPERTY)).thenReturn(" \t10\\.1\\.\\d{1,3}\\.\\d{1,3}\n ");

    new TomcatRemoteIp().configure(mockTomcat, untrimmed);

    assertThat(captureValve().getInternalProxies()).isEqualTo("10\\.1\\.\\d{1,3}\\.\\d{1,3}");
  }

  @Test
  public void configure_fails_naming_the_property_when_regex_is_invalid() {
    Props invalid = props("10\\.(");

    assertThatThrownBy(() -> new TomcatRemoteIp().configure(mockTomcat, invalid))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(PROPERTY)
      .hasCauseInstanceOf(java.util.regex.PatternSyntaxException.class);
  }

  @Test
  public void request_from_trusted_peer_skips_trusted_entries_right_to_left() throws Exception {
    startTomcat(new Properties());

    Echo echo = get("1.2.3.4, 203.0.113.9, 10.0.0.5");

    assertThat(echo.remote).isEqualTo("203.0.113.9");
  }

  @Test
  public void request_with_single_forwarded_entry_uses_it_as_client() throws Exception {
    startTomcat(new Properties());

    Echo echo = get("203.0.113.9");

    assertThat(echo.remote).isEqualTo("203.0.113.9");
  }

  @Test
  public void request_without_forwarded_header_keeps_peer_address() throws Exception {
    startTomcat(new Properties());

    Echo echo = get(null);

    assertThat(echo.remote).isEqualTo("127.0.0.1");
  }

  @Test
  public void forwarded_for_header_is_left_as_sent_for_log_and_audit_readers() throws Exception {
    startTomcat(new Properties());

    assertThat(get("1.2.3.4, 203.0.113.9, 10.0.0.5").forwardedFor).isEqualTo("1.2.3.4, 203.0.113.9, 10.0.0.5");
    assertThat(get("203.0.113.9").forwardedFor).isEqualTo("203.0.113.9");
    assertThat(get(null).forwardedFor).isEqualTo("null");
  }

  @Test
  public void client_sent_copy_of_internal_header_is_ignored() throws Exception {
    startTomcat(new Properties());

    assertThat(get(null, TomcatRemoteIp.CLIENT_CHAIN_HEADER, "9.9.9.9").remote).isEqualTo("127.0.0.1");
    assertThat(get("203.0.113.9", TomcatRemoteIp.CLIENT_CHAIN_HEADER, "9.9.9.9").remote).isEqualTo("203.0.113.9");
  }

  @Test
  public void protocol_header_is_ignored_so_scheme_is_unchanged() throws Exception {
    startTomcat(new Properties());

    Echo echo = get("203.0.113.9", "X-Forwarded-Proto", "https");

    assertThat(echo.scheme).isEqualTo("http");
    assertThat(echo.secure).isEqualTo("false");
  }

  @Test
  public void peer_not_matching_trusted_proxies_property_is_not_trusted() throws Exception {
    Properties p = new Properties();
    p.setProperty(PROPERTY, "10\\.9\\.9\\.9");
    startTomcat(p);

    Echo echo = get("1.2.3.4, 203.0.113.9");

    assertThat(echo.remote).isEqualTo("127.0.0.1");
  }

  @Test
  public void trusted_proxies_property_replaces_default_list() throws Exception {
    Properties p = new Properties();
    p.setProperty(PROPERTY, "127\\.0\\.0\\.1|203\\.0\\.113\\.9");
    startTomcat(p);

    Echo echo = get("1.2.3.4, 203.0.113.9");

    assertThat(echo.remote).isEqualTo("1.2.3.4");
  }

  private Props props(String trustedProxies) {
    Properties p = new Properties();
    p.setProperty(PROPERTY, trustedProxies);
    return new Props(p);
  }

  private RemoteIpValve captureValve() {
    ArgumentCaptor<Valve> captor = ArgumentCaptor.forClass(Valve.class);
    verify(mockTomcat.getEngine().getPipeline()).addValve(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RemoteIpValve.class);
    return (RemoteIpValve) captor.getValue();
  }

  private int port;

  private void startTomcat(Properties properties) throws Exception {
    port = NetworkUtilsImpl.INSTANCE.getNextLoopbackAvailablePort();
    File base = temp.newFolder();
    realTomcat = new Tomcat();
    realTomcat.setBaseDir(base.getAbsolutePath());
    realTomcat.setSilent(true);
    realTomcat.setPort(port);
    realTomcat.getConnector().setProperty("address", InetAddress.getLoopbackAddress().getHostAddress());
    realTomcat.getHost().setAppBase(base.getAbsolutePath());
    new TomcatRemoteIp().configure(realTomcat, new Props(properties));
    Context context = realTomcat.addContext("", temp.newFolder().getAbsolutePath());
    Tomcat.addServlet(context, "echo", new EchoServlet());
    context.addServletMappingDecoded("/*", "echo");
    realTomcat.start();
  }

  private Echo get(String forwardedFor, String... otherHeaders) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/echo").openConnection();
    connection.setConnectTimeout(5_000);
    connection.setReadTimeout(5_000);
    if (forwardedFor != null) {
      connection.setRequestProperty("X-Forwarded-For", forwardedFor);
    }
    for (int i = 0; i < otherHeaders.length; i += 2) {
      connection.setRequestProperty(otherHeaders[i], otherHeaders[i + 1]);
    }
    try {
      return new Echo(IOUtils.toString(connection.getInputStream(), StandardCharsets.UTF_8));
    } finally {
      connection.disconnect();
    }
  }

  private static class EchoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/plain");
      resp.getWriter().print(req.getRemoteAddr() + "|" + req.getHeader("X-Forwarded-For") + "|" + req.getScheme() + "|" + req.isSecure());
    }
  }

  private static class Echo {
    final String remote;
    final String forwardedFor;
    final String scheme;
    final String secure;

    Echo(String body) {
      String[] parts = body.split("\\|", -1);
      remote = parts[0];
      forwardedFor = parts[1];
      scheme = parts[2];
      secure = parts[3];
    }
  }
}
