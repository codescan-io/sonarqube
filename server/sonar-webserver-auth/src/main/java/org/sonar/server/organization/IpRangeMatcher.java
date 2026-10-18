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

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * An IPv4 or IPv6 network that can tell whether a client address is inside it.
 *
 * <p>The rules are those of codescanng's {@code IpRange}, so the fork and the admin API agree on every address:
 * only address literals are parsed (a host name is never resolved), an IPv4-mapped IPv6 client address is
 * compared in its IPv4 form, IPv6 digits may be in either case, and the two address families never match.
 * Both families are parsed here instead of by {@code InetAddress}, which falls back to a DNS lookup for text
 * it does not recognise as a literal.
 */
final class IpRangeMatcher {

  private static final String OCTET = "(0|[1-9]\\d{0,2})";
  private static final Pattern IPV4 = Pattern.compile(OCTET + "\\." + OCTET + "\\." + OCTET + "\\." + OCTET);
  private static final Pattern HEX_GROUP = Pattern.compile("[0-9a-fA-F]{1,4}");
  private static final Pattern PREFIX = Pattern.compile("0|[1-9]\\d{0,2}");

  private final byte[] network;
  private final int prefixLength;

  private IpRangeMatcher(byte[] network, int prefixLength) {
    this.network = network;
    this.prefixLength = prefixLength;
  }

  /**
   * A stored allowlist range. Host bits after the prefix are an error, as in codescanng, which stores
   * ranges already normalised.
   *
   * @throws IllegalArgumentException when {@code cidr} is not a valid network
   */
  static IpRangeMatcher parse(@Nullable String cidr) {
    Parsed parsed = parseParts(cidr);
    byte[] masked = mask(parsed.address, parsed.prefixLength);
    if (!Arrays.equals(masked, parsed.address)) {
      throw new IllegalArgumentException(parsed.text + " has bits set after the /" + parsed.prefixLength + " prefix");
    }
    return new IpRangeMatcher(masked, parsed.prefixLength);
  }

  /**
   * A network setting: host bits are masked off, as codescanng's internal-network setting is.
   *
   * @throws IllegalArgumentException when {@code cidr} is not a valid network
   */
  static IpRangeMatcher parseLenient(@Nullable String cidr) {
    Parsed parsed = parseParts(cidr);
    return new IpRangeMatcher(mask(parsed.address, parsed.prefixLength), parsed.prefixLength);
  }

  /** Surrounding spaces, including no-break spaces, are ignored, as {@code IpRange.Address.parse} does. */
  boolean contains(@Nullable String ip) {
    return ip != null && containsLiteral(stripSpaces(ip));
  }

  /** Like {@link #contains(String)}, but the text must be exactly an address literal, with no spaces around it. */
  boolean containsLiteral(@Nullable String ip) {
    if (ip == null) {
      return false;
    }
    byte[] bytes = parseLiteral(ip);
    if (bytes == null) {
      return false;
    }
    byte[] candidate = isIpv4Mapped(bytes) ? Arrays.copyOfRange(bytes, 12, 16) : bytes;
    return candidate.length == network.length && Arrays.equals(mask(candidate, prefixLength), network);
  }

  private record Parsed(String text, byte[] address, int prefixLength) {
  }

  private static Parsed parseParts(@Nullable String input) {
    String text = input == null ? "" : stripSpaces(input);
    if (text.isEmpty()) {
      throw new IllegalArgumentException("Empty IP range");
    }
    int slash = text.indexOf('/');
    String addressText = slash < 0 ? text : text.substring(0, slash);
    byte[] address = parseLiteral(addressText);
    if (address == null) {
      throw new IllegalArgumentException("'" + addressText + "' is not a valid IP address");
    }
    if (isIpv4Mapped(address)) {
      throw new IllegalArgumentException("'" + addressText + "' is an IPv4-mapped address");
    }
    int maxPrefix = address.length * 8;
    if (slash < 0) {
      return new Parsed(text, address, maxPrefix);
    }
    String prefixText = text.substring(slash + 1);
    if (!PREFIX.matcher(prefixText).matches() || Integer.parseInt(prefixText) > maxPrefix) {
      throw new IllegalArgumentException(text + " has an invalid prefix length");
    }
    return new Parsed(text, address, Integer.parseInt(prefixText));
  }

  private static String stripSpaces(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && isSpace(text.charAt(start))) {
      start++;
    }
    while (end > start && isSpace(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(start, end);
  }

  private static boolean isSpace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  /** The 4 or 16 bytes of an address literal, or null when {@code text} is not one. */
  private static byte[] parseLiteral(String text) {
    byte[] v4 = parseIpv4(text);
    if (v4 != null) {
      return v4;
    }
    return text.indexOf(':') >= 0 ? parseIpv6(text) : null;
  }

  private static byte[] parseIpv4(String text) {
    Matcher v4 = IPV4.matcher(text);
    if (!v4.matches()) {
      return null;
    }
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      int octet = Integer.parseInt(v4.group(i + 1));
      if (octet > 255) {
        return null;
      }
      bytes[i] = (byte) octet;
    }
    return bytes;
  }

  private static byte[] parseIpv6(String text) {
    int gap = text.indexOf("::");
    if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) {
      return null;
    }
    int[] head = parseGroups(gap < 0 ? text : text.substring(0, gap), gap < 0);
    int[] tail = gap < 0 ? new int[0] : parseGroups(text.substring(gap + 2), true);
    if (head == null || tail == null) {
      return null;
    }
    int count = head.length + tail.length;
    if (gap < 0 ? count != 8 : count > 7) {
      return null;
    }
    int[] groups = new int[8];
    System.arraycopy(head, 0, groups, 0, head.length);
    System.arraycopy(tail, 0, groups, 8 - tail.length, tail.length);
    byte[] bytes = new byte[16];
    for (int i = 0; i < 8; i++) {
      bytes[2 * i] = (byte) (groups[i] >> 8);
      bytes[2 * i + 1] = (byte) groups[i];
    }
    return bytes;
  }

  /**
   * The 16-bit groups of one side of an IPv6 literal, or null if any group is malformed. A dotted IPv4 address
   * counts as two groups and is allowed only as the last group of the whole literal.
   */
  private static int[] parseGroups(String part, boolean ipv4AllowedLast) {
    if (part.isEmpty()) {
      return new int[0];
    }
    String[] pieces = part.split(":", -1);
    int[] groups = new int[pieces.length + 1];
    int count = 0;
    for (int i = 0; i < pieces.length; i++) {
      String piece = pieces[i];
      if (HEX_GROUP.matcher(piece).matches()) {
        groups[count++] = Integer.parseInt(piece, 16);
        continue;
      }
      byte[] v4 = ipv4AllowedLast && i == pieces.length - 1 ? parseIpv4(piece) : null;
      if (v4 == null) {
        return null;
      }
      groups[count++] = ((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF);
      groups[count++] = ((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF);
    }
    return Arrays.copyOf(groups, count);
  }

  private static boolean isIpv4Mapped(byte[] address) {
    if (address.length != 16 || address[10] != (byte) 0xFF || address[11] != (byte) 0xFF) {
      return false;
    }
    for (int i = 0; i < 10; i++) {
      if (address[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private static byte[] mask(byte[] address, int prefixLength) {
    byte[] masked = address.clone();
    for (int i = 0; i < masked.length; i++) {
      int bitsInThisByte = Math.max(0, Math.min(8, prefixLength - i * 8));
      masked[i] = (byte) (masked[i] & (0xFF << (8 - bitsInThisByte)));
    }
    return masked;
  }
}
