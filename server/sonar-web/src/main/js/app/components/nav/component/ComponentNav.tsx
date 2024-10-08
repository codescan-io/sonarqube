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
import { LAYOUT_GLOBAL_NAV_HEIGHT, LAYOUT_PROJECT_NAV_HEIGHT, TopBar } from 'design-system';
import * as React from 'react';
import { translate } from '../../../../helpers/l10n';
import {
  ProjectAlmBindingConfigurationErrors,
  ProjectAlmBindingResponse,
} from '../../../../types/alm-settings';
import { BranchLike } from '../../../../types/branch-like';
import { ComponentQualifier } from '../../../../types/component';
import { Task, TaskWarning } from '../../../../types/tasks';
import { Component, Organization } from '../../../../types/types';
import RecentHistory from '../../RecentHistory';
import ComponentNavProjectBindingErrorNotif from './ComponentNavProjectBindingErrorNotif';
import Header from './Header';
import HeaderMeta from './HeaderMeta';
import Menu from './Menu';
import InfoDrawer from './projectInformation/InfoDrawer';
import ProjectInformation from './projectInformation/ProjectInformation';

export interface ComponentNavProps {
  branchLikes: BranchLike[];
  currentBranchLike: BranchLike | undefined;
  component: Component;
  currentTask?: Task;
  currentTaskOnSameBranch?: boolean;
  isInProgress?: boolean;
  isPending?: boolean;
  onComponentChange: (changes: Partial<Component>) => void;
  onWarningDismiss: () => void;
  projectBinding?: ProjectAlmBindingResponse;
  projectBindingErrors?: ProjectAlmBindingConfigurationErrors;
  warnings: TaskWarning[];
  organization: Organization;
  comparisonBranchesEnabled: boolean;
}

export default function ComponentNav(props: ComponentNavProps) {
  const {
    branchLikes,
    component,
    currentBranchLike,
    currentTask,
    currentTaskOnSameBranch,
    isInProgress,
    isPending,
    projectBinding,
    projectBindingErrors,
    warnings,
    organization,
    comparisonBranchesEnabled,
  } = props;

  const [displayProjectInfo, setDisplayProjectInfo] = React.useState(false);

  React.useEffect(() => {
    const { breadcrumbs, key, name } = component;
    const { qualifier } = breadcrumbs[breadcrumbs.length - 1];
    if (
      [
        ComponentQualifier.Project,
        ComponentQualifier.Portfolio,
        ComponentQualifier.Application,
        ComponentQualifier.Developper,
      ].includes(qualifier as ComponentQualifier)
    ) {
      RecentHistory.add(key, name, qualifier.toLowerCase());
    }
  }, [component, component.key]);

  let prDecoNotifComponent;
  if (projectBindingErrors !== undefined) {
    prDecoNotifComponent = <ComponentNavProjectBindingErrorNotif component={component} />;
  }

  return (
    <>
      <TopBar id="context-navigation" aria-label={translate('qualifier', component.qualifier)}>
        <div className="sw-flex sw-justify-between">
          <Header
            branchLikes={branchLikes}
            component={component}
            currentBranchLike={currentBranchLike}
            projectBinding={projectBinding}
            organization={organization}
            comparisonBranchesEnabled={comparisonBranchesEnabled}
          />
          <HeaderMeta
            branchLike={currentBranchLike}
            component={component}
            currentTask={currentTask}
            currentTaskOnSameBranch={currentTaskOnSameBranch}
            isInProgress={isInProgress}
            isPending={isPending}
            onWarningDismiss={props.onWarningDismiss}
            warnings={warnings}
          />
        </div>
        <Menu
          branchLike={currentBranchLike}
          branchLikes={branchLikes}
          component={component}
          isInProgress={isInProgress}
          isPending={isPending}
          onToggleProjectInfo={() => {
            setDisplayProjectInfo(!displayProjectInfo);
          }}
          projectInfoDisplayed={displayProjectInfo}
          comparisonBranchesEnabled={comparisonBranchesEnabled}
        />
        <InfoDrawer
          displayed={displayProjectInfo}
          onClose={() => {
            setDisplayProjectInfo(false);
          }}
          top={LAYOUT_GLOBAL_NAV_HEIGHT + LAYOUT_PROJECT_NAV_HEIGHT}
        >
          <ProjectInformation
            branchLike={currentBranchLike}
            component={component}
            onComponentChange={props.onComponentChange}
          />
        </InfoDrawer>
      </TopBar>
      {prDecoNotifComponent}
    </>
  );
}
