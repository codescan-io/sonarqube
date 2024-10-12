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
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as React from 'react';
import selectEvent from 'react-select-event';
import { getGitlabProjects } from '../../../../api/alm-integrations';
import AlmIntegrationsServiceMock from '../../../../api/mocks/AlmIntegrationsServiceMock';
import AlmSettingsServiceMock from '../../../../api/mocks/AlmSettingsServiceMock';
import NewCodePeriodsServiceMock from '../../../../api/mocks/NewCodePeriodsServiceMock';
import { renderApp } from '../../../../helpers/testReactTestingUtils';
import { byLabelText, byRole, byText } from '../../../../helpers/testSelector';
import CreateProjectPage, { CreateProjectPageProps } from '../CreateProjectPage';

jest.mock('../../../../api/alm-integrations');
jest.mock('../../../../api/alm-settings');

let almIntegrationHandler: AlmIntegrationsServiceMock;
let almSettingsHandler: AlmSettingsServiceMock;
let newCodePeriodHandler: NewCodePeriodsServiceMock;

const ui = {
  gitlabCreateProjectButton: byText('onboarding.create_project.select_method.gitlab'),

  personalAccessTokenInput: byRole('textbox', {
    name: /onboarding.create_project.enter_pat/,
  }),
  instanceSelector: byLabelText(/alm.configuration.selector.label/),
};

beforeAll(() => {
  almIntegrationHandler = new AlmIntegrationsServiceMock();
  almSettingsHandler = new AlmSettingsServiceMock();
  newCodePeriodHandler = new NewCodePeriodsServiceMock();
});

beforeEach(() => {
  jest.clearAllMocks();
  almIntegrationHandler.reset();
  almSettingsHandler.reset();
  newCodePeriodHandler.reset();
});

it('should ask for PAT when it is not set yet and show the import project feature afterwards', async () => {
  const user = userEvent.setup();
  renderCreateProject();
  expect(ui.gitlabCreateProjectButton.get()).toBeInTheDocument();

  await user.click(ui.gitlabCreateProjectButton.get());
  expect(screen.getByText('onboarding.create_project.gitlab.title')).toBeInTheDocument();
  expect(ui.instanceSelector.get()).toBeInTheDocument();

  expect(screen.getByText('onboarding.create_project.enter_pat')).toBeInTheDocument();
  expect(screen.getByText('onboarding.create_project.pat_help.title')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'save' })).toBeInTheDocument();
  await act(async () => {
    await user.click(ui.personalAccessTokenInput.get());
    await user.keyboard('secret');
    await user.click(screen.getByRole('button', { name: 'save' }));
  });

  expect(screen.getByText('Gitlab project 1')).toBeInTheDocument();
  expect(screen.getByText('Gitlab project 2')).toBeInTheDocument();
  expect(screen.getAllByText('onboarding.create_project.set_up')).toHaveLength(2);
  expect(screen.getByText('onboarding.create_project.repository_imported')).toBeInTheDocument();
});

it('should show import project feature when PAT is already set', async () => {
  const user = userEvent.setup();
  let projectItem;
  renderCreateProject();
  await act(async () => {
    await user.click(ui.gitlabCreateProjectButton.get());
    await selectEvent.select(ui.instanceSelector.get(), [/conf-final-2/]);
  });

  expect(screen.getByText('Gitlab project 1')).toBeInTheDocument();
  expect(screen.getByText('Gitlab project 2')).toBeInTheDocument();

  projectItem = screen.getByRole('row', { name: /Gitlab project 1/ });
  expect(
    within(projectItem).getByText('onboarding.create_project.repository_imported')
  ).toBeInTheDocument();
  expect(within(projectItem).getByRole('link', { name: /Gitlab project 1/ })).toBeInTheDocument();
  expect(within(projectItem).getByRole('link', { name: /Gitlab project 1/ })).toHaveAttribute(
    'href',
    '/dashboard?id=key'
  );

  projectItem = screen.getByRole('row', { name: /Gitlab project 2/ });
  const setupButton = within(projectItem).getByRole('button', {
    name: 'onboarding.create_project.set_up',
  });

  await user.click(setupButton);

  expect(
    screen.getByRole('heading', { name: 'onboarding.create_project.new_code_definition.title' })
  ).toBeInTheDocument();

  await user.click(screen.getByRole('radio', { name: 'new_code_definition.global_setting' }));
  await user.click(
    screen.getByRole('button', {
      name: 'onboarding.create_project.new_code_definition.create_project',
    })
  );

  expect(await screen.findByText('/dashboard?id=key')).toBeInTheDocument();
});

it('should show search filter when PAT is already set', async () => {
  const user = userEvent.setup();
  renderCreateProject();

  await act(async () => {
    await user.click(ui.gitlabCreateProjectButton.get());
    await selectEvent.select(ui.instanceSelector.get(), [/conf-final-2/]);
  });

  const inputSearch = screen.getByRole('searchbox', {
    name: 'onboarding.create_project.search_prompt',
  });
  await user.click(inputSearch);
  await user.keyboard('sea');

  expect(getGitlabProjects).toHaveBeenLastCalledWith({
    almSetting: 'conf-final-2',
    page: 1,
    pageSize: 30,
    query: 'sea',
  });
});

it('should have load more', async () => {
  const user = userEvent.setup();
  almIntegrationHandler.createRandomGitlabProjectsWithLoadMore(10, 20);
  renderCreateProject();
  await act(async () => {
    await user.click(ui.gitlabCreateProjectButton.get());
    await selectEvent.select(ui.instanceSelector.get(), [/conf-final-2/]);
  });
  const loadMore = screen.getByRole('button', { name: 'show_more' });
  expect(loadMore).toBeInTheDocument();

  /*
   * Next api call response will simulate reaching the last page so we can test the
   * loadmore button disapperance.
   */
  almIntegrationHandler.createRandomGitlabProjectsWithLoadMore(20, 20);
  await user.click(loadMore);
  expect(getGitlabProjects).toHaveBeenLastCalledWith({
    almSetting: 'conf-final-2',
    page: 2,
    pageSize: 30,
    query: '',
  });
  expect(loadMore).not.toBeInTheDocument();
});

it('should show no result message when there are no projects', async () => {
  const user = userEvent.setup();
  almIntegrationHandler.setGitlabProjects([]);
  renderCreateProject();
  await act(async () => {
    await user.click(ui.gitlabCreateProjectButton.get());
    await selectEvent.select(ui.instanceSelector.get(), [/conf-final-2/]);
  });

  expect(screen.getByText('onboarding.create_project.gitlab.no_projects')).toBeInTheDocument();
});

function renderCreateProject(props: Partial<CreateProjectPageProps> = {}) {
  renderApp('project/create', <CreateProjectPage {...props} />);
}
