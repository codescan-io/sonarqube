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
import { Cell, Legend, Pie, PieChart, Tooltip } from 'recharts';
import { getGrcViolationsUrl } from '../../../../js/helpers/urls';
import { RawHotspot } from '../../../types/security-hotspots';
import { getHotspotsBasedOnRiskExposure } from '../dashboard/utils';
import '../grc-dashboard.css';
import LinkWidget from './LinkWidget';
import { colors } from '../../../app/theme';
import { translate } from 'sonar-ui-common/helpers/l10n';
import HelpIcon from 'sonar-ui-common/components/icons/HelpIcon';
import HelpTooltip from 'sonar-ui-common/components/controls/HelpTooltip';

const RADIAN = Math.PI / 180;
const renderCustomizedLabel = ({
  cx,
  cy,
  midAngle,
  innerRadius,
  outerRadius,
  percent
}: any) => {
  const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
  const x = cx + radius * Math.cos(-midAngle * RADIAN);
  const y = cy + radius * Math.sin(-midAngle * RADIAN);

  return (
    <text
      x={x}
      y={y}
      fill="white"
      textAnchor={x > cx ? "start" : "end"}
      dominantBaseline="central"
    >
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
};

interface Props {
  hotspots:RawHotspot[],
  componentKey:string
}

export default function IssuesByPriority({hotspots,componentKey}:Props) {
  const processedHotspots:{high:number,medium:number,low:number} = getHotspotsBasedOnRiskExposure(hotspots);

  const highColorCode = "#d4333f";
  const mediumColorCode = "#ed7d20";
  const lowColorCode = "#eabe06"

  const redirectUrl = getGrcViolationsUrl(componentKey);

  const helpIcon = <HelpIcon fill={colors.gray71} size={12} />

  const data = [
  ];

  const COLORS: string[] = [];

  if(processedHotspots.high>0){
    data.push({ name: "High", value: processedHotspots.high });
    COLORS.push(highColorCode);
  }

  if(processedHotspots.medium>0){
    data.push({ name: "Medium", value: processedHotspots.medium });
    COLORS.push(mediumColorCode);
  }

  if(processedHotspots.low>0){
    data.push({ name: "Low", value: processedHotspots.low });
    COLORS.push(lowColorCode);
  }

  return (
    <>
      <div className="widget">
        <label className='name'>{translate('grc.dashboard.violations')}</label>
        <HelpTooltip
            overlay={
              <>
                <p>{translate('grc.dashboard.violations.description')}</p>
              </>
            }>
           {helpIcon}
          </HelpTooltip>
        <LinkWidget link={redirectUrl}></LinkWidget>
        {
          hotspots.length > 0 ?(
<div id="severity-pie-chart-cntr" className="pie-chart-cntr">
          <PieChart width={200} height={300}>
            <Legend layout="horizontal" verticalAlign="bottom" align="center" />
            <Tooltip/>
            <Pie
              data={data}
              cx={125}
              cy={100}
              labelLine={false}
              label={renderCustomizedLabel}
              outerRadius={80}
              startAngle={180}
              endAngle={-180}
              fill="#8884d8"
              dataKey="value">
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
          </PieChart>
        </div>
          ) :(<>
          <br/><br/>
            <div className="error">No Data</div>
            </>
          )
        }
      </div>
    </>
  );
}
