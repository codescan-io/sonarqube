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
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.rule.RuleDto;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.server.issue.index.IssueIndexDefinition.TYPE_ISSUE;

/**
 * Covers {@link IssueIndexer#reindexByRuleUuids}, the rule-scoped backfill reindex driven by
 * {@code BackfillCodefixStatusMigration}. Lives in this module rather than next to the indexer because
 * {@code sonar-server-common}'s own test source set does not currently compile.
 */
class IssueIndexerReindexByRuleUuidsTest {

  @RegisterExtension
  public EsTester es = EsTester.create();
  @RegisterExtension
  public DbTester db = DbTester.create();

  private final IssueIndexer underTest = new IssueIndexer(es.client(), db.getDbClient(),
    new IssueIteratorFactory(db.getDbClient()), mock(AsyncIssueIndexing.class));

  @Test
  void reindexes_issues_of_the_rule_on_every_branch() {
    RuleDto rule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    ComponentDto mainBranch = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto fileOnMain = db.components().insertComponent(newFileDto(mainBranch));
    ComponentDto otherBranch = db.components().insertProjectBranch(mainBranch);
    ComponentDto fileOnOther = db.components().insertComponent(newFileDto(otherBranch));

    IssueDto onMain = db.issues().insert(rule, mainBranch, fileOnMain);
    IssueDto onOther = db.issues().insert(rule, otherBranch, fileOnOther);

    long reindexed = underTest.reindexByRuleUuids(List.of(rule.getUuid()));

    assertThat(reindexed).isEqualTo(2);
    assertThat(indexedIssueKeys()).containsExactlyInAnyOrder(onMain.getKey(), onOther.getKey());
  }

  @Test
  void returns_zero_and_indexes_nothing_when_no_rules_are_given() {
    RuleDto rule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    ComponentDto mainBranch = db.components().insertPrivateProject().getMainBranchComponent();
    db.issues().insert(rule, mainBranch, db.components().insertComponent(newFileDto(mainBranch)));

    long reindexed = underTest.reindexByRuleUuids(List.of());

    assertThat(reindexed).isZero();
    assertThat(indexedIssueKeys()).isEmpty();
  }

  @Test
  void flushes_the_docs_queued_before_a_drain_failure() {
    RuleDto rule = db.rules().insert(r -> r.setAiCodeFixEnabled(true));
    ComponentDto mainBranch = db.components().insertPrivateProject().getMainBranchComponent();
    ComponentDto file = db.components().insertComponent(newFileDto(mainBranch));
    db.issues().insert(rule, mainBranch, file);
    db.issues().insert(rule, mainBranch, file);

    IssueIndexer indexer = new IssueIndexer(es.client(), db.getDbClient(),
      failingAfterFirstDoc(db.getDbClient()), mock(AsyncIssueIndexing.class));

    // the run must fail rather than silently report a partial backfill; how the cause is wrapped is not the contract
    assertThatThrownBy(() -> indexer.reindexByRuleUuids(List.of(rule.getUuid())))
      .isInstanceOf(RuntimeException.class);

    // the doc handed over before the failure was queued in the bulk indexer, and must still reach the index: a run that
    // stops early is reported as FAILED, so leaving queued docs unflushed would strand them until the next re-run
    assertThat(indexedIssueKeys()).hasSize(1);
  }

  /**
   * Yields exactly one real issue and then throws, to model a scroll dying part-way through a branch.
   */
  private static IssueIteratorFactory failingAfterFirstDoc(org.sonar.db.DbClient dbClient) {
    return new IssueIteratorFactory(dbClient) {
      @Override
      public IssueIterator createForBranchAndRuleUuids(String branchUuid, java.util.Collection<String> ruleUuids) {
        IssueIterator delegate = super.createForBranchAndRuleUuids(branchUuid, ruleUuids);
        return new IssueIterator() {
          private boolean servedOne;

          @Override
          public boolean hasNext() {
            return true;
          }

          @Override
          public IssueDoc next() {
            if (servedOne) {
              throw new IllegalStateException("drain blew up");
            }
            servedOne = true;
            return delegate.next();
          }

          @Override
          public void close() {
            delegate.close();
          }
        };
      }
    };
  }

  private List<String> indexedIssueKeys() {
    es.client().refresh(IssueIndexDefinition.DESCRIPTOR);
    SearchRequest request = EsClient.prepareSearch(TYPE_ISSUE.getMainType())
      .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()).size(500));
    return Arrays.stream(es.client().search(request).getHits().getHits())
      .map(SearchHit::getId)
      .toList();
  }
}
