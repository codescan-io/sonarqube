import React, { useEffect, useState } from "react";
import { connect } from "react-redux";
import { WithRouterProps } from "react-router";
import { runIntegration } from "../../../api/codescan";
import { getAppState, Store } from '../../../store/rootReducer';
import CreateProjectPageSonarCloud from "../../create/project/CreateProjectPageSonarCloud";
import AuthorizeForm from "../AddProject/AuthorizeForm";
import { parseError } from "../AddProject/Salesforce";
import CreateProjectPage from "./CreateProjectPage";

interface Props {
    appState: T.AppState | undefined;
    onNextClick: (org: string) => any;
    onOrganizationUpgrade: () => void;
    organization: T.Organization;
}
const CreateProject = (props: Props & WithRouterProps) => {
    const [org, setOrg] = useState(props.organization);
    const [openAuthorize, setOpenAuthorize] = useState(false);
    const [hashState, setHashState] = useState();

    const nextClick = (data: T.Organization) => {
        setOrg(data);
    }

    useEffect(() => {
      if (window.location.hash) {
        const params = (window.location.hash.substr(1)).split("&");
        const state: any = {};
        for (let i = 0; i < params.length; i++)
        {
            const a: any = params[i].split("=");
            // Now every parameter from the hash is beind handled this way
            state[a[0]] = decodeURIComponent(a[1]);
        }
        if ( typeof(state['action']) == 'string' && state['action'] === "integrations_create" ){
          setHashState(state)
          setOpenAuthorize(true);
        }
      }
    }, []);

    const onRefresh = () => {
      setOpenAuthorize(false);
      /* let findProjects = findCiProjects({q: this.state.searchQuery, organizationId: this.props.organization.key});
      let findQueues = findCiQueues({organizationId: this.props.organization.key});
      let billing = getBillingCheck(this.props.organization.key);
      Promise.all([findProjects, findQueues, billing]).then(
        (results) => {
          this.setState({
            projects: results[0],
            queues: results[1],
            loading: false,
            hasMore: true,
            billing: results[2]['billing']
          });
          this.statusMonitor.start(results[1], this.onRefreshQueue);
        }
      ).catch(e => {
        return parseError(e).then(message => this.setState({ errorMsg: message }));
      }); */
    }

    const onAuthorizeDone = (projectData: any) => {
      runIntegration({
        organizationId: projectData.organizationId,
        projectKey: projectData.projectKey
      }).then(()=>{
        onRefresh();
      }).catch(e => {
        // eslint-disable-next-line no-console
        return parseError(e).then((message: any) => console.log(message));
      });
    }
    
  return (
    <div>
      { openAuthorize && (<AuthorizeForm organization={props.organization} hashState={hashState} onModified={onAuthorizeDone} originalProject={null} />) }
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