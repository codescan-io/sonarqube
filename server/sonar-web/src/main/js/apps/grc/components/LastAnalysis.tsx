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
import { getQualityGateInfo, getQualityProfileInfo, getReadableDateFormat, getVersionInfo } from "../dashboard/utils";
import '../grc-dashboard.css';

interface Props {
  event: T.Analysis;
}

export default function LastAnalysis({ event }: Props) {

  const lastScanDate: string = getReadableDateFormat(event.date);
  const qualityGate: string | undefined = getQualityGateInfo(event);
  const qualityProfile: string | undefined = getQualityProfileInfo(event);
  const version: string | undefined = getVersionInfo(event);

  return (
      <>
      <div className="widget last-analysis-cntr">
        <label>Last Analysis </label><br/><br/>
        <div><label className="value first">Time: <b>{lastScanDate}</b></label></div>
        {
          qualityGate? (
            <div><label className="value">Quality Gate: <b>{qualityGate}</b></label></div>
          ):(
            <></>
          )
        }
        {
          qualityProfile? (
            <div><label className="value">Quality Profile: <b>{qualityProfile}</b></label></div>
          ):(
            <></>
          )
        }
        {
          version? (
            <div><label className="value">Version: <b>{version}</b></label></div>
          ):(
            <></>
          )
        }
      </div>
      </>
  );

}
