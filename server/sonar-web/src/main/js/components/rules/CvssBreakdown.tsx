/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
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
import { ToggleButton } from '~design-system';
import { translate } from '../../helpers/l10n';
import { CvssBreakdown as CvssBreakdownType, CvssMetrics } from '../../types/types';

interface Props {
  cvssBreakdown: CvssBreakdownType;
}

type CvssTabType = 'base' | 'temporal' | 'environmental';

interface State {
  selectedTab: CvssTabType;
}

export default class CvssBreakdown extends React.PureComponent<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      selectedTab: 'base',
    };
  }

  handleTabChange = (value: CvssTabType) => {
    this.setState({ selectedTab: value });
  };

  renderScoreSummary = () => {
    const { scores } = this.props.cvssBreakdown;
    return (
      <div className="sw-mb-6">
        <div className="sw-grid sw-grid-cols-4 sw-gap-4">
          {scores.overall !== undefined && (
            <div className="sw-flex sw-flex-col">
              <div className="sw-text-sm sw-text-muted">CVSS Score</div>
              <div className="sw-text-2xl sw-font-semibold sw-text-primary">
                {scores.overall.toFixed(1)}
              </div>
            </div>
          )}
          {scores.base !== undefined && (
            <div className="sw-flex sw-flex-col">
              <div className="sw-text-sm sw-text-muted">Base Score</div>
              <div className="sw-text-2xl sw-font-semibold sw-text-primary">
                {scores.base.toFixed(1)}
              </div>
            </div>
          )}
          {scores.temporal !== undefined && (
            <div className="sw-flex sw-flex-col">
              <div className="sw-text-sm sw-text-muted">Temporal Score</div>
              <div className="sw-text-2xl sw-font-semibold sw-text-primary">
                {scores.temporal.toFixed(1)}
              </div>
            </div>
          )}
          {scores.environmental !== undefined && (
            <div className="sw-flex sw-flex-col">
              <div className="sw-text-sm sw-text-muted">Environmental Score</div>
              <div className="sw-text-2xl sw-font-semibold sw-text-primary">
                {scores.environmental.toFixed(1)}
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  renderMetricsTable = (metrics?: CvssMetrics) => {
    if (!metrics || !metrics.metrics || metrics.metrics.length === 0) {
      return null;
    }

    return (
      <table className="sw-w-full sw-border-collapse">
        <thead>
          <tr className="sw-border-b sw-border-separator">
            <th className="sw-text-left sw-py-3 sw-px-4 sw-font-semibold">Metric Name</th>
            <th className="sw-text-left sw-py-3 sw-px-4 sw-font-semibold">Value</th>
            <th className="sw-text-left sw-py-3 sw-px-4 sw-font-semibold">Justification</th>
          </tr>
        </thead>
        <tbody>
          {metrics.metrics.map((metric, index) => (
            <tr key={index} className="sw-border-b sw-border-separator">
              <td className="sw-py-3 sw-px-4">
                <a
                  href={`#${metric.name.toLowerCase().replace(/\s+/g, '-')}`}
                  className="sw-text-primary sw-underline"
                >
                  {metric.name}
                </a>
              </td>
              <td className="sw-py-3 sw-px-4">{metric.value}</td>
              <td className="sw-py-3 sw-px-4">{metric.justification || '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  render() {
    const { cvssBreakdown } = this.props;
    const { selectedTab } = this.state;

    const tabs = [
      {
        value: 'base' as CvssTabType,
        label: translate('coding_rules.cvss_breakdown.tab.base'),
      },
      {
        value: 'temporal' as CvssTabType,
        label: translate('coding_rules.cvss_breakdown.tab.temporal'),
      },
      {
        value: 'environmental' as CvssTabType,
        label: translate('coding_rules.cvss_breakdown.tab.environmental'),
      },
    ].filter((tab) => {
      if (tab.value === 'base') return cvssBreakdown.base;
      if (tab.value === 'temporal') return cvssBreakdown.temporal;
      if (tab.value === 'environmental') return cvssBreakdown.environmental;
      return false;
    });

    const selectedMetrics =
      selectedTab === 'base'
        ? cvssBreakdown.base
        : selectedTab === 'temporal'
          ? cvssBreakdown.temporal
          : cvssBreakdown.environmental;

    return (
      <div className="sw-p-6">
        {this.renderScoreSummary()}
        {tabs.length > 1 && (
          <div className="sw-mb-6">
            <ToggleButton
              role="tablist"
              value={selectedTab}
              options={tabs}
              onChange={this.handleTabChange}
            />
          </div>
        )}
        <div className="sw-mt-4">{this.renderMetricsTable(selectedMetrics)}</div>
      </div>
    );
  }
}
