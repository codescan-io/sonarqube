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
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import '../grc-dashboard.css';

export default function ProgressDonut() {
  const currentProgress = 200;
  const remainingProgress = 200;

  const data = [
    { name: "Current Progress", value: currentProgress },
    { name: "Remaining Progress", value: remainingProgress }
  ];

  return (
    <>
      <div className="widget">
        <label>Progress Donut</label>
        <br />
        <div className="donut-chart-cntr">
          <PieChart height={200} width={200}>
            <Tooltip/>
            <Pie
             cx={135}
             cy={100}
              innerRadius={60}
              outerRadius={80}
              data={data}
              dataKey="value"
              isAnimationActive={true}>
              <Cell fill="#0088FE" />
              <Cell fill="#00C49F" />
            </Pie>
          </PieChart>
        </div>
      </div>
    </>
  );
  }
