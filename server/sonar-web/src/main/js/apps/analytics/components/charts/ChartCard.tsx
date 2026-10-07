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

import { Text } from '@sonarsource/echoes-react';
import classNames from 'classnames';
import * as React from 'react';
import { Card } from '~design-system';

interface Props {
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  description?: string;
  loading?: boolean;
  title: string;
}

/**
 * Frame for one chart. While its data reloads the previous render stays in place, dimmed,
 * so the layout never jumps.
 */
export default function ChartCard(props: Readonly<Props>) {
  const { action, children, className, description, loading = false, title } = props;
  return (
    <Card className={classNames('sw-flex sw-flex-col sw-gap-4 sw-min-w-0', className)}>
      <div className="sw-flex sw-items-start sw-justify-between sw-gap-4">
        <div>
          <h3 className="sw-heading-sm">{title}</h3>
          {description && (
            <Text as="p" className="sw-mt-1" isSubdued>
              {description}
            </Text>
          )}
        </div>
        {action}
      </div>
      <div
        aria-busy={loading}
        className={classNames('sw-flex-1 sw-transition-opacity', { 'sw-opacity-50': loading })}
      >
        {children}
      </div>
    </Card>
  );
}
