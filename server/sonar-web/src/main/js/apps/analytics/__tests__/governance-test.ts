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

import { CustomRuleStats, GovernedException } from '../../../api/analytics';
import {
  auditUsersTable,
  countOf,
  customRulesTable,
  exceptionsTable,
  matrixTable,
  needsTuning,
  sumTypeSeverity,
  userLabel,
} from '../governance';

const matrix = sumTypeSeverity([
  { issues: { BUG: { MAJOR: 2 }, SECURITY_HOTSPOT: { CRITICAL: 1 } } },
  { issues: { BUG: { MAJOR: 1, BLOCKER: 4 }, VULNERABILITY: { CRITICAL: 3 } } },
]);

it('adds up the counts of every project', () => {
  expect(matrix).toEqual({
    BUG: { MAJOR: 3, BLOCKER: 4 },
    SECURITY_HOTSPOT: { CRITICAL: 1 },
    VULNERABILITY: { CRITICAL: 3 },
  });
});

it('counts by type, by severity, by both or in all', () => {
  expect(countOf(matrix)).toBe(11);
  expect(countOf(matrix, 'BUG')).toBe(7);
  expect(countOf(matrix, undefined, 'CRITICAL')).toBe(4);
  expect(countOf(matrix, 'VULNERABILITY', 'CRITICAL')).toBe(3);
  expect(countOf(matrix, 'CODE_SMELL')).toBe(0);
});

it('lays the matrix out with totals', () => {
  const table = matrixTable(
    matrix,
    (t) => t,
    (s) => s,
    'Total',
  );

  expect(table.header).toEqual(['', 'BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'INFO', 'Total']);
  expect(table.rows).toEqual([
    ['VULNERABILITY', 0, 3, 0, 0, 0, 3],
    ['BUG', 4, 0, 3, 0, 0, 7],
    ['CODE_SMELL', 0, 0, 0, 0, 0, 0],
    ['SECURITY_HOTSPOT', 0, 1, 0, 0, 0, 1],
    ['Total', 4, 4, 3, 0, 0, 11],
  ]);
});

it('labels users with their name and login', () => {
  expect(userLabel({ login: 'alice', name: 'Alice Martin' })).toBe('Alice Martin (alice)');
  expect(userLabel({ login: 'bob' })).toBe('bob');
  expect(userLabel(undefined)).toBe('');
});

it('exports exceptions with who marked them and why', () => {
  const exception: GovernedException = {
    comments: 2,
    expired: false,
    expiresAt: '2026-12-01T00:00:00+0000',
    issueKey: 'I1',
    issueStatus: 'ACCEPTED',
    markedAt: '2026-10-01T00:00:00+0000',
    markedBy: { login: 'alice', name: 'Alice' },
    projectKey: 'p',
    rationale: { source: 'EXCEPTION_REASON', text: 'Validated upstream', user: { login: 'alice' } },
    rule: 'apex:A',
  };

  const { header, rows } = exceptionsTable([exception]);

  const row = Object.fromEntries(header.map((title, i) => [title, rows[0][i]]));
  expect(row).toMatchObject({
    'Issue key': 'I1',
    Project: 'p',
    Status: 'ACCEPTED',
    'Marked by': 'Alice (alice)',
    Rationale: 'Validated upstream',
    'Rationale by': 'alice',
    'Expires at': '2026-12-01T00:00:00+0000',
    Expired: false,
  });
});

it('exports the activity of every user, the analysis included', () => {
  const table = auditUsersTable(
    [
      { login: 'alice', total: 3, actions: { accepted: 2, commented: 1 } },
      { total: 5, actions: { fixed: 5 } },
    ],
    'Analysis',
  );

  expect(table.rows[0].slice(0, 4)).toEqual(['alice', 3, 2, 0]);
  expect(table.rows[1][0]).toBe('Analysis');
});

describe('custom rules', () => {
  const rule = (total: number, dismissed: number): CustomRuleStats => ({
    closed: 0,
    dismissalRate: total === 0 ? undefined : dismissed / total,
    introduced: 0,
    issues: {
      accepted: dismissed,
      falsePositive: 0,
      fixed: 0,
      open: total - dismissed,
      openBySeverity: { MAJOR: total - dismissed },
      total,
    },
    key: 'apex:custom',
    template: 'apex:XPath',
    timeline: [],
  });

  it('flags rules dismissed often enough to tell', () => {
    expect(needsTuning(rule(20, 8))).toBe(true);
    expect(needsTuning(rule(20, 2))).toBe(false);
    // too few findings to judge
    expect(needsTuning(rule(4, 4))).toBe(false);
    expect(needsTuning(rule(0, 0))).toBe(false);
  });

  it('exports severities and the dismissal rate', () => {
    const { header, rows } = customRulesTable([rule(20, 8)]);
    const row = Object.fromEntries(header.map((title, i) => [title, rows[0][i]]));

    expect(row).toMatchObject({
      Rule: 'apex:custom',
      'Open MAJOR': 12,
      'Open BLOCKER': 0,
      'Dismissal rate': '0.400',
    });
  });
});
