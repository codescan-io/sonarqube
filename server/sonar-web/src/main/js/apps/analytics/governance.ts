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

import {
  AnalyticsIssue,
  AnalyticsUser,
  AuditChange,
  AuditUserActivity,
  CustomRuleStats,
  GovernedException,
  ScanChange,
  TypeSeverityCounts,
} from '../../api/analytics';
import { CsvTable } from './csv';
import { SEVERITIES } from './utils';

/** The four issue types the security reports cover, hotspots included. */
export const REPORT_ISSUE_TYPES = [
  'VULNERABILITY',
  'BUG',
  'CODE_SMELL',
  'SECURITY_HOTSPOT',
] as const;
export type ReportIssueType = (typeof REPORT_ISSUE_TYPES)[number];

/** The audiences of the governance reports. */
export enum ReportGroup {
  Compliance = 'compliance',
  Security = 'security',
  Admin = 'admin',
}

export const REPORT_GROUPS = Object.values(ReportGroup);

/** Adds up the type and severity counts of every project. */
export function sumTypeSeverity(
  projects: Array<{ issues: TypeSeverityCounts }>,
): TypeSeverityCounts {
  const total: TypeSeverityCounts = {};
  projects.forEach(({ issues }) => {
    Object.entries(issues).forEach(([type, bySeverity]) => {
      const row = (total[type] = total[type] ?? {});
      Object.entries(bySeverity ?? {}).forEach(([severity, count]) => {
        row[severity] = (row[severity] ?? 0) + (count ?? 0);
      });
    });
  });
  return total;
}

export function countOf(matrix: TypeSeverityCounts, type?: string, severity?: string): number {
  return Object.entries(matrix)
    .filter(([t]) => type === undefined || t === type)
    .reduce(
      (sum, [, bySeverity]) =>
        sum +
        Object.entries(bySeverity ?? {})
          .filter(([s]) => severity === undefined || s === severity)
          .reduce((rowSum, [, count]) => rowSum + (count ?? 0), 0),
      0,
    );
}

/** The type × severity matrix as a table, types as rows, with totals. */
export function matrixTable(
  matrix: TypeSeverityCounts,
  typeLabel: (type: string) => string,
  severityLabel: (severity: string) => string,
  totalLabel: string,
): CsvTable {
  return {
    header: ['', ...SEVERITIES.map(severityLabel), totalLabel],
    rows: [
      ...REPORT_ISSUE_TYPES.map((type) => [
        typeLabel(type),
        ...SEVERITIES.map((severity) => countOf(matrix, type, severity)),
        countOf(matrix, type),
      ]),
      [
        totalLabel,
        ...SEVERITIES.map((severity) => countOf(matrix, undefined, severity)),
        countOf(matrix),
      ],
    ],
  };
}

export function userLabel(user?: AnalyticsUser) {
  if (!user) {
    return '';
  }
  return user.name ? `${user.name} (${user.login})` : user.login;
}

const ISSUE_HEADER = [
  'Issue key',
  'Project',
  'File',
  'Line',
  'Rule',
  'Rule name',
  'Type',
  'Severity',
];

function issueCells(issue: AnalyticsIssue) {
  return [
    issue.issueKey,
    issue.projectName ?? issue.projectKey,
    issue.filePath,
    issue.line,
    issue.rule,
    issue.ruleName,
    issue.type,
    issue.severity,
  ];
}

export function exceptionsTable(exceptions: GovernedException[]): CsvTable {
  return {
    header: [
      ...ISSUE_HEADER,
      'Status',
      'Marked by',
      'Marked at',
      'Rationale source',
      'Rationale',
      'Rationale by',
      'Comments',
      'Expires at',
      'Expired',
    ],
    rows: exceptions.map((exception) => [
      ...issueCells(exception),
      exception.issueStatus,
      userLabel(exception.markedBy),
      exception.markedAt,
      exception.rationale?.source,
      exception.rationale?.text,
      userLabel(exception.rationale?.user),
      exception.comments,
      exception.expiresAt,
      exception.expired,
    ]),
  };
}

export function auditTable(changes: AuditChange[]): CsvTable {
  return {
    header: [
      'Date',
      'User',
      'Action',
      'From',
      'To',
      'Comment',
      'Issue key',
      'Project',
      'File',
      'Rule',
      'Rule name',
      'Type',
      'Severity',
    ],
    rows: changes.map((change) => [
      change.date,
      change.user ? userLabel(change.user) : 'Analysis',
      change.action,
      change.from,
      change.to,
      change.text,
      change.issueKey,
      change.projectName ?? change.projectKey,
      change.filePath,
      change.rule,
      change.ruleName,
      change.type,
      change.severity,
    ]),
  };
}

/** Lower-case audit action keys, as the per-user summary counts them. */
export const AUDIT_SUMMARY_ACTIONS = [
  'accepted',
  'false_positive',
  'reopened',
  'safe',
  'acknowledged',
  'fixed',
  'severity_changed',
  'status_changed',
  'commented',
  'exception_reason',
];

export function auditUsersTable(users: AuditUserActivity[], analysisLabel: string): CsvTable {
  return {
    header: ['User', 'Total', ...AUDIT_SUMMARY_ACTIONS],
    rows: users.map((user) => [
      user.login ? userLabel({ login: user.login, name: user.name }) : analysisLabel,
      user.total,
      ...AUDIT_SUMMARY_ACTIONS.map((action) => user.actions[action] ?? 0),
    ]),
  };
}

export function scanIssuesTable(issues: AnalyticsIssue[]): CsvTable {
  return {
    header: [...ISSUE_HEADER, 'Status', 'Resolution', 'Created at', 'Closed at'],
    rows: issues.map((issue) => [
      ...issueCells(issue),
      issue.status,
      issue.resolution,
      issue.createdAt,
      issue.closedAt,
    ]),
  };
}

export function scanChangesTable(changes: ScanChange[]): CsvTable {
  return {
    header: [
      'Date',
      'User',
      'Issue key',
      'File',
      'Rule',
      'Rule name',
      'Type',
      'Severity now',
      'Change',
      'From',
      'To',
    ],
    rows: changes.map((change) => [
      change.date,
      userLabel(change.user),
      change.issueKey,
      change.filePath,
      change.rule,
      change.ruleName,
      change.type,
      change.severity,
      change.outcome ?? 'SEVERITY',
      change.from,
      change.to,
    ]),
  };
}

export function customRulesTable(rules: CustomRuleStats[]): CsvTable {
  return {
    header: [
      'Rule',
      'Name',
      'Language',
      'Template',
      'Default severity',
      'Type',
      'Open',
      ...SEVERITIES.map((severity) => `Open ${severity}`),
      'Accepted',
      'False positive',
      'Fixed',
      'Total',
      'Dismissal rate',
      'Introduced in period',
      'Closed in period',
    ],
    rows: rules.map((rule) => [
      rule.key,
      rule.name,
      rule.language,
      rule.template,
      rule.severity,
      rule.type,
      rule.issues.open,
      ...SEVERITIES.map((severity) => rule.issues.openBySeverity[severity] ?? 0),
      rule.issues.accepted,
      rule.issues.falsePositive,
      rule.issues.fixed,
      rule.issues.total,
      rule.dismissalRate === undefined ? undefined : rule.dismissalRate.toFixed(3),
      rule.introduced,
      rule.closed,
    ]),
  };
}

/** Rules dismissed this often, with enough findings to tell, are worth tuning. */
export const TUNING_DISMISSAL_RATE = 0.3;
export const TUNING_MIN_ISSUES = 10;

export function needsTuning(rule: CustomRuleStats) {
  return (
    rule.issues.total >= TUNING_MIN_ISSUES &&
    rule.dismissalRate !== undefined &&
    rule.dismissalRate >= TUNING_DISMISSAL_RATE
  );
}
