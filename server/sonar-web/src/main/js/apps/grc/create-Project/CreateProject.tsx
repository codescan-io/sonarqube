import React, { useEffect, useRef, useState } from "react";
import { connect } from "react-redux";
import { Router, WithRouterProps } from "react-router";
import Modal from "sonar-ui-common/components/controls/Modal";
import { findCiProjects, findCiQueues, getBillingCheck, runIntegration } from "../../../api/codescan";
import { getAppState, getCurrentUser, Store } from '../../../store/rootReducer';
import CreateProjectPageSonarCloud from "../../create/project/CreateProjectPageSonarCloud";
import AuthorizeForm from "../AddProject/AuthorizeForm";
import { parseError } from "../AddProject/Salesforce";
import CreateProjectPage from "./CreateProjectPage";
import StatusMonitor from "../run-analysis/StatusMonitor";
import { addGlobalErrorMessage } from "../../../store/globalMessages";

interface Props {
    appState: T.AppState | undefined;
    onNextClick: (org: string) => any;
    onOrganizationUpgrade: () => void;
    organization: T.Organization;
    currentUser: T.CurrentUser | undefined;
}

const CreateProject = (props: Props & WithRouterProps) => {
    const [org, setOrg] = useState(props.organization);
    const [hideCreateProjectPage, setHideCreateProjectPage] = useState(true);
    const [openAuthorize, setOpenAuthorize] = useState(false);
    const [hashState, setHashState] = useState();
    const [organizationKey, setOrganizationKey] = useState('');
    const [projectKey, setProjectKey] = useState('');
    const [showWaiting, setShowWaiting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [status, setStatus] = useState('preparing');
    
    const componentMounted = useRef(true);

    const nextClick = (data: T.Organization) => {
        setOrg(data);
        setHideCreateProjectPage(false);
    }

    useEffect(() => {
      const { currentUser } = props;
      //TODO need to get org key from current user.
        if (componentMounted.current && window.location.hash) {
          const params = (window.location.hash.substr(1)).split("&");
          const state: any = {};
          for (let i = 0; i < params.length; i++) {
              const a: any = params[i].split("=");
              state[a[0]] = decodeURIComponent(a[1]);
          }
          if ( typeof(state['action']) == 'string' && state['action'] === "integrations_create" ){
            setHashState(state)
          }
          if(state['organization']) {
            setOrganizationKey(state['organization']);
          }
          if(state['projectKey']) {
            setProjectKey(state['projectKey']);
          }
          setOpenAuthorize(true);
        }
      return () => { // This code runs when component is unmounted
          componentMounted.current = false;
      }
    }, []);

    const onRefreshQueue = () => {
      const findQueues = findCiQueues({organizationId: organizationKey});
      findQueues.then( queues => {
        setStatus(queues[0].status);
          if(queues[0].status === 'done') {
            setShowWaiting(false);
            props.router.replace('/grc/dashboard?id='+projectKey);
            return;
          }
          if(queues[0].status === 'failed') {
            setShowWaiting(false);
            addGlobalErrorMessage('Analysis failed. Try again later.');
            props.router.replace('/grc/dashboard');
            return;
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

    const hideProjectPage = () => {
      setOrg(props.organization);
      setHideCreateProjectPage(true);
    }
    
  return (
    <div>
      {showWaiting && 
      (<Modal
      contentLabel="modal form"
      className="modal"
      overlayClassName="modal-overlay">
      <header className="modal-head">
        <h2>Please Wait... Status: {status}</h2>
      </header>
      <div className="modal-body">
        <label htmlFor="Analysis-waiting">Analysis is in progress, this will take some time. Once it is completed you will be automatically redirected to dashboard.</label>
      </div>
      </Modal>
      )
    }
    {<div className="text-danger">{ errorMsg }</div>}
      { openAuthorize && (<AuthorizeForm organization={props.organization} hashState={hashState} onModified={onAuthorizeDone} closeForm={closeAuthorize}/>) }
      {!org && hideCreateProjectPage && <CreateProjectPageSonarCloud {...props} onNextClick={nextClick} />}
      {org && !hideCreateProjectPage && <CreateProjectPage {...props} onOrganizationUpgrade={props.onOrganizationUpgrade}
          organization={org} closeCreateForm={hideProjectPage}/>}
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
    appState: getAppState(state),
    currentUser: getCurrentUser(state)
});

export default connect(mapStateToProps)(CreateProject);