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
import * as classNames from 'classnames';
import * as React from 'react';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { connect } from 'react-redux';
import { getAppState, Store } from '../../../store/rootReducer';
import { MetricKey } from '../../../types/metrics';

interface Props {
  baseComponent?: T.ComponentMeasure;
  canBePinned?: boolean;
  metrics: string[];
  rootComponent: T.ComponentMeasure;
  appState: T.AppState | undefined;
}

const SHORT_NAME_METRICS = [
  'duplicated_lines_density',
  'new_lines',
  'new_coverage',
  'new_duplicated_lines_density'
];

function ComponentsHeader({
  baseComponent,
  canBePinned = true,
  metrics,
  rootComponent,
  appState
}: Props) {
  //TODO remove negation flag, once the grc project is assigned - TODO
  const isGRC = !(appState?.grc !== undefined ? appState.grc : false);
  const isPortfolio = ['VW', 'SVW'].includes(rootComponent.qualifier);
  let columns: string[] = [];
  if (isPortfolio) {
    columns = [
      translate('metric_domain.Releasability'),
      translate('metric_domain.Reliability'),
      translate('portfolio.metric_domain.vulnerabilities'),
      translate('portfolio.metric_domain.security_hotspots'),
      translate('metric_domain.Maintainability'),
      translate('metric', 'ncloc', 'name')
    ];
  } else {
    columns = metrics.map(metric => {
      if (metric === MetricKey.security_hotspots && isGRC) {
        return translate('grc.security_hotspots');
      } else {
        return translate('metric', metric, SHORT_NAME_METRICS.includes(metric) ? 'short_name' : 'name');
      }
    });
  }

  return (
    <thead>
      <tr className="code-components-header">
        <th className="thin nowrap" colSpan={canBePinned ? 2 : 1} />
        <th />
        {baseComponent &&
          columns.map((column, index) => (
            <th
              className={classNames('thin', {
                'code-components-cell': !isPortfolio && index > 0,
                nowrap: !isPortfolio,
                'text-center': isPortfolio && index < columns.length - 1,
                'text-right': !isPortfolio || index === columns.length - 1
              })}
              key={column}>
              {column}
            </th>
          ))}
        <th />
      </tr>
    </thead>
  );
}

const mapStateToProps = (state: Store) => ({
  appState: getAppState(state)
});

export default connect(mapStateToProps)(ComponentsHeader);
