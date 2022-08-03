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
import '../grc-dashboard.css';

export default function LastAnalysis() {

  const lastScanDate: string = "07/30/2022, 05:08 AM";
  const qualityGate: string = "Red"
  const qualityProfile: string = "Used IBM MO Profile(SF Metadata)"
  const version: string = "1"

  return (
      <>
      <div className="widget last-analysis-cntr">
        <label>Last Analysis </label><br/><br/>
        <label className="value first">Time: <b>{lastScanDate}</b></label><br/>
        <label className="value">Quality Gate: <b>{qualityGate}</b></label><br/>
        <label className="value">Quality Profile: <b>{qualityProfile}</b></label><br/>
        <label className="value">Version: <b>{version}</b></label><br/>

      </div>
      </>
  );

}
