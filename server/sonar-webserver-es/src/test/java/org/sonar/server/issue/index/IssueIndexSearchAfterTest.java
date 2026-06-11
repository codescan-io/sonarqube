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
package org.sonar.server.issue.index;

import java.util.Arrays;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.es.SearchOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newPrivateProjectDto;
import static org.sonar.server.issue.IssueDocTesting.newDoc;

class IssueIndexSearchAfterTest extends IssueIndexTestCommon {

  @Test
  void search_after_continues_after_last_hit_sort_values() {
    ComponentDto project = newPrivateProjectDto();
    ComponentDto file1 = newFileDto(project, null, "F1").setPath("src/main/xoo/org/sonar/samples/File.xoo");
    ComponentDto file2 = newFileDto(project, null, "F2").setPath("src/main/xoo/org/sonar/samples/File2.xoo");

    indexIssues(
      newDoc("F1_2", project.uuid(), file1).setLine(20),
      newDoc("F1_1", project.uuid(), file1).setLine(null),
      newDoc("F1_3", project.uuid(), file1).setLine(25),
      newDoc("F2_1", project.uuid(), file2).setLine(9),
      newDoc("F2_2", project.uuid(), file2).setLine(109),
      newDoc("F2_3", project.uuid(), file2).setLine(109));

    SearchResponse firstPage = underTest.search(
      IssueQuery.builder().sort(IssueQuery.SORT_BY_FILE_LINE).asc(true).build(),
      new SearchOptions().setLimit(2));

    assertThat(Arrays.stream(firstPage.getHits().getHits()).map(SearchHit::getId).toList())
      .containsExactly("F1_1", "F1_2");
    assertThat(firstPage.getHits().getHits()[0].getSortValues()).doesNotContainNull();

    SearchResponse secondPage = underTest.search(
      IssueQuery.builder()
        .sort(IssueQuery.SORT_BY_FILE_LINE)
        .asc(true)
        .searchAfter(Arrays.stream(firstPage.getHits().getHits()[0].getSortValues()).map(String::valueOf).toList())
        .build(),
      new SearchOptions().setLimit(2));

    assertThat(Arrays.stream(secondPage.getHits().getHits()).map(SearchHit::getId).toList())
      .containsExactly("F1_2", "F1_3");
  }
}
