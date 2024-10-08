/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import { Query } from '../../apps/issues/utils';
import { ReferencedRule } from '../../types/issues';
import { IssueChangelog } from '../../types/types';

export function mockReferencedRule(overrides: Partial<ReferencedRule> = {}): ReferencedRule {
  return {
    langName: 'Javascript',
    name: 'RuleFoo',
    ...overrides,
  };
}

export function mockIssueChangelog(overrides: Partial<IssueChangelog> = {}): IssueChangelog {
  return {
    creationDate: '2018-10-01',
    isUserActive: true,
    user: 'luke.skywalker',
    userName: 'Luke Skywalker',
    diffs: [
      {
        key: 'assign',
        newValue: 'darth.vader',
        oldValue: 'luke.skywalker',
      },
    ],
    ...overrides,
  };
}

export function mockQuery(overrides: Partial<Query> = {}): Query {
  return {
    assigned: false,
    assignees: [],
    author: [],
    createdAfter: undefined,
    createdAt: '',
    createdBefore: undefined,
    createdInLast: '',
    cwe: [],
    directories: [],
    files: [],
    issues: [],
    languages: [],
    owaspTop10: [],
    'owaspTop10-2021': [],
    'pciDss-3.2': [],
    'pciDss-4.0': [],
    'owaspAsvs-4.0': [],
    owaspAsvsLevel: '',
    projects: [],
    resolutions: [],
    resolved: false,
    rules: [],
    scopes: [],
    severities: [],
    inNewCodePeriod: false,
    sonarsourceSecurity: [],
    sort: '',
    statuses: [],
    tags: [],
    types: [],
    ...overrides,
  };
}
