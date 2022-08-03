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
import BoxedTabs from 'sonar-ui-common/components/controls/BoxedTabs';
import DeferredSpinner from 'sonar-ui-common/components/ui/DeferredSpinner';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { isDiffMetric } from 'sonar-ui-common/helpers/measures';
import { rawSizes } from '../../../app/theme';
import { findMeasure } from '../../../helpers/measures';
import { ApplicationPeriod } from '../../../types/application';
import { Branch } from '../../../types/branch-like';
import { ComponentQualifier } from '../../../types/component';
import { IssueType } from '../../../types/issues';
import { MetricKey } from '../../../types/metrics';
import MeasurementLabel from '../components/MeasurementLabel';
import { MeasurementType } from '../utils';
import { DrilldownMeasureValue } from './DrilldownMeasureValue';
import { LeakPeriodInfo } from './LeakPeriodInfo';
import MeasuresPanelIssueMeasureRow from './MeasuresPanelIssueMeasureRow';
import MeasuresPanelNoNewCode from './MeasuresPanelNoNewCode';
import { getAppState, Store } from '../../../store/rootReducer'; 
import { connect } from 'react-redux';

export interface MeasuresPanelProps {
  appLeak?: ApplicationPeriod;
  branch?: Branch;
  component: T.Component;
  loading?: boolean;
  measures?: T.MeasureEnhanced[];
  period?: T.Period;
  appState: T.AppState | undefined
}

export enum MeasuresPanelTabs {
  New,
  Overall
}

export function MeasuresPanel(props: MeasuresPanelProps) {
  const { appLeak, branch, component, loading, measures = [], period, appState } = props;
  // TODO Remove the negation flag once the grc project is loaded
  const isGRC =  !(appState?.grc !== undefined ? appState.grc : false);

  const hasDiffMeasures = measures.some(m => isDiffMetric(m.metric.key));
  const isApp = component.qualifier === ComponentQualifier.Application;
  const leakPeriod = isApp ? appLeak : period;

  const [tab, selectTab] = React.useState(MeasuresPanelTabs.New);

  const isNewCodeTab = tab === MeasuresPanelTabs.New;

  React.useEffect(() => {
    // Open Overall tab by default if there are no new measures.
    if (loading === false && !hasDiffMeasures && isNewCodeTab) {
      selectTab(MeasuresPanelTabs.Overall);
    }
    // In this case, we explicitly do NOT want to mark tab as a dependency, as
    // it would prevent the user from selecting it, even if it's empty.
    /* eslint-disable-next-line react-hooks/exhaustive-deps */
  }, [loading, hasDiffMeasures]);

  const newCodeLabel = isGRC ?translate('grc.new_violations') :translate('overview.new_code');
  const overallCodeLabel = isGRC ?translate('grc.existing_violations') :translate('overview.overall_code');

  const tabs = [
    {
      key: MeasuresPanelTabs.New,
      label: (
        <div className="text-left overview-measures-tab">
          <span className="text-bold">{newCodeLabel}</span>
          {leakPeriod && <LeakPeriodInfo leakPeriod={leakPeriod} />}
        </div>
      )
    },
    {
      key: MeasuresPanelTabs.Overall,
      label: (
        <div className="text-left overview-measures-tab">
          <span className="text-bold" style={{ position: 'absolute', top: 2 * rawSizes.grid }}>
            {overallCodeLabel}
          </span>
        </div>
      )
    }
  ];

  return (
    <div className="overview-panel" data-test="overview__measures-panel">
      <h2 className="overview-panel-title">{translate('overview.measures')}</h2>

      {loading ? (
        <div className="overview-panel-content overview-panel-big-padded">
          <DeferredSpinner loading={loading} />
        </div>
      ) : (
        <>
          <BoxedTabs onSelect={selectTab} selected={tab} tabs={tabs} />

          <div className="overview-panel-content flex-1 bordered">
            {!hasDiffMeasures && isNewCodeTab ? (
              <MeasuresPanelNoNewCode branch={branch} component={component} period={period} />
            ) : (
              <>
                {[
                  IssueType.Bug,
                  IssueType.Vulnerability,
                  IssueType.SecurityHotspot,
                  IssueType.CodeSmell
                ].map((type: IssueType) => (
                  <MeasuresPanelIssueMeasureRow
                    branchLike={branch}
                    component={component}
                    isNewCodeTab={isNewCodeTab}
                    key={type}
                    measures={measures}
                    type={type}
                  />
                ))}

                <div className="display-flex-row overview-measures-row">
                  {(findMeasure(measures, MetricKey.coverage) ||
                    findMeasure(measures, MetricKey.new_coverage)) && (
                    <div
                      className="overview-panel-huge-padded flex-1 bordered-right display-flex-center"
                      data-test="overview__measures-coverage">
                      <MeasurementLabel
                        branchLike={branch}
                        centered={isNewCodeTab}
                        component={component}
                        measures={measures}
                        type={MeasurementType.Coverage}
                        useDiffMetric={isNewCodeTab}
                      />

                      {tab === MeasuresPanelTabs.Overall && (
                        <div className="huge-spacer-left">
                          <DrilldownMeasureValue
                            branchLike={branch}
                            component={component}
                            measures={measures}
                            metric={MetricKey.tests}
                          />
                        </div>
                      )}
                    </div>
                  )}
                  <div className="overview-panel-huge-padded flex-1 display-flex-center">
                    <MeasurementLabel
                      branchLike={branch}
                      centered={isNewCodeTab}
                      component={component}
                      measures={measures}
                      type={MeasurementType.Duplication}
                      useDiffMetric={isNewCodeTab}
                    />

                    {tab === MeasuresPanelTabs.Overall && (
                      <div className="huge-spacer-left">
                        <DrilldownMeasureValue
                          branchLike={branch}
                          component={component}
                          measures={measures}
                          metric={MetricKey.duplicated_blocks}
                        />
                      </div>
                    )}
                  </div>
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}


const mapStateToProps = (state: Store) => ({
  appState: getAppState(state)
});

export default connect(mapStateToProps)(React.memo(MeasuresPanel));
