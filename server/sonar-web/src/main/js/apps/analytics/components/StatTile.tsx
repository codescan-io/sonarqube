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
import classNames from 'classnames';
import * as React from 'react';
import { Spinner, themeBorder, themeColor } from '~design-system';
import { tint } from './charts/colors';

interface Props {
  children: React.ReactNode;
  color: string;
  footnote?: string;
  icon: React.ReactNode;
  label: string;
  loading?: boolean;
  onClick?: () => void;
  selected?: boolean;
}

/** Headline number with a tinted icon badge. Clickable tiles toggle a filter. */
export default function StatTile(props: Readonly<Props>) {
  const { children, color, footnote, icon, label, loading = false, onClick, selected } = props;

  const content = (
    <>
      <Badge style={{ backgroundColor: tint(color, 15), color }}>{icon}</Badge>
      <span className="sw-flex sw-flex-col sw-min-w-0">
        <Text isSubdued>{label}</Text>
        <Spinner loading={loading}>
          <span className="sw-heading-xl">{children}</span>
        </Spinner>
        {footnote && <Text isSubdued>{footnote}</Text>}
      </span>
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
    <Tile>{content}</Tile>
  );
}

const Tile = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: ${themeBorder('default')};
  border-radius: 8px;
  background-color: ${themeColor('backgroundSecondary')};
  text-align: left;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;

  &:is(button):hover,
  &.selected {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
`;

const TileButton = Tile.withComponent('button');

const Badge = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  font-size: 1.5rem;
`;
