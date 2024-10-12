/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import classNames from 'classnames';
import * as React from 'react';
import { injectIntl, IntlShape, WrappedComponentProps } from 'react-intl';
import Radio from '../../../components/controls/Radio';
import Select from '../../../components/controls/Select';
import Tooltip from '../../../components/controls/Tooltip';
import DateFormatter, { longFormatterOption } from '../../../components/intl/DateFormatter';
import TimeFormatter from '../../../components/intl/TimeFormatter';
import DeferredSpinner from '../../../components/ui/DeferredSpinner';
import { parseDate, toShortISO8601String } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { ParsedAnalysis } from '../../../types/project-activity';
import Events from '../../projectActivity/components/Events';
import { getAnalysesByVersionByDay } from '../../projectActivity/utils';

export interface BranchAnalysisListRendererProps {
  analyses: ParsedAnalysis[];
  handleRangeChange: ({ value }: { value: number }) => void;
  handleScroll: (e: React.SyntheticEvent<HTMLDivElement>) => void;
  loading: boolean;
  onSelectAnalysis: (analysis: ParsedAnalysis) => void;
  range: number;
  registerBadgeNode: (version: string) => (el: HTMLDivElement) => void;
  selectedAnalysisKey: string;
  shouldStick: (version: string) => boolean;
}

function renderAnalysis(args: {
  analysis: ParsedAnalysis;
  isFirst: boolean;
  onSelectAnalysis: (analysis: ParsedAnalysis) => void;
  selectedAnalysisKey: string;
  intl: IntlShape;
}) {
  const { analysis, isFirst, selectedAnalysisKey, intl } = args;
  return (
    <li
      className={classNames('branch-analysis', {
        selected: analysis.key === selectedAnalysisKey,
      })}
      data-date={parseDate(analysis.date).valueOf()}
      key={analysis.key}
    >
      <div className="branch-analysis-time spacer-right">
        <TimeFormatter date={parseDate(analysis.date)} long={false}>
          {(formattedTime) => (
            <time className="text-middle" dateTime={parseDate(analysis.date).toISOString()}>
              {formattedTime}
            </time>
          )}
        </TimeFormatter>
      </div>

      {analysis.events.length > 0 && (
        <Events analysisKey={analysis.key} events={analysis.events} isFirst={isFirst} />
      )}

      <div className="analysis-selection-button">
        <Radio
          checked={analysis.key === selectedAnalysisKey}
          ariaLabel={translateWithParameters(
            'baseline.branch_analyses.analysis_for_x',
            `${intl.formatDate(analysis.date, longFormatterOption)}, ${intl.formatTime(
              analysis.date
            )}`
          )}
          onCheck={() => {}}
          value=""
          disabled
        />
      </div>
    </li>
  );
}

function BranchAnalysisListRenderer(
  props: BranchAnalysisListRendererProps & WrappedComponentProps
) {
  const { analyses, loading, range, selectedAnalysisKey, intl } = props;

  const byVersionByDay = React.useMemo(
    () =>
      getAnalysesByVersionByDay(analyses, {
        category: '',
      }),
    [analyses]
  );

  const hasFilteredData =
    byVersionByDay.length > 1 ||
    (byVersionByDay.length === 1 && Object.keys(byVersionByDay[0].byDay).length > 0);

  const options = [
    {
      label: translate('baseline.branch_analyses.ranges.30days'),
      value: 30,
    },
    {
      label: translate('baseline.branch_analyses.ranges.allTime'),
      value: 0,
    },
  ];

  return (
    <>
      <div className="spacer-bottom">
        <label htmlFor="branch-analysis-from-input" className="spacer-right">
          {translate('baseline.analysis_from')}
        </label>
        <Select
          blurInputOnSelect
          inputId="branch-analysis-from-input"
          className="input-medium spacer-left"
          onChange={props.handleRangeChange}
          options={options}
          isSearchable={false}
          value={options.filter((o) => o.value === range)}
        />
      </div>
      <div className="branch-analysis-list-wrapper">
        <div className="bordered branch-analysis-list" onScroll={props.handleScroll}>
          <DeferredSpinner className="big-spacer-top" loading={loading} />

          {!loading && !hasFilteredData ? (
            <div className="big-spacer-top big-spacer-bottom strong">
              {translate('baseline.no_analyses')}
            </div>
          ) : (
            <ul>
              {byVersionByDay.map((version, idx) => {
                const days = Object.keys(version.byDay);
                if (days.length <= 0) {
                  return null;
                }
                return (
                  <li key={version.key || 'noversion'}>
                    {version.version && (
                      <div
                        className={classNames('branch-analysis-version-badge', {
                          first: idx === 0,
                          sticky: props.shouldStick(version.version),
                        })}
                        ref={props.registerBadgeNode(version.version)}
                      >
                        <Tooltip
                          mouseEnterDelay={0.5}
                          overlay={`${translate('version')} ${version.version}`}
                        >
                          <span className="badge">{version.version}</span>
                        </Tooltip>
                      </div>
                    )}
                    <ul className="branch-analysis-days-list">
                      {days.map((day) => (
                        <li
                          className="branch-analysis-day"
                          data-day={toShortISO8601String(Number(day))}
                          key={day}
                        >
                          <div className="branch-analysis-date">
                            <DateFormatter date={Number(day)} long />
                          </div>
                          <ul className="branch-analysis-analyses-list">
                            {version.byDay[day]?.map((analysis) =>
                              renderAnalysis({
                                analysis,
                                selectedAnalysisKey,
                                isFirst: analyses[0].key === analysis.key,
                                onSelectAnalysis: props.onSelectAnalysis,
                                intl,
                              })
                            )}
                          </ul>
                        </li>
                      ))}
                    </ul>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </>
  );
}

export default injectIntl(BranchAnalysisListRenderer);
