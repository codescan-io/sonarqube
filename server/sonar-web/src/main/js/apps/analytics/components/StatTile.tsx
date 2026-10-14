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
import { Text, TextSize } from '@sonarsource/echoes-react';
import classNames from 'classnames';
import * as React from 'react';
import { Spinner, themeBorder, themeColor } from '~design-system';
import { tint } from './charts/colors';
import Delta, { DeltaProps } from './charts/Delta';
import Sparkline from './charts/Sparkline';

interface Props {
  children: React.ReactNode;
  color: string;
  /** Change against a reference period, shown as a pill next to the value. */
  delta?: DeltaProps;
  footnote?: string;
  icon: React.ReactNode;
  label: string;
  loading?: boolean;
  onClick?: () => void;
  selected?: boolean;
  /** Recent values, oldest first, drawn as a sparkline. */
  trend?: Array<number | undefined>;
}

/**
 * Headline number: label with a tinted icon badge, the value with its change, a sparkline of its recent trend,
 * and a footnote. Clickable tiles toggle a filter or open the items behind the number.
 */
export default function StatTile(props: Readonly<Props>) {
  const {
    children,
    color,
    delta,
    footnote,
    icon,
    label,
    loading = false,
    onClick,
    selected,
    trend,
  } = props;

  const content = (
    <>
      <span className="sw-flex sw-items-center sw-gap-2 sw-min-w-0">
        <Badge style={{ backgroundColor: tint(color, 15), color }}>{icon}</Badge>
        <Text className="sw-truncate" isSubdued>
          {label}
        </Text>
      </span>
      <Spinner loading={loading}>
        <span className="sw-flex sw-items-center sw-flex-wrap sw-gap-x-2 sw-gap-y-1">
          <span className="sw-heading-xl">{children}</span>
          {delta && <Delta {...delta} />}
        </span>
      </Spinner>
      {trend && <Sparkline color={color} values={trend} width={160} />}
      {footnote && (
        <Text className="sw-mt-auto" isSubdued size={TextSize.Small}>
          {footnote}
        </Text>
      )}
    </>
  );

  return onClick ? (
    <TileButton
      aria-pressed={selected}
      className={classNames('sw-cursor-pointer', { selected })}
      onClick={onClick}
      style={selected ? { borderColor: color } : undefined}
      type="button"
    >
      {content}
    </TileButton>
  ) : (
    <Tile aria-label={label} role="group">
      {content}
    </Tile>
  );
}

const Tile = styled.div`
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  min-width: 0;
  padding: 16px 20px;
  border: ${themeBorder('default')};
  border-radius: 12px;
  background-color: ${themeColor('backgroundSecondary')};
  text-align: left;
  transition:
    box-shadow 0.2s,
    border-color 0.2s,
    transform 0.2s;

  &:is(button):hover,
  &.selected {
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.08);
  }

  &:is(button):hover {
    transform: translateY(-1px);
  }

  &:is(button):focus-visible {
    outline: 2px solid var(--echoes-color-focus-default, currentColor);
    outline-offset: 2px;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    &:is(button):hover {
      transform: none;
    }
  }
`;

const TileButton = Tile.withComponent('button');

const Badge = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 1.125rem;
`;
