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

import static org.sonarqube.ws.client.issue.IssuesWsParameters.ACTION_CONTEXT;
import static org.sonarqube.ws.client.issue.IssuesWsParameters.PARAM_ISSUE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewAction;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.protobuf.DbFileSources.Line;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.issue.IssueFinder;
import org.sonar.server.user.UserSession;
import org.sonar.server.source.SourceService;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.ai.CsAiRuleCatalogDto;
import org.sonar.db.ai.CsAiRulesCatalogMapper;

public class IssueContextAction implements IssuesWsAction {

    private final UserSession userSession;
    private final DbClient dbClient;
    private final IssueFinder issueFinder;
    private final ComponentFinder componentFinder;
    private final SourceService sourceService;

    public IssueContextAction(UserSession userSession, DbClient dbClient, IssueFinder issueFinder,
            ComponentFinder componentFinder, SourceService sourceService) {
        this.userSession = userSession;
        this.dbClient = dbClient;
        this.issueFinder = issueFinder;
        this.componentFinder = componentFinder;
        this.sourceService = sourceService;
    }

    @Override
    public void define(NewController controller) {
        NewAction action = controller.createAction(ACTION_CONTEXT).setDescription(
                        "Returns violation metadata and a contextual code snippet.")
                .setSince("10.9").setHandler(this);

        action.createParam(PARAM_ISSUE).setDescription("Issue key").setRequired(true)
                .setExampleValue("AU-Tpxb--iU5OvuD2FLy");

        action.createParam("rule").setDescription("Optional rule key. If not provided, taken from the issue")
                .setRequired(false).setExampleValue("java:S100");

        action.createParam("Component Key")
                .setDescription("Optional component key. If not provided, taken from the issue")
                .setRequired(false).setExampleValue("abc/sample.cls");

        action.createParam("Context Severity")
                .setDescription("Optional severity of context. If not provided, taken from the issue")
                .setRequired(false).setExampleValue("MEDIUM");
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        userSession.checkLoggedIn();
        String issueKey = request.mandatoryParam(PARAM_ISSUE);

        try (DbSession dbSession = dbClient.openSession(false); JsonWriter json = response.newJsonWriter()) {
            IssueDto issue = issueFinder.getByKey(dbSession, issueKey);
            ComponentDto file = componentFinder.getByKey(dbSession, issue.getComponentKey());
            userSession.checkComponentPermission(UserRole.CODEVIEWER, file);

            String ruleKey = getRuleKey(request, issue);
            RuleCatalogInfo catalogInfo = lookupRuleCatalog(dbSession, ruleKey);
            int centerLine = getCenterLine(issue);
            SnippetRange snippetRange = determineSnippetRange(dbSession, file, centerLine, catalogInfo.contextSeverity);
            String snippet = extractSnippet(dbSession, file, snippetRange);

            writeResponse(json, issueKey, ruleKey, issue.getComponentKey(), centerLine, catalogInfo, snippet);
        }
    }

    private String getRuleKey(Request request, IssueDto issue) {
        String ruleParam = request.param("rule");
        if (ruleParam != null) {
            return ruleParam;
        }
        if (issue.getRuleRepo() != null && issue.getRule() != null) {
            return issue.getRuleRepo() + ":" + issue.getRule();
        }
        return null;
    }

    private RuleCatalogInfo lookupRuleCatalog(DbSession dbSession, String ruleKey) {
        if (ruleKey == null) {
            return new RuleCatalogInfo(null, null);
        }
        CsAiRulesCatalogMapper mapper = dbSession.getMapper(CsAiRulesCatalogMapper.class);
        CsAiRuleCatalogDto dto = mapper.selectByRuleKey(ruleKey);
        if (dto == null) {
            return new RuleCatalogInfo(null, null);
        }
        return new RuleCatalogInfo(dto.getDescription(), dto.getContextSeverity());
    }

    private int getCenterLine(IssueDto issue) {
        Integer line = issue.getLine();
        return line != null && line > 0 ? line : 1;
    }

    private SnippetRange determineSnippetRange(DbSession dbSession, ComponentDto file, int centerLine,
            String contextSeverity) {
        if ("MEDIUM".equalsIgnoreCase(contextSeverity) || "HIGH".equalsIgnoreCase(contextSeverity)) {
            List<String> contents = loadFileContents(dbSession, file);
            int[] range = "HIGH".equalsIgnoreCase(contextSeverity)
                    ? findEnclosingClassRange(centerLine, contents)
                    : findEnclosingMethodRange(centerLine, contents);
            return new SnippetRange(Math.max(1, range[0]), Math.max(range[0], range[1]));
        }
        return new SnippetRange(Math.max(1, centerLine - 5), centerLine + 5);
    }

    private List<String> loadFileContents(DbSession dbSession, ComponentDto file) {
        Optional<Iterable<Line>> allLinesOpt = sourceService.getLines(dbSession, file.uuid(), 1, Integer.MAX_VALUE);
        List<String> contents = new ArrayList<>();
        if (allLinesOpt.isPresent()) {
            for (Line l : allLinesOpt.get()) {
                contents.add(l.getSource());
            }
        }
        return contents;
    }

    private String extractSnippet(DbSession dbSession, ComponentDto file, SnippetRange range) {
        Optional<Iterable<Line>> optLines = sourceService.getLines(dbSession, file.uuid(), range.from, range.to);
        StringBuilder snippetBuilder = new StringBuilder();
        if (optLines.isPresent()) {
            boolean first = true;
            for (Line l : optLines.get()) {
                if (!first) {
                    snippetBuilder.append('\n');
                }
                first = false;
                snippetBuilder.append(l.getSource());
            }
        }
        return snippetBuilder.toString();
    }

    private void writeResponse(JsonWriter json, String issueKey, String ruleKey, String componentKey,
            int centerLine, RuleCatalogInfo catalogInfo, String snippet) {
        json.beginObject();
        json.prop("issueKey", issueKey);
        if (ruleKey != null) {
            json.prop("ruleKey", ruleKey);
        }
        json.prop("componentKey", componentKey);
        json.prop("line", centerLine);
        if (catalogInfo.description != null) {
            json.prop("ruleDescription", catalogInfo.description);
        }
        if (catalogInfo.contextSeverity != null) {
            json.prop("contextSeverity", catalogInfo.contextSeverity);
        }
        json.prop("snippet", snippet);
        json.endObject();
    }

    private record RuleCatalogInfo(String description, String contextSeverity) {

    }

    private record SnippetRange(int from, int to) {

    }

    private int[] findEnclosingMethodRange(int centerLine, List<String> lines) {
        int n = lines.size();
        int i = Math.max(1, centerLine);
        // 1) find a likely method signature line above the center line
        int signatureLine = -1;
        for (int l = i; l >= 1; l--) {
            String s = lines.get(l - 1).trim();
            if (isLikelyMethodSignature(s)) {
                signatureLine = l;
                break;
            }
        }
        if (signatureLine == -1) {
            // Fallback to a small context when we cannot be confident
            int from = Math.max(1, i - 5);
            int to = Math.min(n, i + 5);
            return new int[]{from, to};
        }

        // 2) from the signature line, find the opening brace on this or subsequent lines (to handle signatures broken across lines)
        int openBraceLine = findOpeningBraceAtOrBelow(signatureLine, lines);
        if (openBraceLine == -1) {
            // Likely a language without braces (e.g. Python) or unformatted source, fallback
            int from = Math.max(1, signatureLine);
            int to = Math.min(n, signatureLine + 20);
            return new int[]{from, to};
        }

        // 3) match braces to find the end of the method block
        int methodEnd = Math.max(openBraceLine, findMatchingBraceDownwards(openBraceLine, lines));
        return new int[]{openBraceLine, methodEnd};
    }

    private int[] findEnclosingClassRange(int centerLine, List<String> lines) {
        int i = Math.max(1, centerLine);
        // Search upwards for a line hinting a class declaration
        int classStartCandidate = i;
        for (int l = i; l >= 1; l--) {
            String s = lines.get(l - 1).trim();
            if (s.matches(".*\\bclass\\b.*\\{.*") || s.startsWith("class ") || s.matches(".*\\bclass\\b.*(:|$)")) {
                classStartCandidate = l;
                break;
            }
        }
        int classStart = Math.max(1, findOpeningBraceAtOrAbove(classStartCandidate, lines));
        int classEnd = Math.max(classStart, findMatchingBraceDownwards(classStart, lines));
        return new int[]{classStart, classEnd};
    }

    private int findOpeningBraceAtOrAbove(int fromLine, List<String> lines) {
        for (int l = fromLine; l >= 1; l--) {
            String s = lines.get(l - 1);
            int idx = s.indexOf('{');
            if (idx >= 0) {
                return l;
            }
        }
        return Math.max(1, fromLine - 5);
    }

    private int findOpeningBraceAtOrBelow(int fromLine, List<String> lines) {
        int n = lines.size();
        for (int l = fromLine; l <= n; l++) {
            String s = lines.get(l - 1);
            if (s.indexOf('{') >= 0) {
                return l;
            }
            // stop early if we hit another declaration to avoid spanning too far
            String t = s.trim();
            if (isLikelyMethodSignature(t) || t.contains(" class ")) {
                break;
            }
        }
        return -1;
    }

    private int findMatchingBraceDownwards(int startLineWithBrace, List<String> lines) {
        int depth = 0;
        boolean started = false;
        int n = lines.size();
        for (int l = startLineWithBrace; l <= n; l++) {
            String s = lines.get(l - 1);
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '{') {
                    depth++;
                    started = true;
                } else if (ch == '}') {
                    depth--;
                    if (started && depth == 0) {
                        return l;
                    }
                }
            }
        }
        return Math.min(n, startLineWithBrace + 50);
    }

    private boolean isLikelyMethodSignature(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        // Exclude common control statements to avoid capturing blocks like if/for/while/switch/catch
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("if ") || lower.startsWith("if(")
                || lower.startsWith("for ") || lower.startsWith("for(")
                || lower.startsWith("while ") || lower.startsWith("while(")
                || lower.startsWith("switch ") || lower.startsWith("switch(")
                || lower.startsWith("catch ") || lower.startsWith("catch(")
                || lower.startsWith("else") || lower.startsWith("do ") || lower.equals("do")) {
            return false;
        }
        // Heuristic for method: contains '(' and ')' and not ending with ';' (to exclude declarations) and often followed by '{'
        if (trimmed.contains("(") && trimmed.contains(")") && !trimmed.endsWith(";")) {
            // Also ensure there's an identifier before '('
            int idx = trimmed.indexOf('(');
            if (idx > 0) {
                char prev = trimmed.charAt(idx - 1);
                if (Character.isJavaIdentifierPart(prev)) {
                    return true;
                }
            }
        }
        return false;
    }
}
