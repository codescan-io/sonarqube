import React from "react";
import IssuesByPolicyType from "../components/IssuesByPolicyType";
import IssuesBySeverity from "../components/IssuesBySeverity";
import LastScanDate from "../components/LastScanDate";
import PolicyCoverage from "../components/PolicyCoverage";
import RulesCoverage from "../components/RulesCoverage";
import Violations from "../components/Violations";
import '../grc-dashboard.css';

export default function Dashboard() {

  return (
      <div className="dashboard-page">
        <div className="row">
          <div className="col-4"><LastScanDate></LastScanDate></div>
          <div className="col-4"><PolicyCoverage></PolicyCoverage></div>
          <div className="col-4"><RulesCoverage></RulesCoverage></div>
        </div>
        <div className="row">
          <div className="col-4"><Violations></Violations></div>
          <div className="col-4"><IssuesByPolicyType></IssuesByPolicyType></div>
          <div className="col-4"><IssuesBySeverity></IssuesBySeverity></div>
        </div>
      </div>
  );

}
