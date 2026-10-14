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
import { screen } from '@testing-library/react';
import { getCodefixIntegration, getCodefixStatus } from '../../../../api/ai-codefix';
import { getValues } from '../../../../api/settings';
import { mockIssue } from '../../../../helpers/testMocks';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { aiCodefixIntegrationQueryOptions } from '../../../../queries/ai-codefix';
import AiCodefixBadge from '../IssueCodefixStatusBadge';

jest.mock('../../../../api/ai-codefix');
jest.mock('../../../../api/settings');

const AI_FIX_AVAILABLE = 'AI Fix Available';
const PROJECT_KEY = 'my-project';
const INTEGRATION_LOADED = 'integration-loaded';

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(getCodefixStatus).mockResolvedValue({ status: '' });
  jest.mocked(getValues).mockResolvedValue([]);
});

it.each([['github'], ['gitlab']])(
  'shows the availability badge for a %s project',
  async (integrationType) => {
    renderBadge(integrationType);

    expect(await screen.findByText(AI_FIX_AVAILABLE)).toBeInTheDocument();
  },
);

it.each([['metadata_api (Salesforce)'], ['bitbucket'], ['bitbucket-enterprise'], ['webhook']])(
  'hides the availability badge for a %s project',
  async (integrationType) => {
    renderBadge(integrationType);

    // Waiting on the loaded marker — not merely on the request being issued — means this absence cannot be a
    // not-yet-loaded false negative.
    expect(await screen.findByText(INTEGRATION_LOADED)).toBeInTheDocument();
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
  },
);

it('hides the availability badge while the integration is still unknown', () => {
  renderBadge('github');

  expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
});

it.each([
  ['a branch', { branch: 'feature/my-branch' }],
  ['a pull request', { pullRequest: '1234' }],
])('shows the availability badge for an issue on %s of a github project', async (_, overrides) => {
  renderBadge('github', overrides);

  expect(await screen.findByText(AI_FIX_AVAILABLE)).toBeInTheDocument();
  // The integration belongs to the project, not the branch, so the lookup is by project key alone.
  expect(getCodefixIntegration).toHaveBeenCalledWith(PROJECT_KEY);
});

it.each([
  ['a branch', { branch: 'feature/my-branch' }],
  ['a pull request', { pullRequest: '1234' }],
])(
  'hides the availability badge for an issue on %s of a salesforce project',
  async (_, overrides) => {
    renderBadge('metadata_api', overrides);

    expect(await screen.findByText(INTEGRATION_LOADED)).toBeInTheDocument();
    expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
  },
);

it('still reports a real codefix status on an unsupported integration', async () => {
  jest.mocked(getCodefixStatus).mockResolvedValue({ status: 'PULL_REQUEST_CREATED' });
  renderBadge('metadata_api', { codefixStatus: 'PULL_REQUEST_CREATED' });

  expect(await screen.findByText('Pull Request Created')).toBeInTheDocument();
  expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
});

it('does not advertise availability for an unrecognised status on an unsupported integration', async () => {
  // getAiCodefixStatusDisplay falls through to "available" for any status it does not know.
  jest.mocked(getCodefixStatus).mockResolvedValue({ status: 'SOME_NEW_STATUS' });
  renderBadge('metadata_api', { codefixStatus: 'SOME_NEW_STATUS' });

  expect(await screen.findByText(INTEGRATION_LOADED)).toBeInTheDocument();
  expect(screen.queryByText(AI_FIX_AVAILABLE)).not.toBeInTheDocument();
});

function renderBadge(integrationType: string, issueOverrides = {}) {
  jest.mocked(getCodefixIntegration).mockResolvedValue({
    integrationType,
    supported: ['github', 'gitlab'].includes(integrationType),
  });

  return renderComponent(
    <>
      <AiCodefixBadge issue={mockIssue(false, { project: PROJECT_KEY, ...issueOverrides })} />
      <ProjectTagsLoaded />
    </>,
  );
}

/** Renders a marker once the badge's integration lookup has resolved, giving the tests a real load barrier. */
function ProjectTagsLoaded() {
  const { data } = useQuery(aiCodefixIntegrationQueryOptions(PROJECT_KEY));

  return <span>{data ? INTEGRATION_LOADED : 'integration-loading'}</span>;
}
