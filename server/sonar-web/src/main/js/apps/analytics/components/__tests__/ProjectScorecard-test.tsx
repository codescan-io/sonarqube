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
import { getTasksForComponent } from '../../../../api/ce';
import { searchIssues } from '../../../../api/issues';
import { getMeasures } from '../../../../api/measures';
import { getQualityGateProjectStatus } from '../../../../api/quality-gates';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import ProjectScorecard from '../ProjectScorecard';

jest.mock('../../../../api/ce', () => ({ getTasksForComponent: jest.fn() }));
jest.mock('../../../../api/issues', () => ({ searchIssues: jest.fn() }));
jest.mock('../../../../api/measures', () => ({ getMeasures: jest.fn() }));
jest.mock('../../../../api/quality-gates', () => ({ getQualityGateProjectStatus: jest.fn() }));

const recent = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();

beforeEach(() => {
  jest.mocked(getQualityGateProjectStatus).mockResolvedValue({
    status: 'ERROR',
    ignoredConditions: false,
    caycStatus: 'non-compliant',
    conditions: [
      {
        status: 'ERROR',
        metricKey: 'new_coverage',
        comparator: 'LT',
        errorThreshold: '80',
        actualValue: '42',
        periodIndex: 1,
      },
      {
        status: 'OK',
        metricKey: 'new_violations',
        comparator: 'GT',
        errorThreshold: '0',
        actualValue: '0',
        periodIndex: 1,
      },
    ],
  } as never);
  jest.mocked(getMeasures).mockResolvedValue([
    { metric: 'violations', value: '120' },
    { metric: 'security_rating', value: '4.0' },
    { metric: 'coverage', value: '42.0' },
  ]);
  jest
    .mocked(searchIssues)
    .mockResolvedValue({ paging: { pageIndex: 1, pageSize: 1, total: 2 } } as never);
  jest.mocked(getTasksForComponent).mockResolvedValue({
    queue: [],
    current: { status: 'SUCCESS', executedAt: recent, submittedAt: recent } as never,
  });
});

it('says why a project is not ready to release', async () => {
  renderComponent(<ProjectScorecard name="Force App" onClose={jest.fn()} projectKey="force-app" />);

  expect(await screen.findByText('analytics.scorecard.not_ready')).toBeInTheDocument();
  expect(screen.getByText('analytics.scorecard.reason.quality_gate_failed')).toBeInTheDocument();
  expect(
    screen.getByText('analytics.scorecard.reason.critical_vulnerabilities.2'),
  ).toBeInTheDocument();
  expect(
    screen.getByText('analytics.scorecard.failed_condition.metric.new_coverage.name.42.80'),
  ).toBeInTheDocument();
  expect(screen.queryByText(/new_violations\.name/)).not.toBeInTheDocument();
  expect(screen.getByText('120')).toBeInTheDocument();
  expect(searchIssues).toHaveBeenCalledWith(
    expect.objectContaining({ components: 'force-app', impactSoftwareQualities: 'SECURITY' }),
  );
});

it('says when a project is ready to release', async () => {
  jest
    .mocked(getQualityGateProjectStatus)
    .mockResolvedValue({ status: 'OK', conditions: [] } as never);
  jest
    .mocked(searchIssues)
    .mockResolvedValue({ paging: { pageIndex: 1, pageSize: 1, total: 0 } } as never);
  renderComponent(<ProjectScorecard name="Force App" onClose={jest.fn()} projectKey="force-app" />);

  expect(await screen.findByText('analytics.scorecard.ready')).toBeInTheDocument();
});
