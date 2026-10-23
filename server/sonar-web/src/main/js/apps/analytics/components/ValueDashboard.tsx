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

import {
  IconBranch,
  IconCheckCircle,
  IconPullrequest,
  IconStar,
  Text,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { FlagMessage, Spinner, ToggleButton } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { AiValue, IdeAdoption, getAiValue, getIdeAdoption } from '../../../api/codescan-analytics';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { DEFAULT_QUALITY_PERIOD, QUALITY_PERIODS, QualityPeriod } from '../quality';
import BarList from './charts/BarList';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import SegmentedMeter from './charts/SegmentedMeter';
import EngineeringRoi from './EngineeringRoi';
import StatTile from './StatTile';

interface Props {
  /** Period shown first; the report sets it for every tab. */
  defaultPeriod?: QualityPeriod;
  organization: string;
}

/**
 * What CodeScan delivered beyond analysis: AI fixes, the credits they used, and how many members bring
 * analysis into their IDE.
 */
export default function ValueDashboard({
  defaultPeriod = DEFAULT_QUALITY_PERIOD,
  organization,
}: Readonly<Props>) {
  const [period, setPeriod] = React.useState<QualityPeriod>(defaultPeriod);
  const [ai, setAi] = React.useState<AiValue>();
  const [ide, setIde] = React.useState<IdeAdoption>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    Promise.all([getAiValue(organization, period), getIdeAdoption(organization, period)]).then(
      ([aiResult, ideResult]) => {
        if (active) {
          setAi(aiResult);
          setIde(ideResult);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [organization, period]);

  const integer = (value: number) => formatMeasure(value, MetricType.Integer);

  return (
    <>
      <div className="sw-flex sw-items-center sw-gap-4 sw-mb-4">
        <ToggleButton
          label={translate('analytics.activity.period')}
          onChange={(value) => setPeriod(value as QualityPeriod)}
          options={QUALITY_PERIODS.map((days) => ({
            label: translate('analytics.quality.period', String(days)),
            value: days,
          }))}
          value={period}
        />
      </div>

      <EngineeringRoi ai={ai} aiLoading={loading} days={period} organization={organization} />

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.value.load_error')}
        </FlagMessage>
      )}

      {ai === undefined || ide === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <div className="sw-grid sw-grid-cols-4 sw-gap-4 sw-mb-6">
            <StatTile
              color={STATUS_COLORS.success}
              footnote={translateWithParameters(
                'analytics.value.fix_jobs_footnote',
                integer(ai.fixJobs.succeeded),
                integer(ai.fixJobs.total),
              )}
              icon={<IconCheckCircle />}
              label={translate('analytics.value.issues_resolved')}
            >
              {integer(ai.fixJobs.issuesResolved)}
            </StatTile>
            <StatTile
              color={MAGNITUDE_COLOR}
              footnote={translateWithParameters(
                'analytics.value.pull_requests_footnote',
                integer(ai.fixJobs.pullRequests),
              )}
              icon={<IconPullrequest />}
              label={translate('analytics.value.merged')}
            >
              {integer(ai.fixJobs.merged)}
            </StatTile>
            <StatTile
              color={MAGNITUDE_COLOR}
              footnote={translateWithParameters(
                'analytics.value.calls_footnote',
                integer(ai.credits.reduce((sum, { calls }) => sum + calls, 0)),
              )}
              icon={<IconStar />}
              label={translate('analytics.value.credits')}
            >
              {formatMeasure(ai.totalCredits, MetricType.Float)}
            </StatTile>
            <StatTile
              color={STATUS_COLORS.info}
              footnote={
                ide.members > 0
                  ? translateWithParameters(
                      'analytics.value.ide_footnote',
                      integer(ide.members),
                      Math.round((ide.activeUsers / ide.members) * 100),
                    )
                  : undefined
              }
              icon={<IconBranch />}
              label={translate('analytics.value.ide_users')}
            >
              {integer(ide.activeUsers)}
            </StatTile>
          </div>

          <div className="sw-grid sw-grid-cols-12 sw-gap-6 sw-mb-8">
            <ChartCard
              className="sw-col-span-4"
              description={translate('analytics.value.credits_by_addon.description')}
              title={translate('analytics.value.credits_by_addon')}
            >
              {ai.credits.length > 0 ? (
                <BarList
                  items={ai.credits.map(({ addon, credits }) => ({
                    color: MAGNITUDE_COLOR,
                    key: addon,
                    label: translate('analytics.value.addon', addon),
                    value: Math.round(credits),
                  }))}
                />
              ) : (
                <Text isSubdued>{translate('analytics.value.no_ai')}</Text>
              )}
            </ChartCard>

            <ChartCard
              className="sw-col-span-4"
              description={translate('analytics.value.fix_jobs.description')}
              title={translate('analytics.value.fix_jobs')}
            >
              {ai.fixJobs.total > 0 ? (
                <SegmentedMeter
                  segments={[
                    { key: 'succeeded', color: STATUS_COLORS.success, value: ai.fixJobs.succeeded },
                    { key: 'failed', color: STATUS_COLORS.danger, value: ai.fixJobs.failed },
                    { key: 'in_progress', color: STATUS_COLORS.info, value: ai.fixJobs.inProgress },
                  ].map((segment) => ({
                    ...segment,
                    label: translate('analytics.value.fix_job', segment.key),
                  }))}
                />
              ) : (
                <Text isSubdued>{translate('analytics.value.no_ai')}</Text>
              )}
            </ChartCard>

            <ChartCard
              className="sw-col-span-4"
              description={translate('analytics.value.ide.description')}
              title={translate('analytics.value.ide')}
            >
              {ide.members > 0 && (
                <div className="sw-mb-4">
                  <SegmentedMeter
                    segments={[
                      {
                        key: 'active',
                        color: STATUS_COLORS.info,
                        label: translate('analytics.value.ide_active'),
                        value: Math.min(ide.activeUsers, ide.members),
                      },
                      {
                        key: 'inactive',
                        color: 'var(--echoes-color-border-weak)',
                        label: translate('analytics.value.ide_inactive'),
                        value: Math.max(ide.members - ide.activeUsers, 0),
                      },
                    ]}
                  />
                </div>
              )}
              {ide.byIde.length > 0 ? (
                <BarList
                  items={ide.byIde.map(({ ideType, users }) => ({
                    color: MAGNITUDE_COLOR,
                    key: ideType,
                    label: ideType,
                    value: users,
                  }))}
                />
              ) : (
                <Text isSubdued>{translate('analytics.value.no_ide')}</Text>
              )}
            </ChartCard>
          </div>
        </div>
      )}
    </>
  );
}
