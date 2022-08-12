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
import classNames from 'classnames';
import React from 'react';
import SecurityHotspotIcon from 'sonar-ui-common/components/icons/SecurityHotspotIcon';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { RawHotspot } from '../../../types/security-hotspots';
import { getGroupHotspots } from '../dashboard/utils';
import '../grc-dashboard.css';
import '../../security-hotspots/components/HotspotList.css';
import LinkWidget from './LinkWidget';
import { getGrcViolationsUrl } from '../../../../js/helpers/urls';


interface Props {
  hotspots:RawHotspot[],
  totalHotspots:number,
  securityCategories:T.StandardSecurityCategories,
  componentKey:string
}

export default function GrcViolations(props:Props) {
  const {totalHotspots,hotspots,securityCategories, componentKey} = props;

  const groupedHotspots = getGroupHotspots(hotspots, securityCategories);

  const redirectUrl = getGrcViolationsUrl(componentKey);

  return (
    <>
      <div className="widget grc-violations-cntr">
        <LinkWidget link={redirectUrl}></LinkWidget>  
        {hotspots.length === 0 ?(
          <div className='grc-violations-empty-cntr'>
            <SecurityHotspotIcon className="spacer-right" /> No Violations Found
          </div>
        ):(
          <>
            <div className='grc-total-violations-cntr'>
              <SecurityHotspotIcon className="spacer-right" />{totalHotspots} Violations found
            </div>
            <hr/>
            <ul>
              {groupedHotspots.map((riskGroup, riskGroupIndex) => {
                return (
                  <li className="big-spacer-bottom" key={riskGroup.risk}>
                    <div className='hotspot-risk-header little-spacer-left'>
                    <span>{translate('hotspots.risk_exposure')}:</span>
                    <span
                      className={classNames('hotspot-risk-badge', 'spacer-left', riskGroup.risk)}>
                      {translate('risk_exposure', riskGroup.risk)}
                    </span>
                    </div>
                    <ul>
                      {riskGroup.categories.map((cat, categoryIndex) => {
                       return( <li className="spacer-bottom" key={cat.key}>
                          <div className={classNames('hotspot-category',riskGroup.risk)}>
                          <div className="hotspot-category-header display-flex-space-between display-flex-center contains-selected-hotspot">
                            <strong className="flex-1 spacer-right break-word">{cat.title}</strong>
                            <span>
                              <span className="counter-badge">{cat.hotspots.length}</span>
                            </span>
                          </div>
                          </div>
                        </li>)
                      })}
                    </ul>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>
    </>
  );
  }
