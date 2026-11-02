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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogTester;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.process.NetworkUtilsImpl;
import org.sonar.process.Props;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TomcatRemoteIpTest {

  private static final String PROPERTY = "sonar.web.trustedProxiesRegex";
  private static final String ENV_VAR = "CODESCAN_TRUSTED_PROXIES";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public LogTester logTester = new LogTester();

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
  public void configure_warns_when_trusted_proxies_property_is_missing_or_blank() {
    new TomcatRemoteIp().configure(mockTomcat, new Props(new Properties()));
    new TomcatRemoteIp().configure(mockTomcat, props("   "));

    assertThat(logTester.logs(Level.WARN)).filteredOn(l -> l.startsWith(ENV_VAR + " is not set")).hasSize(2);
  }

  @Test
  public void warning_says_the_ip_allowlist_cannot_be_turned_on_and_blocks_only_while_it_is_on() {
    new TomcatRemoteIp().configure(mockTomcat, new Props(new Properties()));

    assertThat(logTester.logs(Level.WARN)).containsExactly(ENV_VAR + " is not set: X-Forwarded-For is taken from any private address. "
      + "The IP allowlist cannot be turned on, and while codescan.cloud.ipAllowlist.enabled is true every organization with an IP allowlist "
      + "blocks every request it applies to, until it is set to the addresses of the load balancer");
  }

  @Test
  public void configure_does_not_warn_when_trusted_proxies_property_is_set() {
    new TomcatRemoteIp().configure(mockTomcat, props("10\\.1\\.\\d{1,3}\\.\\d{1,3}"));

    assertThat(logTester.logs(Level.WARN)).isEmpty();
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
  public void configure_fails_naming_the_environment_variable_and_the_property_when_regex_is_invalid() {
    Props invalid = props("10\\.(");

    assertThatThrownBy(() -> new TomcatRemoteIp().configure(mockTomcat, invalid))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(ENV_VAR)
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
  public void request_with_100_headers_including_forwarded_for_is_served() throws Exception {
    startTomcat(new Properties());
    List<String> headers = new ArrayList<>();
    headers.add("X-Forwarded-For: 1.2.3.4, 203.0.113.9");
    for (int i = 0; i < 97; i++) {
      headers.add("X-Filler-" + i + ": v");
    }

    Raw raw = raw(headers);

    assertThat(raw.status).isEqualTo(200);
    assertThat(raw.echo.remote).isEqualTo("203.0.113.9");
    assertThat(raw.echo.forwardedFor).isEqualTo("1.2.3.4, 203.0.113.9");
  }

  @Test
  public void request_with_many_forwarded_for_lines_at_the_header_limit_is_served() throws Exception {
    startTomcat(new Properties());
    List<String> headers = new ArrayList<>();
    for (int i = 1; i <= 98; i++) {
      headers.add("X-Forwarded-For: 203.0.113." + i);
    }

    Raw raw = raw(headers);

    assertThat(raw.status).isEqualTo(200);
    assertThat(raw.echo.remote).isEqualTo("203.0.113.98");
    assertThat(raw.echo.forwardedFor.split(";")).hasSize(98).startsWith("203.0.113.1", "203.0.113.2").endsWith("203.0.113.98");
  }

  @Test
  public void several_forwarded_for_lines_are_read_as_one_chain_and_left_as_sent() throws Exception {
    startTomcat(new Properties());

    Raw raw = raw(List.of("X-Forwarded-For: 1.2.3.4", "X-Forwarded-For: 5.6.7.8, 203.0.113.9", "X-Forwarded-For: 10.0.0.5"));

    assertThat(raw.status).isEqualTo(200);
    assertThat(raw.echo.remote).isEqualTo("203.0.113.9");
    assertThat(raw.echo.forwardedFor).isEqualTo("1.2.3.4;5.6.7.8, 203.0.113.9;10.0.0.5");
  }

  @Test
  public void ipv6_client_address_in_the_chain_is_used() throws Exception {
    startTomcat(new Properties());

    assertThat(raw(List.of("X-Forwarded-For: 2001:db8::1, 10.0.0.5")).echo.remote).isEqualTo("2001:db8::1");
    assertThat(raw(List.of("X-Forwarded-For: 203.0.113.9, ::1")).echo.remote).isEqualTo("203.0.113.9");
  }

  @Test
  public void non_address_text_in_the_chain_is_returned_as_the_client_address() throws Exception {
    startTomcat(new Properties());

    assertThat(raw(List.of("X-Forwarded-For: not-an-ip, 10.0.0.5")).echo.remote).isEqualTo("not-an-ip");
    assertThat(raw(List.of("X-Forwarded-For: 1.2.3.4, unknown")).echo.remote).isEqualTo("unknown");
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

  @Test
  public void restoring_valve_still_passes_the_request_on_when_restoring_fails() throws Exception {
    Request request = mock(Request.class);
    Response response = mock(Response.class);
    Mockito.when(request.getNote(TomcatRemoteIp.ORIGINAL_FORWARDED_FOR_NOTE)).thenReturn(List.of("1.2.3.4"));
    Mockito.when(request.getCoyoteRequest()).thenThrow(new IllegalStateException("boom"));
    Valve next = mock(Valve.class);
    TomcatRemoteIp.ForwardedForRestoringValve valve = new TomcatRemoteIp.ForwardedForRestoringValve();
    valve.setNext(next);

    valve.invoke(request, response);

    verify(next).invoke(request, response);
  }

  @Test
  public void restoring_valve_passes_the_request_on_when_nothing_was_remembered() throws Exception {
    Request request = mock(Request.class);
    Response response = mock(Response.class);
    Valve next = mock(Valve.class);
    TomcatRemoteIp.ForwardedForRestoringValve valve = new TomcatRemoteIp.ForwardedForRestoringValve();
    valve.setNext(next);

    valve.invoke(request, response);

    verify(next).invoke(request, response);
    verify(request, Mockito.never()).getCoyoteRequest();
  }

  private Props props(String trustedProxies) {
    Properties p = new Properties();
    p.setProperty(PROPERTY, trustedProxies);
    return new Props(p);
  }

  private RemoteIpValve captureValve() {
    ArgumentCaptor<Valve> captor = ArgumentCaptor.forClass(Valve.class);
    verify(mockTomcat.getEngine().getPipeline(), times(2)).addValve(captor.capture());
    assertThat(captor.getAllValues().get(0)).isInstanceOf(RemoteIpValve.class);
    return (RemoteIpValve) captor.getAllValues().get(0);
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

  private Raw raw(List<String> headerLines) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
      socket.setSoTimeout(5_000);
      StringBuilder request = new StringBuilder("GET /echo HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n");
      headerLines.forEach(line -> request.append(line).append("\r\n"));
      request.append("\r\n");
      OutputStream out = socket.getOutputStream();
      out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
      out.flush();
      String response = IOUtils.toString(socket.getInputStream(), StandardCharsets.UTF_8);
      int status = Integer.parseInt(response.split(" ", 3)[1]);
      int bodyStart = response.indexOf("\r\n\r\n");
      String body = bodyStart < 0 ? "" : response.substring(bodyStart + 4);
      return new Raw(status, status == 200 ? new Echo(body) : null);
    }
  }

  private static class Raw {
    final int status;
    final Echo echo;

    Raw(int status, Echo echo) {
      this.status = status;
      this.echo = echo;
    }
  }

  private static class EchoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      List<String> forwardedFor = Collections.list(req.getHeaders("X-Forwarded-For"));
      String body = req.getRemoteAddr() + "|" + (forwardedFor.isEmpty() ? "null" : String.join(";", forwardedFor)) + "|" + req.getScheme() + "|" + req.isSecure();
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      resp.setContentType("text/plain");
      resp.setContentLength(bytes.length);
      resp.getOutputStream().write(bytes);
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
