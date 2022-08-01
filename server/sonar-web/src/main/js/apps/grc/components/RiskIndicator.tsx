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
import { Cell, Pie, PieChart, Tooltip } from 'recharts';
import '../grc-dashboard.css';

export default function RiskIndicator() {
  const totalPolicies = 75;
  const usedPolicies = 60;

  const policyCoverage = Math.ceil(((usedPolicies / totalPolicies)*100));
  const policyCoveragePercentage = policyCoverage ? policyCoverage+"%": "0%";
  const unusedPolicies = totalPolicies - usedPolicies;

  const data = [
    { name: "Used Policies", value: usedPolicies },
    { name: "Unused Policies", value: unusedPolicies }
  ];

  return (
    <>
      <div className="widget">
        <label>Risk Indicator</label>
        <br />
        <div className="guage-chart-cntr">
          <PieChart height={300} width={260}>
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
              <Cell fill="#57ACFB" />
              <Cell fill="#DDDDDD" />
            </Pie>
          </PieChart>
        </div>
      </div>
    </>
  );
  }
