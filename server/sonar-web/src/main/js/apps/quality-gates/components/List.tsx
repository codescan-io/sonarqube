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
import { NavLink } from 'react-router-dom';
import Tooltip from '../../../components/controls/Tooltip';
import AlertWarnIcon from '../../../components/icons/AlertWarnIcon';
import { translate } from '../../../helpers/l10n';
import { getQualityGateUrl } from '../../../helpers/urls';
import { CaycStatus, QualityGate } from '../../../types/types';
import BuiltInQualityGateBadge from './BuiltInQualityGateBadge';

interface Props {
  organization: string;
  qualityGates: QualityGate[];
  currentQualityGate?: string;
}

export default function List({ organization, qualityGates, currentQualityGate }: Props) {
  return (
    <div className="list-group">
      {qualityGates.map((qualityGate) => (
        <NavLink
          className="list-group-item display-flex-center"
          aria-current={currentQualityGate === qualityGate.name && 'page'}
          data-id={qualityGate.name}
          key={qualityGate.name}
          to={getQualityGateUrl(organization, qualityGate.name)}
        >
          <span className="flex-1 text-ellipsis" title={qualityGate.name}>
            {qualityGate.name}
          </span>
          {qualityGate.isDefault && (
            <span className="badge little-spacer-left">{translate('default')}</span>
          )}
          {qualityGate.isBuiltIn && <BuiltInQualityGateBadge className="little-spacer-left" />}

          {qualityGate.caycStatus === CaycStatus.NonCompliant && (
            <Tooltip overlay={translate('quality_gates.cayc.tooltip.message')}>
              <AlertWarnIcon
                className="spacer-left"
                description={translate('quality_gates.cayc.tooltip.message')}
              />
            </Tooltip>
          )}
        </NavLink>
      ))}
    </div>
  );
}
