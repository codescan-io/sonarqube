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
package org.sonar.server.issue.ws;

import com.google.common.io.Resources;
import java.util.Date;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.issue.impact.Severity;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.core.util.Uuids;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.property.PropertyDto;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.issue.TransitionService;
import org.sonar.server.pushapi.issues.IssueChangeEventService;
import org.sonar.server.user.UserSession;

import static java.lang.String.format;
import static org.sonar.api.issue.DefaultTransitions.OPEN_AS_VULNERABILITY;
import static org.sonar.api.issue.DefaultTransitions.RESET_AS_TO_REVIEW;
import static org.sonar.api.issue.DefaultTransitions.RESOLVE_AS_REVIEWED;
import static org.sonar.api.issue.DefaultTransitions.SET_AS_IN_REVIEW;
import static org.sonar.api.issue.Issue.RESOLUTION_EXCEPTION;
import static org.sonar.core.issue.IssueChangeContext.issueChangeContextByUserBuilder;
import static org.sonar.db.component.BranchType.BRANCH;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.ACTION_DO_TRANSITION;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_ISSUE;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_TRANSITION;

public class DoTransitionAction implements IssuesWsAction {

  private static final Logger LOG = LoggerFactory.getLogger(DoTransitionAction.class);

  /**
   * Last-resort default when DB and {@link Configuration} yield no positive value. Must match
   * {@code io.codescan.cloud.DeveloperPlugin#DEFAULT_AUTO_ASSIGN_EXPIRY_DAYS}.
   */
  private static final int FALLBACK_AUTO_ASSIGN_EXPIRY_DAYS = 10;

  /**
   * Global setting keys for exception expiry (DB {@code properties} / {@link org.sonar.api.config.Configuration}).
   * Must stay identical to {@code io.codescan.cloud.DeveloperPlugin#KEY_AUTO_ASSIGN_EXPIRY_*} in codescanng — the web API
   * module cannot depend on that plugin JAR, so the strings are defined here only.
   */
  private static final String AUTO_ASSIGN_EXPIRY_BLOCKER = "codescan.cloud.autoAssignExpiry.blocker";
  private static final String AUTO_ASSIGN_EXPIRY_CRITICAL = "codescan.cloud.autoAssignExpiry.critical";
  private static final String AUTO_ASSIGN_EXPIRY_MAJOR = "codescan.cloud.autoAssignExpiry.major";
  private static final String AUTO_ASSIGN_EXPIRY_MINOR = "codescan.cloud.autoAssignExpiry.minor";
  private static final String AUTO_ASSIGN_EXPIRY_INFO = "codescan.cloud.autoAssignExpiry.info";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final IssueChangeEventService issueChangeEventService;
  private final IssueFinder issueFinder;
  private final IssueUpdater issueUpdater;
  private final TransitionService transitionService;
  private final OperationResponseWriter responseWriter;
  private final System2 system2;
  private final Configuration configuration;

  public DoTransitionAction(DbClient dbClient, UserSession userSession, IssueChangeEventService issueChangeEventService,
    IssueFinder issueFinder, IssueUpdater issueUpdater, TransitionService transitionService,
    OperationResponseWriter responseWriter, System2 system2, Configuration configuration) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.issueChangeEventService = issueChangeEventService;
    this.issueFinder = issueFinder;
    this.issueUpdater = issueUpdater;
    this.transitionService = transitionService;
    this.responseWriter = responseWriter;
    this.system2 = system2;
    this.configuration = configuration;
  }

  @Override
  public void define(WebService.NewController controller) {
    WebService.NewAction action = controller.createAction(ACTION_DO_TRANSITION)
      .setDescription("""
        Do workflow transition on an issue. Requires authentication and Browse permission on project.<br/>
        The transitions '%s', '%s' and '%s' require the permission 'Administer Issues'.<br/>
        The transitions involving security hotspots require the permission 'Administer Security Hotspot'.
        """.formatted(DefaultTransitions.ACCEPT, DefaultTransitions.WONT_FIX, DefaultTransitions.FALSE_POSITIVE))
      .setSince("3.6")
      .setChangelog(
        new Change("10.8", "The response fields 'severity' and 'type' are not deprecated anymore."),
        new Change("10.8", format("Possible values '%s' and '%s' for response field 'severity' of 'impacts' have been added.", Severity.INFO.name(), Severity.BLOCKER.name())),
        new Change("10.4", "The transitions '%s' and '%s' are deprecated. Please use '%s' instead. The transition '%s' is deprecated too. "
          .formatted(DefaultTransitions.WONT_FIX, DefaultTransitions.CONFIRM, DefaultTransitions.ACCEPT, DefaultTransitions.UNCONFIRM)),
        new Change("10.4", "Add transition '%s'.".formatted(DefaultTransitions.ACCEPT)),
        new Change("10.4", "The response fields 'severity' and 'type' are deprecated. Please use 'impacts' instead."),
        new Change("10.4", "The response fields 'status' and 'resolution' are deprecated. Please use 'issueStatus' instead."),
        new Change("10.4", "Add 'issueStatus' field to the response."),
        new Change("10.2", "Add 'impacts', 'cleanCodeAttribute', 'cleanCodeAttributeCategory' fields to the response"),
        new Change("9.6", "Response field 'ruleDescriptionContextKey' added"),
        new Change("8.8", "The response field components.uuid is removed"),
        new Change("8.1", format("transitions '%s' and '%s' are no more supported", SET_AS_IN_REVIEW, OPEN_AS_VULNERABILITY)),
        new Change("7.8", format("added '%s', %s, %s and %s transitions for security hotspots ", SET_AS_IN_REVIEW, RESOLVE_AS_REVIEWED, OPEN_AS_VULNERABILITY, RESET_AS_TO_REVIEW)),
        new Change("7.3", "added transitions for security hotspots"),
        new Change("6.5", "the database ids of the components are removed from the response"),
        new Change("6.5", "the response field components.uuid is deprecated. Use components.key instead."))
      .setHandler(this)
      .setResponseExample(Resources.getResource(this.getClass(), "do_transition-example.json"))
      .setPost(true);

    action.createParam(PARAM_ISSUE)
      .setDescription("Issue key")
      .setRequired(true)
      .setExampleValue(Uuids.UUID_EXAMPLE_01);
    action.createParam(PARAM_TRANSITION)
      .setDescription("Transition")
      .setRequired(true)
      .setPossibleValues(DefaultTransitions.ALL);
  }

  @Override
  public void handle(Request request, Response response) {
    userSession.checkLoggedIn();
    String issue = request.mandatoryParam(PARAM_ISSUE);
    try (DbSession dbSession = dbClient.openSession(false)) {
      IssueDto issueDto = issueFinder.getByKey(dbSession, issue);
      SearchResponseData preloadedSearchResponseData = doTransition(dbSession, issueDto, request.mandatoryParam(PARAM_TRANSITION));
      responseWriter.write(issue, preloadedSearchResponseData, request, response, true);
    }
  }

  private SearchResponseData doTransition(DbSession session, IssueDto issueDto, String transitionKey) {
    DefaultIssue defaultIssue = issueDto.toDefaultIssue();
    IssueChangeContext context = issueChangeContextByUserBuilder(new Date(system2.now()), userSession.getUuid()).withRefreshMeasures().build();
    transitionService.checkTransitionPermission(transitionKey, defaultIssue);
    if (transitionService.doTransition(defaultIssue, context, transitionKey)) {
      defaultIssue.setIssueResolutionExpiresAt(calculateExceptionExpiryTimestamp(session, issueDto, defaultIssue, transitionKey));
      BranchDto branch = issueUpdater.getBranch(session, defaultIssue);
      SearchResponseData response = issueUpdater.saveIssueAndPreloadSearchResponseData(session, issueDto, defaultIssue, context, branch);

      if (branch.getBranchType().equals(BRANCH) && response.getComponentByUuid(defaultIssue.projectUuid()) != null) {
        issueChangeEventService.distributeIssueChangeEvent(defaultIssue, null, Map.of(), null, transitionKey, branch,
          response.getComponentByUuid(defaultIssue.projectUuid()).getKey());
      }
      return response;
    }
    return new SearchResponseData(issueDto);
  }

  private Long calculateExceptionExpiryTimestamp(DbSession session, IssueDto issueDto, DefaultIssue defaultIssue, String transitionKey) {
    // Only set expiry for EXCEPTION resolution
    if (!RESOLUTION_EXCEPTION.equals(defaultIssue.resolution())) {
      return null;
    }

    // Set new expiry timestamp only when transitioning TO exception
    if (DefaultTransitions.EXCEPTION.equals(transitionKey)) {
      int expiryDays = getExpiryDaysBySeverity(session, issueDto.getSeverity());
      return system2.now() + TimeUnit.DAYS.toMillis(expiryDays);
    }

    // Preserve existing expiry for other transitions that keep EXCEPTION resolution
    return issueDto.getIssueResolutionExpiresAt();
  }

  private int getExpiryDaysBySeverity(DbSession session, String severity) {
    String propertyKey = mapSeverityToPropertyKey(severity);
    return resolveAutoAssignExpiryDays(session, propertyKey);
  }

  private String mapSeverityToPropertyKey(String severity) {
    if (severity == null) {
      return AUTO_ASSIGN_EXPIRY_MAJOR;
    }
    return switch (severity) {
      case "BLOCKER" -> AUTO_ASSIGN_EXPIRY_BLOCKER;
      case "CRITICAL" -> AUTO_ASSIGN_EXPIRY_CRITICAL;
      case "MAJOR" -> AUTO_ASSIGN_EXPIRY_MAJOR;
      case "MINOR" -> AUTO_ASSIGN_EXPIRY_MINOR;
      case "INFO" -> AUTO_ASSIGN_EXPIRY_INFO;
      default -> AUTO_ASSIGN_EXPIRY_MAJOR;
    };
  }

  private int resolveAutoAssignExpiryDays(DbSession session, String propertyKey) {
    // 1. Try database property for specific severity
    PropertyDto property = dbClient.propertiesDao().selectGlobalProperty(session, propertyKey);
    if (property != null && property.getValue() != null) {
      OptionalInt fromDb = parsePositiveDays(property.getValue());
      if (fromDb.isPresent()) {
        return fromDb.getAsInt();
      }
    }

    // 2. Try configuration for specific severity (plugin defaults)
    OptionalInt fromConfig = parsePositiveDays(configuration.get(propertyKey).orElse(null));
    if (fromConfig.isPresent()) {
      return fromConfig.getAsInt();
    }

    // 3. Fallback to MAJOR severity configuration
    if (!AUTO_ASSIGN_EXPIRY_MAJOR.equals(propertyKey)) {
      OptionalInt fromMajor = parsePositiveDays(configuration.get(AUTO_ASSIGN_EXPIRY_MAJOR).orElse(null));
      if (fromMajor.isPresent()) {
        return fromMajor.getAsInt();
      }
    }

    // 4. Degrade gracefully (misconfigured instance should still allow exception transitions)
    LOG.warn(
      "No positive auto-assign expiry days for {} — using fallback {} days. "
        + "Set global properties or install the CodeScan Developer plugin (keys codescan.cloud.autoAssignExpiry.*).",
      propertyKey, FALLBACK_AUTO_ASSIGN_EXPIRY_DAYS);
    return FALLBACK_AUTO_ASSIGN_EXPIRY_DAYS;
  }

  private static OptionalInt parsePositiveDays(String raw) {
    if (raw == null || raw.isBlank()) {
      return OptionalInt.empty();
    }
    try {
      int days = Integer.parseInt(raw.trim());
      return days > 0 ? OptionalInt.of(days) : OptionalInt.empty();
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
