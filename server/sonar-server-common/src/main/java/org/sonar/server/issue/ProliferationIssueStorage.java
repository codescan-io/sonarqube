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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.issue.Issue;
import org.sonar.core.issue.DefaultIssueComment;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.util.UuidFactory;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.issue.IssueChangeMapper;
import org.sonar.db.issue.ProliferationChangeQuery;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.rule.ServerRuleFinder;


@ServerSide
@ComputeEngineSide
public class ProliferationIssueStorage {

  private static final IssueStorage CHANGE_WRITER = new IssueStorage();

  private final System2 system2;
  private final DbClient dbClient;
  private final ServerRuleFinder ruleFinder;
  private final IssueIndexer indexer;
  private final UuidFactory uuidFactory;

  public ProliferationIssueStorage(System2 system2, DbClient dbClient, ServerRuleFinder ruleFinder,
    IssueIndexer indexer, UuidFactory uuidFactory) {
    this.system2 = system2;
    this.dbClient = dbClient;
    this.ruleFinder = ruleFinder;
    this.indexer = indexer;
    this.uuidFactory = uuidFactory;
  }

  /**
   * Resolution to the issue-status the changelog records, for the two resolutions the set-based path covers.
   * Any other resolution falls back to the per-issue path rather than guessing.
   */
  private static final Map<String, String> SET_BASED_ISSUE_STATUS = Map.of(
    Issue.RESOLUTION_FALSE_POSITIVE, "FALSE_POSITIVE",
    Issue.RESOLUTION_WONT_FIX, "ACCEPTED");

  /**
   * The fields the set-based update writes, and so the only ones its changelog row can account for.
   * <p>
   * RESOLUTION and STATUS are deprecated as fields to set, but they are still the keys
   * {@link IssueFieldsSetter#setResolution} and {@link IssueFieldsSetter#setStatus} put in the diff map, which is
   * what this set is compared against. Spelling them as literals here would only hide that dependency.
   */
  @SuppressWarnings("deprecation")
  private static final Set<String> SET_BASED_DIFF_FIELDS = Set.of(
    IssueFieldsSetter.RESOLUTION, IssueFieldsSetter.STATUS, IssueFieldsSetter.ISSUE_STATUS);


  public List<String> save(DbSession dbSession, Collection<DefaultIssue> issues, String branchUuid, long markedAt,
    @Nullable String authorUuid, Collection<String> candidateStatuses) {

    if (issues.isEmpty()) {
      return List.of();
    }
    long now = system2.now();
    List<String> written = new ArrayList<>(issues.size());

    Map<SetBasedGroup, List<DefaultIssue>> setBased = new LinkedHashMap<>();
    List<DefaultIssue> perIssue = new ArrayList<>();
    for (DefaultIssue issue : issues) {
      SetBasedGroup group = SetBasedGroup.of(issue);
      if (group == null) {
        perIssue.add(issue);
      } else {
        setBased.computeIfAbsent(group, k -> new ArrayList<>()).add(issue);
      }
    }

    // The bind variables every set-based statement of this call shares. Each statement copies it and adds its own
    // keys and payload, so the common half is assembled once instead of travelling as six more arguments.
    ProliferationChangeQuery shared = new ProliferationChangeQuery()
      .setBranchUuid(branchUuid)
      .setChangeAuthor(authorUuid)
      .setNow(now)
      .setMarkedAt(markedAt)
      .setStatuses(candidateStatuses);

    setBased.forEach((group, grouped) -> {
      List<String> keys = grouped.stream().map(DefaultIssue::key).toList();
      writeSetBased(dbSession, group, keys, shared);
      written.addAll(keys);
    });

    if (!perIssue.isEmpty()) {
      written.addAll(writePerIssue(dbSession, perIssue, now));
    }

    dbSession.commit();
    return written;
  }


  public void indexBranch(String branchUuid, Collection<String> issueKeys) {
    if (issueKeys.isEmpty()) {
      return;
    }
    indexer.indexOnAnalysis(branchUuid, issueKeys);
  }


  private void writeSetBased(DbSession dbSession, SetBasedGroup group, List<String> keys,
    ProliferationChangeQuery shared) {

    dbClient.issueChangeDao().insertProliferationDiffs(dbSession, shared.copyFor(keys)
      .setResolution(group.resolution())
      .setIssueStatus(SET_BASED_ISSUE_STATUS.get(group.resolution())), uuidFactory);

    for (String comment : group.comments()) {
      dbClient.issueChangeDao().insertProliferationComments(dbSession, shared.copyFor(keys)
        .setComment(comment)
        .setChangeType(IssueChangeDto.TYPE_COMMENT), uuidFactory);
    }

    dbClient.issueDao().bulkTransitionWithExpiry(dbSession, keys, group.resolution(), group.expiresAt(),
      shared.getNow(), shared.getStatuses());
  }


  private List<String> writePerIssue(DbSession dbSession, Collection<DefaultIssue> issues, long now) {
    IssueChangeMapper changeMapper = dbSession.getMapper(IssueChangeMapper.class);
    List<String> written = new ArrayList<>(issues.size());
    for (DefaultIssue issue : issues) {
      IssueDto dto = IssueDto.toDtoForUpdate(issue, now);

      dbClient.issueDao().updateWithoutIssueImpacts(dbSession, dto);
      ruleFinder.findDtoByKey(issue.ruleKey()).ifPresent(rule -> {
        dto.setRuleUuid(rule.getUuid());
        dto.setCleanCodeAttribute(rule.getCleanCodeAttribute());
        dto.setRuleDefaultImpacts(rule.getDefaultImpacts());
      });
      CHANGE_WRITER.insertChanges(changeMapper, issue, uuidFactory);
      written.add(issue.key());
    }
    return written;
  }


  private record SetBasedGroup(String resolution, @Nullable Long expiresAt, Set<String> comments) {

    @Nullable
    static SetBasedGroup of(DefaultIssue issue) {
      String resolution = issue.resolution();
      if (resolution == null
        || !SET_BASED_ISSUE_STATUS.containsKey(resolution)
        || !Issue.STATUS_RESOLVED.equals(issue.status())
        || !issue.defaultIssueExceptionReasons().isEmpty()
        || changedMoreThanTheTransition(issue)) {
        return null;
      }
      Set<String> comments = new LinkedHashSet<>();
      for (DefaultIssueComment comment : issue.defaultIssueComments()) {
        if (comment.isNew()) {
          comments.add(comment.markdownText());
        }
      }
      return new SetBasedGroup(resolution, issue.issueResolutionExpiresAt(), comments);
    }

    /**
     * True when the workflow recorded a change the set-based update does not write. The three fields a resolving
     * transition records are the three the statement sets; anything else means the per-issue path.
     */
    private static boolean changedMoreThanTheTransition(DefaultIssue issue) {
      FieldDiffs diffs = issue.currentChange();
      if (diffs == null) {
        return false;
      }
      return !SET_BASED_DIFF_FIELDS.containsAll(diffs.diffs().keySet());
    }
  }
}
