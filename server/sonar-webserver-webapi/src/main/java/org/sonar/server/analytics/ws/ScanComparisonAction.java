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
package org.sonar.server.analytics.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.config.Configuration;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.Pagination;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.component.SnapshotQuery;
import org.sonar.db.issue.AnalyticsIssueDto;
import org.sonar.db.issue.IssueAuditChangeDto;
import org.sonar.db.issue.IssueGroupCountDto;
import org.sonar.db.measure.PastMeasureQuery;
import org.sonar.db.measure.ProjectMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.sonar.api.issue.Issue.RESOLUTION_FALSE_POSITIVE;
import static org.sonar.api.issue.Issue.RESOLUTION_WONT_FIX;
import static org.sonar.api.measures.CoreMetrics.ACCEPTED_ISSUES_KEY;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.CoreMetrics.BLOCKER_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.BUGS_KEY;
import static org.sonar.api.measures.CoreMetrics.CODE_SMELLS_KEY;
import static org.sonar.api.measures.CoreMetrics.CRITICAL_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.FALSE_POSITIVE_ISSUES_KEY;
import static org.sonar.api.measures.CoreMetrics.INFO_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.MAJOR_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.MINOR_VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.SECURITY_HOTSPOTS_KEY;
import static org.sonar.api.measures.CoreMetrics.VIOLATIONS_KEY;
import static org.sonar.api.measures.CoreMetrics.VULNERABILITIES_KEY;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.core.config.PurgeConstants.DAYS_BEFORE_DELETING_CLOSED_ISSUES;
import static org.sonar.db.component.SnapshotDto.STATUS_PROCESSED;
import static org.sonar.server.analytics.ws.AnalyticsWsSupport.PARAM_ORGANIZATION;

/**
 * Compares two analyses of a project's main branch: how the counts moved, which issues are new, which were remediated, and
 * which changed severity or were accepted, marked as false positive or reopened in between.
 */
public class ScanComparisonAction implements AnalyticsWsAction {

  static final String PARAM_PROJECT = "project";
  static final String PARAM_BASE_ANALYSIS = "baseAnalysis";
  static final String PARAM_ANALYSIS = "analysis";
  static final String PARAM_LIST = "list";
  static final String LIST_NEW = "new";
  static final String LIST_REMEDIATED = "remediated";
  static final String LIST_SEVERITY_CHANGES = "severity_changes";
  static final String LIST_EXCEPTION_CHANGES = "exception_changes";
  static final List<String> LISTS = List.of(LIST_NEW, LIST_REMEDIATED, LIST_SEVERITY_CHANGES, LIST_EXCEPTION_CHANGES);
  private static final long DAY_MS = 24L * 60 * 60 * 1000;

  static final List<String> METRIC_KEYS = List.of(ALERT_STATUS_KEY, VIOLATIONS_KEY, BUGS_KEY, VULNERABILITIES_KEY, CODE_SMELLS_KEY,
    SECURITY_HOTSPOTS_KEY, BLOCKER_VIOLATIONS_KEY, CRITICAL_VIOLATIONS_KEY, MAJOR_VIOLATIONS_KEY, MINOR_VIOLATIONS_KEY,
    INFO_VIOLATIONS_KEY, ACCEPTED_ISSUES_KEY, FALSE_POSITIVE_ISSUES_KEY);

  private final DbClient dbClient;
  private final AnalyticsWsSupport support;
  private final System2 system2;
  private final Configuration configuration;

  public ScanComparisonAction(DbClient dbClient, AnalyticsWsSupport support, System2 system2, Configuration configuration) {
    this.dbClient = dbClient;
    this.support = support;
    this.system2 = system2;
    this.configuration = configuration;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction("scan_comparison")
      .setDescription("Compares an analysis of a project's main branch with an earlier one: the counts of both (issues by type and " +
        "severity, accepted, false positives, quality gate), how many issues the analyses in between raised (new) and fixed or removed " +
        "(remediated), and how many changed severity or were accepted, marked as false positive or reopened in between. " +
        "Security hotspots are only counted.<br/>" +
        "With '" + PARAM_LIST + "', returns one page of that list instead: the issues or changes themselves, any number of them.<br/>" +
        "Closed issues are deleted after '" + DAYS_BEFORE_DELETING_CLOSED_ISSUES + "' days: when the earlier analysis is older, " +
        "the remediated issues listed are incomplete ('remediated.complete' is false), while the counts stay exact.<br/>" +
        "Requires 'Administer' permission on the organization.")
      .setSince("10.8")
      .setInternal(true)
      .setResponseExample(getClass().getResource("scan_comparison-example.json"))
      .setHandler(this);
    AnalyticsWsSupport.defineOrganizationParam(action);
    action.createParam(PARAM_PROJECT)
      .setDescription("Project key")
      .setRequired(true)
      .setExampleValue("my-project");
    action.createParam(PARAM_BASE_ANALYSIS)
      .setDescription("Key of the earlier analysis of the main branch, as listed by api/project_analyses/search")
      .setRequired(true)
      .setExampleValue("AU-Tpxb--iU5OvuD2FLy");
    action.createParam(PARAM_ANALYSIS)
      .setDescription("Key of the later analysis. Defaults to the latest analysis of the main branch.")
      .setExampleValue("AU-TpxcA-iU5OvuD2FL0");
    action.createParam(PARAM_LIST)
      .setDescription("The list to page through instead of the summary: new issues, remediated issues, severity changes, or issues " +
        "accepted, marked as false positive or reopened")
      .setPossibleValues(LISTS);
    AnalyticsJson.definePaging(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto organization = support.checkCanViewAnalytics(dbSession, request.mandatoryParam(PARAM_ORGANIZATION));
      ProjectDto project = AnalyticsIssues.checkProjectOfOrganization(dbClient, dbSession, organization, request.mandatoryParam(PARAM_PROJECT));
      BranchDto branch = dbClient.branchDao().selectMainBranchByProjectUuid(dbSession, project.getUuid())
        .orElseThrow(() -> new NotFoundException("The project has no main branch"));
      SnapshotDto base = analysis(dbSession, branch, request.mandatoryParam(PARAM_BASE_ANALYSIS));
      String analysisKey = request.param(PARAM_ANALYSIS);
      SnapshotDto current = analysisKey == null
        ? dbClient.snapshotDao().selectLastAnalysisByComponentUuid(dbSession, branch.getUuid())
          .orElseThrow(() -> new NotFoundException("The project has no analysis"))
        : analysis(dbSession, branch, analysisKey);
      if (base.getCreatedAt() >= current.getCreatedAt()) {
        throw BadRequestException.create("'" + PARAM_BASE_ANALYSIS + "' must be older than '" + PARAM_ANALYSIS + "'");
      }

      // Issues are persisted right after their analysis is: those of the analyses after the base one were created from the
      // processing of the next analysis on, until the processing of the one after the current analysis.
      List<SnapshotDto> later = dbClient.snapshotDao().selectAnalysesByQuery(dbSession, new SnapshotQuery()
        .setRootComponentUuid(branch.getUuid())
        .setStatus(STATUS_PROCESSED)
        .setCreatedAfter(base.getCreatedAt() + 1)
        .setSort(SnapshotQuery.SORT_FIELD.BY_DATE, SnapshotQuery.SORT_ORDER.ASC));
      Range range = new Range(branch.getUuid(), base.getCreatedAt(), current.getCreatedAt(), processedAt(later.get(0)),
        later.stream().filter(s -> s.getCreatedAt() > current.getCreatedAt()).findFirst()
          .map(ScanComparisonAction::processedAt).orElse(null));

      String list = request.param(PARAM_LIST);
      try (JsonWriter json = response.newJsonWriter()) {
        json.beginObject();
        if (list == null) {
          writeSummary(json, dbSession, project, branch, base, current, range);
        } else {
          writeList(json, dbSession, range, list, AnalyticsJson.pagination(request));
        }
        json.endObject();
      }
    }
  }

  /** The two analyses compared, and when the issues of the analyses in between were created and closed. */
  private record Range(String branchUuid, long baseAt, long currentAt, long newFrom, @Nullable Long newTo) {
  }

  private void writeSummary(JsonWriter json, DbSession dbSession, ProjectDto project, BranchDto branch, SnapshotDto base,
    SnapshotDto current, Range range) {
    List<IssueGroupCountDto> newCounts = dbClient.issueDao().countIssuesCreatedOnBranch(dbSession, range.branchUuid(), range.newFrom(),
      range.newTo());
    List<IssueGroupCountDto> closedCounts = dbClient.issueDao().countIssuesClosedOnBranch(dbSession, range.branchUuid(), range.baseAt(),
      range.currentAt());
    ChangeTracks changes = changes(dbSession, range);
    int retentionDays = configuration.getInt(DAYS_BEFORE_DELETING_CLOSED_ISSUES).orElse(IssueResolutionsAction.DEFAULT_RETENTION_DAYS);

    json.name("project").beginObject().prop("key", project.getKey()).prop("name", project.getName()).endObject();
    writeAnalysis(json, "base", base);
    writeAnalysis(json, "analysis", current);
    writeMeasures(json, dbSession, branch, base, current);

    json.name("newIssues").beginObject();
    writeCounts(json, newCounts);
    json.endObject();

    json.name("remediated").beginObject()
      .prop("complete", base.getCreatedAt() >= system2.now() - retentionDays * DAY_MS)
      .prop("retentionDays", retentionDays);
    writeCounts(json, closedCounts);
    json.endObject();

    changes.writeSummary(json);
  }

  private void writeList(JsonWriter json, DbSession dbSession, Range range, String list, Pagination pagination) {
    switch (list) {
      case LIST_NEW -> writeIssues(json, pagination,
        sum(dbClient.issueDao().countIssuesCreatedOnBranch(dbSession, range.branchUuid(), range.newFrom(), range.newTo())),
        dbClient.issueDao().selectIssuesCreatedOnBranch(dbSession, range.branchUuid(), range.newFrom(), range.newTo(), pagination));
      case LIST_REMEDIATED -> writeIssues(json, pagination,
        sum(dbClient.issueDao().countIssuesClosedOnBranch(dbSession, range.branchUuid(), range.baseAt(), range.currentAt())),
        dbClient.issueDao().selectIssuesClosedOnBranch(dbSession, range.branchUuid(), range.baseAt(), range.currentAt(), pagination));
      default -> {
        ChangeTracks changes = changes(dbSession, range);
        Map<String, UserDto> users = AnalyticsIssues.usersByUuid(dbClient, dbSession, changes.userUuids());
        changes.writeList(json, list.equals(LIST_SEVERITY_CHANGES), pagination, users);
      }
    }
  }

  private ChangeTracks changes(DbSession dbSession, Range range) {
    ChangeTracks changes = new ChangeTracks();
    dbClient.issueChangeDao().selectResolutionAndSeverityChangesOnBranch(dbSession, range.branchUuid(), range.baseAt(), range.currentAt())
      .forEach(changes::add);
    return changes;
  }

  private SnapshotDto analysis(DbSession dbSession, BranchDto branch, String analysisKey) {
    return dbClient.snapshotDao().selectByUuid(dbSession, analysisKey)
      .filter(s -> branch.getUuid().equals(s.getRootComponentUuid()) && STATUS_PROCESSED.equals(s.getStatus()))
      .orElseThrow(() -> new NotFoundException(String.format("Analysis '%s' of the main branch not found", analysisKey)));
  }

  private static long processedAt(SnapshotDto analysis) {
    return Optional.ofNullable(analysis.getAnalysisDate()).orElse(analysis.getCreatedAt());
  }

  private static void writeAnalysis(JsonWriter json, String name, SnapshotDto analysis) {
    json.name(name).beginObject()
      .prop("key", analysis.getUuid())
      .prop("date", formatDateTime(analysis.getCreatedAt()))
      .prop("projectVersion", analysis.getProjectVersion())
      .endObject();
  }

  private void writeMeasures(JsonWriter json, DbSession dbSession, BranchDto branch, SnapshotDto base, SnapshotDto current) {
    Map<String, String> keysByUuid = new HashMap<>();
    for (MetricDto metric : dbClient.metricDao().selectByKeys(dbSession, METRIC_KEYS)) {
      keysByUuid.put(metric.getUuid(), metric.getKey());
    }
    Map<String, ProjectMeasureDto> baseMeasures = new HashMap<>();
    Map<String, ProjectMeasureDto> currentMeasures = new HashMap<>();
    if (!keysByUuid.isEmpty()) {
      for (ProjectMeasureDto measure : dbClient.projectMeasureDao().selectPastMeasures(dbSession, new PastMeasureQuery(branch.getUuid(),
        new ArrayList<>(keysByUuid.keySet()), base.getCreatedAt(), current.getCreatedAt() + 1))) {
        String key = keysByUuid.get(measure.getMetricUuid());
        if (base.getUuid().equals(measure.getAnalysisUuid())) {
          baseMeasures.put(key, measure);
        } else if (current.getUuid().equals(measure.getAnalysisUuid())) {
          currentMeasures.put(key, measure);
        }
      }
    }
    json.name("measures").beginArray();
    for (String key : METRIC_KEYS) {
      ProjectMeasureDto before = baseMeasures.get(key);
      ProjectMeasureDto after = currentMeasures.get(key);
      if (before == null && after == null) {
        continue;
      }
      json.beginObject().prop("metric", key);
      if (ALERT_STATUS_KEY.equals(key)) {
        json.prop("base", before == null ? null : before.getData())
          .prop("analysis", after == null ? null : after.getData());
      } else {
        long beforeValue = before == null || before.getValue() == null ? 0 : before.getValue().longValue();
        long afterValue = after == null || after.getValue() == null ? 0 : after.getValue().longValue();
        json.prop("base", beforeValue).prop("analysis", afterValue).prop("change", afterValue - beforeValue);
      }
      json.endObject();
    }
    json.endArray();
  }

  private static long sum(List<IssueGroupCountDto> counts) {
    return counts.stream().mapToLong(IssueGroupCountDto::getCount).sum();
  }

  private static void writeCounts(JsonWriter json, List<IssueGroupCountDto> counts) {
    json.prop("total", sum(counts))
      .prop("open", counts.stream().filter(c -> c.getResolution() == null).mapToLong(IssueGroupCountDto::getCount).sum());
    AnalyticsIssues.writeByTypeAndSeverity(json, "byType", AnalyticsIssues.byTypeAndSeverity(counts));
  }

  private static void writeIssues(JsonWriter json, Pagination pagination, long total, List<AnalyticsIssueDto> issues) {
    AnalyticsJson.writePaging(json, pagination, (int) total);
    json.name("issues").beginArray();
    for (AnalyticsIssueDto issue : issues) {
      json.beginObject();
      AnalyticsIssues.writeIssueProps(json, issue);
      json.endObject();
    }
    json.endArray();
  }

  /** The net severity and resolution change of every issue between the two analyses. */
  private static class ChangeTracks {
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    void add(IssueAuditChangeDto change) {
      FieldDiffs diffs = FieldDiffs.parse(change.getChangeData());
      Track track = tracks.computeIfAbsent(change.getIssueKey(), k -> new Track(change));
      FieldDiffs.Diff<?> severity = diffs.get("severity");
      if (severity != null) {
        if (!track.severityChanged) {
          track.severityFrom = valueOf(severity.oldValue());
          track.severityChanged = true;
        }
        track.severityTo = valueOf(severity.newValue());
        track.severityChange = change;
      }
      FieldDiffs.Diff<?> resolution = diffs.get("resolution");
      if (resolution != null) {
        if (!track.resolutionChanged) {
          track.resolutionFrom = valueOf(resolution.oldValue());
          track.resolutionChanged = true;
        }
        track.resolutionTo = valueOf(resolution.newValue());
        track.resolutionChange = change;
      }
    }

    Set<String> userUuids() {
      Set<String> uuids = new HashSet<>();
      for (Track track : tracks.values()) {
        for (IssueAuditChangeDto change : new IssueAuditChangeDto[] {track.severityChange, track.resolutionChange}) {
          if (change != null && change.getUserUuid() != null) {
            uuids.add(change.getUserUuid());
          }
        }
      }
      return uuids;
    }

    private List<Track> severityChanges() {
      return tracks.values().stream()
        .filter(t -> t.severityChanged && !Objects.equals(t.severityFrom, t.severityTo))
        .toList();
    }

    private List<Track> exceptionChanges() {
      return tracks.values().stream().filter(t -> t.exceptionOutcome() != null).toList();
    }

    void writeSummary(JsonWriter json) {
      List<Track> severityChanges = severityChanges();
      int raised = (int) severityChanges.stream().filter(t -> rank(t.severityTo) > rank(t.severityFrom)).count();
      json.name("severityChanges").beginObject()
        .prop("total", severityChanges.size())
        .prop("raised", raised)
        .prop("lowered", severityChanges.size() - raised)
        .endObject();

      Map<String, Integer> outcomes = new LinkedHashMap<>();
      outcomes.put("ACCEPTED", 0);
      outcomes.put("FALSE_POSITIVE", 0);
      outcomes.put("REOPENED", 0);
      exceptionChanges().forEach(t -> outcomes.merge(t.exceptionOutcome(), 1, Integer::sum));
      json.name("exceptionChanges").beginObject();
      outcomes.forEach((outcome, count) -> json.prop(outcome.equals("FALSE_POSITIVE") ? "falsePositive" : outcome.toLowerCase(Locale.ENGLISH),
        count.intValue()));
      json.endObject();
    }

    void writeList(JsonWriter json, boolean severity, Pagination pagination, Map<String, UserDto> users) {
      List<Track> changes = severity ? severityChanges() : exceptionChanges();
      AnalyticsJson.writePaging(json, pagination, changes.size());
      json.name("changes").beginArray();
      changes.stream().skip(pagination.getOffset()).limit(pagination.getPageSize()).forEach(t -> {
        if (severity) {
          writeChange(json, t.severityChange, t.severityFrom, t.severityTo, null, users);
        } else {
          writeChange(json, t.resolutionChange, AnalyticsIssues.exceptionStatus(t.resolutionFrom),
            AnalyticsIssues.exceptionStatus(t.resolutionTo), t.exceptionOutcome(), users);
        }
      });
      json.endArray();
    }

    private static void writeChange(JsonWriter json, IssueAuditChangeDto change, @Nullable String from, @Nullable String to,
      @Nullable String outcome, Map<String, UserDto> users) {
      json.beginObject()
        .prop("issueKey", change.getIssueKey())
        .prop("type", AnalyticsIssues.typeName(change.getIssueType()))
        .prop("severity", change.getSeverity())
        .prop("rule", change.getRuleRepository() + ":" + change.getRuleKey())
        .prop("ruleName", change.getRuleName())
        .prop("filePath", change.getFilePath())
        .prop("outcome", outcome)
        .prop("from", from)
        .prop("to", to)
        .prop("date", formatDateTime(change.getChangedAt()));
      AnalyticsIssues.writeUser(json, "user", change.getUserUuid(), users);
      json.endObject();
    }

    private static int rank(@Nullable String severity) {
      return severity == null ? -1 : Severity.ALL.indexOf(severity);
    }

    @CheckForNull
    private static String valueOf(@Nullable Object value) {
      return value == null ? null : value.toString();
    }
  }

  private static class Track {
    private boolean severityChanged;
    private String severityFrom;
    private String severityTo;
    private IssueAuditChangeDto severityChange;
    private boolean resolutionChanged;
    private String resolutionFrom;
    private String resolutionTo;
    private IssueAuditChangeDto resolutionChange;

    Track(IssueAuditChangeDto first) {
      this.severityChange = first;
      this.resolutionChange = first;
    }

    /** ACCEPTED or FALSE_POSITIVE when it ended so, REOPENED when an exception ended open; null otherwise. */
    @CheckForNull
    String exceptionOutcome() {
      if (!resolutionChanged || Objects.equals(resolutionFrom, resolutionTo)) {
        return null;
      }
      if (RESOLUTION_WONT_FIX.equals(resolutionTo)) {
        return "ACCEPTED";
      }
      if (RESOLUTION_FALSE_POSITIVE.equals(resolutionTo)) {
        return "FALSE_POSITIVE";
      }
      boolean wasException = RESOLUTION_WONT_FIX.equals(resolutionFrom) || RESOLUTION_FALSE_POSITIVE.equals(resolutionFrom);
      return wasException && isEmpty(resolutionTo) ? "REOPENED" : null;
    }
  }
}
