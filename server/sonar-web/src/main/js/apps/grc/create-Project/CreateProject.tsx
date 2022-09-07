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
import { getOrganizationNavigation } from "../../../api/organizations";
import getStore from "../../../app/utils/getStore";
import { setGrcUi } from "../../../store/appState";

interface Props {
    appState: T.AppState | undefined;
    onNextClick: (org: string) => any;
    onOrganizationUpgrade: () => void;
    organization: T.Organization;
    currentUser: T.CurrentUser | undefined;
}

const CreateProject = (props: Props & WithRouterProps) => {
    const [org, setOrg] = useState(props.organization);
    const [hideCreateProjectPage, setHideCreateProjectPage] = useState(false);
    const [openAuthorize, setOpenAuthorize] = useState(false);
    const [hashState, setHashState] = useState();
    const [organizationKey, setOrganizationKey] = useState('');
    const [projectKey, setProjectKey] = useState('');
    const [showWaiting, setShowWaiting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [status, setStatus] = useState('preparing');
    
    const componentMounted = useRef(true);

    useEffect(() => {
      getStore().dispatch(setGrcUi(true));
        if (window.location.hash) {
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
          setShowWaiting(true);
        }
        return () => { // This code runs when component is unmounted
          getStore().dispatch(setGrcUi(false));
      }

    }, []);

    useEffect(() => {
      const { currentUser } = props;
      let key: string;
      if(currentUser?.homepage?.type === 'ORGANIZATION') {
        key = currentUser?.homepage?.organization;
      } else {
        key = currentUser?.orgGroups[0]?.organizationKey
      }
      if(!org) {
        getOrganizationNavigation(key).then((res: any) => {
          if(res) {
            res.key = key;
            setOrg(res);
          } 
        }).catch(() => {});
      }
    }, [org, props]);

    const onRefreshQueue = () => {
      const findQueues = findCiQueues({organizationId: organizationKey});
      findQueues.then( queues => {
        setStatus(queues[0].status);
          if(queues[0].status === 'done') {

            props.router.replace('/grc/dashboard?id='+projectKey);
            return;
          }
          if(queues[0].status === 'failed') {

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
      { showWaiting &&
      (<Modal
      contentLabel="modal form"
      className="modal"
      overlayClassName="modal-overlay">
      <header className="modal-head">
        <img className="emblem" src='/images/grc/CodeScanShieldEmblem.svg' alt="" />
        <h2>Your analysis has started</h2>
        <p className="status">Status: {status}</p>
      </header>
      <div className="modal-body text-align-center">
        <label htmlFor="Analysis-waiting">Please note that the analysis is in progress. Once completed, your results dashboard will open automatically.</label>
      </div>
      </Modal>
      )
    }
    {<div className="text-danger">{ errorMsg }</div>}
      { openAuthorize && (<AuthorizeForm organization={props.organization} hashState={hashState} onModified={onAuthorizeDone} closeForm={closeAuthorize}/>) }
      {org && <CreateProjectPage {...props} onOrganizationUpgrade={props.onOrganizationUpgrade}
          organization={org} />}
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
    appState: getAppState(state),
    currentUser: getCurrentUser(state)
});

export default connect(mapStateToProps)(CreateProject);