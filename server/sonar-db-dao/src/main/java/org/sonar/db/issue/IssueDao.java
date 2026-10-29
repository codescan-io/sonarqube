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
package org.sonar.db.issue;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.RowNotFoundException;
import org.sonar.db.component.ComponentDto;

import static org.sonar.db.DatabaseUtils.executeLargeInputs;
import static org.sonar.db.DatabaseUtils.executeLargeUpdates;

public class IssueDao implements Dao {
  public static final int DEFAULT_PAGE_SIZE = 1000;
  public static final int BIG_PAGE_SIZE = 1000000;

  public Optional<IssueDto> selectByKey(DbSession session, String key) {
    return Optional.ofNullable(mapper(session).selectByKey(key));
  }

  public IssueDto selectOrFailByKey(DbSession session, String key) {
    Optional<IssueDto> issue = selectByKey(session, key);
    if (issue.isEmpty()) {
      throw new RowNotFoundException(String.format("Issue with key '%s' does not exist", key));
    }
    return issue.get();
  }

  /**
   * Gets a list issues by their keys. The result does NOT contain {@code null} values for issues not found, so
   * the size of result may be less than the number of keys. A single issue is returned
   * if input keys contain multiple occurrences of a key.
   * <p>Results may be in a different order as input keys.</p>
   */
  public List<IssueDto> selectByKeys(DbSession session, Collection<String> keys) {
    return executeLargeInputs(keys, mapper(session)::selectByKeys);
  }

  /**
   * Unresolved issues on the given branches that share a marking signature - file path, rule, line and message -
   * with the issue being proliferated. {@code branchUuids} are project_uuid values, which is how issues are
   * partitioned per branch. An empty branch list short-circuits rather than issuing an {@code in ()}.
   */
  public List<IssueDto> selectUnresolvedBySignatureInBranches(DbSession session, Collection<String> branchUuids,
    String ruleUuid, String filePath, @Nullable Integer line, String message, Collection<String> statuses) {
    if (branchUuids.isEmpty() || statuses.isEmpty()) {
      return List.of();
    }
    return executeLargeInputs(branchUuids,
      partition -> mapper(session).selectUnresolvedBySignatureInBranches(partition, ruleUuid, filePath, line, message, statuses));
  }


  public List<IssueDto> selectUnresolvedByRuleAndFileInBranches(DbSession session, Collection<String> branchUuids,
    String ruleUuid, String filePath, Collection<String> statuses) {
    if (branchUuids.isEmpty() || statuses.isEmpty()) {
      return List.of();
    }
    return executeLargeInputs(branchUuids,
      partition -> mapper(session).selectUnresolvedByRuleAndFileInBranches(partition, ruleUuid, filePath, statuses));
  }

  public Set<String> selectIssueKeysByComponentUuid(DbSession session, String componentUuid) {
    return mapper(session).selectIssueKeysByComponentUuid(componentUuid);
  }

  public Set<String> selectIssueKeysByComponentUuid(DbSession session, String componentUuid,
    List<String> includingRepositories, List<String> excludingRepositories,
    List<String> languages, int page) {
    return mapper(session).selectIssueKeysByComponentUuidWithFilters(componentUuid, includingRepositories, excludingRepositories,
      languages, Pagination.forPage(page).andSize(BIG_PAGE_SIZE));
  }

  public Set<String> selectIssueKeysByComponentUuidAndChangedSinceDate(DbSession session, String componentUuid, long changedSince,
    List<String> includingRepositories, List<String> excludingRepositories,
    List<String> languages, int page) {
    return mapper(session).selectIssueKeysByComponentUuidAndChangedSinceDate(componentUuid, changedSince,
      includingRepositories, excludingRepositories, languages, Pagination.forPage(page).andSize(BIG_PAGE_SIZE));
  }

  public Set<String> selectComponentUuidsOfOpenIssuesForProjectUuid(DbSession session, String projectUuid) {
    return mapper(session).selectComponentUuidsOfOpenIssuesForProjectUuid(projectUuid);
  }

  public List<PrIssueDto> selectOpenByComponentUuids(DbSession dbSession, Collection<String> componentUuids) {
    return executeLargeInputs(componentUuids, mapper(dbSession)::selectOpenByComponentUuids);
  }

  public Collection<IssueGroupDto> selectIssueGroupsByComponent(DbSession dbSession, ComponentDto component, long leakPeriodBeginningDate) {
    return mapper(dbSession).selectIssueGroupsByComponent(component, leakPeriodBeginningDate);
  }

  public Collection<IssueImpactGroupDto> selectIssueImpactGroupsByComponent(DbSession dbSession, ComponentDto component, long leakPeriodBeginningDate) {
    return mapper(dbSession).selectIssueImpactGroupsByComponent(component, leakPeriodBeginningDate);
  }

  public Collection<IssueImpactSeverityGroupDto> selectIssueImpactSeverityGroupsByComponent(DbSession dbSession, ComponentDto component,
    long leakPeriodBeginningDate) {
    return mapper(dbSession).selectIssueImpactSeverityGroupsByComponent(component, leakPeriodBeginningDate);
  }

  public Cursor<IndexedIssueDto> scrollIssuesForIndexation(DbSession dbSession, @Nullable @Param("branchUuid") String branchUuid,
    @Nullable @Param("issueKeys") Collection<String> issueKeys) {
    return mapper(dbSession).scrollIssuesForIndexation(branchUuid, issueKeys);
  }

  /**
   * Streams the issues of ONE branch whose rule is one of {@code ruleUuids} (the ai_code_fix_enabled, non-REMOVED rules,
   * looked up once by the caller). Used by BackfillCodefixStatusMigration, which runs one of these per branch, several
   * at a time. One branch at a time on purpose: the query has to sort by issue key, and Postgres cannot sort and stream
   * at the same time, so one query over the whole table had to sort everything before returning its first row.
   *
   * <p>It writes {@code codefixStatus = COALESCE(codefix_status, 'AVAILABLE')} — everything in scope becomes AVAILABLE,
   * keeping any value already stored. That is blunter than what normal analysis indexing writes, on purpose: old issues
   * have no {@code issue_ai_metadata}, so the extra check analysis applies would leave the variable-naming rules NULL,
   * and the point of the backfill is to mark them all.
   */
  public Cursor<IndexedIssueDto> scrollIssuesForIndexationByBranchAndRuleUuids(DbSession dbSession, String branchUuid,
    Collection<String> ruleUuids) {
    return mapper(dbSession).scrollIssuesForIndexationForMigration(branchUuid, ruleUuids);
  }

  /**
   * The branches that have at least one issue on one of {@code ruleUuids}. ({@code issues.project_uuid} holds the branch
   * uuid, despite the name.) This is the work list for BackfillCodefixStatusMigration: one pass over the matching
   * issues, no joins, and only the branches that actually have something to do.
   *
   * <p><b>Biggest branches first, and callers must keep that order.</b> The migration hands these to a fixed pool of
   * threads, so the run is only finished when the slowest thread is. Starting with the biggest branches stops a huge one
   * from being picked up near the end and grinding away on its own while every other thread sits idle.
   */
  public List<String> selectBranchUuidsForRuleUuids(DbSession dbSession, Collection<String> ruleUuids) {
    return mapper(dbSession).selectBranchUuidsForRuleUuids(ruleUuids);
  }

  public void insert(DbSession session, IssueDto dto) {
    mapper(session).insert(dto);
    insertIssueImpacts(session, dto);
  }

  /**
   * In certain circumstances, most notably persisting issues in the CE, importing issues and web issue storage, we wish to avoid persisting
   * both issues and their issue impacts in the same batch transactions, as this introduces a significant performance regression. In those
   * situations, issues will be inserted first, and then subsequently their issue impacts using
   * {@link IssueDao#insertIssueImpacts(DbSession, IssueDto) ()}.
   */
  public void insertWithoutImpacts(DbSession session, IssueDto dto) {
    mapper(session).insert(dto);
  }

  public void insertIssueImpacts(DbSession session, IssueDto issueDto) {
    IssueMapper mapper = mapper(session);
    issueDto.getImpacts()
      .forEach(impact -> mapper.insertIssueImpact(issueDto.getKey(), impact));
  }

  private void updateIssueImpacts(DbSession session, IssueDto issueDto) {
    deleteIssueImpacts(session, issueDto);
    insertIssueImpacts(session, issueDto);
  }

  public void deleteIssueImpacts(DbSession session, IssueDto issueDto) {
    mapper(session).deleteIssueImpacts(issueDto.getKey());
  }

  public void insert(DbSession session, IssueDto dto, IssueDto... others) {
    insert(session, dto);
    for (IssueDto other : others) {
      insert(session, other);
    }
  }

  public void update(DbSession session, IssueDto dto) {
    mapper(session).update(dto);
    updateIssueImpacts(session, dto);
  }

  /**
   * CodeScan: the issues a bulk transition would move, with the status each moves from. Read in the same
   * transaction as {@link #bulkTransition} and under the same predicate, so the caller can write the changelog
   * from this result instead of reading the issues back afterwards.
   */
  public List<IssueKeyStatusDto> selectTransitionable(DbSession session, Collection<String> keys,
    String branchUuid) {
    return executeLargeInputs(keys, partition -> mapper(session).selectTransitionable(partition, branchUuid));
  }

  /**
   * CodeScan: for each of {@code keys} that the resolution transfer {@code transferUuid} paired with a source issue
   * that is still an exception, that source and its expiry. A key paired with several such sources appears once per
   * source; a key with none is left out.
   */
  public List<IssueExceptionSourceDto> selectExceptionSourcesOfTransfer(DbSession session, String transferUuid,
    Collection<String> keys) {
    return executeLargeInputs(keys, partition -> mapper(session).selectExceptionSourcesOfTransfer(transferUuid,
      partition));
  }

  /**
   * CodeScan: resolves a whole chunk of issues in one statement per partition rather than one per issue, under the
   * same predicate as {@link #selectTransitionable}. {@code expiresAt} is written as the exception expiry; null
   * clears it, as the workflow does for any other resolution.
   *
   * <p>No row count is returned, and none can be: the caller runs this in a batch session so the chunk and its
   * changelog commit together, and a batched statement has not executed when the call returns - MyBatis answers
   * with {@code Integer.MIN_VALUE}, its batch sentinel, not a count. Reading the rows back to count them is the
   * round trip this whole path exists to avoid.
   *
   * <p>No row lock is held between the select and this update either, so a concurrent writer can leave the two
   * disagreeing. See BulkTransitionAction for what that costs and why it is accepted.
   */
  public void bulkTransition(DbSession session, Collection<String> keys, String branchUuid, String resolution,
    @Nullable Long expiresAt, long now) {
    executeLargeUpdates(keys,
      partition -> mapper(session).bulkTransition(partition, branchUuid, resolution, expiresAt, now));
  }


  public int bulkTransitionWithExpiry(DbSession session, Collection<String> keys, String resolution,
    @Nullable Long expiresAt, long now, Collection<String> statuses) {
    if (keys.isEmpty() || statuses.isEmpty()) {
      return 0;
    }
    AtomicInteger updated = new AtomicInteger();
    executeLargeUpdates(keys, partition -> updated
      .addAndGet(mapper(session).bulkTransitionWithExpiry(partition, resolution, expiresAt, now, statuses)));
    return updated.get();
  }

  /**
   * Update issue without updating issue impacts. Used only in batch update.
   * Issue impacts should be updated separately.
   */
  public void updateWithoutIssueImpacts(DbSession session, IssueDto dto) {
    mapper(session).update(dto);
  }

  public boolean updateIfBeforeSelectedDate(DbSession session, IssueDto dto) {
    return mapper(session).updateIfBeforeSelectedDate(dto) != 0;
  }

  public List<IssueDto> selectByKeysIfNotUpdatedAt(DbSession session, List<String> keys, long updatedAt) {
    return mapper(session).selectByKeysIfNotUpdatedAt(keys, updatedAt);
  }

  public void insertAsNewCodeOnReferenceBranch(DbSession session, NewCodeReferenceIssueDto dto) {
    mapper(session).insertAsNewCodeOnReferenceBranch(dto);
  }

  public void deleteAsNewCodeOnReferenceBranch(DbSession session, String issueKey) {
    mapper(session).deleteAsNewCodeOnReferenceBranch(issueKey);
  }

  private static IssueMapper mapper(DbSession session) {
    return session.getMapper(IssueMapper.class);
  }

  public List<IssueDto> selectByBranch(DbSession dbSession, Set<String> issueKeysSnapshot, IssueQueryParams issueQueryParams) {
    return mapper(dbSession).selectByBranch(issueKeysSnapshot, issueQueryParams.getChangedSince());
  }

  public List<String> selectRecentlyClosedIssues(DbSession dbSession, IssueQueryParams issueQueryParams) {
    return mapper(dbSession).selectRecentlyClosedIssues(issueQueryParams);
  }

  /**
   * Returned results are unordered.
   */
  public List<String> selectIssueKeysByQuery(DbSession dbSession, IssueListQuery issueListQuery, Pagination pagination) {
    return mapper(dbSession).selectIssueKeysByQuery(issueListQuery, pagination);
  }

  public void updateIssueResolutionExpiryDate(DbSession dbSession, String issueKey, Long expiryDate) {
    mapper(dbSession).updateIssueResolutionExpiryDate(issueKey, expiryDate);
  }

}
