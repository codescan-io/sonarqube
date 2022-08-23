
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
import { IssueType } from '../../../types/issues';
import { BranchLike } from '../../../types/branch-like';
import MeasuresPanelIssueMeasureRow from '../../overview/branches/MeasuresPanelIssueMeasureRow';
import '../grc-dashboard.css';
import '../../overview/styles.css';
import { getGrcOverviewUrl } from '../../../../js/helpers/urls';
import LinkWidget from './LinkWidget';

interface Props {
  branchLike: BranchLike,
  component: T.Component,
  measures: T.MeasureEnhanced[],
  componentKey:string
}

export default function ViolationDetails(props:Props) {
  const {branchLike,component,measures,componentKey} =  props;
  const type = IssueType.SecurityHotspot
  const grc = true;
  const redirectUrl = getGrcOverviewUrl(componentKey)

  return (
    <>
      <div className="widget violation-details-cntr">
        
        <div className="flex-parent">
          <div className="flex-left-child">
          <LinkWidget link={redirectUrl}></LinkWidget>
            New <br/>Violations
          </div>
          <div className="flex-right-child">
          <MeasuresPanelIssueMeasureRow
                    branchLike={branchLike}
                    component={component}
                    isNewCodeTab={true}
                    key={type}
                    measures={measures}
                    type={type}
                    grc={grc}
                    renderLink={false}
                  />
          </div>
        </div>
        <hr className="seperator-small"/>
        <div className="flex-parent">
          <div className="flex-left-child">
          Existing <br/> Violations
          </div>
          <div className="flex-right-child">
          <MeasuresPanelIssueMeasureRow
                    branchLike={branchLike}
                    component={component}
                    isNewCodeTab={false}
                    key={type}
                    measures={measures}
                    type={type}
                    grc={grc}
                    renderLink={false}
                  />
          </div>
        </div>
      </div>
    </>
  );
  }