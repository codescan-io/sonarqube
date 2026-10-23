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

import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { downloadPdf } from '../../pdf';
import { useIsReport } from '../../report';
import AnalyticsReport from '../AnalyticsReport';

// Each tab stands in for itself: it says which period it got, whether it knows it is in the report, and stays busy
// until the test lets it load.
let mockLoaded = false;

function mockTab(name: string) {
  return function FakeTab({ defaultPeriod }: { defaultPeriod?: number }) {
    const isReport = useIsReport();
    return (
      <div aria-busy={!mockLoaded}>
        {name} {defaultPeriod ?? 'default'} {isReport ? 'report' : 'page'}
      </div>
    );
  };
}

jest.mock('../ExecutiveBrief', () => ({ __esModule: true, default: mockTab('brief') }));
jest.mock('../AnalyticsDashboard', () => ({ __esModule: true, default: mockTab('overview') }));
jest.mock('../QualityDashboard', () => ({ __esModule: true, default: mockTab('quality') }));
jest.mock('../ActivityDashboard', () => ({ __esModule: true, default: mockTab('activity') }));
jest.mock('../ValueDashboard', () => ({ __esModule: true, default: mockTab('value') }));
jest.mock('../../pdf', () => ({ downloadPdf: jest.fn() }));

beforeEach(() => {
  mockLoaded = false;
  jest.useFakeTimers();
  jest.mocked(downloadPdf).mockReset().mockResolvedValue(undefined);
});

afterEach(() => {
  jest.runOnlyPendingTimers();
  jest.useRealTimers();
});

function renderReport(onDone = jest.fn()) {
  renderComponent(
    <AnalyticsReport onDone={onDone} organization="acme" organizationName="Acme" period={90} />,
  );
  return onDone;
}

function finishLoading() {
  mockLoaded = true;
  act(() => {
    screen.getAllByText(/report$/).forEach((tab) => tab.setAttribute('aria-busy', 'false'));
  });
}

it('renders every tab over the report period, in report mode', () => {
  renderReport();

  expect(screen.getByText('analytics.report.title')).toBeInTheDocument();
  expect(
    screen.getByText('analytics.report.subtitle.Acme.analytics.quality.period.90'),
  ).toBeInTheDocument();
  expect(screen.getByText('brief 90 report')).toBeInTheDocument();
  // the overview has no period
  expect(screen.getByText('overview default report')).toBeInTheDocument();
  expect(screen.getByText('quality 90 report')).toBeInTheDocument();
  expect(screen.getByText('activity 90 report')).toBeInTheDocument();
  expect(screen.getByText('value 90 report')).toBeInTheDocument();
  expect(
    screen.getAllByRole('region').map((section) => section.getAttribute('aria-label')),
  ).toEqual([
    'analytics.tab.brief',
    'analytics.tab.overview',
    'analytics.tab.quality',
    'analytics.tab.activity',
    'analytics.tab.value',
  ]);
});

it('downloads the PDF once every tab has loaded, then goes back', async () => {
  const onDone = renderReport();

  act(() => jest.advanceTimersByTime(5000));
  expect(downloadPdf).not.toHaveBeenCalled();
  expect(screen.getByText('analytics.report.preparing')).toBeInTheDocument();

  finishLoading();
  await act(() => jest.advanceTimersByTimeAsync(2000));

  expect(downloadPdf).toHaveBeenCalledTimes(1);
  const report = jest.mocked(downloadPdf).mock.calls[0][0];
  expect(report.fileName).toMatch(/^analytics-report-acme-\d{4}-\d{2}-\d{2}\.pdf$/);
  expect(report.header).toBe('analytics.report.title · Acme · analytics.quality.period.90');
  expect(report.cover).toHaveTextContent('analytics.report.title');
  expect(report.sections.map((section) => section.getAttribute('data-report-section'))).toEqual([
    'brief',
    'overview',
    'quality',
    'activity',
    'value',
  ]);
  expect(report.pageLabel(2, 9)).toBe('analytics.report.page.2.9');
  expect(onDone).toHaveBeenCalledTimes(1);
});

it('shows progress while the PDF is created', async () => {
  jest.mocked(downloadPdf).mockImplementation(({ onProgress }) => {
    onProgress?.(3, 6);
    return new Promise<void>(jest.fn());
  });
  renderReport();
  finishLoading();
  await act(() => jest.advanceTimersByTimeAsync(2000));

  expect(screen.getByText('analytics.report.generating.3.6')).toBeInTheDocument();
});

it('offers to try again when the PDF cannot be created', async () => {
  const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
  jest.mocked(downloadPdf).mockRejectedValueOnce(new Error('canvas too large'));
  const onDone = renderReport();
  finishLoading();
  await act(() => jest.advanceTimersByTimeAsync(2000));

  expect(screen.getByText('analytics.report.failed')).toBeInTheDocument();
  expect(onDone).not.toHaveBeenCalled();

  await user.click(screen.getByRole('button', { name: 'analytics.report.retry' }));
  await act(() => jest.advanceTimersByTimeAsync(100));

  expect(downloadPdf).toHaveBeenCalledTimes(2);
  expect(onDone).toHaveBeenCalledTimes(1);
});

it('creates the PDF with what it has after waiting too long, and says so', async () => {
  renderReport();

  await act(() => jest.advanceTimersByTimeAsync(121_000));

  expect(downloadPdf).toHaveBeenCalledTimes(1);
  expect(screen.getByText('analytics.report.incomplete')).toBeInTheDocument();
});

it('can be left before the PDF is made', async () => {
  const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
  const onDone = renderReport();

  await user.click(screen.getByRole('button', { name: 'analytics.report.close' }));

  expect(onDone).toHaveBeenCalled();
  expect(downloadPdf).not.toHaveBeenCalled();
});
