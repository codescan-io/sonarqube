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
import { sortBy, uniq } from 'lodash';
import * as React from 'react';
import { parseDate, toNotSoISOString } from 'sonar-ui-common/helpers/dates';
import { isDefined } from 'sonar-ui-common/helpers/types';
import { getAppState, Store } from '../../../store/rootReducer';
import { getApplicationDetails, getApplicationLeak } from '../../../api/application';
import { getMeasuresWithPeriodAndMetrics } from '../../../api/measures';
import { getProjectActivity } from '../../../api/projectActivity';
import { getApplicationQualityGate, getQualityGateProjectStatus } from '../../../api/quality-gates';
import { getAllTimeMachineData } from '../../../api/time-machine';
import {
  getActivityGraph,
  getHistoryMetrics,
  saveActivityGraph
} from '../../../components/activity-graph/utils';
import {
  getBranchLikeDisplayName,
  getBranchLikeQuery,
  isMainBranch,
  isSameBranchLike
} from '../../../helpers/branch-like';
import { enhanceConditionWithMeasure, enhanceMeasuresWithMetrics } from '../../../helpers/measures';
import {
  extractStatusConditionsFromApplicationStatusChildProject,
  extractStatusConditionsFromProjectStatus
} from '../../../helpers/qualityGates';
import { ApplicationPeriod } from '../../../types/application';
import { Branch, BranchLike } from '../../../types/branch-like';
import { ComponentQualifier } from '../../../types/component';
import { MetricKey } from '../../../types/metrics';
import { GraphType, MeasureHistory } from '../../../types/project-activity';
import { QualityGateStatus, QualityGateStatusCondition } from '../../../types/quality-gates';
import '../styles.css';
import { HISTORY_METRICS_LIST, METRICS } from '../utils';
import BranchOverviewRenderer from './BranchOverviewRenderer';
import { connect } from 'react-redux';

interface Props {
  branch?: Branch;
  component: T.Component;
  appState: Pick<T.AppState,'grc'>
}

interface State {
  analyses?: T.Analysis[];
  appLeak?: ApplicationPeriod;
  graph: GraphType;
  loadingHistory?: boolean;
  loadingStatus?: boolean;
  measures?: T.MeasureEnhanced[];
  measuresHistory?: MeasureHistory[];
  metrics?: T.Metric[];
  period?: T.Period;
  qgStatuses?: QualityGateStatus[];
}

export const BRANCH_OVERVIEW_ACTIVITY_GRAPH = 'sonar_branch_overview.graph';

// Get all history data over the past year.
const FROM_DATE = toNotSoISOString(new Date().setFullYear(new Date().getFullYear() - 1));

class BranchOverview extends React.PureComponent<Props, State> {
  mounted = false;
  state: State;

  constructor(props: Props) {
    super(props);

    const { graph } = getActivityGraph(BRANCH_OVERVIEW_ACTIVITY_GRAPH, props.component.key);
    this.state = { graph };
  }

  componentDidMount() {
    this.mounted = true;
    this.loadStatus();
    this.loadHistory();
  }

  componentDidUpdate(prevProps: Props) {
    if (
      this.props.component.key !== prevProps.component.key ||
      !isSameBranchLike(this.props.branch, prevProps.branch)
    ) {
      this.loadStatus();
      this.loadHistory();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  loadStatus = () => {
    if (this.props.component.qualifier === ComponentQualifier.Application) {
      this.loadApplicationStatus();
    } else {
      this.loadProjectStatus();
    }
  };

  loadApplicationStatus = async () => {
    const { branch, component } = this.props;
    this.setState({ loadingStatus: true });
    // Start by loading the application quality gate info, as well as the meta
    // data for the application as a whole.
    const appStatus = await getApplicationQualityGate({
      application: component.key,
      ...getBranchLikeQuery(branch)
    });
    const { measures: appMeasures, metrics, period } = await this.loadMeasuresAndMeta(
      component.key,
      branch
    );

    const appBranchName =
      (branch && !isMainBranch(branch) && getBranchLikeDisplayName(branch)) || undefined;

    const appDetails = await getApplicationDetails(component.key, appBranchName);

    // We also need to load the application leak periods separately.
    getApplicationLeak(component.key, appBranchName).then(
      leaks => {
        if (this.mounted && leaks && leaks.length) {
          const sortedLeaks = sortBy(leaks, leak => {
            return new Date(leak.date);
          });
          this.setState({
            appLeak: sortedLeaks[0]
          });
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ appLeak: undefined });
        }
      }
    );

    // We need to load the measures for each project in an application
    // individually, in order to display all QG conditions correctly. Loading
    // them at the parent application level will not get all the necessary
    // information, unfortunately, as they are aggregated.
    Promise.all(
      appStatus.projects.map(project => {
        const projectDetails = appDetails.projects.find(p => p.key === project.key);
        const projectBranchLike = projectDetails
          ? { isMain: projectDetails.isMain, name: projectDetails.branch, excludedFromPurge: false }
          : undefined;

        return this.loadMeasuresAndMeta(
          project.key,
          projectBranchLike,
          // Only load metrics that apply to failing QG conditions; we don't
          // need the others anyway.
          project.conditions.filter(c => c.status !== 'OK').map(c => c.metric)
        ).then(({ measures }) => ({
          measures,
          project,
          projectBranchLike
        }));
      })
    ).then(
      results => {
        if (this.mounted) {
          const qgStatuses = results.map(({ measures = [], project, projectBranchLike }) => {
            const { key, name, status } = project;
            const conditions = extractStatusConditionsFromApplicationStatusChildProject(project);
            const failedConditions = this.getFailedConditions(conditions, measures);

            return {
              failedConditions,
              key,
              name,
              status,
              branchLike: projectBranchLike
            };
          });

          this.setState({
            loadingStatus: false,
            measures: appMeasures,
            metrics,
            period,
            qgStatuses
          });
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ loadingStatus: false, qgStatuses: undefined });
        }
      }
    );
  };

  loadProjectStatus = async () => {
    const {
      branch,
      component: { key, name }
    } = this.props;
    this.setState({ loadingStatus: true });

    const projectStatus = await getQualityGateProjectStatus({
      projectKey: key,
      ...getBranchLikeQuery(branch)
    });

    // Get failing condition metric keys. We need measures for them as well to
    // render them.
    const metricKeys =
      projectStatus.conditions !== undefined
        ? uniq([...METRICS, ...projectStatus.conditions.map(c => c.metricKey)])
        : METRICS;

    this.loadMeasuresAndMeta(key, branch, metricKeys).then(
      ({ measures, metrics, period }) => {
        if (this.mounted && measures) {
          const { ignoredConditions, status } = projectStatus;
          const conditions = extractStatusConditionsFromProjectStatus(projectStatus);
          const failedConditions = this.getFailedConditions(conditions, measures);

          const qgStatus = {
            ignoredConditions,
            failedConditions,
            key,
            name,
            status,
            branchLike: branch
          };

          this.setState({
            loadingStatus: false,
            measures,
            metrics,
            period,
            qgStatuses: [qgStatus]
          });
        } else if (this.mounted) {
          this.setState({ loadingStatus: false, qgStatuses: undefined });
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ loadingStatus: false, qgStatuses: undefined });
        }
      }
    );
  };

  loadMeasuresAndMeta = (
    componentKey: string,
    branchLike?: BranchLike,
    metricKeys: string[] = []
  ) => {
    return getMeasuresWithPeriodAndMetrics(
      componentKey,
      metricKeys.length > 0 ? metricKeys : METRICS,
      getBranchLikeQuery(branchLike)
    ).then(({ component: { measures }, metrics, period }) => {
      return {
        measures: enhanceMeasuresWithMetrics(measures || [], metrics || []),
        metrics,
        period
      };
    });
  };

  loadHistory = () => {
    this.setState({ loadingHistory: true });

    return Promise.all([this.loadHistoryMeasures(), this.loadAnalyses()]).then(
      this.doneLoadingHistory,
      this.doneLoadingHistory
    );
  };

  loadHistoryMeasures = () => {
    const { branch, component } = this.props;
    const { graph } = this.state;

    const graphMetrics = getHistoryMetrics(graph, []);
    const metrics = uniq([...HISTORY_METRICS_LIST, ...graphMetrics]);

    return getAllTimeMachineData({
      ...getBranchLikeQuery(branch),
      from: FROM_DATE,
      component: component.key,
      metrics: metrics.join()
    }).then(
      ({ measures }) => {
        if (this.mounted) {
          this.setState({
            measuresHistory: measures.map(measure => ({
              metric: measure.metric,
              history: measure.history.map(analysis => ({
                date: parseDate(analysis.date),
                value: analysis.value
              }))
            }))
          });
        }
      },
      () => {}
    );
  };

  loadAnalyses = () => {
    const { branch } = this.props;

    return getProjectActivity({
      ...getBranchLikeQuery(branch),
      project: this.getTopLevelComponent(),
      from: FROM_DATE
    }).then(
      ({ analyses }) => {
        if (this.mounted) {
          this.setState({
            analyses
          });
        }
      },
      () => {}
    );
  };

  getFailedConditions = (
    conditions: QualityGateStatusCondition[],
    measures: T.MeasureEnhanced[]
  ) => {
    return (
      conditions
        .filter(c => c.level !== 'OK')
        // Enhance them with Metric information, which will be needed
        // to render the conditions properly.
        .map(c => enhanceConditionWithMeasure(c, measures))
        // The enhancement will return undefined if it cannot find the
        // appropriate measure. Make sure we filter them out.
        .filter(isDefined)
    );
  };

  getTopLevelComponent = () => {
    const { component } = this.props;
    let current = component.breadcrumbs.length - 1;
    while (
      current > 0 &&
      !([
        ComponentQualifier.Project,
        ComponentQualifier.Portfolio,
        ComponentQualifier.Application
      ] as string[]).includes(component.breadcrumbs[current].qualifier)
    ) {
      current--;
    }
    return component.breadcrumbs[current].key;
  };

  doneLoadingHistory = () => {
    if (this.mounted) {
      this.setState({
        loadingHistory: false
      });
    }
  };

  handleGraphChange = (graph: GraphType) => {
    const { component } = this.props;
    saveActivityGraph(BRANCH_OVERVIEW_ACTIVITY_GRAPH, component.key, graph);
    this.setState({ graph, loadingHistory: true }, () => {
      this.loadHistoryMeasures().then(this.doneLoadingHistory, this.doneLoadingHistory);
    });
  };

  render() {
    const { branch, component, appState } = this.props;
    const {
      analyses,
      appLeak,
      graph,
      loadingStatus,
      loadingHistory,
      measures,
      measuresHistory,
      metrics,
      period,
      qgStatuses
    } = this.state;
    const isGRC = appState.grc === undefined? false: appState.grc;

    const projectIsEmpty =
      loadingStatus === false &&
      (measures === undefined ||
        measures.find(measure =>
          ([MetricKey.lines, MetricKey.new_lines] as string[]).includes(measure.metric.key)
        ) === undefined);

    return (
      <BranchOverviewRenderer
        analyses={analyses}
        appLeak={appLeak}
        branch={branch}
        component={component}
        graph={graph}
        loadingHistory={loadingHistory}
        loadingStatus={loadingStatus}
        measures={measures}
        measuresHistory={measuresHistory}
        metrics={metrics}
        onGraphChange={this.handleGraphChange}
        period={period}
        projectIsEmpty={projectIsEmpty}
        qgStatuses={qgStatuses}
        grc={isGRC}
      />
    );
  }
}



const mapStateToProps = (state: Store) => ({
  appState: getAppState(state)
});

export default connect(mapStateToProps)(BranchOverview);
