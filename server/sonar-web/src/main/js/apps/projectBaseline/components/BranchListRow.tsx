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
import * as React from 'react';
import ActionsDropdown, { ActionsDropdownItem } from '../../../components/controls/ActionsDropdown';
import Tooltip from '../../../components/controls/Tooltip';
import BranchLikeIcon from '../../../components/icons/BranchLikeIcon';
import WarningIcon from '../../../components/icons/WarningIcon';
import DateTimeFormatter from '../../../components/intl/DateTimeFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { isNewCodeDefinitionCompliant } from '../../../helpers/periods';
import { BranchWithNewCodePeriod } from '../../../types/branch-like';
import { NewCodePeriod, NewCodePeriodSettingType } from '../../../types/types';

export interface BranchListRowProps {
  branch: BranchWithNewCodePeriod;
  existingBranches: Array<string>;
  inheritedSetting: NewCodePeriod;
  onOpenEditModal: (branch: BranchWithNewCodePeriod) => void;
  onResetToDefault: (branchName: string) => void;
}

function renderNewCodePeriodSetting(newCodePeriod: NewCodePeriod) {
  switch (newCodePeriod.type) {
    case NewCodePeriodSettingType.SPECIFIC_ANALYSIS:
      return (
        <>
          {`${translate('baseline.specific_analysis')}: `}
          {newCodePeriod.effectiveValue ? (
            <DateTimeFormatter date={newCodePeriod.effectiveValue} />
          ) : (
            '?'
          )}
        </>
      );
    case NewCodePeriodSettingType.NUMBER_OF_DAYS:
      return `${translate('new_code_definition.number_days')}: ${newCodePeriod.value}`;
    case NewCodePeriodSettingType.PREVIOUS_VERSION:
      return translate('new_code_definition.previous_version');
    case NewCodePeriodSettingType.REFERENCE_BRANCH:
      return `${translate('baseline.reference_branch')}: ${newCodePeriod.value}`;
    default:
      return newCodePeriod.type;
  }
}

function branchInheritsItselfAsReference(
  branch: BranchWithNewCodePeriod,
  inheritedSetting: NewCodePeriod
) {
  return (
    !branch.newCodePeriod &&
    inheritedSetting.type === NewCodePeriodSettingType.REFERENCE_BRANCH &&
    branch.name === inheritedSetting.value
  );
}

function referenceBranchDoesNotExist(
  branch: BranchWithNewCodePeriod,
  existingBranches: Array<string>
) {
  return (
    branch.newCodePeriod &&
    branch.newCodePeriod.value &&
    branch.newCodePeriod.type === NewCodePeriodSettingType.REFERENCE_BRANCH &&
    !existingBranches.includes(branch.newCodePeriod.value)
  );
}

export default function BranchListRow(props: BranchListRowProps) {
  const { branch, existingBranches, inheritedSetting } = props;

  let settingWarning: string | undefined;
  if (branchInheritsItselfAsReference(branch, inheritedSetting)) {
    settingWarning = translateWithParameters(
      'baseline.reference_branch.invalid_branch_setting',
      branch.name
    );
  } else if (referenceBranchDoesNotExist(branch, existingBranches)) {
    settingWarning = translateWithParameters(
      'baseline.reference_branch.does_not_exist',
      branch.newCodePeriod?.value || ''
    );
  }

  const isCompliant = isNewCodeDefinitionCompliant(inheritedSetting);

  return (
    <tr className={settingWarning ? 'branch-setting-warning' : ''}>
      <td className="nowrap">
        <BranchLikeIcon branchLike={branch} className="little-spacer-right" />
        {branch.name}
        {branch.isMain && (
          <div className="badge spacer-left">{translate('branches.main_branch')}</div>
        )}
      </td>
      <td className="huge-spacer-right nowrap">
        <Tooltip overlay={settingWarning}>
          <span>
            {settingWarning && <WarningIcon className="little-spacer-right" />}
            {branch.newCodePeriod
              ? renderNewCodePeriodSetting(branch.newCodePeriod)
              : translate('branch_list.default_setting')}
          </span>
        </Tooltip>
      </td>
      <td className="text-right">
        <ActionsDropdown
          label={translateWithParameters('branch_list.show_actions_for_x', branch.name)}
        >
          <ActionsDropdownItem onClick={() => props.onOpenEditModal(branch)}>
            {translate('edit')}
          </ActionsDropdownItem>
          {branch.newCodePeriod && (
            <ActionsDropdownItem
              disabled={!isCompliant}
              onClick={() => props.onResetToDefault(branch.name)}
              tooltipOverlay={
                isCompliant ? null : translate('project_baseline.compliance.warning.title.project')
              }
            >
              {translate('reset_to_default')}
            </ActionsDropdownItem>
          )}
        </ActionsDropdown>
      </td>
    </tr>
  );
}
