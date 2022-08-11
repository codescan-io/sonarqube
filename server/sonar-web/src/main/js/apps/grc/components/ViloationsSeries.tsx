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
import React from 'react';
import { parseDate } from 'sonar-ui-common/helpers/dates';
import { getDisplayedHistoryMetrics, splitSeriesInGraphs } from '../../../components/activity-graph/utils';
import { generateSeries } from '../../../components/activity-graph/utils';
import GraphsHistory from '../../../../js/components/activity-graph/GraphsHistory';
import { GraphType, MeasureHistory } from '../../../types/project-activity';
import '../grc-dashboard.css';

interface Props {
  analyses: T.ParsedAnalysis[];
  measureSeries: MeasureHistory[];
  component: T.Component,
  selectedGraphs:string,
  metrics: T.Metric[]
}



export default function ViolationsSeries(props:Props) {

  const {analyses,measureSeries,component,selectedGraphs,metrics} = props;

  const MAX_GRAPH_NB = 2;
  const MAX_SERIES_PER_GRAPH = 3;

  const graph = GraphType.custom;

  const leakPeriodDate = component.leakPeriodDate? parseDate(component.leakPeriodDate) : undefined;

  const series = generateSeries(
    measureSeries,
    graph,
    metrics,
    getDisplayedHistoryMetrics(graph, selectedGraphs.split(",")),
    true
  );

  const graphs = splitSeriesInGraphs(series, MAX_GRAPH_NB, MAX_SERIES_PER_GRAPH)

  return (
    <>
      <div className="widget hot-spot-series-cntr">
      <GraphsHistory
          analyses={analyses}
          leakPeriodDate = {leakPeriodDate}
          graph={graph}
          graphs={graphs}
          loading={false}
          measuresHistory={measureSeries}
          series={series}
        />
        </div>
    </>
  );
  }
