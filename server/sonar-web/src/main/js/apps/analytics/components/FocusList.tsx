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
import { Text } from '@sonarsource/echoes-react';
import * as React from 'react';
import { themeColor } from '~design-system';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { TaskStatuses } from '../../../types/tasks';
import { FocusReason, getFocusList } from '../quality';
import { AnalyticsProject, IssueMatrix } from '../utils';
import ChartCard from './charts/ChartCard';
import { STATUS_COLORS, tint } from './charts/colors';

interface Props {
  loading: boolean;
  matrices: Record<string, IssueMatrix>;
  now: Date;
  onOpen: (project: AnalyticsProject) => void;
  projects: AnalyticsProject[];
  taskStatuses: Record<string, TaskStatuses>;
}

const SERIOUS = new Set([FocusReason.QualityGateFailed, FocusReason.Vulnerabilities]);

/**
 * The projects that most need attention among the selected ones, riskiest first, each with why.
 */
export default function FocusList(props: Readonly<Props>) {
  const { loading, matrices, now, onOpen, projects, taskStatuses } = props;
  const items = React.useMemo(
    () => getFocusList(projects, matrices, taskStatuses, now),
    [projects, matrices, taskStatuses, now],
  );

  return (
    <ChartCard
      className="sw-mb-6"
      description={translate('analytics.focus.description')}
      loading={loading}
      title={translate('analytics.focus.title')}
    >
      {items.length === 0 ? (
        <Text isSubdued>{translate('analytics.focus.none')}</Text>
      ) : (
        <ol className="sw-flex sw-flex-col sw-gap-1">
          {items.map(({ project, reasons }, index) => (
            <li key={project.key}>
              <Row
                aria-label={translateWithParameters('analytics.focus.open', project.name)}
                onClick={() => onOpen(project)}
                type="button"
              >
                <Text className="sw-w-6 sw-shrink-0" isSubdued>
                  {index + 1}
                </Text>
                <Text className="sw-typo-semibold sw-truncate sw-w-abs-300 sw-shrink-0 sw-text-left">
                  {project.name}
                </Text>
                <span className="sw-flex sw-flex-wrap sw-gap-2">
                  {reasons.map(({ count, reason }) => (
                    <Chip
                      key={reason}
                      style={{
                        backgroundColor: tint(
                          SERIOUS.has(reason) ? STATUS_COLORS.danger : STATUS_COLORS.warning,
                          14,
                        ),
                      }}
                    >
                      {count === undefined
                        ? translate('analytics.focus.reason', reason)
                        : translateWithParameters(`analytics.focus.reason.${reason}`, count)}
                    </Chip>
                  ))}
                </span>
              </Row>
            </li>
          ))}
        </ol>
      )}
    </ChartCard>
  );
}

const Row = styled.button`
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px;
  border: none;
  border-radius: 4px;
  background: none;
  cursor: pointer;

  &:hover {
    background-color: ${themeColor('dropdownMenuHover')};
  }
`;

const Chip = styled.span`
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  line-height: 1.25rem;
  color: var(--echoes-color-text-default);
`;
