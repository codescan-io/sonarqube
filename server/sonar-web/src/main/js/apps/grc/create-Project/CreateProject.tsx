import React, { useState } from "react";
import { connect } from "react-redux";
import { WithRouterProps } from "react-router";
import { getAppState, Store } from '../../../store/rootReducer';
import CreateProjectPageSonarCloud from "../../create/project/CreateProjectPageSonarCloud";
import CreateProjectPage from "./CreateProjectPage";

interface Props {
    appState: T.AppState | undefined;
    onNextClick: (org: string) => any;
    onOrganizationUpgrade: () => void;
    organization: T.Organization;
}
const CreateProject = (props: Props & WithRouterProps) => {
    const [org, setOrg] = useState(props.organization);

    const nextClick = (data: T.Organization) => {
        setOrg(data);
    }
    
  return (
    <div>
    {!org && <CreateProjectPageSonarCloud {...props} onNextClick={nextClick} />}
    {org && <CreateProjectPage {...props} onOrganizationUpgrade={props.onOrganizationUpgrade}
          organization={org}/>}
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
    appState: getAppState(state)
});

export default connect(mapStateToProps)(CreateProject);