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
import { without } from 'lodash';
import { useIntl } from 'react-intl';
import { FacetBox, FacetItem } from '~design-system';
import { getCachedSeverityLabel } from '../../../helpers/severityMasking';
import { translate } from '../../../helpers/l10n';
import { Dict } from '../../../types/types';
import { Query, formatFacetStat } from '../utils';
import { FacetItemsList } from './FacetItemsList';

export const STATIC_RESOURCE_TAG = 'static-resource';
export const STATIC_RESOURCES_FACET_PROPERTY = 'staticResources';

export interface StaticResourcesFacetProps {
  configuredSeverity?: string;
  fetching: boolean;
  onChange: (changes: Partial<Query>) => void;
  onToggle: (property: string) => void;
  open: boolean;
  stats: Dict<number> | undefined;
  tags: string[];
}

export function StaticResourcesFacet(props: Readonly<StaticResourcesFacetProps>) {
  const { configuredSeverity, fetching, onToggle, open, stats = {}, tags } = props;
  const intl = useIntl();
  const active = tags.includes(STATIC_RESOURCE_TAG);
  const headerId = `facet_${STATIC_RESOURCES_FACET_PROPERTY}`;

  const severityLabel = configuredSeverity
    ? getCachedSeverityLabel(`severity.${configuredSeverity}`) ||
      translate(`severity.${configuredSeverity}`) ||
      configuredSeverity
    : undefined;

  const itemName = severityLabel
    ? intl.formatMessage(
        {
          id: 'issues.facet.static_resources.with_severity',
          defaultMessage: 'Static Resources ({severity})',
        },
        { severity: severityLabel },
      )
    : intl.formatMessage({
        id: 'issues.facet.static_resources',
        defaultMessage: 'Static Resources',
      });

  return (
    <FacetBox
      className="it__search-navigator-facet-box it__search-navigator-facet-header"
      count={active ? 1 : 0}
      data-property={STATIC_RESOURCES_FACET_PROPERTY}
      id={headerId}
      loading={fetching}
      name={intl.formatMessage({
        id: 'issues.facet.static_resources.category',
        defaultMessage: 'Static Resources',
      })}
      onClear={() =>
        props.onChange({
          tags: without(tags, STATIC_RESOURCE_TAG),
        })
      }
      onClick={() => onToggle(STATIC_RESOURCES_FACET_PROPERTY)}
      open={open}
    >
      <FacetItemsList labelledby={headerId}>
        <FacetItem
          active={active}
          name={itemName}
          onClick={() => {
            props.onChange({
              tags: active
                ? without(tags, STATIC_RESOURCE_TAG)
                : [...tags, STATIC_RESOURCE_TAG],
            });
          }}
          stat={formatFacetStat(stats[STATIC_RESOURCE_TAG]) ?? 0}
          value={STATIC_RESOURCE_TAG}
        />
      </FacetItemsList>
    </FacetBox>
  );
}
