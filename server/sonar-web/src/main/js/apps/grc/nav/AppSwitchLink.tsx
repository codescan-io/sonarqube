import React from 'react';
import {withRouter} from "../../../components/hoc/withRouter";
import {getHomePageUrl} from "../../../helpers/urls";
import {WithRouterProps} from "react-router";

interface Props {
  appState: { grc: boolean };
  currentUser: T.LoggedInUser;
}

const AppSwitchLink = ({appState, currentUser, router}: Props & WithRouterProps) => {
  return (
      <li>
        <a onClick={() => {
          if (appState.grc) {
            if (currentUser.homepage) {
              const homepage = getHomePageUrl(currentUser.homepage);
              router.replace(homepage);
            } else {
              router.replace('/projects');
            }
          } else {
            router.replace('/grc/dashboard');
          }
        }}>
          {appState.grc ? 'Open Codescan' : 'Open GRC'}
        </a>
      </li>
  );
}

export default withRouter(AppSwitchLink);