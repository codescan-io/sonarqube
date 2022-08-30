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
import { differenceBy } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import { getBranches, getPullRequests } from '../../../api/branches';
import { getAnalysisStatus, getTasksForComponent } from '../../../api/ce';
import { getComponentData, searchProjects } from '../../../api/components';
import { getComponentNavigation } from '../../../api/nav';
import { getValues } from '../../../api/settings';
import { Location, Router, withRouter } from '../../../components/hoc/withRouter';
import {
  getBranchLikeQuery,
  isBranch,
  isMainBranch,
  isPullRequest
} from '../../../helpers/branch-like';
import { getPortfolioUrl } from '../../../helpers/urls';
import {
  fetchOrganization,
  registerBranchStatus,
  requireAuthorization
} from '../../../store/rootActions';
import { BranchLike } from '../../../types/branch-like';
import { isPortfolioLike } from '../../../types/component';
import { Task, TaskStatuses, TaskWarning } from '../../../types/tasks';
import ComponentContainerNotFound from '../../../app/components/ComponentContainerNotFound';
import { ComponentContext } from '../../../app/components/ComponentContext';
import PageUnavailableDueToIndexation from '../../../app/components/indexation/PageUnavailableDueToIndexation';
import { setGrcUi } from '../../../store/appState';
import getStore from '../../../app/utils/getStore';
import "../styles.css";
import QualifierIcon from 'sonar-ui-common/components/icons/QualifierIcon';
import GenericAvatar from 'sonar-ui-common/components/ui/GenericAvatar';
import "../grc-dashboard.css";

interface Props {
  children: React.ReactElement;
  fetchOrganization: (organization: string) => void;
  location: Pick<Location, 'query' | 'pathname'>;
  registerBranchStatus: (branchLike: BranchLike, component: string, status: T.Status) => void;
  requireAuthorization: (router: Pick<Router, 'replace'>) => void;
  router: Pick<Router, 'replace'>;
}

interface State {
  branchLike?: BranchLike;
  branchLikes: BranchLike[];
  component?: T.Component;
  currentTask?: Task;
  isPending: boolean;
  loading: boolean;
  tasksInProgress?: Task[];
  warnings: TaskWarning[];
  comparisonBranchesEnabled: boolean;
}

const FETCH_STATUS_WAIT_TIME = 3000;

export class GRCNewPage extends React.PureComponent<Props, State> {
  watchStatusTimer?: number;
  mounted = false;
  state: State = { branchLikes: [], isPending: false, loading: true, warnings: [], comparisonBranchesEnabled: false };

  componentDidMount() {
    this.mounted = true;
    getStore().dispatch(setGrcUi(true));
    if(!this.props.location.query.id){
      this.loadGRCProjects();
    }else{
      this.fetchComponent();
    }
  }

  componentDidUpdate(prevProps: Props) {
    const {pathname,query} = this.props.location;
    if (prevProps.location.query.id !== this.props.location.query.id) {
      if(!pathname.includes("create")){
        if(!query.id){
          this.loadGRCProjects();
        }else{
          this.fetchComponent();
        }
      }
    } else if(!this.props.location.query.id && !prevProps.location.query.id) {
      if(!pathname.includes("create")){
        this.loadGRCProjects();
      }
    }
  }

  componentWillUnmount() {
    this.mounted = false;
    getStore().dispatch(setGrcUi(false));
    window.clearTimeout(this.watchStatusTimer);
  }

  // This will be used only when the route is hit without any project id.
  loadGRCProjects(){
    searchProjects({filter: 'tags=grc'})
    .then(({components}) => {
      if (!components.length) {
        return Promise.reject();
      }else{
        const id = components[0].key;
       
        let pathName = this.props.location.pathname;
        if(!pathName || pathName==="/grc"){
          pathName = '/grc/dashboard';
        }
        this.props.router.replace(pathName+'?id='+id);
      }
    }).catch(() => {});
  }

  addQualifier = (component: T.Component) => ({
    ...component,
    qualifier: component.breadcrumbs[component.breadcrumbs.length - 1].qualifier
  });

  fetchComponent() {
    const { branch, id: key, pullRequest } = this.props.location.query;
    this.setState({ loading: true });

    const onError = (response?: Response) => {
      if (this.mounted) {
        if (response && response.status === 403) {
          this.props.requireAuthorization(this.props.router);
        } else {
          this.setState({ component: undefined, loading: false });
        }
      }
    };

    Promise.all([
      getComponentNavigation({ component: key, branch, pullRequest }),
      getComponentData({ component: key, branch, pullRequest })
    ]).then(([nav, { component }]) => {
        const componentWithQualifier = this.addQualifier({ ...nav, ...component });

        this.props.fetchOrganization(component.organization);

        this.fetchProjectProperties(component);

        /*
         * There used to be a redirect from /dashboard to /portfolio which caused issues.
         * Links should be fixed to not rely on this redirect, but:
         * This is a fail-safe in case there are still some faulty links remaining.
         */
        if (
          this.props.location.pathname.match('dashboard') &&
          isPortfolioLike(componentWithQualifier.qualifier)
        ) {
          this.props.router.replace(getPortfolioUrl(component.key));
        }

        return componentWithQualifier;
      }, onError)
      .then(this.fetchBranches)
      .then(
        ({ branchLike, branchLikes, component }) => {
          if (this.mounted) {
            this.setState({
              branchLike,
              branchLikes,
              component,
              loading: false
            });
            this.fetchStatus(component);
            this.fetchWarnings(component, branchLike);
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
        this.props.registerBranchStatus(
          branchLike,
          component.key,
          branchLike.status.qualityGateStatus
        );
      }
    });
  };

  handleComponentChange = (changes: Partial<T.Component>) => {
    if (this.mounted) {
      this.setState(state => {
        if (state.component) {
          const newComponent: T.Component = { ...state.component, ...changes };
          return { component: newComponent };
        }
        return null;
      });
    }
  };

  handleBranchesChange = () => {
    if (this.mounted && this.state.component) {
      this.fetchBranches(this.state.component).then(
        ({ branchLike, branchLikes }) => {
          if (this.mounted) {
            this.setState({ branchLike, branchLikes });
          }
        },
        () => {}
      );
    }
  };

  handleWarningDismiss = () => {
    const { component } = this.state;
    if (component !== undefined) {
      this.fetchWarnings(component);
    }
  };

  render() {
    const { component, loading } = this.state;
    const organization:string = component ? component.organization : "Default";
    const showGRCNav:boolean = this.props.location.pathname !== "/grc/create" && this.props.location.pathname !== "/grc";

    if (!loading && !component) {
      return <ComponentContainerNotFound />;
    }

    if (component?.needIssueSync) {
      return <PageUnavailableDueToIndexation component={component} />;
    }
    const { branchLike, branchLikes, currentTask, isPending, tasksInProgress, comparisonBranchesEnabled } = this.state;
    const isInProgress = tasksInProgress && tasksInProgress.length > 0;
    const projectName = "Project Name :: "+component?.name;
    return (
      <>
        {showGRCNav ? (
          <div className="grc-nav-cntr">
            <div className="grc-nav">
              <GenericAvatar name={organization} size={30} />
              <span className="grc-nav-details">
                <span className="org-name" title="Organization Name">
                  {organization}
                </span>
                <span className="nav-seperator">/</span>
                <span className="qualifier-icon">
                  <QualifierIcon qualifier={component?.qualifier} />
                </span>
                <span className="project-name text-ellipsis" title={projectName}>
                  {component?.name}
                </span>
              </span>
            </div>
          </div>
        ) : (
          <></>
        )}
        <div className="grc-container">
          {loading ? (
            <div className="page page-limited">
              <i className="spinner" />
            </div>
          ) : (
            <>
              <ComponentContext.Provider value={{ branchLike, component }}>
                {React.cloneElement(this.props.children, {
                  branchLike,
                  branchLikes,
                  component,
                  comparisonBranchesEnabled,
                  isInProgress,
                  isPending,
                  onBranchesChange: this.handleBranchesChange,
                  onComponentChange: this.handleComponentChange
                })}
              </ComponentContext.Provider>
            </>
          )}
        </div>
      </>
    );
  }
}

const mapDispatchToProps = { fetchOrganization, registerBranchStatus, requireAuthorization };

export default withRouter(connect(null, mapDispatchToProps)(GRCNewPage));
