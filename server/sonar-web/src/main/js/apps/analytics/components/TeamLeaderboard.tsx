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
import { Button, ButtonVariety, IconPeople, Text, TextSize } from '@sonarsource/echoes-react';
import * as React from 'react';
import { ContentCell, NumericalCell, Table, TableRow } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { TaskStatuses } from '../../../types/tasks';
import { useIsReport } from '../report';
import { formatHours } from '../roi';
import { Team, computeTeams, hasTeams } from '../teams';
import { AnalyticsProject, IssueMatrix, UNTAGGED } from '../utils';
import ChartCard from './charts/ChartCard';
import { STATUS_COLORS, tint } from './charts/colors';
import InlineBar from './charts/InlineBar';
import HealthGrade from './HealthGrade';

interface Props {
  effortMinutes: Record<string, number>;
  loading: boolean;
  matrices: Record<string, IssueMatrix>;
  now: Date;
  onSelect: (tag: string) => void;
  projects: AnalyticsProject[];
  selectedTag?: string;
  taskStatuses: Record<string, TaskStatuses>;
}

const SHOWN = 8;
const COLUMNS = ['24%', '8%', '9%', '17%', '10%', '12%', '20%'];

/**
 * Teams side by side: the projects of each tag rolled up into a health grade, quality gate pass rate, open issues,
 * security exposure and remediation cost, healthiest first. Clicking a team scopes the whole page to it.
 */
export default function TeamLeaderboard(props: Readonly<Props>) {
  const isReport = useIsReport();
  const { effortMinutes, loading, matrices, now, onSelect, projects, selectedTag, taskStatuses } =
    props;
  const [expanded, setExpanded] = React.useState(false);
  const teams = React.useMemo(
    () => computeTeams({ effortMinutes, matrices, now, projects, taskStatuses }),
    [effortMinutes, matrices, now, projects, taskStatuses],
  );

  if (!hasTeams(projects)) {
    return (
      <Hint className="sw-flex sw-items-center sw-gap-3 sw-mb-6 sw-px-4 sw-py-3 sw-rounded-2">
        <IconPeople />
        <Text>{translate('analytics.teams.no_tags')}</Text>
      </Hint>
    );
  }

  const shown = expanded || isReport ? teams : teams.slice(0, SHOWN);
  const name = (tag: string) => (tag === UNTAGGED ? translate('analytics.teams.untagged') : tag);

  return (
    <ChartCard
      className="sw-mb-6"
      description={translate('analytics.teams.description')}
      loading={loading}
      title={translate('analytics.teams.title')}
    >
      <Table
        columnCount={COLUMNS.length}
        columnWidths={COLUMNS}
        header={
          <TableRow>
            <ContentCell>{translate('analytics.teams.team')}</ContentCell>
            <NumericalCell>{translate('analytics.teams.projects')}</NumericalCell>
            <ContentCell>{translate('analytics.teams.health')}</ContentCell>
            <ContentCell>{translate('analytics.quality.pass_rate')}</ContentCell>
            <NumericalCell>{translate('analytics.quality.open_issues')}</NumericalCell>
            <NumericalCell>{translate('analytics.quality.vulnerabilities')}</NumericalCell>
            <NumericalCell>{translate('analytics.teams.remediation')}</NumericalCell>
          </TableRow>
        }
      >
        {shown.map((team) => (
          <TeamRow
            key={team.tag}
            name={name(team.tag)}
            onSelect={onSelect}
            selected={team.tag === selectedTag}
            team={team}
          />
        ))}
      </Table>
      {!isReport && teams.length > SHOWN && (
        <div className="sw-flex sw-justify-center sw-mt-3">
          <Button onClick={() => setExpanded(!expanded)} variety={ButtonVariety.DefaultGhost}>
            {expanded
              ? translate('analytics.teams.show_less')
              : translateWithParameters('analytics.teams.show_all', teams.length)}
          </Button>
        </div>
      )}
    </ChartCard>
  );
}

function TeamRow({
  name,
  onSelect,
  selected,
  team,
}: Readonly<{
  name: string;
  onSelect: (tag: string) => void;
  selected: boolean;
  team: Team;
}>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  return (
    <TableRow style={selected ? { backgroundColor: tint(STATUS_COLORS.info, 10) } : undefined}>
      <ContentCell>
        <span className="sw-flex sw-flex-col sw-min-w-0">
          <TeamButton aria-pressed={selected} onClick={() => onSelect(team.tag)} type="button">
            {name}
          </TeamButton>
          {(team.failedAnalyses > 0 || team.stale > 0) && (
            <Text isSubdued size={TextSize.Small}>
              {[
                team.failedAnalyses > 0 &&
                  translateWithParameters('analytics.teams.failed_analyses', team.failedAnalyses),
                team.stale > 0 && translateWithParameters('analytics.teams.stale', team.stale),
              ]
                .filter(Boolean)
                .join(' · ')}
            </Text>
          )}
        </span>
      </ContentCell>
      <NumericalCell>{integer(team.projects)}</NumericalCell>
      <ContentCell>
        <span className="sw-flex sw-items-center sw-gap-2">
          <HealthGrade grade={team.health.grade} score={team.health.score} />
          <Text isSubdued>{team.health.score ?? '-'}</Text>
        </span>
      </ContentCell>
      <ContentCell>
        {team.passRate === undefined ? (
          '-'
        ) : (
          <span className="sw-flex sw-items-center sw-gap-2 sw-w-full">
            <InlineBar
              color={team.passRate >= 80 ? STATUS_COLORS.success : STATUS_COLORS.warning}
              max={100}
              value={team.passRate}
            />
            <Text className="sw-whitespace-nowrap">{Math.round(team.passRate)}%</Text>
          </span>
        )}
      </ContentCell>
      <NumericalCell>{integer(team.openIssues)}</NumericalCell>
      <NumericalCell>
        <span className="sw-flex sw-flex-col">
          {integer(team.vulnerabilities)}
          {team.atRisk > 0 && (
            <small className="sw-typo-sm" style={{ color: STATUS_COLORS.danger }}>
              {translateWithParameters('analytics.teams.at_risk', team.atRisk)}
            </small>
          )}
        </span>
      </NumericalCell>
      <NumericalCell>{formatHours(team.remediationMinutes)}</NumericalCell>
    </TableRow>
  );
}

const Hint = styled.div`
  border: 1px dashed var(--echoes-color-border-weak);
  color: var(--echoes-color-text-subdued);
`;

const TeamButton = styled.button`
  padding: 0;
  border: none;
  background: none;
  color: var(--echoes-color-text-link-default, var(--echoes-color-text-accent));
  font: var(--echoes-typography-text-default-semi-bold);
  text-align: left;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;
