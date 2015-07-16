/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.protocol.output;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.batch.protocol.Constants;
import org.sonar.batch.protocol.ProtobufUtil;
import org.sonar.batch.protocol.output.BatchReport.Range;

import java.io.File;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class BatchReportWriterTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  File dir;
  BatchReportWriter underTest;

  @Before
  public void setUp() throws Exception {
    dir = temp.newFolder();
    underTest = new BatchReportWriter(dir);
  }

  @Test
  public void create_dir_if_does_not_exist() {
    FileUtils.deleteQuietly(dir);
    underTest = new BatchReportWriter(dir);

    assertThat(dir).isDirectory().exists();
  }

  @Test
  public void write_metadata() {
    BatchReport.Metadata.Builder metadata = BatchReport.Metadata.newBuilder()
      .setAnalysisDate(15000000L)
      .setProjectKey("PROJECT_A")
      .setRootComponentRef(1);
    underTest.writeMetadata(metadata.build());

    BatchReport.Metadata read = ProtobufUtil.readFile(underTest.getFileStructure().metadataFile(), BatchReport.Metadata.PARSER);
    assertThat(read.getAnalysisDate()).isEqualTo(15000000L);
    assertThat(read.getProjectKey()).isEqualTo("PROJECT_A");
    assertThat(read.getRootComponentRef()).isEqualTo(1);
  }

  @Test
  public void write_component() {
    // no data yet
    assertThat(underTest.hasComponentData(FileStructure.Domain.COMPONENT, 1)).isFalse();

    // write data
    BatchReport.Component.Builder component = BatchReport.Component.newBuilder()
      .setRef(1)
      .setLanguage("java")
      .setPath("src/Foo.java")
      .setType(Constants.ComponentType.FILE)
      .setIsTest(false)
      .addChildRef(5)
      .addChildRef(42);
    underTest.writeComponent(component.build());

    assertThat(underTest.hasComponentData(FileStructure.Domain.COMPONENT, 1)).isTrue();
    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.COMPONENT, 1);
    assertThat(file).exists().isFile();
    BatchReport.Component read = ProtobufUtil.readFile(file, BatchReport.Component.PARSER);
    assertThat(read.getRef()).isEqualTo(1);
    assertThat(read.getChildRefList()).containsOnly(5, 42);
    assertThat(read.hasName()).isFalse();
    assertThat(read.getIsTest()).isFalse();
  }

  @Test
  public void write_issues() {
    // no data yet
    assertThat(underTest.hasComponentData(FileStructure.Domain.ISSUES, 1)).isFalse();

    // write data
    BatchReport.Issue issue = BatchReport.Issue.newBuilder()
      .setLine(50)
      .setMsg("the message")
      .build();

    underTest.writeComponentIssues(1, Arrays.asList(issue));

    assertThat(underTest.hasComponentData(FileStructure.Domain.ISSUES, 1)).isTrue();
    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.ISSUES, 1);
    assertThat(file).exists().isFile();
    BatchReport.Issues read = ProtobufUtil.readFile(file, BatchReport.Issues.PARSER);
    assertThat(read.getComponentRef()).isEqualTo(1);
    assertThat(read.getIssueCount()).isEqualTo(1);
  }

  @Test
  public void write_measures() {
    assertThat(underTest.hasComponentData(FileStructure.Domain.MEASURES, 1)).isFalse();

    BatchReport.Measure measure = BatchReport.Measure.newBuilder()
      .setStringValue("text-value")
      .setDoubleValue(2.5d)
      .setValueType(Constants.MeasureValueType.DOUBLE)
      .setDescription("description")
      .build();

    underTest.writeComponentMeasures(1, Arrays.asList(measure));

    assertThat(underTest.hasComponentData(FileStructure.Domain.MEASURES, 1)).isTrue();
    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.MEASURES, 1);
    assertThat(file).exists().isFile();
    BatchReport.Measures measures = ProtobufUtil.readFile(file, BatchReport.Measures.PARSER);
    assertThat(measures.getComponentRef()).isEqualTo(1);
    assertThat(measures.getMeasureCount()).isEqualTo(1);
    assertThat(measures.getMeasure(0).getStringValue()).isEqualTo("text-value");
    assertThat(measures.getMeasure(0).getDoubleValue()).isEqualTo(2.5d);
    assertThat(measures.getMeasure(0).getValueType()).isEqualTo(Constants.MeasureValueType.DOUBLE);
    assertThat(measures.getMeasure(0).getDescription()).isEqualTo("description");
  }

  @Test
  public void write_scm() {
    assertThat(underTest.hasComponentData(FileStructure.Domain.CHANGESETS, 1)).isFalse();

    BatchReport.Changesets scm = BatchReport.Changesets.newBuilder()
      .setComponentRef(1)
      .addChangesetIndexByLine(0)
      .addChangeset(BatchReport.Changesets.Changeset.newBuilder()
        .setRevision("123-456-789")
        .setAuthor("author")
        .setDate(123_456_789L))
      .build();

    underTest.writeComponentChangesets(scm);

    assertThat(underTest.hasComponentData(FileStructure.Domain.CHANGESETS, 1)).isTrue();
    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.CHANGESETS, 1);
    assertThat(file).exists().isFile();
    BatchReport.Changesets read = ProtobufUtil.readFile(file, BatchReport.Changesets.PARSER);
    assertThat(read.getComponentRef()).isEqualTo(1);
    assertThat(read.getChangesetCount()).isEqualTo(1);
    assertThat(read.getChangesetList()).hasSize(1);
    assertThat(read.getChangeset(0).getDate()).isEqualTo(123_456_789L);
  }

  @Test
  public void write_duplications() {
    assertThat(underTest.hasComponentData(FileStructure.Domain.DUPLICATIONS, 1)).isFalse();

    BatchReport.Duplication duplication = BatchReport.Duplication.newBuilder()
      .setOriginPosition(Range.newBuilder()
        .setStartLine(1)
        .setEndLine(5)
        .build())
      .addDuplicate(BatchReport.Duplicate.newBuilder()
        .setOtherFileKey("COMPONENT_A")
        .setOtherFileRef(2)
        .setRange(Range.newBuilder()
          .setStartLine(6)
          .setEndLine(10)
          .build())
        .build())
      .build();
    underTest.writeComponentDuplications(1, Arrays.asList(duplication));

    assertThat(underTest.hasComponentData(FileStructure.Domain.DUPLICATIONS, 1)).isTrue();
    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.DUPLICATIONS, 1);
    assertThat(file).exists().isFile();
    BatchReport.Duplications duplications = ProtobufUtil.readFile(file, BatchReport.Duplications.PARSER);
    assertThat(duplications.getComponentRef()).isEqualTo(1);
    assertThat(duplications.getDuplicationList()).hasSize(1);
    assertThat(duplications.getDuplication(0).getOriginPosition()).isNotNull();
    assertThat(duplications.getDuplication(0).getDuplicateList()).hasSize(1);
  }

  @Test
  public void write_symbols() {
    // no data yet
    assertThat(underTest.hasComponentData(FileStructure.Domain.SYMBOLS, 1)).isFalse();

    // write data
    BatchReport.Symbols.Symbol symbol = BatchReport.Symbols.Symbol.newBuilder()
      .setDeclaration(BatchReport.Range.newBuilder()
        .setStartLine(1)
        .setStartOffset(3)
        .setEndLine(1)
        .setEndOffset(5)
        .build())
      .addReference(BatchReport.Range.newBuilder()
        .setStartLine(10)
        .setStartOffset(15)
        .setEndLine(11)
        .setEndOffset(2)
        .build())
      .build();

    underTest.writeComponentSymbols(1, Arrays.asList(symbol));

    assertThat(underTest.hasComponentData(FileStructure.Domain.SYMBOLS, 1)).isTrue();

    File file = underTest.getFileStructure().fileFor(FileStructure.Domain.SYMBOLS, 1);
    assertThat(file).exists().isFile();
    BatchReport.Symbols read = ProtobufUtil.readFile(file, BatchReport.Symbols.PARSER);
    assertThat(read.getFileRef()).isEqualTo(1);
    assertThat(read.getSymbolList()).hasSize(1);
    assertThat(read.getSymbol(0).getDeclaration().getStartLine()).isEqualTo(1);
    assertThat(read.getSymbol(0).getReference(0).getStartLine()).isEqualTo(10);
  }

  @Test
  public void write_syntax_highlighting() {
    // no data yet
    assertThat(underTest.hasComponentData(FileStructure.Domain.SYNTAX_HIGHLIGHTINGS, 1)).isFalse();

    underTest.writeComponentSyntaxHighlighting(1, Arrays.asList(
      BatchReport.SyntaxHighlighting.newBuilder()
        .setRange(BatchReport.Range.newBuilder()
          .setStartLine(1)
          .setEndLine(1)
          .build())
        .setType(Constants.HighlightingType.ANNOTATION)
        .build()
    ));

    assertThat(underTest.hasComponentData(FileStructure.Domain.SYNTAX_HIGHLIGHTINGS, 1)).isTrue();
  }

  @Test
  public void write_coverage() {
    // no data yet
    assertThat(underTest.hasComponentData(FileStructure.Domain.COVERAGES, 1)).isFalse();

    underTest.writeComponentCoverage(1, Arrays.asList(
      BatchReport.Coverage.newBuilder()
        .setLine(1)
        .setConditions(1)
        .setUtHits(true)
        .setItHits(false)
        .setUtCoveredConditions(1)
        .setItCoveredConditions(1)
        .setOverallCoveredConditions(1)
        .build()
    ));

    assertThat(underTest.hasComponentData(FileStructure.Domain.COVERAGES, 1)).isTrue();
  }

  @Test
  public void write_tests() {
    assertThat(underTest.hasComponentData(FileStructure.Domain.TESTS, 1)).isFalse();

    underTest.writeTests(1, Arrays.asList(
      BatchReport.Test.getDefaultInstance()
    ));

    assertThat(underTest.hasComponentData(FileStructure.Domain.TESTS, 1)).isTrue();

  }

  @Test
  public void write_coverage_details() {
    assertThat(underTest.hasComponentData(FileStructure.Domain.COVERAGE_DETAILS, 1)).isFalse();

    underTest.writeCoverageDetails(1, Arrays.asList(
      BatchReport.CoverageDetail.getDefaultInstance()
    ));

    assertThat(underTest.hasComponentData(FileStructure.Domain.COVERAGE_DETAILS, 1)).isTrue();
  }
}
