import React, { useState } from "react";
import { connect } from "react-redux";
import { WithRouterProps } from "react-router";
import { getAppState, Store } from '../../../store/rootReducer';
import CreateProjectPageSonarCloud from "../../create/project/CreateProjectPageSonarCloud";
import CreateProjectPage from "./CreateProjectPage";

interface Props {
    appState: T.AppState | undefined;
    onNextClick: (key: string) => any;
    onOrganizationUpgrade: () => void;
    organization: T.Organization;
}
const CreateProject = (props: Props & WithRouterProps) => {
    const [orgKey, setOrgKey] = useState('');

    const nextClick = (key: string) => {
        setOrgKey(key);
    }
    
  return (
    <div>
    {!orgKey && <CreateProjectPageSonarCloud {...props} onNextClick={nextClick} />}
    {orgKey && <CreateProjectPage {...props} onOrganizationUpgrade={props.onOrganizationUpgrade}
          organization={props.organization}/>}
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
    appState: getAppState(state)
});

export default connect(mapStateToProps)(CreateProject);