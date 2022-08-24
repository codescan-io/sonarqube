import React, { useEffect, useRef, useState } from "react";
import { connect } from "react-redux";
import { Router, WithRouterProps } from "react-router";
import Modal from "sonar-ui-common/components/controls/Modal";
import { findCiProjects, findCiQueues, getBillingCheck, runIntegration } from "../../../api/codescan";
import { getAppState, Store } from '../../../store/rootReducer';
import CreateProjectPageSonarCloud from "../../create/project/CreateProjectPageSonarCloud";
import AuthorizeForm from "../AddProject/AuthorizeForm";
import { parseError } from "../AddProject/Salesforce";
import CreateProjectPage from "./CreateProjectPage";
import StatusMonitor from "../run-analysis/StatusMonitor";

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
    const [organizationKey, setOrganizationKey] = useState('');
    const [projectKey, setProjectKey] = useState('');
    const [showWaiting, setShowWaiting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    
    const componentMounted = useRef(true);

    const nextClick = (data: T.Organization) => {
        setOrg(data);
    }

    useEffect(() => {
        if (componentMounted.current && window.location.hash) {
          const params = (window.location.hash.substr(1)).split("&");
          const state: any = {};
          for (let i = 0; i < params.length; i++) {
              const a: any = params[i].split("=");
              state[a[0]] = decodeURIComponent(a[1]);
          }
          if ( typeof(state['action']) == 'string' && state['action'] === "integrations_create" ){
            setHashState(state)
            setOpenAuthorize(true);
          }
          if(state['organization']) {
            setOrganizationKey(state['organization']);
          }
          if(state['projectKey']) {
            setProjectKey(state['projectKey']);
          }
        }
      return () => { // This code runs when component is unmounted
          componentMounted.current = false;
      }
    }, []);

    const onRefreshQueue = () => {
      const findQueues = findCiQueues({organizationId: organizationKey});
      findQueues.then( queues => {
          if(queues[0].status === 'done') {
            setShowWaiting(false);
            props.router.replace('/grc/dashboard?id='+projectKey);
          }
          new StatusMonitor(props).start(queues, onRefreshQueue);
        }
      ).catch(e => {
        return parseError(e).then((message: any) => setErrorMsg(message));
      });
    }

    const onRefresh = () => {
      setOpenAuthorize(false);
      setShowWaiting(true);
      const findQueues = findCiQueues({organizationId: organizationKey});
      //const billing = getBillingCheck(organizationKey);
      Promise.all([findQueues]).then(
        (results) => {
          new StatusMonitor(props).start(results[0], onRefreshQueue);
        }
      ).catch(e => {
        return parseError(e).then((message: any) => setErrorMsg(message));
      });
    }

    const onAuthorizeDone = (projectData: any) => {
      runIntegration({
        organizationId: projectData.organizationId,
        projectKey: projectData.projectKey
      }).then(()=>{
        onRefresh();
      }).catch(e => {
        return parseError(e).then((message: any) => setErrorMsg(message));
      });
    }

    const closeAuthorize = () => {
      setOpenAuthorize(false);
    }
    
  return (
    <div>
      {showWaiting && 
      (<Modal
      contentLabel="modal form"
      className="modal"
      overlayClassName="modal-overlay">
      <header className="modal-head">
        <h2>Please Wait...</h2>
      </header>
      <div className="modal-body">
        <label htmlFor="Analysis-waiting">Analysis is in progress, this will take some time. Once it is completed you will be automatically redirected to dashboard.</label>
      </div>
      </Modal>
      )
    }
    {<div className="text-danger">{ errorMsg }</div>}
      { openAuthorize && (<AuthorizeForm organization={props.organization} hashState={hashState} onModified={onAuthorizeDone} closeForm={closeAuthorize}/>) }
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