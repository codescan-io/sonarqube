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
package org.sonar.server.issue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProliferationTaskPayloadTest {

  private static final List<String> KEYS = List.of("AYx8kP2mQ1sR7tV3wY8a", "AYx8kP2mQ1sR7tV3wY8b");

  @Test
  public void carriesKeysAndCommentBackUnchanged() {
    ProliferationTaskPayload payload = roundTrip(KEYS, "False alarm - generated code", null);

    assertThat(payload.issueKeys()).isEqualTo(KEYS);
    assertThat(payload.comment()).isEqualTo("False alarm - generated code");
    assertThat(payload.exceptionReason()).isNull();
  }

  @Test
  public void carriesAMarkdownCommentWithBlankLinesAndBullets() {
    // The reason the text is length-prefixed rather than delimited: every one of these lines would otherwise
    // come back as an issue key.
    String comment = "Reviewed with security on 12 Oct.\n"
      + "\n"
      + "* The scheduler is internal only\n"
      + "* See SEC-4471\n";

    ProliferationTaskPayload payload = roundTrip(KEYS, comment, null);

    assertThat(payload.issueKeys()).isEqualTo(KEYS);
    assertThat(payload.comment()).isEqualTo(comment);
  }

  @Test
  public void aCommentLineThatLooksLikeAnIssueKeyIsStillPartOfTheComment() {
    String comment = "See also:\nAYx8kP2mQ1sR7tV3wY8z\nwhich we already dismissed";

    ProliferationTaskPayload payload = roundTrip(KEYS, comment, null);

    assertThat(payload.issueKeys()).isEqualTo(KEYS);
    assertThat(payload.comment()).isEqualTo(comment);
  }

  @Test
  public void aCommentContainingTheMarkerIsStillPartOfTheComment() {
    String comment = "Pasted from the log:\n---CSN-PROLIFERATION-V1---\ncomment 99\nand the rest";

    ProliferationTaskPayload payload = roundTrip(KEYS, comment, null);

    assertThat(payload.issueKeys()).isEqualTo(KEYS);
    assertThat(payload.comment()).isEqualTo(comment);
  }

  @Test
  public void countsBytesRatherThanCharacters() {
    // café is 4 characters and 5 bytes; 日本語 is 3 and 9; the emoji is a surrogate pair and 4 bytes. A count of
    // characters would leave the parser out of step for everything after the comment.
    String comment = "café 日本語 👍 end";

    ProliferationTaskPayload payload = roundTrip(KEYS, comment, "réponse 中文");

    assertThat(payload.comment()).isEqualTo(comment);
    assertThat(payload.exceptionReason()).isEqualTo("réponse 中文");
    assertThat(payload.issueKeys()).isEqualTo(KEYS);
  }

  @Test
  public void carriesACommentAndAnExceptionReasonTogether() {
    ProliferationTaskPayload payload = roundTrip(KEYS, "the comment", "the reason");

    assertThat(payload.comment()).isEqualTo("the comment");
    assertThat(payload.exceptionReason()).isEqualTo("the reason");
  }

  @Test
  public void tellsAnEmptyTextApartFromAnAbsentOne() {
    ProliferationTaskPayload payload = roundTrip(KEYS, "", null);

    assertThat(payload.comment()).isEmpty();
    assertThat(payload.exceptionReason()).isNull();
  }

  @Test
  public void carriesATextFarLongerThanACharacteristicColumnHolds() {
    String comment = "x".repeat(10_000);

    ProliferationTaskPayload payload = roundTrip(KEYS, comment, null);

    assertThat(payload.comment()).hasSize(10_000);
    assertThat(payload.issueKeys()).isEqualTo(KEYS);
  }

  @Test
  public void withoutTextThePayloadIsWhatTheSubmitterUsedToWrite() {
    assertThat(bytesOf(ProliferationTaskPayload.serialize(KEYS, null, null)))
      .isEqualTo("AYx8kP2mQ1sR7tV3wY8a\nAYx8kP2mQ1sR7tV3wY8b");
  }

  @Test
  public void readsAPayloadWrittenBeforeThisFormatExisted() {
    byte[] old = "AYx8kP2mQ1sR7tV3wY8a\nAYx8kP2mQ1sR7tV3wY8b\n".getBytes(StandardCharsets.UTF_8);

    ProliferationTaskPayload payload = ProliferationTaskPayload.parse(old);

    assertThat(payload.issueKeys()).isEqualTo(KEYS);
    assertThat(payload.comment()).isNull();
    assertThat(payload.exceptionReason()).isNull();
  }

  @Test
  public void aReaderOfTheOldFormatStillFindsEveryKeyOfANewPayload() {
    // A compute engine rolled back while the web server stays: it reads every line as a key. The lines the
    // header and the comment add match no issue, so selectByKeys drops them and the marking still lands.
    String written = bytesOf(ProliferationTaskPayload.serialize(KEYS, "a\nmultiline\ncomment", null));

    List<String> asOldReaderWouldSee = written.lines().filter(line -> !line.isBlank()).toList();

    assertThat(asOldReaderWouldSee).containsAll(KEYS);
  }

  @Test
  public void readsAMalformedHeaderAsKeysRatherThanFailing() {
    // Losing a comment is better than losing the marking it belongs to.
    byte[] broken = ("AYx8kP2mQ1sR7tV3wY8a\n---CSN-PROLIFERATION-V1---\nnot-a-count\n")
      .getBytes(StandardCharsets.UTF_8);

    ProliferationTaskPayload payload = ProliferationTaskPayload.parse(broken);

    assertThat(payload.issueKeys()).contains("AYx8kP2mQ1sR7tV3wY8a");
    assertThat(payload.comment()).isNull();
  }

  @Test
  public void readsAHeaderThatPromisesMoreBytesThanItCarriesAsKeys() {
    byte[] truncated = ("AYx8kP2mQ1sR7tV3wY8a\n---CSN-PROLIFERATION-V1---\ncomment 9999\nreason -1\nshort")
      .getBytes(StandardCharsets.UTF_8);

    ProliferationTaskPayload payload = ProliferationTaskPayload.parse(truncated);

    assertThat(payload.issueKeys()).contains("AYx8kP2mQ1sR7tV3wY8a");
    assertThat(payload.comment()).isNull();
  }

  @Test
  public void handlesAnEmptyKeyList() {
    ProliferationTaskPayload payload = roundTrip(List.of(), "orphaned comment", null);

    assertThat(payload.issueKeys()).isEmpty();
    assertThat(payload.comment()).isEqualTo("orphaned comment");
  }

  @Test
  public void handlesAnEmptyPayload() {
    ProliferationTaskPayload payload = ProliferationTaskPayload.parse(new byte[0]);

    assertThat(payload.issueKeys()).isEmpty();
    assertThat(payload.comment()).isNull();
  }

  private static ProliferationTaskPayload roundTrip(List<String> keys, @Nullable String comment,
    @Nullable String exceptionReason) {
    try (InputStream stream = ProliferationTaskPayload.serialize(keys, comment, exceptionReason)) {
      return ProliferationTaskPayload.parse(stream.readAllBytes());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String bytesOf(InputStream stream) {
    try (InputStream open = stream) {
      return new String(open.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
