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
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UserEvent } from '@testing-library/user-event/dist/types/setup/setup';
import { uniq } from 'lodash';
import { byLabelText, byRole } from 'testing-library-selector';
import PermissionsServiceMock from '../../../../api/mocks/PermissionsServiceMock';
import { mockPermissionGroup, mockPermissionUser } from '../../../../helpers/mocks/permissions';
import { PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE } from '../../../../helpers/permissions';
import { mockAppState } from '../../../../helpers/testMocks';
import {
  findTooltipWithContent,
  renderAppWithAdminContext,
} from '../../../../helpers/testReactTestingUtils';
import { ComponentQualifier } from '../../../../types/component';
import { Permissions } from '../../../../types/permissions';
import { PermissionGroup, PermissionUser } from '../../../../types/types';
import routes from '../../routes';

const serviceMock = new PermissionsServiceMock();

beforeEach(() => {
  serviceMock.reset();
});

describe('rendering', () => {
  it('should render the list of templates', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    // Lists all templates.
    expect(ui.templateLink('Permission Template 1').get()).toBeInTheDocument();
    expect(ui.templateLink('Permission Template 2').get()).toBeInTheDocument();

    // Shows all permission table headers.
    PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE.forEach((permission, i) => {
      expect(
        ui.getTableHeaderHelpTooltip(i + 1, `projects_role.${permission}.desc`)
      ).toBeInTheDocument();
    });

    // Shows warning for browse and code viewer permissions.
    [Permissions.Browse, Permissions.CodeViewer].forEach((_permission, i) => {
      expect(
        ui.getTableHeaderHelpTooltip(i + 1, 'projects_role.public_projects_warning')
      ).toBeInTheDocument();
    });

    // Check summaries.
    // Note: because of the intricacies of these table cells, and the verbosity
    // this would introduce in this test, I went ahead and relied on snapshots.
    // The snapshots only focus on the text content, so any updates in styling
    // or DOM structure should not alter the snapshots.
    const row1 = within(screen.getByRole('row', { name: /Permission Template 1/ }));
    PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE.forEach((permission, i) => {
      expect(row1.getAllByRole('cell').at(i + 1)?.textContent).toMatchSnapshot(
        `Permission Template 1: ${permission}`
      );
    });
    const row2 = within(screen.getByRole('row', { name: /Permission Template 2/ }));
    PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE.forEach((permission, i) => {
      expect(row2.getAllByRole('cell').at(i + 1)?.textContent).toMatchSnapshot(
        `Permission Template 2: ${permission}`
      );
    });
  });

  it('should render the correct template', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(screen.getByText('This is permission template 1')).toBeInTheDocument();
    PERMISSIONS_ORDER_FOR_PROJECT_TEMPLATE.forEach((permission, i) => {
      expect(ui.permissionCheckbox('johndoe', permission).get()).toBeInTheDocument();
      expect(
        ui.getTableHeaderHelpTooltip(i, `projects_role.${permission}.desc`)
      ).toBeInTheDocument();
    });
  });
});

describe('CRUD', () => {
  it('should allow the creation of new templates', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await act(async () => {
      await ui.createNewTemplate('New Permission Template', 'New template description');
    });
    await ui.appLoaded();

    expect(screen.getByRole('heading', { name: 'New Permission Template' })).toBeInTheDocument();
    expect(screen.getByText('New template description')).toBeInTheDocument();
  });

  it('should allow the create modal to be opened and closed', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openCreateModal();
    await ui.closeModal();

    expect(ui.modal.query()).not.toBeInTheDocument();
  });

  it('should allow template details to be updated from the list', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.updateTemplate(
      'Permission Template 2',
      'Updated name',
      'Updated description',
      '/new pattern/'
    );

    expect(ui.templateLink('Updated name').get()).toBeInTheDocument();
    expect(screen.getByText('Updated description')).toBeInTheDocument();
    expect(screen.getByText('/new pattern/')).toBeInTheDocument();
  });

  it('should allow template details to be updated from the template page directly', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 2');
    await ui.appLoaded();

    await ui.updateTemplate(
      'Permission Template 2',
      'Updated name',
      'Updated description',
      '/new pattern/'
    );

    expect(screen.getByText('Updated name')).toBeInTheDocument();
    expect(screen.getByText('Updated description')).toBeInTheDocument();
    expect(screen.getByText('/new pattern/')).toBeInTheDocument();
  });

  it('should allow the update modal to be opened and closed', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openUpdateModal('Permission Template 2');
    await ui.closeModal();

    expect(ui.modal.query()).not.toBeInTheDocument();
  });

  it('should allow templates to be deleted from the list', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await act(async () => {
      await ui.deleteTemplate('Permission Template 2');
    });
    await ui.appLoaded();

    expect(ui.templateLink('Permission Template 1').get()).toBeInTheDocument();
    expect(ui.templateLink('Permission Template 2').query()).not.toBeInTheDocument();
  });

  it('should allow templates to be deleted from the template page directly', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 2');
    await ui.appLoaded();

    await act(async () => {
      await ui.deleteTemplate('Permission Template 2');
    });
    await ui.appLoaded();

    expect(ui.templateLink('Permission Template 1').get()).toBeInTheDocument();
    expect(ui.templateLink('Permission Template 2').query()).not.toBeInTheDocument();
  });

  it('should allow the delete modal to be opened and closed', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openDeleteModal('Permission Template 2');
    await ui.closeModal();

    expect(ui.modal.query()).not.toBeInTheDocument();
  });

  it('should not allow a default template to be deleted', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await user.click(ui.cogMenuBtn('Permission Template 1').get());

    expect(ui.deleteBtn.query()).not.toBeInTheDocument();
  });
});

describe('filtering', () => {
  it('should allow to filter permission holders', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(screen.getByText('sonar-users')).toBeInTheDocument();
    expect(screen.getByText('johndoe')).toBeInTheDocument();

    await ui.showOnlyUsers();
    expect(screen.queryByText('sonar-users')).not.toBeInTheDocument();
    expect(screen.getByText('johndoe')).toBeInTheDocument();

    await ui.showOnlyGroups();
    expect(screen.getByText('sonar-users')).toBeInTheDocument();
    expect(screen.queryByText('johndoe')).not.toBeInTheDocument();

    await ui.showAll();
    expect(screen.getByText('sonar-users')).toBeInTheDocument();
    expect(screen.getByText('johndoe')).toBeInTheDocument();

    await ui.searchFor('sonar-adm');
    expect(screen.getByText('sonar-admins')).toBeInTheDocument();
    expect(screen.queryByText('sonar-users')).not.toBeInTheDocument();
    expect(screen.queryByText('johndoe')).not.toBeInTheDocument();

    await ui.clearSearch();
    expect(screen.getByText('sonar-users')).toBeInTheDocument();
    expect(screen.getByText('johndoe')).toBeInTheDocument();
  });

  it('should allow to show only permission holders with a specific permission', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(screen.getAllByRole('row').length).toBe(12);
    await ui.toggleFilterByPermission(Permissions.Admin);
    expect(screen.getAllByRole('row').length).toBe(2);
    await ui.toggleFilterByPermission(Permissions.Admin);
    expect(screen.getAllByRole('row').length).toBe(12);
  });
});

describe('assigning/revoking permissions', () => {
  it('should add and remove permissions to/from a group', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(ui.permissionCheckbox('sonar-users', Permissions.Admin).get()).not.toBeChecked();

    await ui.togglePermission('sonar-users', Permissions.Admin);
    await ui.appLoaded();
    expect(ui.permissionCheckbox('sonar-users', Permissions.Admin).get()).toBeChecked();

    await ui.togglePermission('sonar-users', Permissions.Admin);
    await ui.appLoaded();
    expect(ui.permissionCheckbox('sonar-users', Permissions.Admin).get()).not.toBeChecked();
  });

  it('should add and remove permissions to/from a user', async () => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(ui.permissionCheckbox('johndoe', Permissions.Scan).get()).not.toBeChecked();

    await ui.togglePermission('johndoe', Permissions.Scan);
    await ui.appLoaded();
    expect(ui.permissionCheckbox('johndoe', Permissions.Scan).get()).toBeChecked();

    await ui.togglePermission('johndoe', Permissions.Scan);
    await ui.appLoaded();
    expect(ui.permissionCheckbox('johndoe', Permissions.Scan).get()).not.toBeChecked();
  });

  it('should handle errors correctly', async () => {
    serviceMock.setIsAllowedToChangePermissions(false);
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp();
    await ui.appLoaded();

    await ui.openTemplateDetails('Permission Template 1');
    await ui.appLoaded();

    expect(ui.permissionCheckbox('johndoe', Permissions.Scan).get()).not.toBeChecked();
    await ui.togglePermission('johndoe', Permissions.Scan);
    await ui.appLoaded();
    expect(ui.permissionCheckbox('johndoe', Permissions.Scan).get()).not.toBeChecked();
  });
});

it('should correctly handle pagination', async () => {
  const groups: PermissionGroup[] = [];
  const users: PermissionUser[] = [];
  Array.from(Array(20).keys()).forEach((i) => {
    groups.push(mockPermissionGroup({ name: `Group ${i}` }));
    users.push(mockPermissionUser({ login: `user-${i}` }));
  });
  serviceMock.setGroups(groups);
  serviceMock.setUsers(users);

  const user = userEvent.setup();
  const ui = getPageObject(user);
  renderPermissionTemplatesApp();
  await ui.appLoaded();

  await ui.openTemplateDetails('Permission Template 1');
  await ui.appLoaded();

  expect(screen.getAllByRole('row').length).toBe(14);
  await ui.clickLoadMore();
  expect(screen.getAllByRole('row').length).toBe(24);
});

it.each([ComponentQualifier.Project, ComponentQualifier.Application, ComponentQualifier.Portfolio])(
  'should correctly be assignable by default to %s',
  async (qualifier) => {
    const user = userEvent.setup();
    const ui = getPageObject(user);
    renderPermissionTemplatesApp(uniq([ComponentQualifier.Project, qualifier]));
    await ui.appLoaded();

    await ui.setTemplateAsDefaultFor('Permission Template 2', qualifier);

    const row1 = within(screen.getByRole('row', { name: /Permission Template 1/ }));
    const row2 = within(screen.getByRole('row', { name: /Permission Template 2/ }));
    const regex = new RegExp(`permission_template\\.default_for\\.(.*)qualifiers.${qualifier}`);
    expect(row2.getByText(regex)).toBeInTheDocument();
    expect(row1.queryByText(regex)).not.toBeInTheDocument();
  }
);

function getPageObject(user: UserEvent) {
  const ui = {
    loading: byLabelText('loading'),
    templateLink: (name: string) => byRole('link', { name }),
    permissionCheckbox: (target: string, permission: Permissions) =>
      byRole('checkbox', {
        name: `permission.assign_x_to_y.projects_role.${permission}.${target}`,
      }),
    tableHeaderFilter: (permission: Permissions) =>
      byRole('link', { name: `projects_role.${permission}` }),
    onlyUsersBtn: byRole('button', { name: 'users.page' }),
    onlyGroupsBtn: byRole('button', { name: 'user_groups.page' }),
    showAllBtn: byRole('button', { name: 'all' }),
    searchInput: byRole('searchbox', { name: 'search.search_for_users_or_groups' }),
    loadMoreBtn: byRole('button', { name: 'show_more' }),
    createNewTemplateBtn: byRole('button', { name: 'create' }),
    modal: byRole('dialog'),
    cogMenuBtn: (name: string) =>
      byRole('button', { name: `permission_templates.show_actions_for_x.${name}` }),
    deleteBtn: byRole('button', { name: 'delete' }),
    updateDetailsBtn: byRole('button', { name: 'update_details' }),
    setDefaultBtn: (qualifier: ComponentQualifier) =>
      byRole('button', {
        name:
          qualifier === ComponentQualifier.Project
            ? 'permission_templates.set_default'
            : `permission_templates.set_default_for qualifier.${qualifier} qualifiers.${qualifier}`,
      }),
  };

  return {
    ...ui,
    async appLoaded() {
      await waitFor(() => {
        expect(ui.loading.query()).not.toBeInTheDocument();
      });
    },
    async openTemplateDetails(name: string) {
      await user.click(ui.templateLink(name).get());
    },
    async toggleFilterByPermission(permission: Permissions) {
      await user.click(ui.tableHeaderFilter(permission).get());
    },
    async showOnlyUsers() {
      await user.click(ui.onlyUsersBtn.get());
    },
    async showOnlyGroups() {
      await user.click(ui.onlyGroupsBtn.get());
    },
    async showAll() {
      await user.click(ui.showAllBtn.get());
    },
    async searchFor(name: string) {
      await user.type(ui.searchInput.get(), name);
    },
    async clearSearch() {
      await user.clear(ui.searchInput.get());
    },
    async clickLoadMore() {
      await user.click(ui.loadMoreBtn.get());
    },
    async togglePermission(target: string, permission: Permissions) {
      await user.click(ui.permissionCheckbox(target, permission).get());
    },
    async openCreateModal() {
      await user.click(ui.createNewTemplateBtn.get());
    },
    async createNewTemplate(name: string, description: string, pattern?: string) {
      await user.click(ui.createNewTemplateBtn.get());
      const modal = within(ui.modal.get());
      await user.type(modal.getByRole('textbox', { name: /name/ }), name);
      await user.type(modal.getByRole('textbox', { name: 'description' }), description);
      if (pattern) {
        await user.type(
          modal.getByRole('textbox', { name: 'permission_template.key_pattern' }),
          pattern
        );
      }
      await user.click(modal.getByRole('button', { name: 'create' }));
    },
    async openDeleteModal(name: string) {
      await user.click(ui.cogMenuBtn(name).get());
      await user.click(ui.deleteBtn.get());
    },
    async deleteTemplate(name: string) {
      await user.click(ui.cogMenuBtn(name).get());
      await user.click(ui.deleteBtn.get());
      const modal = within(ui.modal.get());
      await user.click(modal.getByRole('button', { name: 'delete' }));
    },
    async openUpdateModal(name: string) {
      await user.click(ui.cogMenuBtn(name).get());
      await user.click(ui.updateDetailsBtn.get());
    },
    async updateTemplate(
      name: string,
      newName: string,
      newDescription: string,
      newPattern: string
    ) {
      await user.click(ui.cogMenuBtn(name).get());
      await user.click(ui.updateDetailsBtn.get());

      const modal = within(ui.modal.get());
      const nameInput = modal.getByRole('textbox', { name: /name/ });
      const descriptionInput = modal.getByRole('textbox', { name: 'description' });
      const patternInput = modal.getByRole('textbox', { name: 'permission_template.key_pattern' });

      await user.clear(nameInput);
      await user.type(nameInput, newName);
      await user.clear(descriptionInput);
      await user.type(descriptionInput, newDescription);
      await user.clear(patternInput);
      await user.type(patternInput, newPattern);

      await user.click(modal.getByRole('button', { name: 'update_verb' }));
    },
    async closeModal() {
      const modal = within(ui.modal.get());
      await user.click(modal.getByRole('button', { name: 'cancel' }));
    },
    async setTemplateAsDefaultFor(name: string, qualifier: ComponentQualifier) {
      await user.click(ui.cogMenuBtn(name).get());
      await user.click(ui.setDefaultBtn(qualifier).get());
    },
    getTableHeaderHelpTooltip(i: number, text: string) {
      const th = byRole('columnheader').getAll().at(i);
      if (th === undefined) {
        throw new Error(`Couldn't locate the <th> at index ${i}`);
      }
      return findTooltipWithContent((_content, element) => {
        // For some reason, using the `content` parameter doesn't work for 1 of the
        // tests. Explicitly using the element's `textContent` always works.
        return Boolean(element?.textContent?.includes(text));
      }, th);
    },
  };
}

function renderPermissionTemplatesApp(qualifiers = [ComponentQualifier.Project]) {
  renderAppWithAdminContext('admin/permission_templates', routes, {
    appState: mockAppState({ qualifiers }),
  });
}
