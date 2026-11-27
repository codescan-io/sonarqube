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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IpRangeMatcherTest {

  @Test
  public void ipv4_range_contains_addresses_inside_only() {
    IpRangeMatcher range = IpRangeMatcher.parse("203.0.113.0/24");
    assertThat(range.contains("203.0.113.0")).isTrue();
    assertThat(range.contains("203.0.113.255")).isTrue();
    assertThat(range.contains("203.0.114.1")).isFalse();
  }

  @Test
  public void single_address_and_odd_prefix() {
    assertThat(IpRangeMatcher.parse("198.51.100.7/32").contains("198.51.100.7")).isTrue();
    assertThat(IpRangeMatcher.parse("198.51.100.7/32").contains("198.51.100.8")).isFalse();
    assertThat(IpRangeMatcher.parse("198.51.100.7").contains("198.51.100.7")).isTrue();
    assertThat(IpRangeMatcher.parse("198.51.100.7").contains("198.51.100.8")).isFalse();
    assertThat(IpRangeMatcher.parse("10.0.0.0/9").contains("10.127.255.255")).isTrue();
    assertThat(IpRangeMatcher.parse("10.0.0.0/9").contains("10.128.0.0")).isFalse();
  }

  @Test
  public void zero_prefix_contains_every_address_of_its_family() {
    assertThat(IpRangeMatcher.parse("0.0.0.0/0").contains("203.0.113.1")).isTrue();
    assertThat(IpRangeMatcher.parse("::/0").contains("2001:db8::1")).isTrue();
  }

  @Test
  public void ipv6_range() {
    IpRangeMatcher range = IpRangeMatcher.parse("2001:db8::/32");
    assertThat(range.contains("2001:db8:ffff::1")).isTrue();
    assertThat(range.contains("2001:db9::1")).isFalse();
  }

  @Test
  public void ipv6_is_compared_whatever_its_case_or_spelling() {
    assertThat(IpRangeMatcher.parse("2001:db8::/32").contains("2001:DB8:FFFF::1")).isTrue();
    assertThat(IpRangeMatcher.parse("2001:DB8::/32").contains("2001:db8::1")).isTrue();
    assertThat(IpRangeMatcher.parse("2001:db8::/32").contains("2001:0db8:0000:0000:0000:0000:0000:0001")).isTrue();
  }

  @Test
  public void families_never_match_each_other() {
    assertThat(IpRangeMatcher.parse("0.0.0.0/0").contains("2001:db8::1")).isFalse();
    assertThat(IpRangeMatcher.parse("::/0").contains("203.0.113.1")).isFalse();
    // same number as 172.31.4.5, but it is an IPv6 address
    assertThat(IpRangeMatcher.parse("172.31.0.0/16").contains("::ac1f:405")).isFalse();
  }

  @Test
  public void ipv4_mapped_ipv6_client_is_compared_as_ipv4() {
    assertThat(IpRangeMatcher.parse("203.0.113.0/24").contains("::ffff:203.0.113.9")).isTrue();
    assertThat(IpRangeMatcher.parse("203.0.113.0/24").contains("::FFFF:cb00:7109")).isTrue();
    assertThat(IpRangeMatcher.parse("203.0.113.0/24").contains("::ffff:203.0.114.9")).isFalse();
    assertThat(IpRangeMatcher.parse("::/0").contains("::ffff:203.0.113.9")).isFalse();
  }

  @Test
  public void surrounding_spaces_of_the_client_address_are_ignored() {
    assertThat(IpRangeMatcher.parse("203.0.113.0/24").contains(" 203.0.113.9 ")).isTrue();
    assertThat(IpRangeMatcher.parse("203.0.113.0/24").contains("\u00a0203.0.113.9")).isTrue();
  }

  @Test
  public void null_blank_or_hostname_never_matches() {
    IpRangeMatcher everything = IpRangeMatcher.parse("0.0.0.0/0");
    assertThat(everything.contains(null)).isFalse();
    assertThat(everything.contains("")).isFalse();
    assertThat(everything.contains("   ")).isFalse();
    assertThat(everything.contains("example.com")).isFalse();
  }

  @Test
  public void host_name_is_not_resolved() {
    // "localhost" resolves to 127.0.0.1, which this range holds. A lookup would make this true.
    assertThat(IpRangeMatcher.parse("127.0.0.0/8").contains("localhost")).isFalse();
    assertThat(IpRangeMatcher.parse("::/0").contains("ip6-localhost")).isFalse();
  }

  @Test
  public void malformed_client_addresses_never_match() {
    IpRangeMatcher v4 = IpRangeMatcher.parse("0.0.0.0/0");
    IpRangeMatcher v6 = IpRangeMatcher.parse("::/0");
    for (String bad : new String[] {"256.0.0.1", "1.2.3", "1.2.3.4.5", "010.0.0.1", "1.2.3.4/24", "1.2.3.4:80", "203.0.113.9, 10.0.0.1",
      "2001:db8::1::2", "2001:db8:::1", "12345::1", "2001:db8::g", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9"}) {
      assertThat(v4.contains(bad)).as(bad).isFalse();
      assertThat(v6.contains(bad)).as(bad).isFalse();
    }
  }

  @Test
  public void ipv6_zone_never_matches() {
    assertThat(IpRangeMatcher.parse("::/0").contains("fe80::1%eth0")).isFalse();
    assertThat(IpRangeMatcher.parse("fe80::/10").contains("fe80::1%1")).isFalse();
  }

  @Test
  public void parse_rejects_a_stored_range_that_is_not_a_valid_network() {
    for (String bad : new String[] {"", "  ", "not-an-ip", "example.com/24", "203.0.113.0/33", "203.0.113.0/", "203.0.113.0/-1",
      "203.0.113.0/024", "203.0.113.0/24/8", "203.0.113.5/24", "010.0.0.0/8", "2001:db8::/129", "2001:db8::1/32",
      "::ffff:203.0.113.0/120", "fe80::1%eth0/64", "2001:db8::g/32"}) {
      assertThatThrownBy(() -> IpRangeMatcher.parse(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void parse_rejects_null() {
    assertThatThrownBy(() -> IpRangeMatcher.parse(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void parseLenient_masks_host_bits_like_a_network_setting() {
    IpRangeMatcher range = IpRangeMatcher.parseLenient("172.31.5.9/16");
    assertThat(range.contains("172.31.200.1")).isTrue();
    assertThat(range.contains("172.32.0.1")).isFalse();
  }

  @Test
  public void parseLenient_rejects_what_is_not_an_address_literal() {
    for (String bad : new String[] {"", "not-an-ip/16", "localhost/8", "10.0.0.0/33", "fe80::1%eth0/64", "::ffff:10.0.0.0/104"}) {
      assertThatThrownBy(() -> IpRangeMatcher.parseLenient(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void containsLiteral_does_not_trim_spaces() {
    IpRangeMatcher range = IpRangeMatcher.parse("203.0.113.0/24");
    assertThat(range.containsLiteral("203.0.113.9")).isTrue();
    assertThat(range.containsLiteral(" 203.0.113.9")).isFalse();
    assertThat(range.containsLiteral("203.0.113.9 ")).isFalse();
  }
}
