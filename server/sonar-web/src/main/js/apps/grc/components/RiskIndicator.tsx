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
import '../grc-dashboard.css';
import HelpIcon from 'sonar-ui-common/components/icons/HelpIcon';
import HelpTooltip from 'sonar-ui-common/components/controls/HelpTooltip';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { colors } from '../../../app/theme';
import GaugeChart from 'react-gauge-chart';
 

interface Props {
  totalProfilesDefined:number,
  totalProfilesEnforced:number,
  componentKey:string
}

export default function RiskIndicator({ totalProfilesDefined, totalProfilesEnforced}: Props) {
  const usedPolicies = totalProfilesEnforced;
  const totalPolicies = totalProfilesDefined;
  const helpIcon = <HelpIcon fill={colors.gray71} size={12} />

  const policyRisk = 100 - Math.ceil((usedPolicies / totalPolicies)*100);
  const policyRisk2 = policyRisk/100
  const unusedPolicies = 100 - policyRisk;

  const data = [
    { name: "Policy Risk Score", value: policyRisk },
    { name: "Left Over", value: unusedPolicies }
  ];

  const highColorCode = "#d4333f";
  const mediumColorCode = "#ed7d20";
  const lowColorCode = "#eabe06"

  let fillColor = "#57ACFB";
  if(policyRisk<25){
    fillColor = "green";
  }else if(policyRisk>=25 && policyRisk<50){
    fillColor = lowColorCode;
  }else if(policyRisk>=50 && policyRisk<75){
    fillColor = mediumColorCode;
  }else if(policyRisk>=75){
    fillColor = highColorCode;
  }
  let isValidData = true;
  let errorMsg = "";
  if(usedPolicies>0 && totalPolicies>0 && usedPolicies<=totalPolicies){

  }else{
    isValidData = false;
    if(totalPolicies<=0){
      errorMsg = "Total Policies should be greater than Zero";
    }else if(usedPolicies<=0){
      errorMsg = "Policies Activated should be greater than Zero";
    }else if(usedPolicies>totalPolicies){
      errorMsg = "Total Policies should be greater than or equal to Policies Activated.";
    }
    console.log("Risk Indiator Error :: "+errorMsg);
  }

  return (
    <>
      <div className="widget">
        <label className="name">{translate('grc.dashboard.policy.risk.score')}</label>
        <HelpTooltip
            overlay={
              <>
                <p>{translate('grc.dashboard.policy.risk.score.description')}</p>
              </>
            }>
           {helpIcon}
          </HelpTooltip>
        <br />
        <div className="guage-chart-cntr">
          {isValidData?(<>
            {/* <PieChart height={300} width={260}>
            <Tooltip/>
            <Pie
              startAngle={180}
              endAngle={0}
              innerRadius="56%"
              data={data}
              dataKey="value"
              labelLine={false}
              blendStroke
              isAnimationActive={true}>
              <Cell fill={fillColor} />
              <Cell fill="#DDDDDD" />
            </Pie>
          </PieChart>
          <label className="value">{policyRisk}</label> */}
          <GaugeChart id="gauge-chart2"
              nrOfLevels={20}
              percent={policyRisk2}
              css="custom-guage-chart"
            />
          </>):(
            <>
              <br/>
              <label className='error'>
                Invalid Data
              </label>
            </>
          )}
        </div>
      </div>
    </>
  );
  }
