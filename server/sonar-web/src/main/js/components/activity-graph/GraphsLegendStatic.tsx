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

import { NewCodeLegend } from 'design-system';
import * as React from 'react';
import { translate } from '../../helpers/l10n';
import { Serie } from '../../types/project-activity';
import { GraphsLegendItem } from './GraphsLegendItem';

export interface GraphsLegendStaticProps {
  series: Array<Pick<Serie, 'name' | 'translatedName'>>;
}

export default function GraphsLegendStatic({ series }: GraphsLegendStaticProps) {
  return (
    <ul className="activity-graph-legends">
      {series.map((serie, idx) => (
        <li key={serie.name}>
          <GraphsLegendItem
            className="sw-ml-3"
            index={idx}
            metric={serie.name}
            name={serie.translatedName}
          />
        </li>
      ))}
      <li key={translate('hotspot.filters.period.since_leak_period')}>
        <NewCodeLegend
          className="sw-ml-3 big-spacer-right"
          text={translate('hotspot.filters.period.since_leak_period')}
        />
      </li>
    </ul>
  );
}
