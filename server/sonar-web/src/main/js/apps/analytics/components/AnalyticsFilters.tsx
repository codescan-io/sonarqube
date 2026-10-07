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

import styled from '@emotion/styled';
import {
  Button,
  ButtonVariety,
  IconCalendar,
  IconFilter,
  IconProject,
  IconWarning,
  IconX,
  InputSize,
  Select,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { DateRangePicker, PopupZLevel, themeBorder, themeColor } from '~design-system';
import { translate } from '../../../helpers/l10n';
import {
  AnalyticsFilterState,
  AnalyticsIssueType,
  AnalyticsProject,
  AnalyticsSeverity,
  DatePreset,
  ISSUE_TYPES,
  NO_TASK,
  SEVERITIES,
} from '../utils';

interface Props {
  filters: AnalyticsFilterState;
  onChange: (changes: Partial<AnalyticsFilterState>) => void;
  onReset: () => void;
  projects: AnalyticsProject[];
}

const DATE_PRESETS = [
  DatePreset.AnyTime,
  DatePreset.Last7Days,
  DatePreset.Last30Days,
  DatePreset.Last90Days,
  DatePreset.Custom,
];

/**
 * One toolbar above everything it scopes, followed by a chip for every active filter -
 * including the ones set by clicking a chart - so each can be removed on its own.
 */
export default function AnalyticsFilters({
  filters,
  onChange,
  onReset,
  projects,
}: Readonly<Props>) {
  const projectOptions = React.useMemo(
    () => projects.map(({ key, name }) => ({ value: key, label: name })),
    [projects],
  );

  const chips: Array<{ key: string; label: string; remove: () => void }> = [];
  if (filters.datePreset !== DatePreset.AnyTime) {
    chips.push({
      key: 'date',
      label: translate('analytics.filter.date', filters.datePreset),
      remove: () => onChange({ datePreset: DatePreset.AnyTime, dateRange: {} }),
    });
  }
  if (filters.projectKey !== null) {
    chips.push({
      key: 'project',
      label: projects.find(({ key }) => key === filters.projectKey)?.name ?? filters.projectKey,
      remove: () => onChange({ projectKey: null }),
    });
  }
  if (filters.type) {
    chips.push({
      key: 'type',
      label: translate('issue.type', filters.type, 'plural'),
      remove: () => onChange({ type: undefined }),
    });
  }
  if (filters.severity) {
    chips.push({
      key: 'severity',
      label: translate('severity', filters.severity),
      remove: () => onChange({ severity: undefined }),
    });
  }
  if (filters.qualityGate) {
    chips.push({
      key: 'qualityGate',
      label: `${translate('analytics.chart.quality_gate')}: ${translate('analytics.status', filters.qualityGate)}`,
      remove: () => onChange({ qualityGate: undefined }),
    });
  }
  if (filters.taskStatus) {
    chips.push({
      key: 'taskStatus',
      label: `${translate('analytics.chart.background_tasks')}: ${
        filters.taskStatus === NO_TASK
          ? translate('analytics.chart.no_task')
          : translate('background_task.status', filters.taskStatus)
      }`,
      remove: () => onChange({ taskStatus: undefined }),
    });
  }
  if (filters.recency) {
    chips.push({
      key: 'recency',
      label: `${translate('analytics.chart.analysis_age')}: ${translate('analytics.recency', filters.recency)}`,
      remove: () => onChange({ recency: undefined }),
    });
  }

  return (
    <section className="sw-mb-6">
      <Toolbar className="sw-flex sw-flex-wrap sw-items-center sw-gap-3 sw-px-4 sw-py-3 sw-rounded-2">
        <span className="sw-flex sw-items-center sw-gap-2 sw-typo-semibold sw-mr-2">
          <IconFilter />
          {translate('analytics.filter.title')}
        </span>

        <Select
          ariaLabel={translate('analytics.filter.analysis_date')}
          className="sw-w-abs-200"
          data={DATE_PRESETS.map((preset) => ({
            value: preset,
            label: translate('analytics.filter.date', preset),
          }))}
          isNotClearable
          onChange={(preset) =>
            onChange({ datePreset: (preset as DatePreset | null) ?? DatePreset.AnyTime })
          }
          size={InputSize.Medium}
          value={filters.datePreset}
          valueIcon={<IconCalendar />}
        />

        {filters.datePreset === DatePreset.Custom && (
          <DateRangePicker
            endClearButtonLabel={translate('clear.end')}
            fromLabel={translate('start_date')}
            inputSize="small"
            onChange={(range) => onChange({ dateRange: range })}
            separatorText={translate('to_')}
            startClearButtonLabel={translate('clear.start')}
            toLabel={translate('end_date')}
            value={filters.dateRange}
            zLevel={PopupZLevel.Content}
          />
        )}

        <Select
          ariaLabel={translate('analytics.filter.project')}
          className="sw-w-abs-250"
          data={projectOptions}
          isSearchable
          onChange={(key) => onChange({ projectKey: key })}
          placeholder={translate('analytics.filter.all_projects')}
          size={InputSize.Medium}
          value={filters.projectKey}
          valueIcon={<IconProject />}
        />

        <Select
          ariaLabel={translate('analytics.filter.issue_type')}
          className="sw-w-abs-200"
          data={ISSUE_TYPES.map((type) => ({
            value: type,
            label: translate('issue.type', type, 'plural'),
          }))}
          onChange={(type) => onChange({ type: (type as AnalyticsIssueType | null) ?? undefined })}
          placeholder={translate('analytics.filter.all_types')}
          size={InputSize.Medium}
          value={filters.type ?? null}
        />

        <Select
          ariaLabel={translate('analytics.filter.severity')}
          className="sw-w-abs-200"
          data={SEVERITIES.map((severity) => ({
            value: severity,
            label: translate('severity', severity),
          }))}
          onChange={(severity) =>
            onChange({ severity: (severity as AnalyticsSeverity | null) ?? undefined })
          }
          placeholder={translate('analytics.filter.all_severities')}
          size={InputSize.Medium}
          value={filters.severity ?? null}
          valueIcon={<IconWarning />}
        />
      </Toolbar>

      {chips.length > 0 && (
        <ul className="sw-flex sw-flex-wrap sw-items-center sw-gap-2 sw-mt-3">
          {chips.map(({ key, label, remove }) => (
            <li key={key}>
              <Chip aria-label={`${translate('remove')} ${label}`} onClick={remove} type="button">
                {label}
                <IconX />
              </Chip>
            </li>
          ))}
          <li>
            <Button onClick={onReset} variety={ButtonVariety.DefaultGhost}>
              {translate('analytics.filter.clear')}
            </Button>
          </li>
        </ul>
      )}
    </section>
  );
}

const Toolbar = styled.div`
  background-color: ${themeColor('backgroundSecondary')};
  border: ${themeBorder('default')};
`;

const Chip = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--echoes-color-border-accent);
  border-radius: 999px;
  background-color: var(--echoes-color-background-accent-weak-default);
  color: var(--echoes-color-text-accent);
  cursor: pointer;
  font: var(--echoes-typography-text-small-semi-bold);

  &:hover {
    background-color: var(--echoes-color-background-accent-weak-hover);
  }
`;
