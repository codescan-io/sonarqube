import React from "react";
import { Link } from 'react-router';
import { translate } from "sonar-ui-common/helpers/l10n";
import './Create.css';

export default function CreateGRCProject() {
  
  return (
        <div className="create-container">
          <span className="info">{translate('grc.no_projects')}</span>
          <div className="create">
            <Link to="/grc/create">
            <img src='/images/scanner-logos/msbuild.svg' alt="" />
            {translate('grc.create_project')}</Link>
          </div>
        </div>
  );
}
