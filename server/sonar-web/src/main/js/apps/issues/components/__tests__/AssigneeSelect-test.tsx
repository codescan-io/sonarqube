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
import userEvent from '@testing-library/user-event';
import { byRole } from '~sonar-aligned/helpers/testSelector';
import CurrentUserContextProvider from '../../../../app/components/current-user/CurrentUserContextProvider';
import { mockUserBase } from '../../../../helpers/mocks/users';
import { mockCurrentUser, mockIssue, mockLoggedInUser } from '../../../../helpers/testMocks';
import { renderComponent } from '../../../../helpers/testReactTestingUtils';
import { CurrentUser } from '../../../../types/users';
import AssigneeSelect, { AssigneeSelectProps, MIN_QUERY_LENGTH } from '../AssigneeSelect';
import { getCodefixIntegration } from '../../../../api/ai-codefix';
import { isAiAssistantEnabled } from '../../../../api/settings';

const AI_CODE_ASSISTANT = 'AI Code Assistant';

beforeEach(() => {
  jest.clearAllMocks();
  // Default: AI assistant off, so the existing suggestion tests are unaffected by the AI option.
  jest.mocked(isAiAssistantEnabled).mockResolvedValue(false);
  mockIntegration('webhook');
});

function mockIntegration(integrationType: string) {
  jest.mocked(getCodefixIntegration).mockResolvedValue({
    integrationType,
    supported: ['github', 'gitlab'].includes(integrationType),
  });
}

jest.mock('../../../../api/settings');
jest.mock('../../../../api/ai-codefix');

jest.mock('../../utils', () => ({
  searchAssignees: jest.fn().mockResolvedValue({
    results: [
      mockUserBase({
        active: true,
        avatar: 'avatar1',
        login: 'toto@toto',
        name: 'toto',
      }),
      mockUserBase({
        active: false,
        avatar: 'avatar2',
        login: 'tata@tata',
        name: 'tata',
      }),
      mockUserBase({
        active: true,
        avatar: 'avatar3',
        login: 'titi@titi',
      }),
      // A real user row, so the gate has to filter it out of search results too.
      mockUserBase({ active: true, login: 'ai-code-assistant', name: 'AI Code Assistant' }),
    ],
  }),
}));

const ui = {
  combobox: byRole('combobox', { name: 'issue_bulk_change.assignee.change' }),
};

it('should show correct suggestions when there is assignable issue for the current user', async () => {
  const user = userEvent.setup();
  renderAssigneeSelect(
    {
      issues: [mockIssue(false, { assignee: 'someone' })],
    },
    mockLoggedInUser({ name: 'Skywalker' }),
  );

  await user.click(ui.combobox.get());
  expect(await screen.findByText('Skywalker')).toBeInTheDocument();
});

it('should show correct suggestions when all issues are already assigned to current user', async () => {
  const user = userEvent.setup();
  renderAssigneeSelect(
    {
      issues: [mockIssue(false, { assignee: 'luke' })],
    },
    mockLoggedInUser({ login: 'luke', name: 'Skywalker' }),
  );

  await user.click(ui.combobox.get());
  expect(screen.queryByText('Skywalker')).not.toBeInTheDocument();
});

it('should show correct suggestions when there is no assigneable issue', async () => {
  const user = userEvent.setup();
  renderAssigneeSelect({}, mockLoggedInUser({ name: 'Skywalker' }));

  await user.click(ui.combobox.get());
  expect(screen.queryByText('Skywalker')).not.toBeInTheDocument();
});

it('should handle assignee search correctly', async () => {
  const user = userEvent.setup();
  renderAssigneeSelect();

  // Minimum MIN_QUERY_LENGTH charachters to trigger search
  await user.click(ui.combobox.get());
  await user.type(ui.combobox.get(), 'a');

  expect(await screen.findByText(`select.search.tooShort.${MIN_QUERY_LENGTH}`)).toBeInTheDocument();

  // Trigger search
  await user.click(ui.combobox.get());
  await user.type(ui.combobox.get(), 'someone');

  expect(await screen.findByText('toto')).toBeInTheDocument();
  expect(await screen.findByText('user.x_deleted.tata')).toBeInTheDocument();
  expect(await screen.findByText('user.x_deleted.titi@titi')).toBeInTheDocument();
});

it('should handle assignee selection', async () => {
  const onAssigneeSelect = jest.fn();
  const user = userEvent.setup();
  renderAssigneeSelect({ onAssigneeSelect });

  await user.click(ui.combobox.get());
  await user.type(ui.combobox.get(), 'tot');

  // Do not select assignee until suggestion is selected
  expect(onAssigneeSelect).not.toHaveBeenCalled();

  // Select assignee when suggestion is selected
  await user.click(screen.getByLabelText('toto'));
  expect(onAssigneeSelect).toHaveBeenCalledTimes(1);
});

describe('AI Code Assistant option', () => {
  const aiIssue = (project: string) =>
    mockIssue(false, { assignee: 'someone', project, aiCodeFixEnabled: true });

  it.each([['github'], ['gitlab']])(
    'is offered when every issue sits on %s',
    async (integrationType) => {
    jest.mocked(isAiAssistantEnabled).mockResolvedValue(true);
    mockIntegration(integrationType);
    const user = userEvent.setup();
    renderAssigneeSelect({ issues: [aiIssue('proj')] }, mockLoggedInUser());

    await user.click(ui.combobox.get());
    expect(await screen.findByText(AI_CODE_ASSISTANT)).toBeInTheDocument();
    },
  );

  it.each([['metadata_api'], ['bitbucket'], ['webhook']])(
    'is not offered when the issues sit on %s',
    async (integrationType) => {
    jest.mocked(isAiAssistantEnabled).mockResolvedValue(true);
    mockIntegration(integrationType);
    const user = userEvent.setup();
    renderAssigneeSelect({ issues: [aiIssue('proj')] }, mockLoggedInUser());

    await user.click(ui.combobox.get());
    await waitFor(() => {
      expect(getCodefixIntegration).toHaveBeenCalledWith('proj');
    });
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
    },
  );

  it('is not offered when a multi-project selection mixes supported and unsupported integrations', async () => {
    jest.mocked(isAiAssistantEnabled).mockResolvedValue(true);
    jest.mocked(getCodefixIntegration).mockImplementation((projectKey) =>
      Promise.resolve(
        projectKey === 'gh'
          ? { integrationType: 'github', supported: true }
          : { integrationType: 'metadata_api', supported: false },
      ),
    );
    const user = userEvent.setup();
    renderAssigneeSelect({ issues: [aiIssue('gh'), aiIssue('sf')] }, mockLoggedInUser());

    await user.click(ui.combobox.get());
    await waitFor(() => {
      expect(getCodefixIntegration).toHaveBeenCalledWith('sf');
    });
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
  });

  it('does not look up any project when the AI assistant is switched off', async () => {
    jest.mocked(isAiAssistantEnabled).mockResolvedValue(false);
    mockIntegration('github');
    const user = userEvent.setup();
    renderAssigneeSelect({ issues: [aiIssue('proj')] }, mockLoggedInUser());

    await user.click(ui.combobox.get());
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
    expect(getCodefixIntegration).not.toHaveBeenCalled();
  });

  it('keeps ai-code-assistant out of search results when the integration is unsupported', async () => {
    jest.mocked(isAiAssistantEnabled).mockResolvedValue(true);
    mockIntegration('metadata_api');
    const user = userEvent.setup();
    renderAssigneeSelect({ issues: [aiIssue('proj')] }, mockLoggedInUser());

    await user.click(ui.combobox.get());
    await user.type(ui.combobox.get(), 'assistant');

    expect(await screen.findByText('toto')).toBeInTheDocument();
    expect(screen.queryByText(AI_CODE_ASSISTANT)).not.toBeInTheDocument();
  });
});

function renderAssigneeSelect(
  overrides: Partial<AssigneeSelectProps> = {},
  currentUser: CurrentUser = mockCurrentUser(),
) {
  return renderComponent(
    <CurrentUserContextProvider currentUser={currentUser}>
      <AssigneeSelect
        inputId="id"
        issues={[]}
        label=""
        onAssigneeSelect={jest.fn()}
        {...overrides}
      />
    </CurrentUserContextProvider>,
  );
}
