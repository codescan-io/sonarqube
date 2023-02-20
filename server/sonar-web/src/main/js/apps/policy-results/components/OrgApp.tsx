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
import { differenceBy} from 'lodash';
import { Router, withRouter } from '../../../components/hoc/withRouter';
import { searchProjects } from '../../../api/components';
import { getComponentData } from '../../../api/components';
import { getComponentNavigation } from '../../../api/nav';
import { getBranches, getPullRequests } from '../../../api/branches';

import { getAnalysisStatus, getTasksForComponent } from '../../../api/ce';
import { getValues } from '../../../api/settings';
import {
    fetchOrganization as fetchOrganizationOA,
    registerBranchStatus as registerBranchStatusOA,
    requireAuthorization as requireAuthorizationOA
  } from '../../../store/rootActions'
import { BranchLike } from '../../../../js/types/branch-like';
import { Task, TaskStatuses, TaskWarning } from '../../../../js/types/tasks';
import {
    getBranchLikeQuery,
    isBranch,
    isMainBranch,
    isPullRequest
  } from '../../../helpers/branch-like';

import { Location } from '../../../components/hoc/withRouter';
import BranchOverview from '../../overview/branches/BranchOverview';
import { connect } from 'react-redux';

const FETCH_STATUS_WAIT_TIME = 3000;

interface Props {
  component: T.Component;
  router: Pick<Router, 'replace'>;
  location: Pick<Location, 'query' | 'pathname'>;
  fetchOrganizationOA: (organization: string) => void;
  requireAuthorizationOA: (router: Pick<Router, 'replace'>) => void;
  registerBranchStatusOA: (branchLike: BranchLike, component: string, status: T.Status) => void;
}

interface State {
  projects : any;
  loadingProjects:boolean;
  selectedOption:string;
  loading:boolean;
  branchLike: BranchLike | undefined;
  branchLikes: BranchLike[];
  component: T.Component | undefined;
  comparisonBranchesEnabled:boolean;
  currentTask?: Task;
  isPending: boolean;
  tasksInProgress?: Task[];
  warnings: TaskWarning[];
}

export class OrgApp extends React.PureComponent<Props, State> {
    watchStatusTimer?: number;
  mounted = false;
  orgId = "";
  state : State = {
      projects:[],
      loadingProjects:false,
      selectedOption: "none",
      loading:true,
      branchLikes: [],
      comparisonBranchesEnabled:false,
      isPending: false,
      warnings:[],
      branchLike:undefined,
      component:undefined
    }

  componentDidMount() {
    this.mounted = true;
    const pathName = window.location.pathname;
    this.orgId = pathName.replace("/organizations/","").replace("/policy-results","");
    this.setState({loadingProjects:true})
    searchProjects({filter: 'tags=policy', organization: this.orgId}).then((res) =>{
        this.setState({projects:res.components})
        if(res.components.length>0){
            this.setState({selectedOption:res.components[0].key})
            setTimeout(()=>{
              this.fetchComponent();
            },100)
            
        
        }
        this.setState({loadingProjects:false})
    })
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  handleChange = ({target} : any) => {
    this.setState({selectedOption:target.value});
    this.setState({component: undefined});
    this.setState({branchLike: undefined});
    this.setState({branchLikes: []});
    this.setState({loading:true});
    setTimeout(()=>{
      this.fetchComponent();
    },100)
    
}

addQualifier = (component: T.Component) => ({
    ...component,
    qualifier: component.breadcrumbs[component.breadcrumbs.length - 1].qualifier
  });

fetchComponent() {
    // const { branch, selectedOption: key, pullRequest } = this.state;
    const key = this.state.selectedOption;
    console.log("key ::" + key);
    const branch = undefined;
    const pullRequest = undefined
    
    this.setState({ loading: true });

    const onError = (response?: Response) => {
      if (this.mounted) {
        if (response && response.status === 403) {
          this.props.requireAuthorizationOA(this.props.router);
        } else {
          this.setState({ component: undefined, loading: false });
        }
      }
    };

    Promise.all([
      getComponentNavigation({ component: key, branch, pullRequest }),
      getComponentData({ component: key, branch, pullRequest })
    ])
      .then(([nav, { component }]) => {
        const componentWithQualifier = this.addQualifier({ ...nav, ...component });

        this.props.fetchOrganizationOA(component.organization);

        this.fetchProjectProperties(component);

        // /*
        //  * There used to be a redirect from /dashboard to /portfolio which caused issues.
        //  * Links should be fixed to not rely on this redirect, but:
        //  * This is a fail-safe in case there are still some faulty links remaining.
        //  */
        // if (
        //   this.props.location.pathname.match('dashboard') &&
        //   isPortfolioLike(componentWithQualifier.qualifier)
        // ) {
        //   this.props.router.replace(getPortfolioUrl(component.key));
        // }

        return componentWithQualifier;
      }, onError)
      .then(this.fetchBranches)
      .then(
        ({ branchLike, branchLikes, component }) => {
          if (this.mounted) {
            this.fetchStatus(component);
            this.fetchWarnings(component, branchLike);
            this.setState({
              branchLike,
              branchLikes,
              component,
              loading: false
            });
          }
        },
        () => {}
      );
  }

  fetchBranches = (
    component: T.Component
  ): Promise<{
    branchLike?: BranchLike;
    branchLikes: BranchLike[];
    component: T.Component;
  }> => {
    const breadcrumb = component.breadcrumbs.find(({ qualifier }) => {
      return ['APP', 'TRK'].includes(qualifier);
    });

    if (breadcrumb) {
      const { key } = breadcrumb;
      return Promise.all([
        getBranches(key),
        breadcrumb.qualifier === 'APP' ? Promise.resolve([]) : getPullRequests(key)
      ]).then(([branches, pullRequests]) => {
        const branchLikes = [...branches, ...pullRequests];
        const branchLike = this.getCurrentBranchLike(branchLikes);
        
        this.registerBranchStatuses(branchLikes, component);
        return { branchLike, branchLikes, component };
      });
    } else {
      return Promise.resolve({ branchLikes: [], component });
    }
  };

  fetchProjectProperties = (
    component: T.Component
  ): void => {
    getValues({ keys: 'codescan.comparison.branches', component: component.key }).then(settings => {
      this.setState({ comparisonBranchesEnabled: settings[0].value === "true" });
    });
  };

  fetchStatus = (component: T.Component) => {
    getTasksForComponent(component.key).then(
      ({ current, queue }) => {
        if (this.mounted) {
          let shouldFetchComponent = false;
          this.setState(
            ({ branchLike, component, currentTask, tasksInProgress }) => {
              const newCurrentTask = this.getCurrentTask(current, branchLike);
              const pendingTasks = this.getPendingTasks(queue, branchLike);
              const newTasksInProgress = pendingTasks.filter(
                task => task.status === TaskStatuses.InProgress
              );

              const currentTaskChanged =
                (!currentTask && newCurrentTask) ||
                (currentTask && newCurrentTask && currentTask.id !== newCurrentTask.id);
              const progressChanged =
                tasksInProgress &&
                (newTasksInProgress.length !== tasksInProgress.length ||
                  differenceBy(newTasksInProgress, tasksInProgress, 'id').length > 0);

              shouldFetchComponent = Boolean(currentTaskChanged || progressChanged);
              if (
                !shouldFetchComponent &&
                component &&
                (newTasksInProgress.length > 0 || !component.analysisDate)
              ) {
                // Refresh the status as long as there is tasks in progress or no analysis
                window.clearTimeout(this.watchStatusTimer);
                this.watchStatusTimer = window.setTimeout(
                  () => this.fetchStatus(component),
                  FETCH_STATUS_WAIT_TIME
                );
              }

              const isPending = pendingTasks.some(task => task.status === TaskStatuses.Pending);
              return {
                currentTask: newCurrentTask,
                isPending,
                tasksInProgress: newTasksInProgress
              };
            },
            () => {
              if (shouldFetchComponent) {
                this.fetchComponent();
              }
            }
          );
        }
      },
      () => {}
    );
  };

  fetchWarnings = (component: T.Component, branchLike?: BranchLike) => {
    if (component.qualifier === 'TRK') {
      getAnalysisStatus({
        component: component.key,
        ...getBranchLikeQuery(branchLike)
      }).then(
        ({ component }) => {
          this.setState({ warnings: component.warnings });
        },
        () => {}
      );
    }
  };

  getCurrentBranchLike = (branchLikes: BranchLike[]) => {
    const { query } = this.props.location;
    return query.pullRequest
      ? branchLikes.find(b => isPullRequest(b) && b.key === query.pullRequest)
      : branchLikes.find(b => isBranch(b) && (query.branch ? b.name === query.branch : b.isMain));
  };

  getCurrentTask = (current: Task, branchLike?: BranchLike) => {
    if (!current) {
      return undefined;
    }

    return current.status === TaskStatuses.Failed || this.isSameBranch(current, branchLike)
      ? current
      : undefined;
  };

  getPendingTasks = (pendingTasks: Task[], branchLike?: BranchLike) => {
    return pendingTasks.filter(task => this.isSameBranch(task, branchLike));
  };

  isSameBranch = (task: Pick<Task, 'branch' | 'pullRequest'>, branchLike?: BranchLike) => {
    if (branchLike) {
      if (isMainBranch(branchLike)) {
        return (!task.pullRequest && !task.branch) || branchLike.name === task.branch;
      }
      if (isPullRequest(branchLike)) {
        return branchLike.key === task.pullRequest;
      }
      if (isBranch(branchLike)) {
        return branchLike.name === task.branch;
      }
    }
    return !task.branch && !task.pullRequest;
  };

  registerBranchStatuses = (branchLikes: BranchLike[], component: T.Component) => {
    branchLikes.forEach(branchLike => {
      if (branchLike.status) {
        this.props.registerBranchStatusOA(
          branchLike,
          component.key,
          branchLike.status.qualityGateStatus
        );
      }
    });
  };

  render() {
    const {loading, loadingProjects, projects, selectedOption, branchLike, component} = this.state;
    return <> 
        <div className="page page-limited" style={{paddingBottom: "0"}}>
            <header className='page-header'>
                <h1>Policy Results</h1>
            </header>
            {loadingProjects ? (<><i className='loader'></i></>) : (<>
                <div className="display-flex-row">
                    <div className="width-25 big-spacer-right">
                            {
                                projects.length == 0 ? (<><span> No projects found in the organization with "policy" tag </span> </>) : ( <>
                                    <span>Select Project: </span>
                                    <br/>
                                    <select style={{maxWidth:"100%"}}  
                                    value={selectedOption}
                                    onChange={this.handleChange}>
                                        {projects.map(({ key, name }, index) => <option key={key} value={key}>{name}</option>)}
                                    </select>
                                </>)
                            }
                            
                        </div>
                    </div>
            </>)}
            {
                loading ? (<><br/>Loading Info... </>):(
                  <div style= {{marginLeft: "-20px"}} >
                    <BranchOverview branch={branchLike} component={component} grc={true}/>
                  </div>
                )
            }
        </div>
    </>

  }
}


const mapDispatchToProps = { fetchOrganizationOA, registerBranchStatusOA, requireAuthorizationOA };

export default withRouter(connect(null, mapDispatchToProps)(OrgApp));

