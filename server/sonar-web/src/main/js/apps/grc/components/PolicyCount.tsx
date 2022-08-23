/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import React from 'react';
import HelpTooltip from 'sonar-ui-common/components/controls/HelpTooltip';
import { getGrcProfilesUrl } from '../../../helpers/urls';
import '../grc-dashboard.css';
import LinkWidget from './LinkWidget';
import { colors } from '../../../app/theme';
import { translate } from 'sonar-ui-common/helpers/l10n';
import HelpIcon from 'sonar-ui-common/components/icons/HelpIcon';


interface Props {
  totalProfilesDefined: number;
  totalProfilesEnforced: number;
  componentKey: string;
}

export default function PolicyCount({
  componentKey,
  totalProfilesDefined,
  totalProfilesEnforced
}: Props) {
  const redirectUrl = getGrcProfilesUrl(componentKey);
  const helpIcon = <HelpIcon fill={colors.gray71} size={12} />
  return (
    <>
      <div className="widget policy-count">
        <div className="policy">
          <LinkWidget link={redirectUrl}></LinkWidget>
          <label className="name">{translate('grc.dashboard.total.policies')}</label>
          
          <HelpTooltip
            overlay={
              <>
                <p>{translate('grc.dashboard.total.policies.description')}</p>
              </>
            }>
           {helpIcon}
          </HelpTooltip>
          <br />
          <label className="value">{totalProfilesDefined}</label> <br />
        </div>
        <hr className="seperator"></hr>
        <div className="policy">
          <LinkWidget link={redirectUrl}></LinkWidget>
          <label className="name">{translate('grc.dashboard.activated.policies')}</label>
          <HelpTooltip
            overlay={
              <>
                <p>{translate('grc.dashboard.activated.policies.description')}</p>
              </>
            }>
           {helpIcon}
          </HelpTooltip>
          <br />
          <label className="value">{totalProfilesEnforced}</label>
        </div>
      </div>
    </>
  );
}
