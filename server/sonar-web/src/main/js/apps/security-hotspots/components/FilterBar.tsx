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
import HelpTooltip from 'sonar-ui-common/components/controls/HelpTooltip';
import RadioToggle from 'sonar-ui-common/components/controls/RadioToggle';
import Select from 'sonar-ui-common/components/controls/Select';
import DeferredSpinner from 'sonar-ui-common/components/ui/DeferredSpinner';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getAppState, Store } from '../../../store/rootReducer';
import { withCurrentUser } from '../../../components/hoc/withCurrentUser';
import Measure from '../../../components/measure/Measure';
import CoverageRating from '../../../components/ui/CoverageRating';
import { isLoggedIn } from '../../../helpers/users';
import { ComponentQualifier } from '../../../types/component';
import { HotspotFilters, HotspotStatusFilter } from '../../../types/security-hotspots';

export interface FilterBarProps {
  currentUser: T.CurrentUser;
  component: T.Component;
  filters: HotspotFilters;
  hotspotsReviewedMeasure?: string;
  isStaticListOfHotspots: boolean;
  loadingMeasure: boolean;
  onBranch: boolean;
  onChangeFilters: (filters: Partial<HotspotFilters>) => void;
  onShowAllHotspots: () => void;
  appState: T.AppState | undefined;
}

const statusOptions: Array<{ label: string; value: string }> = [
  { value: HotspotStatusFilter.TO_REVIEW, label: translate('hotspot.filters.status.to_review') },
  { value: HotspotStatusFilter.FIXED, label: translate('hotspot.filters.status.fixed') },
  { value: HotspotStatusFilter.SAFE, label: translate('hotspot.filters.status.safe') }
];

const periodOptions = [
  { value: true, label: translate('hotspot.filters.period.since_leak_period') },
  { value: false, label: translate('hotspot.filters.period.overall') }
];

const grcPeriodOptions = [
  { value: true, label: translate('grc.hotspot.filters.period.since_leak_period') },
  { value: false, label: translate('grc.hotspot.filters.period.overall') }
];

export enum AssigneeFilterOption {
  ALL = 'all',
  ME = 'me'
}

const assigneeFilterOptions = [
  { value: AssigneeFilterOption.ME, label: translate('hotspot.filters.assignee.assigned_to_me') },
  { value: AssigneeFilterOption.ALL, label: translate('hotspot.filters.assignee.all') }
];

export function FilterBar(props: FilterBarProps) {
  const {
    currentUser,
    component,
    filters,
    hotspotsReviewedMeasure,
    isStaticListOfHotspots,
    loadingMeasure,
    onBranch
  } = props;
  const isProject = component.qualifier === ComponentQualifier.Project;

  return (
    <div className="filter-bar-outer">
      <div className="filter-bar">
        <div className="filter-bar-inner display-flex-center">
          {isStaticListOfHotspots ? (
            <a
              id="show_all_hotspot"
              onClick={() => props.onShowAllHotspots()}
              role="link"
              tabIndex={0}>
              {translate('hotspot.filters.show_all')}
            </a>
          ) : (
            <div className="display-flex-space-between width-100">
              <div className="display-flex-center">
                <h3 className="huge-spacer-right">{translate('hotspot.filters.title')}</h3>

                {isLoggedIn(currentUser) && (
                  <RadioToggle
                    className="huge-spacer-right"
                    name="assignee-filter"
                    onCheck={(value: AssigneeFilterOption) =>
                      props.onChangeFilters({ assignedToMe: value === AssigneeFilterOption.ME })
                    }
                    options={assigneeFilterOptions}
                    value={
                      filters.assignedToMe ? AssigneeFilterOption.ME : AssigneeFilterOption.ALL
                    }
                  />
                )}

                <span className="spacer-right">{translate('status')}</span>
                <Select
                  className="input-medium big-spacer-right"
                  clearable={false}
                  onChange={(option: { value: HotspotStatusFilter }) =>
                    props.onChangeFilters({ status: option.value })
                  }
                  options={statusOptions}
                  searchable={false}
                  value={filters.status}
                />

                {onBranch && (
                  <Select
                    className="input-medium big-spacer-right"
                    clearable={false}
                    onChange={(option: { value: boolean }) =>
                      props.onChangeFilters({ sinceLeakPeriod: option.value })
                    }
                    options={props.appState?.grc ? grcPeriodOptions : periodOptions}
                    searchable={false}
                    value={filters.sinceLeakPeriod}
                  />
                )}
              </div>

              {isProject && (
                <div className="display-flex-center">
                  <span className="little-spacer-right">
                    {props.appState?.grc ? translate('grc.metric.security_hotspots_reviewed.name') : translate('metric.security_hotspots_reviewed.name')}
                  </span>
                  <HelpTooltip
                    className="big-spacer-right"
                    overlay={props.appState?.grc ? translate('grc.hotspots.reviewed.tooltip') : translate('hotspots.reviewed.tooltip')}
                  />
                  <DeferredSpinner loading={loadingMeasure}>
                    {hotspotsReviewedMeasure && <CoverageRating value={hotspotsReviewedMeasure} />}
                    <Measure
                      className="spacer-left huge"
                      metricKey={
                        onBranch && !filters.sinceLeakPeriod
                          ? 'security_hotspots_reviewed'
                          : 'new_security_hotspots_reviewed'
                      }
                      metricType="PERCENT"
                      value={hotspotsReviewedMeasure}
                    />
                  </DeferredSpinner>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
  appState: getAppState(state)
});

export default withCurrentUser(connect(mapStateToProps)(FilterBar));
