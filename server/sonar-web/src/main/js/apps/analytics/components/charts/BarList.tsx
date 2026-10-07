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
import { Text, Tooltip } from '@sonarsource/echoes-react';
import classNames from 'classnames';
import * as React from 'react';
import { themeColor } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { TRACK_COLOR } from './colors';

export interface BarListItem {
  color: string;
  icon?: React.ReactNode;
  key: string;
  label: string;
  value: number;
}

interface Props {
  items: BarListItem[];
  onSelect?: (key: string) => void;
  selectedKey?: string;
}

/**
 * Horizontal bars, one per category, each labelled with its value and share of the total.
 * When selectable, clicking a bar toggles it as a filter; the other bars fade back.
 */
export default function BarList({ items, onSelect, selectedKey }: Readonly<Props>) {
  const total = items.reduce((sum, item) => sum + item.value, 0);
  const max = Math.max(...items.map((item) => item.value), 1);

  return (
    <ul className="sw-flex sw-flex-col sw-gap-1">
      {items.map(({ color, icon, key, label, value }) => {
        const share = total > 0 ? Math.round((value / total) * 100) : 0;
        const formatted = formatMeasure(value, MetricType.Integer);
        const selected = selectedKey === key;
        const faded = selectedKey !== undefined && !selected;
        const content = (
          <span
            className={classNames(
              'sw-grid sw-grid-cols-12 sw-items-center sw-gap-3 sw-w-full sw-transition-opacity',
              { 'sw-opacity-40': faded },
            )}
          >
            <span className="sw-col-span-5 sw-flex sw-items-center sw-gap-2 sw-min-w-0">
              {icon}
              <Text className={classNames('sw-truncate', { 'sw-typo-semibold': selected })}>
                {label}
              </Text>
            </span>
            <span
              className="sw-col-span-4 sw-h-3 sw-rounded-1 sw-overflow-hidden"
              style={{ backgroundColor: TRACK_COLOR }}
            >
              <Fill style={{ backgroundColor: color, width: `${(value / max) * 100}%` }} />
            </span>
            <span className="sw-col-span-3 sw-flex sw-justify-end sw-items-baseline sw-gap-2">
              <Text className="sw-typo-semibold">{formatted}</Text>
              <Text isSubdued>{share}%</Text>
            </span>
          </span>
        );

        return (
          <li key={key}>
            <Tooltip content={`${label}: ${formatted} (${share}%)`}>
              {onSelect ? (
                <Row
                  aria-pressed={selected}
                  className={classNames({ selected })}
                  onClick={() => onSelect(key)}
                  type="button"
                >
                  {content}
                </Row>
              ) : (
                <div className="sw-px-2 sw-py-1">{content}</div>
              )}
            </Tooltip>
          </li>
        );
      })}
    </ul>
  );
}

const Fill = styled.span`
  display: block;
  height: 100%;
  border-radius: 4px;
  transition: width 0.4s ease;
`;

const Row = styled.button`
  display: flex;
  width: 100%;
  padding: 4px 8px;
  border: none;
  border-radius: 4px;
  background: none;
  cursor: pointer;
  text-align: left;

  &:hover,
  &.selected {
    background-color: ${themeColor('dropdownMenuHover')};
  }
`;
