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
import React from "react";
import { Cell, Pie, PieChart, Tooltip } from "recharts";
import '../grc-dashboard.css';

export default function RulesCoverage() {
  const totalRules = 75;
  const usedRules = 50;

  const ruleCoverage = Math.ceil(((usedRules / totalRules)*100));
  const ruleCoveragePercentage = ruleCoverage ? ruleCoverage+"%": "0%";
  const unusedRules = totalRules - usedRules

  const data = [
    { name: "Used Rules", value: usedRules },
    { name: "Unused Rules", value: unusedRules }
  ];

  return (
    <>
      <div className="widget">
        <label>Rule Coverage</label>
        <br />
        <div className="guage-chart-cntr">
          <PieChart height={200} width={220}>
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
              <Cell fill="#6BDE40" />
              <Cell fill="#DDDDDD" />
            </Pie>
          </PieChart>
          <label className="value">{ruleCoveragePercentage}</label>
        </div>
      </div>
    </>
  );
  }
