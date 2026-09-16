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

import { useQuery } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import { getCodefixIntegration, getCodefixStatus } from '../../../../api/ai-codefix';
import { getValues } from '../../../../api/settings';
import { mockIssue } from '../../../../helpers/testMocks';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { aiCodefixIntegrationQueryOptions } from '../../../../queries/ai-codefix';
import AiCodefixBadge from '../IssueCodefixStatusBadge';

jest.mock('../../../../api/ai-codefix');
jest.mock('../../../../api/settings');

const AI_FIX_AVAILABLE = 'AI Fix Available';
const GH = 'gh-project';
const SF = 'sf-project';

const supported = { integrationType: 'github', supported: true };
const unsupported = { integrationType: 'metadata_api', supported: false };

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getCodefixStatus).mockResolvedValue({ status: '' });
  jest.mocked(getValues).mockResolvedValue([]);
});

function mockIntegrations(byProject: Record<string, unknown>) {
  jest
    .mocked(getCodefixIntegration)
    .mockImplementation((projectKey) => Promise.resolve(byProject[projectKey] as any));
}

/** Marks each badge so a mixed list can be asserted per row. */
function Row({ issueKey, project }: { issueKey: string; project: string }) {
  const { data } = useQuery({ ...aiCodefixIntegrationQueryOptions(project), retry: false });

  return (
    <div data-testid={`row-${issueKey}`}>
      <AiCodefixBadge issue={mockIssue(false, { key: issueKey, project })} />
      {/* `data !== undefined` rather than truthiness, so a null payload still counts as resolved. */}
      <span>{data !== undefined ? `loaded-${issueKey}` : `loading-${issueKey}`}</span>
    </div>
  );
}

describe('QA-A: cross-project contamination in one issue list', () => {
  it('A1 shows the badge only on the supported row of a mixed list', async () => {
    mockIntegrations({ [GH]: supported, [SF]: unsupported });
    renderComponent(
      <>
        <Row project={GH} issueKey="gh-issue" />
        <Row project={SF} issueKey="sf-issue" />
      </>,
    );

    await screen.findByText('loaded-gh-issue');
    await screen.findByText('loaded-sf-issue');

    expect(
      within(screen.getByTestId('row-gh-issue')).getByText(AI_FIX_AVAILABLE),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('row-sf-issue')).queryByText(AI_FIX_AVAILABLE),
    ).not.toBeInTheDocument();
    expect(screen.getAllByText(AI_FIX_AVAILABLE)).toHaveLength(1);
  });

  it('A2 keeps rows independent when the unsupported project resolves first', async () => {
    let resolveGh: (v: unknown) => void = () => {};
    jest.mocked(getCodefixIntegration).mockImplementation((projectKey) =>
      projectKey === SF
        ? Promise.resolve(unsupported as any)
        : new Promise((resolve) => {
            resolveGh = resolve;
          }),
    );
    renderComponent(
      <>
        <Row project={GH} issueKey="gh-issue" />
        <Row project={SF} issueKey="sf-issue" />
      </>,
    );

    await screen.findByText('loaded-sf-issue');
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();

    resolveGh(supported);
    expect(await screen.findByText(AI_FIX_AVAILABLE)).toBeInTheDocument();
    expect(screen.getAllByText(AI_FIX_AVAILABLE)).toHaveLength(1);
  });

  it('A3 issues the integration lookup once for many issues of the same project', async () => {
    mockIntegrations({ [GH]: supported });
    renderComponent(
      <>
        {['i1', 'i2', 'i3', 'i4', 'i5'].map((k) => (
          <Row key={k} project={GH} issueKey={k} />
        ))}
      </>,
    );

    await screen.findByText('loaded-i5');
    expect(screen.getAllByText(AI_FIX_AVAILABLE)).toHaveLength(5);
    expect(jest.mocked(getCodefixIntegration).mock.calls).toHaveLength(1);
  });
});

describe('QA-B: unexpected response shapes fail closed without crashing', () => {
  it.each([
    ['supported field missing', { integrationType: 'github' }],
    ['empty object', {}],
    ['supported as a truthy string', { integrationType: 'github', supported: 'yes' }],
    ['supported null', { integrationType: 'github', supported: null }],
    ['null payload', null],
    ['integration type absent entirely', { supported: false }],
  ])('B: %s', async (_, payload) => {
    mockIntegrations({ [GH]: payload });
    renderComponent(<Row project={GH} issueKey="i1" />);

    await screen.findByText('loaded-i1');
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
  });
});

describe('QA-C: lookup failures fail closed', () => {
  it.each([
    ['403 forbidden', { status: 403 }],
    ['404 not found', { status: 404 }],
    ['network error', new Error('Network request failed')],
  ])('C: %s hides the badge', async (_, rejection) => {
    jest.mocked(getCodefixIntegration).mockRejectedValue(rejection);
    renderComponent(<Row project={GH} issueKey="i1" />);

    await waitFor(() => {
      expect(getCodefixIntegration).toHaveBeenCalled();
    });
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
  });
});

describe('QA-D: real statuses still surface on an unsupported integration', () => {
  it.each([
    ['PENDING', 'AI Fix in Progress'],
    ['IN_PROGRESS', 'AI Fix in Progress'],
    ['FIX_GENERATED', 'AI Fix Generated'],
    ['PULL_REQUEST_CREATED', 'Pull Request Created'],
    ['FAILED', 'AI Fix Failed'],
  ])('D: %s renders as "%s" on a Salesforce project', async (status, label) => {
    mockIntegrations({ [SF]: unsupported });
    jest.mocked(getCodefixStatus).mockResolvedValue({ status });
    renderComponent(
      <AiCodefixBadge
        issue={mockIssue(false, { key: 'i1', project: SF, codefixStatus: status })}
      />,
    );

    expect(await screen.findByText(label)).toBeInTheDocument();
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
  });
});
