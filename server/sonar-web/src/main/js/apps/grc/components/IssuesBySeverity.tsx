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
import '../grc-dashboard.css';

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

export default function IssuesBySeverity() {
  const data = [
    { name: "Blocker", value: 300 },
    { name: "Critical", value: 300 },
    { name: "Major", value: 300 },
    { name: "Minor", value: 300 },
    { name: "Info", value: 300 },
  ];

  const blockerColorCode = "#780000";
  const criticalColorCode = "#DC0000";
  const majorColorCode = "#FD8C00";
  const minorColorCode = "#FDC500";
  const infoColorCode = "#00AC46"

  const COLORS = [blockerColorCode,criticalColorCode,majorColorCode,minorColorCode,infoColorCode];

  return (
    <>
      <div className="widget">
        <label>Issues By Severity</label>
        <div id="severity-pie-chart-cntr">
          <PieChart width={200} height={300}>
            <Legend layout="horizontal" verticalAlign="bottom" align="center" />
            <Tooltip/>
            <Pie
              data={data}
              cx={100}
              cy={125}
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
      </div>
    </>
  );
}
