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
import javax.annotation.Nullable;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;

import static java.util.Collections.singletonList;
import static org.sonar.db.DatabaseUtils.executeLargeInputs;
import static org.sonar.db.DatabaseUtils.executeLargeInputsWithoutOutput;
import static org.sonar.db.DatabaseUtils.executeLargeUpdates;

public class IssueChangeDao implements Dao {

  /** Leaves room in the 40-character uuid column for the row number the SQL appends. */
  private static final int PREFIX_LENGTH = 30;

  /**
   * Writes the diff rows of a bulk transition, one statement per partition instead of one per issue. Must run
   * before the issues are updated: the diff records the status they are moving away from.
   */
  public void insertTransitionDiffs(DbSession session, Collection<String> keys, String resolution,
    String issueStatus, String branchUuid, @Nullable String changeAuthor, long now, UuidFactory uuidFactory) {
    executeLargeInputsWithoutOutput(keys, partition -> mapper(session).insertTransitionDiffs(partition, resolution,
      issueStatus, branchUuid, changeAuthor, now, rowKeyPrefix(uuidFactory)));
  }

  /** Writes one comment row per transitioned issue, under the same predicate and partitioning as the diffs. */
  public void insertTransitionComments(DbSession session, Collection<String> keys, String comment,
    String branchUuid, @Nullable String changeAuthor, long now, UuidFactory uuidFactory) {
    insertTransitionRows(session, keys, IssueChangeDto.TYPE_COMMENT, comment, branchUuid, changeAuthor, now,
      uuidFactory);
  }

  /** Writes one exception reason row per transitioned issue, under the same predicate and partitioning as the diffs. */
  public void insertTransitionExceptionReasons(DbSession session, Collection<String> keys, String exceptionReason,
    String branchUuid, @Nullable String changeAuthor, long now, UuidFactory uuidFactory) {
    insertTransitionRows(session, keys, IssueChangeDto.TYPE_EXCEPTION_REASON, exceptionReason, branchUuid,
      changeAuthor, now, uuidFactory);
  }

  private void insertTransitionRows(DbSession session, Collection<String> keys, String changeType, String text,
    String branchUuid, @Nullable String changeAuthor, long now, UuidFactory uuidFactory) {
    executeLargeInputsWithoutOutput(keys, partition -> mapper(session).insertTransitionComments(partition, changeType,
      text, branchUuid, changeAuthor, now, rowKeyPrefix(uuidFactory), rowKeyPrefix(uuidFactory)));
  }

  /**
   * A per-statement prefix the SQL completes with a row number, because no uuid function is available on every
   * supported dialect. Hyphens are dropped and the tail is kept so the result fits the 40-character uuid column
   * and still varies under {@link org.sonar.core.util.SequenceUuidFactory}, whose uuids differ only at the end.
   */
  private static String rowKeyPrefix(UuidFactory uuidFactory) {
    String compact = uuidFactory.create().replace("-", "");
    return compact.length() <= PREFIX_LENGTH ? compact : compact.substring(compact.length() - PREFIX_LENGTH);
  }

  public List<FieldDiffs> selectChangelogByIssue(DbSession session, String issueKey) {
    return selectByTypeAndIssueKeys(session, singletonList(issueKey), IssueChangeDto.TYPE_FIELD_CHANGE)
      .stream()
      .map(IssueChangeDto::toFieldDiffs)
      .toList();
  }

  public List<IssueChangeDto> selectByTypeAndIssueKeys(DbSession session, Collection<String> issueKeys, String changeType) {
    return executeLargeInputs(issueKeys, issueKeys1 -> mapper(session).selectByIssuesAndType(issueKeys1, changeType));
  }

  public List<IssueChangeDto> selectByIssueKeys(DbSession session, Collection<String> issueKeys) {
    return executeLargeInputs(issueKeys, issueKeys1 -> mapper(session).selectByIssues(issueKeys1));
  }

  public Optional<IssueChangeDto> selectByKey(DbSession session, String key) {
    return Optional.ofNullable(mapper(session).selectByKey(key));
  }

  public Optional<IssueChangeDto> selectCommentByKey(DbSession session, String commentKey) {
    return Optional.ofNullable(mapper(session).selectByKeyAndType(commentKey, IssueChangeDto.TYPE_COMMENT));
  }

  public void scrollDiffChangesOfIssues(DbSession dbSession, Collection<String> issueKeys, ResultHandler<IssueChangeDto> handler) {
    if (issueKeys.isEmpty()) {
      return;
    }

    executeLargeInputsWithoutOutput(issueKeys, issueKeySubList -> mapper(dbSession).scrollDiffChangesOfIssues(issueKeySubList, handler));
  }

  public void insert(DbSession session, IssueChangeDto change) {
    mapper(session).insert(change);
  }

  public boolean deleteByKey(DbSession session, String key) {
    IssueChangeMapper mapper = mapper(session);
    int count = mapper.delete(key);
    session.commit();
    return count == 1;
  }

  public void deleteByUuids(DbSession session, Set<String> uuids) {
    IssueChangeMapper mapper = mapper(session);
    executeLargeUpdates(uuids, mapper::deleteByUuids);
  }

  public boolean update(DbSession dbSession, IssueChangeDto change) {
    int count = mapper(dbSession).update(change);
    return count == 1;
  }

  private static IssueChangeMapper mapper(DbSession session) {
    return session.getMapper(IssueChangeMapper.class);
  }
}
