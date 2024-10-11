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
import React from 'react';
import { Route } from 'react-router-dom';
import selectEvent from 'react-select-event';
import { byDisplayValue, byRole, byTestId, byText } from 'testing-library-selector';
import SecurityHotspotServiceMock from '../../../api/mocks/SecurityHotspotServiceMock';
import { getSecurityHotspots, setSecurityHotspotStatus } from '../../../api/security-hotspots';
import { searchUsers } from '../../../api/users';
import { mockBranch, mockMainBranch } from '../../../helpers/mocks/branch-like';
import { mockComponent } from '../../../helpers/mocks/component';
import { mockLoggedInUser } from '../../../helpers/testMocks';
import { renderAppWithComponentContext } from '../../../helpers/testReactTestingUtils';
import { ComponentContextShape } from '../../../types/component';
import SecurityHotspotsApp from '../SecurityHotspotsApp';

jest.mock('../../../api/measures');
jest.mock('../../../api/security-hotspots');
jest.mock('../../../api/rules');
jest.mock('../../../api/components');
jest.mock('../../../helpers/security-standard');
jest.mock('../../../api/users');

const ui = {
  inputAssignee: byRole('searchbox', { name: 'hotspots.assignee.select_user' }),
  selectStatusButton: byRole('button', {
    name: 'hotspots.status.select_status',
  }),
  editAssigneeButton: byRole('button', {
    name: 'hotspots.assignee.change_user',
  }),
  filterAssigneeToMe: byRole('button', {
    name: 'hotspot.filters.assignee.assigned_to_me',
  }),
  filterSeeAll: byRole('button', { name: 'hotspot.filters.assignee.all' }),
  filterByStatus: byRole('combobox', { name: 'hotspot.filters.status' }),
  filterByPeriod: byRole('combobox', { name: 'hotspot.filters.period' }),
  noHotspotForFilter: byText('hotspots.no_hotspots_for_filters.title'),
  selectStatus: byRole('button', { name: 'hotspots.status.select_status' }),
  toReviewStatus: byText('hotspots.status_option.TO_REVIEW'),
  changeStatus: byRole('button', { name: 'hotspots.status.change_status' }),
  hotspotTitle: (name: string | RegExp) => byRole('heading', { name }),
  hotspotStatus: byRole('heading', { name: 'status: hotspots.status_option.FIXED' }),
  hotpostListTitle: byRole('heading', { name: 'hotspots.list_title.TO_REVIEW.4' }),
  hotspotCommentBox: byRole('textbox', { name: 'hotspots.comment.field' }),
  commentSubmitButton: byRole('button', { name: 'hotspots.comment.submit' }),
  commentEditButton: byRole('button', { name: 'issue.comment.edit' }),
  commentDeleteButton: byRole('button', { name: 'issue.comment.delete' }),
  textboxWithText: (value: string) => byDisplayValue(value),
  activeAssignee: byTestId('assignee-name'),
  successGlobalMessage: byRole('status'),
  currentUserSelectionItem: byText('foo'),
  panel: byTestId('security-hotspot-test'),
};

let handler: SecurityHotspotServiceMock;

beforeEach(() => {
  handler = new SecurityHotspotServiceMock();
});

afterEach(() => {
  handler.reset();
});

describe('rendering', () => {
  it('should render code variants correctly', async () => {
    renderSecurityHotspotsApp(
      'security_hotspots?id=guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed&hotspots=test-2'
    );
    expect(await screen.findAllByText('variant 1, variant 2')).toHaveLength(2);
  });
});

it('should navigate when comming from SonarLint', async () => {
  // On main branch
  const rtl = renderSecurityHotspotsApp(
    'security_hotspots?id=guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed&hotspots=test-1'
  );

  expect(await ui.hotspotTitle(/'3' is a magic number./).find()).toBeInTheDocument();

  // On specific branch
  rtl.unmount();
  renderSecurityHotspotsApp(
    'security_hotspots?id=guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed&hotspots=b1-test-1&branch=b1',
    { branchLike: mockBranch({ name: 'b1' }) }
  );

  expect(await ui.hotspotTitle(/'F' is a magic number./).find()).toBeInTheDocument();
});

it('should be able to self-assign a hotspot', async () => {
  const user = userEvent.setup();
  renderSecurityHotspotsApp();

  expect(await ui.activeAssignee.find()).toHaveTextContent('John Doe');

  await user.click(ui.editAssigneeButton.get());
  await user.click(ui.currentUserSelectionItem.get());

  expect(ui.successGlobalMessage.get()).toHaveTextContent(`hotspots.assign.success.foo`);
  expect(ui.activeAssignee.get()).toHaveTextContent('foo');
});

it('should be able to search for a user on the assignee', async () => {
  const user = userEvent.setup();
  renderSecurityHotspotsApp();

  await user.click(await ui.editAssigneeButton.find());
  await user.click(ui.inputAssignee.get());

  await user.keyboard('User');

  expect(searchUsers).toHaveBeenLastCalledWith({ q: 'User' });
  await user.keyboard('{ArrowDown}{Enter}');
  expect(ui.successGlobalMessage.get()).toHaveTextContent(`hotspots.assign.success.User John`);
});

it('should be able to filter the hotspot list', async () => {
  const user = userEvent.setup();
  renderSecurityHotspotsApp();

  expect(await ui.hotpostListTitle.find()).toBeInTheDocument();

  await user.click(ui.filterAssigneeToMe.get());
  expect(ui.noHotspotForFilter.get()).toBeInTheDocument();
  await selectEvent.select(ui.filterByStatus.get(), ['hotspot.filters.status.to_review']);

  expect(getSecurityHotspots).toHaveBeenLastCalledWith({
    inNewCodePeriod: false,
    onlyMine: true,
    p: 1,
    projectKey: 'guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed',
    ps: 500,
    resolution: undefined,
    status: 'TO_REVIEW',
  });

  await selectEvent.select(ui.filterByPeriod.get(), ['hotspot.filters.period.since_leak_period']);

  expect(getSecurityHotspots).toHaveBeenLastCalledWith({
    inNewCodePeriod: true,
    onlyMine: true,
    p: 1,
    projectKey: 'guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed',
    ps: 500,
    resolution: undefined,
    status: 'TO_REVIEW',
  });

  await user.click(ui.filterSeeAll.get());

  expect(ui.hotpostListTitle.get()).toBeInTheDocument();
});

it('should be able to navigate the hotspot list with keyboard', async () => {
  const user = userEvent.setup();
  renderSecurityHotspotsApp();

  await user.keyboard('{ArrowDown}');
  expect(await ui.hotspotTitle(/'2' is a magic number./).find()).toBeInTheDocument();
  await user.keyboard('{ArrowUp}');
  expect(await ui.hotspotTitle(/'3' is a magic number./).find()).toBeInTheDocument();
});

it('should be able to change the status of a hotspot', async () => {
  const user = userEvent.setup();
  const comment = 'COMMENT-TEXT';

  renderSecurityHotspotsApp();

  expect(await ui.selectStatus.find()).toBeInTheDocument();

  await user.click(ui.selectStatus.get());
  await user.click(ui.toReviewStatus.get());

  await user.click(screen.getByRole('textbox', { name: 'hotspots.status.add_comment' }));
  await user.keyboard(comment);

  await act(async () => {
    await user.click(ui.changeStatus.get());
  });

  expect(setSecurityHotspotStatus).toHaveBeenLastCalledWith('test-1', {
    comment: 'COMMENT-TEXT',
    resolution: undefined,
    status: 'TO_REVIEW',
  });

  expect(ui.hotspotStatus.get()).toBeInTheDocument();
});

it('should not be able to change the status if does not have edit permissions', async () => {
  handler.setHotspotChangeStatusPermission(false);
  renderSecurityHotspotsApp();
  expect(await ui.selectStatus.find()).toBeDisabled();
});

it('should remember the comment when toggling change status panel for the same security hotspot', async () => {
  const user = userEvent.setup();
  renderSecurityHotspotsApp();

  await user.click(await ui.selectStatusButton.find());

  const comment = 'This is a comment';

  const commentSection = within(ui.panel.get()).getByRole('textbox');
  await user.click(commentSection);
  await user.keyboard(comment);

  // Close the panel
  await act(async () => {
    await user.keyboard('{Escape}');
  });

  // Check panel is closed
  expect(ui.panel.query()).not.toBeInTheDocument();

  await user.click(ui.selectStatusButton.get());

  expect(await screen.findByText(comment)).toBeInTheDocument();
});

it('should be able to add, edit and remove own comments', async () => {
  const uiComment = {
    saveButton: byRole('button', { name: 'save' }),
    deleteButton: byRole('button', { name: 'delete' }),
  };
  const user = userEvent.setup();
  const comment = 'This is a comment from john doe';
  renderSecurityHotspotsApp();

  const commentSection = await ui.hotspotCommentBox.find();
  const submitButton = ui.commentSubmitButton.get();

  // Add a new comment
  await user.click(commentSection);
  await user.keyboard(comment);
  await user.click(submitButton);

  expect(await screen.findByText(comment)).toBeInTheDocument();

  // Edit the comment
  await user.click(ui.commentEditButton.get());
  await user.click(ui.textboxWithText(comment).get());
  await user.keyboard(' test');
  await user.click(uiComment.saveButton.get());

  expect(await byText(`${comment} test`).find()).toBeInTheDocument();

  // Delete the comment
  await user.click(ui.commentDeleteButton.get());
  await user.click(uiComment.deleteButton.get());

  expect(screen.queryByText(`${comment} test`)).not.toBeInTheDocument();
});

function renderSecurityHotspotsApp(
  navigateTo?: string,
  component?: Partial<ComponentContextShape>
) {
  return renderAppWithComponentContext(
    'security_hotspots',
    () => <Route path="security_hotspots" element={<SecurityHotspotsApp />} />,
    {
      navigateTo,
      currentUser: mockLoggedInUser({
        login: 'foo',
        name: 'foo',
      }),
    },
    {
      branchLike: mockMainBranch(),
      onBranchesChange: jest.fn(),
      onComponentChange: jest.fn(),
      component: mockComponent({
        key: 'guillaume-peoch-sonarsource_benflix_AYGpXq2bd8qy4i0eO9ed',
        name: 'benflix',
      }),
      ...component,
    }
  );
}
