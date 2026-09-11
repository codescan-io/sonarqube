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

import userEvent from '@testing-library/user-event';
import { Outlet, Route } from 'react-router-dom';
import { addGlobalSuccessMessage } from '~design-system';
import { byLabelText, byRole, byText } from '~sonar-aligned/helpers/testSelector';
import {
  ProfileProject,
  associateProject,
  getProfileProjects,
  searchQualityProfiles,
} from '../../../api/quality-profiles';
import handleRequiredAuthorization from '../../../app/utils/handleRequiredAuthorization';
import { mockComponent } from '../../../helpers/mocks/component';
import { mockQualityProfile } from '../../../helpers/testMocks';
import {
  RenderContext,
  renderAppWithComponentContext,
} from '../../../helpers/testReactTestingUtils';
import { Component, Organization } from '../../../types/types';
import routes from '../routes';

jest.mock('../../../api/quality-profiles', () => {
  const { mockQualityProfile } = jest.requireActual('../../../helpers/testMocks');

  return {
    associateProject: jest.fn().mockResolvedValue({}),
    dissociateProject: jest.fn().mockResolvedValue({}),
    searchQualityProfiles: jest.fn().mockResolvedValue({
      profiles: [
        mockQualityProfile({
          key: 'css',
          language: 'css',
          name: 'css profile',
          languageName: 'CSS',
        }),
        mockQualityProfile({
          key: 'java',
          language: 'java',
          name: 'java profile',
          languageName: 'Java',
        }),
        mockQualityProfile({
          key: 'js',
          language: 'js',
          name: 'js profile',
          languageName: 'JavaScript',
        }),
        mockQualityProfile({
          key: 'ts',
          language: 'ts',
          isDefault: true,
          name: 'ts profile',
          languageName: 'Typescript',
        }),
        mockQualityProfile({
          key: 'html',
          language: 'html',
          name: 'html profile',
          languageName: 'HTML',
        }),
        mockQualityProfile({
          key: 'html_default',
          language: 'html',
          isDefault: true,
          isBuiltIn: true,
          name: 'html default profile',
          languageName: 'HTML',
        }),
      ],
    }),
    getProfileProjects: jest.fn(({ key }) => {
      const results: ProfileProject[] = [];
      if (key === 'css' || key === 'java' || key === 'js' || key === 'ts' || key === 'java') {
        results.push({
          key: 'my-project',
          name: 'My project',
          selected: true,
        });
      }
      return Promise.resolve({ results });
    }),
  };
});

jest.mock('~design-system', () => ({
  ...jest.requireActual('~design-system'),
  addGlobalSuccessMessage: jest.fn(),
}));

jest.mock('../../../app/utils/handleRequiredAuthorization', () => jest.fn());

beforeEach(jest.clearAllMocks);

const ui = {
  pageTitle: byText('project_quality_profiles.page'),
  pageSubTitle: byText('project_quality_profile.subtitle'),
  pageDescription: byText('project_quality_profiles.page.description'),
  helpTooltip: byLabelText('help-tooltip'),
  profileRows: byRole('row'),
  addLanguageButton: byRole('button', { name: 'project_quality_profile.add_language.action' }),
  modalAddLanguageTitle: byText('project_quality_profile.add_language_modal.title'),
  selectLanguage: byRole('combobox', {
    name: 'project_quality_profile.add_language_modal.choose_language',
  }),
  selectProfile: byRole('combobox', {
    name: 'project_quality_profile.add_language_modal.choose_profile',
  }),
  selectUseSpecificProfile: byRole('combobox', {
    name: 'project_quality_profile.always_use_specific',
  }),
  buttonSave: byRole('button', { name: 'save' }),
  htmlLanguage: byText('HTML'),
  htmlProfile: byText('html profile'),
  cssLanguage: byText('CSS'),
  cssProfile: byText('css profile'),
  htmlDefaultProfile: byText('html default profile'),
  htmlActiveRuleslink: byRole('link', { name: '10' }),
  radioButtonUseInstanceDefault: byRole('radio', {
    name: /project_quality_profile.always_use_default/,
  }),
  radioButtonUseSpecific: byRole('radio', { name: /project_quality_profile.always_use_specific/ }),
  newAnalysisWarningMessage: byText('project_quality_profile.requires_new_analysis'),
  builtInTag: byText('quality_profiles.built_in'),
  instanceDefaultTagText: 'project_quality_profile.instance_default',
  rowByProfileName: (name: RegExp) => byRole('row', { name }),
};

it('should be able to add and change profile for languages', async () => {
  const user = userEvent.setup();
  renderProjectQualityProfilesApp({
    languages: {
      css: { key: 'css', name: 'CSS' },
      ts: { key: 'ts', name: 'TS' },
      js: { key: 'js', name: 'JS' },
      java: { key: 'java', name: 'JAVA' },
      html: { key: 'html', name: 'HTML' },
    },
  });

  expect(await ui.pageTitle.find()).toBeInTheDocument();
  expect(ui.pageDescription.get()).toBeInTheDocument();
  expect(await ui.addLanguageButton.find()).toBeInTheDocument();
  await expect(ui.helpTooltip.get()).toHaveATooltipWithContent(
    'quality_profiles.list.projects.help',
  );
  expect(ui.profileRows.getAll()).toHaveLength(5);
  expect(ui.cssLanguage.get()).toBeInTheDocument();
  expect(ui.cssProfile.get()).toBeInTheDocument();

  await user.click(ui.addLanguageButton.get());

  // Opens the add language modal
  expect(ui.modalAddLanguageTitle.get()).toBeInTheDocument();
  expect(ui.selectLanguage.get()).toBeEnabled();
  expect(ui.selectProfile.get()).toBeDisabled();
  expect(ui.buttonSave.get()).toBeInTheDocument();

  await user.click(ui.selectLanguage.get());
  await user.click(byRole('option', { name: 'HTML' }).get());

  expect(ui.selectProfile.get()).toBeEnabled();

  await user.click(ui.selectProfile.get());
  await user.click(byRole('option', { name: 'html profile' }).get());

  await user.click(ui.buttonSave.get());
  expect(associateProject).toHaveBeenLastCalledWith(
    expect.objectContaining({ key: 'html', name: 'html profile' }),
    'my-project',
  );
  expect(addGlobalSuccessMessage).toHaveBeenCalledWith(
    'project_quality_profile.successfully_updated.HTML',
  );

  // Updates the page after API call
  const htmlRow = byRole('row', {
    name: 'HTML html profile 10 Change profile',
  });

  expect(ui.htmlLanguage.get()).toBeInTheDocument();
  expect(ui.htmlProfile.get()).toBeInTheDocument();
  expect(ui.profileRows.getAll()).toHaveLength(6);
  expect(htmlRow.get()).toBeInTheDocument();
  expect(htmlRow.byRole('link', { name: '10' }).get()).toHaveAttribute(
    'href',
    '/organizations/my-org/rules?activation=true&qprofile=html',
  );
  expect(ui.builtInTag.query()).not.toBeInTheDocument();

  await user.click(
    htmlRow.byRole('button', { name: 'project_quality_profile.change_profile_x.HTML' }).get(),
  );

  //Opens modal to change profile
  expect(ui.radioButtonUseInstanceDefault.get()).not.toBeChecked();
  expect(ui.radioButtonUseSpecific.get()).toBeChecked();
  expect(ui.newAnalysisWarningMessage.get()).toBeInTheDocument();
  expect(ui.selectUseSpecificProfile.get()).toBeInTheDocument();

  await user.click(ui.selectUseSpecificProfile.get());
  await user.click(byRole('option', { name: 'html default profile' }).get());

  await user.click(ui.buttonSave.get());

  expect(addGlobalSuccessMessage).toHaveBeenCalledWith(
    'project_quality_profile.successfully_updated.HTML',
  );

  // Updates the page after API call
  expect(ui.htmlProfile.query()).not.toBeInTheDocument();
  expect(ui.htmlDefaultProfile.get()).toBeInTheDocument();
  expect(ui.builtInTag.get()).toBeInTheDocument();
});

it('should name the instance default profile and badge it for inherited languages', async () => {
  jest.mocked(searchQualityProfiles).mockResolvedValueOnce({
    profiles: [
      mockQualityProfile({
        key: 'html_default',
        language: 'html',
        isDefault: true,
        isBuiltIn: true,
        name: 'html default profile',
        languageName: 'HTML',
      }),
      mockQualityProfile({
        key: 'py_default',
        language: 'py',
        isDefault: true,
        name: 'py default profile',
        languageName: 'Python',
      }),
    ],
  });

  // Neither language is explicitly associated with the project, so both rows
  // inherit the instance default profile.
  renderProjectQualityProfilesApp(
    { languages: { html: { key: 'html', name: 'HTML' }, py: { key: 'py', name: 'Python' } } },
    {
      configuration: { showQualityProfiles: true },
      qualityProfiles: [
        { deleted: false, key: 'html_default', language: 'html', name: 'html default profile' },
        { deleted: false, key: 'py_default', language: 'py', name: 'py default profile' },
      ],
    },
  );

  const htmlRow = ui.rowByProfileName(/html default profile/);
  const pyRow = ui.rowByProfileName(/py default profile/);

  // Every inherited row names the profile that is actually applied.
  expect(await htmlRow.find()).toBeInTheDocument();
  expect(pyRow.get()).toBeInTheDocument();
  expect(ui.htmlDefaultProfile.get()).toBeInTheDocument();
  expect(
    ui
      .rowByProfileName(
        /^HTML html default profile quality_profiles\.built_in project_quality_profile\.instance_default 10 Change profile$/,
      )
      .get(),
  ).toBeInTheDocument();
  expect(
    ui
      .rowByProfileName(
        /^Python py default profile project_quality_profile\.instance_default 10 Change profile$/,
      )
      .get(),
  ).toBeInTheDocument();

  // The instance-default distinction is kept as a badge on each inherited row.
  expect(htmlRow.byText(ui.instanceDefaultTagText).get()).toBeInTheDocument();
  expect(pyRow.byText(ui.instanceDefaultTagText).get()).toBeInTheDocument();

  // A profile that is both built-in and the instance default carries both badges.
  expect(htmlRow.byText('quality_profiles.built_in').get()).toBeInTheDocument();
  expect(pyRow.byText('quality_profiles.built_in').query()).not.toBeInTheDocument();
});

it('should call authorization api when permissions is not proper', () => {
  renderProjectQualityProfilesApp({}, { configuration: { showQualityProfiles: false } });
  expect(handleRequiredAuthorization).toHaveBeenCalled();
});

it('should still show page with add language button when api fails', async () => {
  jest.mocked(searchQualityProfiles).mockRejectedValueOnce(null);
  jest.mocked(getProfileProjects).mockRejectedValueOnce(null);

  renderProjectQualityProfilesApp();
  expect(ui.pageTitle.get()).toBeInTheDocument();
  expect(ui.pageDescription.get()).toBeInTheDocument();
  expect(await ui.addLanguageButton.find()).toBeInTheDocument();
});

const organization: Organization = {
  kee: 'my-org',
  name: 'My organization',
  inviteUsersEnabled: false,
};

function renderProjectQualityProfilesApp(
  context?: RenderContext,
  componentOverrides: Partial<Component> = { configuration: { showQualityProfiles: true } },
) {
  // The app is wrapped in withOrganizationContext, which reads the organization
  // from the router outlet context.
  const routesWithOrganization = () => (
    <Route element={<Outlet context={{ organization }} />}>{routes()}</Route>
  );

  return renderAppWithComponentContext(
    'project/quality_profiles',
    routesWithOrganization,
    context,
    { component: mockComponent({ organization: organization.kee, ...componentOverrides }) },
  );
}
