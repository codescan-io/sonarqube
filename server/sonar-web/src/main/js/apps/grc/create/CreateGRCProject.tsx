import React from "react";
import { Link } from 'react-router';
import './Create.css';

export default function CreateGRCProject() {
    //this.props.router.replace('/home');

  return (
        <div className="create-container">
          <span className="info">You have no GRC Projects yet, start by creating a new one and connecting it to your Salesforce Organization.</span>
          <div className="create">
            <Link to="/grc/create">
            <img src='/images/scanner-logos/msbuild.svg' alt="" />
            Create GRC Project</Link>
          </div>
        </div>
  );
}