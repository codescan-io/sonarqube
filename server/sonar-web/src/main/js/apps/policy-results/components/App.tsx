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
import { lazyLoadComponent } from 'sonar-ui-common/components/lazyLoadComponent';
import Suggestions from '../../../app/components/embed-docs-modal/Suggestions';
import { Router, withRouter } from '../../../components/hoc/withRouter';
import { isPullRequest } from '../../../helpers/branch-like';
import { BranchLike } from '../../../types/branch-like';
import { isPortfolioLike } from '../../../types/component';
import BranchOverview from '../../overview/branches/BranchOverview';
import { fetchProjects } from '../../projects/utils';


const EmptyOverview = lazyLoadComponent(() => import('../../overview/components/EmptyOverview'));
const PullRequestOverview = lazyLoadComponent(() => import('../../overview/pullRequests/PullRequestOverview'));

interface Props {
  branchLike?: BranchLike;
  branchLikes: BranchLike[];
  component: T.Component;
  isInProgress?: boolean;
  isPending?: boolean;
  router: Pick<Router, 'replace'>;
}

interface State {
  projects : any;
  loading:boolean;
  selectedOption:string
}

export class App extends React.PureComponent<Props, State> {

  mounted = false;
  state : State = {
      projects:[],
      loading:false,
      selectedOption:"none"
  }

  componentDidMount() {
    this.mounted = true;
    this.setState({loading:true})
    fetchProjects({},false,undefined,1).then((res) =>{
        this.setState({projects:res.projects})
        this.setState({loading:false})
    })
    const params = new Proxy(new URLSearchParams(window.location.search), {
      get: (searchParams, prop) => searchParams.get(prop),
    });
    this.setState({selectedOption:params.id});
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  isPortfolio = () => {
    return isPortfolioLike(this.props.component.qualifier);
  };

  handleChange = ({target} : any) => {
    this.setState({selectedOption:target.value});
    window.location.href = window.location.search.split("=")[0]+"="+target.value;
  }

  render() {
    const { branchLike, branchLikes, component } = this.props;
    const {loading, projects, selectedOption} = this.state;
  

    if (this.isPortfolio()) {
      return null;
    }

    return isPullRequest(branchLike) ? (
      <>
        <Suggestions suggestions="pull_requests" />
        <PullRequestOverview branchLike={branchLike} component={component} grc={true} />
      </>
    ) : (
      <>
        <Suggestions suggestions="overview" />
        {loading?(<i className='loader'></i>):(
          <div className="page page-limited" style={{paddingBottom: "0"}}>
          <div className="display-flex-row">
              <div className="width-25 big-spacer-right">
                    <span>Select Project: </span>
                    <br/>
                    <select style={{maxWidth:"100%"}}  
                      value={selectedOption}
                      onChange={this.handleChange}>
                        {projects.map(({ key, name }, index) => <option key={key} value={key}>{key}</option>)}
                        <option value="none">None</option>
                        
                    </select>
                </div>
            </div>
            </div>
        )}
        {!component.analysisDate ? (
          <EmptyOverview
            branchLike={branchLike}
            branchLikes={branchLikes}
            component={component}
            hasAnalyses={this.props.isPending || this.props.isInProgress}
          />
        ) : (
          <BranchOverview branch={branchLike} component={component} grc={true}/>
        )}
      </>
    );
  }
}

export default withRouter(App);
