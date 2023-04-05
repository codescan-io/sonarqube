/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import { connect } from 'react-redux';
import { Link } from 'react-router';
import { getAppState, getCurrentUser } from '../../../../js/store/rootReducer';
import { Store } from '../../../../js/store/rootReducer';
import "./home.css"
import { setHomePage,skipOnboarding } from '../../../store/users';

interface StateProps {
    appState: T.AppState | undefined;
    currentUser: T.CurrentUser | undefined;
}

interface State {
    firstProjectKey : any;
    loading:boolean;
}
  

interface DispatchProps {
    skipOnboarding: () => void;
    setHomePage: (homepage: T.HomePage) => void;
  }

type Props = StateProps & DispatchProps & WithRouterProps;

class Home extends React.PureComponent<Props, State> {
    mounted = false;
    state : State = {
        firstProjectKey:undefined,
        loading:false
    }

    componentDidMount() {
        this.mounted = true;
    }
    
    componentWillUnmount() {
        this.mounted = false;
    }

    handleProjectsClick = async() => {
        const url = "projects";
        const DEFAULT_HOMEPAGE: T.HomePage = { type: 'PROJECTS' };
        await this.props.skipOnboarding();
        await this.props.setHomePage(DEFAULT_HOMEPAGE);
        this.props.router.replace(url);
    }

    handlePolicyClick = async() => {
        const defaultOrg = (this.props.currentUser as any).orgGroups[0].organizationKey;
        const POLICY_HOMEPAGE: T.HomePage = {type:"POLICY_RESULTS", organization: defaultOrg}
        await this.props.skipOnboarding();
        await this.props.setHomePage(POLICY_HOMEPAGE);
        const url = "organizations/"+defaultOrg+"/policy-results";
        this.props.router.replace(url);
    }

    render() {
        const {loading} = this.state;
        const defaultOrg = (this.props.currentUser as any).orgGroups[0].organizationKey
        const url = "/organizations/"+defaultOrg+"/policy-results";
        return (
            <div className="landing">
                <div className="home">
                    <img className="light-emblem" src='/images/grc/CodeScanShieldEmblem.svg' alt="" />
                    <h1>Welcome to CodeScan</h1>
                    {
                        loading?(<div className="welcome-block"><i className="spinner"></i></div>):(
                            <div className="welcome-block">
                        <div className="block" style={{ marginRight: "20px" }}>
                            <Link onClick={this.handleProjectsClick}>
                                <img className="grc-icon" src='/images/grc/codescan-dashboard.svg' alt="" /><br/>
                                <p>Application Security Testing</p>
                            </Link >
                        </div>
                        <div className="block">
                            <Link onClick={this.handlePolicyClick}>
                                <img className="grc-icon" src='/images/grc/orgscan-dashboard.svg' alt="" /><br/>
                                <p>Policy Management</p>
                            </Link>
                        </div>        
                    </div>
                        )
                    }
                </div>
            </div>
        );
    }
}

const mapStateToProps = (state: Store): StateProps => {
    return {
        appState: getAppState(state),
        currentUser: getCurrentUser(state),
    };
};
  

const mapDispatchToProps = {
    setHomePage,
    skipOnboarding
  };
  
export default connect(mapStateToProps, mapDispatchToProps)(Home);
  



  