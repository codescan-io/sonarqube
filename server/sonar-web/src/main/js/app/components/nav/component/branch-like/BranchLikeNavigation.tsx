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
import { ButtonSecondary, Popup, PopupPlacement, PopupZLevel } from 'design-system';
import * as React from 'react';
import EscKeydownHandler from '../../../../../components/controls/EscKeydownHandler';
import OutsideClickHandler from '../../../../../components/controls/OutsideClickHandler';
import { AlmKeys, ProjectAlmBindingResponse } from '../../../../../types/alm-settings';
import { BranchLike } from '../../../../../types/branch-like';
import { ComponentQualifier } from '../../../../../types/component';
import { Feature } from '../../../../../types/features';
import { Component } from '../../../../../types/types';
import withAvailableFeatures, {
  WithAvailableFeaturesProps,
} from '../../../available-features/withAvailableFeatures';
import BranchHelpTooltip from './BranchHelpTooltip';
import CurrentBranchLike from './CurrentBranchLike';
import Menu from './Menu';
import PRLink from './PRLink';

export interface BranchLikeNavigationProps extends WithAvailableFeaturesProps {
  branchLikes: BranchLike[];
  component: Component;
  currentBranchLike: BranchLike;
  projectBinding?: ProjectAlmBindingResponse;
  comparisonBranchesEnabled: boolean;
}

export function BranchLikeNavigation(props: BranchLikeNavigationProps) {
  const {
    branchLikes,
    component,
    component: { configuration },
    currentBranchLike,
    projectBinding,
  } = props;

  const isApplication = component.qualifier === ComponentQualifier.Application;
  const isGitLab = projectBinding !== undefined && projectBinding.alm === AlmKeys.GitLab;

  const [isMenuOpen, setIsMenuOpen] = React.useState(false);
  const branchSupportEnabled = props.hasFeature(Feature.BranchSupport);
  const canAdminComponent = configuration?.showSettings;
  const hasManyBranches = branchLikes.length >= 2;
  const isMenuEnabled = branchSupportEnabled && hasManyBranches;

  const currentBranchLikeElement = (
    <CurrentBranchLike
      component={component}
      currentBranchLike={currentBranchLike}
      projectBinding={projectBinding}
      comparisonBranchesEnabled={props.comparisonBranchesEnabled}
    />
  );

  return (
    <div className="sw-flex sw-items-center sw-ml-2 it__branch-like-navigation-toggler-container">
      <EscKeydownHandler
        onKeydown={() => {
          setIsMenuOpen(false);
        }}
      >
        <OutsideClickHandler
          onClickOutside={() => {
            setIsMenuOpen(false);
          }}
        >
          <Popup
            allowResizing={true}
            overlay={
              isMenuOpen && (
                <Menu
                  branchLikes={branchLikes}
                  canAdminComponent={canAdminComponent}
                  component={component}
                  currentBranchLike={currentBranchLike}
                  comparisonBranchesEnabled={props.comparisonBranchesEnabled}
                  onClose={() => {
                    setIsMenuOpen(false);
                  }}
                />
              )
            }
            placement={PopupPlacement.BottomLeft}
            zLevel={PopupZLevel.Global}
          >
            <ButtonSecondary
              className="sw-max-w-abs-350"
              onClick={() => {
                setIsMenuOpen(!isMenuOpen);
              }}
              disabled={!isMenuEnabled}
              aria-expanded={isMenuOpen}
              aria-haspopup="menu"
            >
              {currentBranchLikeElement}
            </ButtonSecondary>
          </Popup>
        </OutsideClickHandler>
      </EscKeydownHandler>

      <div className="sw-ml-2">
        <BranchHelpTooltip
          component={component}
          isApplication={isApplication}
          projectBinding={projectBinding}
          hasManyBranches={hasManyBranches}
          canAdminComponent={canAdminComponent}
          branchSupportEnabled={branchSupportEnabled}
          isGitLab={isGitLab}
        />
      </div>

      <PRLink currentBranchLike={currentBranchLike} component={component} />
    </div>
  );
}

export default withAvailableFeatures(React.memo(BranchLikeNavigation));
