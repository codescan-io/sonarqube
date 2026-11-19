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

import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  ScanList,
  getScanComparison,
  getScanComparisonChanges,
  getScanComparisonIssues,
} from '../../../../api/analytics';
import { searchProjects } from '../../../../api/components';
import { getProjectActivity } from '../../../../api/projectActivity';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import ScanComparisonPanel from '../ScanComparisonPanel';

jest.mock('../../../../api/analytics', () => ({
  ...jest.requireActual('../../../../api/analytics'),
  getScanComparison: jest.fn(),
  getScanComparisonChanges: jest.fn(),
  getScanComparisonIssues: jest.fn(),
}));
jest.mock('../../../../api/components', () => ({ searchProjects: jest.fn() }));
jest.mock('../../../../api/projectActivity', () => ({ getProjectActivity: jest.fn() }));

const issue = (n: number) => ({
  filePath: `force-app/main/default/classes/very/long/path/to/Class${n}.cls`,
  issueKey: `ISSUE-${n}`,
  line: n,
  projectKey: 'payments',
  rule: 'apex:ApexCRUDViolation',
  ruleName: 'CRUD permissions should be checked',
  severity: 'CRITICAL',
  status: 'OPEN',
  type: 'VULNERABILITY',
});

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(searchProjects).mockResolvedValue({
    components: [{ key: 'payments', name: 'Payments' }],
    facets: [],
    organizations: [],
    paging: { pageIndex: 1, pageSize: 20, total: 1 },
  } as never);
  jest.mocked(getProjectActivity).mockResolvedValue({
    analyses: [
      { key: 'A2', date: '2026-10-30T08:00:00+0000', events: [] },
      { key: 'A1', date: '2026-09-01T08:00:00+0000', events: [] },
    ],
    paging: { pageIndex: 1, pageSize: 100, total: 2 },
  } as never);
  jest.mocked(getScanComparison).mockResolvedValue({
    project: { key: 'payments', name: 'Payments' },
    base: { key: 'A1', date: '2026-09-01T08:00:00+0000' },
    analysis: { key: 'A2', date: '2026-10-30T08:00:00+0000' },
    measures: [
      { metric: 'alert_status', base: 'ERROR', analysis: 'OK' },
      { metric: 'violations', base: 400, analysis: 2900, change: 2500 },
      { metric: 'bugs', base: 3, analysis: 3, change: 0 },
    ],
    newIssues: { total: 2500, open: 2400, byType: {} },
    remediated: { total: 0, open: 0, byType: {}, complete: true, retentionDays: 30 },
    severityChanges: { total: 1, raised: 1, lowered: 0 },
    exceptionChanges: { accepted: 0, falsePositive: 0, reopened: 0 },
  });
  jest.mocked(getScanComparisonIssues).mockImplementation(({ p = 1, ps = 10 }) =>
    Promise.resolve({
      issues: [issue((p - 1) * ps + 1)],
      paging: { pageIndex: p, pageSize: ps, total: 2500 },
    }),
  );
  jest.mocked(getScanComparisonChanges).mockResolvedValue({
    changes: [
      {
        date: '2026-10-03T10:00:00+0000',
        from: 'MAJOR',
        issueKey: 'ISSUE-9',
        rule: 'apex:ApexDoc',
        ruleName: 'Document public methods',
        to: 'CRITICAL',
        user: { login: 'alice', name: 'Alice' },
      },
    ],
    paging: { pageIndex: 1, pageSize: 10, total: 1 },
  });
});

function renderPanel() {
  return renderComponent(
    <ScanComparisonPanel
      organization="org"
      selection={{ base: 'A1', current: null, project: 'payments' }}
    />,
  );
}

it('pages through thousands of new findings on the server', async () => {
  const user = userEvent.setup();
  renderPanel();

  expect(
    await screen.findByText('force-app/main/default/classes/very/long/path/to/Class1.cls:1'),
  ).toBeInTheDocument();
  expect(getScanComparisonIssues).toHaveBeenLastCalledWith(
    expect.objectContaining({ list: ScanList.New, p: 1, ps: 10, baseAnalysis: 'A1' }),
  );
  // the summary itself lists nothing
  expect(getScanComparison).toHaveBeenCalledWith(
    expect.not.objectContaining({ list: expect.anything() }),
  );

  await user.click(screen.getByRole('button', { name: 'analytics.pagination.page.2' }));

  expect(await screen.findByText(/Class11\.cls:11$/)).toBeInTheDocument();
  expect(getScanComparisonIssues).toHaveBeenLastCalledWith(
    expect.objectContaining({ list: ScanList.New, p: 2, ps: 10 }),
  );
});

it('switches lists from the tabs and the tiles', async () => {
  const user = userEvent.setup();
  renderPanel();
  await screen.findByText(/Class1\.cls:1$/);

  await user.click(
    screen.getByRole('radio', {
      name: 'analytics.governance.scan.list.severity_changes (1)',
    }),
  );
  expect(await screen.findByText('Document public methods')).toBeInTheDocument();
  expect(getScanComparisonChanges).toHaveBeenLastCalledWith(
    expect.objectContaining({ list: ScanList.SeverityChanges, p: 1 }),
  );

  await user.click(screen.getByRole('button', { name: /^analytics\.governance\.scan\.new/ }));
  expect(
    screen.getByRole('radio', { name: 'analytics.governance.scan.list.new (2,500)' }),
  ).toBeChecked();
});

it('shows the measures that changed, and the others on demand', async () => {
  const user = userEvent.setup();
  renderPanel();
  expect(await screen.findByText('metric.violations.name')).toBeInTheDocument();
  expect(screen.getByText('metric.alert_status.name')).toBeInTheDocument();
  expect(screen.queryByText('metric.bugs.name')).not.toBeInTheDocument();

  await user.click(
    screen.getByRole('button', { name: 'analytics.governance.scan.show_unchanged.1' }),
  );

  expect(screen.getByText('metric.bugs.name')).toBeInTheDocument();
});
