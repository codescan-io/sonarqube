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

import { screen, waitFor } from '@testing-library/react';
import { getCodefixIntegration } from '../../../../api/ai-codefix';
import { isAiAssistantEnabled } from '../../../../api/settings';
import CurrentUserContextProvider from '../../../../app/components/current-user/CurrentUserContextProvider';
import { mockIssue, mockLoggedInUser } from '../../../../helpers/testMocks';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import IssueAssign from '../IssueAssign';

jest.mock('../../../../api/settings');
jest.mock('../../../../api/ai-codefix');
jest.mock('../../../../api/users', () => ({
  getUsers: jest.fn().mockResolvedValue({
    users: [
      { active: true, login: 'luke', name: 'Luke' },
      // A real user row, so the gate has to filter it out of search results too.
      { active: true, login: 'ai-code-assistant', name: 'AI Code Assistant' },
    ],
  }),
}));

const AI_CODE_ASSISTANT = 'AI Code Assistant';
const PROJECT_KEY = 'my-project';

beforeEach(() => {
  jest.clearAllMocks();
  jest.mocked(isAiAssistantEnabled).mockResolvedValue(true);
});

it.each([['github'], ['gitlab']])(
  'offers the AI Code Assistant as an assignee on a %s project',
  async (integrationType) => {
    renderIssueAssign(integrationType);

    expect(await screen.findByText(AI_CODE_ASSISTANT)).toBeInTheDocument();
  },
);

it.each([['metadata_api'], ['bitbucket'], ['bitbucket-enterprise'], ['webhook']])(
  'does not offer the AI Code Assistant on a %s project',
  async (integrationType) => {
    renderIssueAssign(integrationType);

    await waitFor(() => {
      expect(getCodefixIntegration).toHaveBeenCalledWith(PROJECT_KEY);
    });
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
  },
);

it.each([
  ['a branch', { branch: 'feature/my-branch' }],
  ['a pull request', { pullRequest: '1234' }],
])('offers the AI Code Assistant for an issue on %s of a github project', async (_, overrides) => {
  renderIssueAssign('github', overrides);

  expect(await screen.findByText(AI_CODE_ASSISTANT)).toBeInTheDocument();
  // The integration belongs to the project, so the lookup is by project key alone.
  expect(getCodefixIntegration).toHaveBeenCalledWith(PROJECT_KEY);
});

it.each([
  ['a branch', { branch: 'feature/my-branch' }],
  ['a pull request', { pullRequest: '1234' }],
])(
  'does not offer the AI Code Assistant for an issue on %s of a salesforce project',
  async (_, overrides) => {
    renderIssueAssign('metadata_api', overrides);

    await waitFor(() => {
      expect(getCodefixIntegration).toHaveBeenCalledWith(PROJECT_KEY);
    });
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
  },
);

it('does not offer the AI Code Assistant when the issue itself is not AI-eligible', async () => {
  renderIssueAssign('github', { aiCodeFixEnabled: false });

  await waitFor(() => {
    expect(getCodefixIntegration).toHaveBeenCalledWith(PROJECT_KEY);
  });
  expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
});

it('does not look the project up when the AI assistant is switched off', async () => {
  jest.mocked(isAiAssistantEnabled).mockResolvedValue(false);
  renderIssueAssign('github');

  await waitFor(() => {
    expect(isAiAssistantEnabled).toHaveBeenCalled();
  });
  expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
  expect(getCodefixIntegration).not.toHaveBeenCalled();
});

function renderIssueAssign(integrationType: string, issueOverrides = {}) {
  jest.mocked(getCodefixIntegration).mockResolvedValue({
    integrationType,
    supported: ['github', 'gitlab'].includes(integrationType),
  });

  return renderComponent(
    <CurrentUserContextProvider currentUser={mockLoggedInUser()}>
      <IssueAssign
        canAssign
        isOpen
        issue={mockIssue(false, {
          aiCodeFixEnabled: true,
          organization: 'my-org',
          project: PROJECT_KEY,
          ...issueOverrides,
        })}
        onAssign={jest.fn()}
        organization="my-org"
        togglePopup={jest.fn()}
      />
    </CurrentUserContextProvider>,
  );
}
