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
import IssuesByPriority from "../components/IssuesByPriority";
import LastAnalysis from "../components/LastAnalysis";
import PolicyCount from "../components/PolicyCount";
import RiskIndicator from "../components/RiskIndicator";
import '../grc-dashboard.css';

export default function Dashboard() {

  return (
      <div className="dashboard-page">
        <div className="row">
          <div className="col-3 no-padding"><PolicyCount></PolicyCount></div>
          <div className="col-3"><RiskIndicator></RiskIndicator></div>
          <div className="col-3"><IssuesByPriority></IssuesByPriority></div>
          <div className="col-3"><LastAnalysis></LastAnalysis></div>
        </div>
        <div className="row">
        </div>
        <div className="row">
        </div>
      </div>
  );

}

//<div className="col-3"><Violations></Violations></div>
//<div className="col-3"><IssuesByPolicyType></IssuesByPolicyType></div>
//<div className="col-3"><IssuesBySeverity></IssuesBySeverity></div>
//<div className="col-3"><LastScanDate></LastScanDate></div>
//<div className="col-3"><PolicyCoverage></PolicyCoverage></div>
//<div className="col-3"><RulesCoverage></RulesCoverage></div>
//<div className="col-3"><RulesCoverage></RulesCoverage></div>