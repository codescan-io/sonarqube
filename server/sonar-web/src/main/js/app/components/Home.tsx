/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';

import { setHomePage,skipOnboarding } from '../../api/users';
import { AppState } from '../../types/appstate';
import { CurrentUser } from '../../types/users';
import withAppStateContext from './app-state/withAppStateContext';
import withCurrentUserContext from './current-user/withCurrentUserContext';

import "../styles/components/home.css";
import { isNonStandardUser } from '../utils/userAccess';

import { getValue } from '../../api/settings';
import MsaGate from '../../apps/sessions/components/MsaGate';
const MSA_TOGGLE_KEY = 'codescan.cloud.msaConsent.displayMessage';
import { getEulaVerification } from '../../api/eula';

interface Props {
  appState: AppState;
  currentUser: CurrentUser
  fetchLanguages: () => Promise<void>;
  fetchMyOrganizations: () => Promise<void>;
}

interface State {
    loading:boolean;
    msaEnabled: boolean;
    msaDismissed: boolean;
    msaVerify: boolean;
    msaLoading:boolean;
}

class Home extends React.PureComponent<Props, State> {
    mounted = false;
    state : State = {
        loading:false,
        msaEnabled: false,
        msaDismissed: false,
        msaVerify: false,
        msaLoading:true,
    }
   async componentDidMount() {
      this.mounted = true;
      try {
        const setting = await getValue({ key: MSA_TOGGLE_KEY });
        if (this.mounted) {
          this.setState({ msaEnabled: setting?.value === 'true' });
        }
      } catch {
        if (this.mounted) {
          this.setState({ msaEnabled: false });
        }
      }

      try {
        const value = await getEulaVerification();
        if (this.mounted) {
          this.setState({ msaVerify: Boolean(value) });
        }
      } catch (error) {
        if (this.mounted) {
          this.setState({ msaVerify: false });
        }
      }
      finally {
        if (this.mounted) {
          this.setState({ msaLoading: false });
        }
      }
  }

    componentWillUnmount() {
        this.mounted = false;
    }

    handleProjectsClick = async() => {
        const url = "projects";
        const type: any = {type:"PROJECTS"}

        await setHomePage(type);
        await skipOnboarding();

        if (isNonStandardUser(this.props.currentUser))
            window.location.href = '/account';
        else
            window.location.href = window.location.href.replace("home", url);
    }

    handlePolicyClick = async() => { 
        const defaultOrg = (this.props.currentUser as any).orgGroups[0].organizationKey;
        const type: any = {type:"POLICY_RESULTS", organization: defaultOrg}
        
        await setHomePage(type);
        await skipOnboarding();
        
        const url = "organizations/"+defaultOrg+"/policy-results";
        window.location.href = window.location.href.replace("home",url);  
    }

    render() {
        const {loading,msaEnabled,msaDismissed,msaVerify,msaLoading} = this.state;
        const isFirstLogin = !this.props.currentUser.onboarded;
        const shouldShowMsa = !msaLoading && msaEnabled && isFirstLogin && !msaDismissed && msaVerify===false;
        return (
          <MsaGate
                  enabled={shouldShowMsa}
                  onDismiss={() => this.setState({ msaDismissed: true })}
                >
            <div className="landing">
                <div className="home">
                    <img className="light-emblem" src='/images/grc/CodeScanShieldEmblem.svg' alt="" />
                    <h1>Welcome to CodeScan</h1>
                    {
                        loading?(<div className="welcome-block"><i className="spinner"></i></div>):(
                            <div className="welcome-block">
                        <div className="block" style={{ marginRight: "20px" }}>
                            <span onClick={this.handleProjectsClick} className="icon-card">
                                <img className="grc-icon" src='/images/grc/codescan-dashboard.svg' alt="" /><br/>
                                <p>Application Security Testing</p>
                            </span >
                        </div>
                        <div className="block">
                            <span onClick={this.handlePolicyClick} className="icon-card">
                                <img className="grc-icon" src='/images/grc/orgscan-dashboard.svg' alt="" /><br/>
                                <p>Policy Management</p>
                            </span>
                        </div>        
                    </div>
                        )
                    }
                </div>
            </div>
            </MsaGate>
        );
    }
}

export default withCurrentUserContext(withAppStateContext(Home));
