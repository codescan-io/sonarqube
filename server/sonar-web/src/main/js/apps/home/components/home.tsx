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
import { fetchLanguages } from '../../../../js/store/rootActions';
import { fetchMyOrganizations } from '../../../apps/account/organizations/actions';
import { Link } from 'react-router';
import { getAppState, getCurrentUser } from '../../../../js/store/rootReducer';
import { Store } from '../../../../js/store/rootReducer';
import "./home.css"
import { searchProjects } from '../../../api/components';

interface StateProps {
    appState: T.AppState | undefined;
    currentUser: T.CurrentUser | undefined;
}

interface State {
    firstProjectKey : any;
    loading:boolean;
}
  

interface DispatchProps {
    fetchLanguages: () => Promise<void>;
    fetchMyOrganizations: () => Promise<void>;
  }

type Props = StateProps & DispatchProps;

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

    render() {
        const defaultOrg = this.props.appState?.defaultOrganization;

        const {loading} = this.state;
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
                            <Link to="/projects">
                                <img className="grc-icon" src='/images/grc/codescan-dashboard.svg' alt="" />
                                <p>Application Security Testing</p>
                            </Link>
                        </div>
                        <div className="block">
                            <Link to={url}>
                                <img className="grc-icon" src='/images/grc/orgscan-dashboard.svg' alt="" />
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
  

const mapDispatchToProps = ({
    fetchMyOrganizations,
    fetchLanguages
  } as any) as DispatchProps;
  
export default connect(mapStateToProps, mapDispatchToProps)(Home);
  



  