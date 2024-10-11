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
import { translate } from '../../helpers/l10n';
import { isNewCodeDefinitionCompliant } from '../../helpers/periods';
import { NewCodePeriodSettingType } from '../../types/types';
import DocLink from '../common/DocLink';
import { Alert } from '../ui/Alert';

export interface NewCodeDefinitionWarningProps {
  newCodeDefinitionType: NewCodePeriodSettingType | undefined;
  newCodeDefinitionValue: string | undefined;
  isBranchSupportEnabled: boolean | undefined;
  level: 'branch' | 'project' | 'global';
}

export default function NewCodeDefinitionWarning({
  newCodeDefinitionType,
  newCodeDefinitionValue,
  isBranchSupportEnabled,
  level,
}: NewCodeDefinitionWarningProps) {
  if (
    newCodeDefinitionType === undefined ||
    isNewCodeDefinitionCompliant({ type: newCodeDefinitionType, value: newCodeDefinitionValue })
  ) {
    return null;
  }

  if (newCodeDefinitionType === NewCodePeriodSettingType.SPECIFIC_ANALYSIS) {
    return (
      <Alert variant="warning" className="sw-mb-4 sw-max-w-[800px]">
        <p className="sw-mb-2 sw-font-bold">
          {translate('baseline.specific_analysis.compliance_warning.title')}
        </p>
        <p className="sw-mb-2">
          {translate('baseline.specific_analysis.compliance_warning.explanation')}
        </p>
        <p>
          {translate('learn_more')}:&nbsp;
          <DocLink to="/project-administration/defining-new-code/">
            {translate('baseline.specific_analysis.compliance_warning.link')}
          </DocLink>
        </p>
      </Alert>
    );
  }

  if (newCodeDefinitionType === NewCodePeriodSettingType.NUMBER_OF_DAYS) {
    return (
      <Alert variant="warning" className="sw-mb-4 sw-max-w-[800px]">
        <p className="sw-mb-2 sw-font-bold">
          {translate('baseline.number_days.compliance_warning.title')}
        </p>
        <p className="sw-mb-2">
          {translate(
            `baseline.number_days.compliance_warning.content.${level}${
              isBranchSupportEnabled && level === 'project' ? '.with_branch_support' : ''
            }`
          )}
        </p>
        <p>
          {translate('learn_more')}:&nbsp;
          <DocLink to="/project-administration/defining-new-code/">
            {translate('baseline.number_days.compliance_warning.link')}
          </DocLink>
        </p>
      </Alert>
    );
  }

  return null;
}
