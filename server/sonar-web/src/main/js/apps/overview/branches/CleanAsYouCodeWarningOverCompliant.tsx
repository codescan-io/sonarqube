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
import { DiscreetLink, Link } from 'design-system';
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import withAppStateContext, {
  WithAppStateContextProps,
} from '../../../app/components/app-state/withAppStateContext';
import { getUrlForDoc } from '../../../helpers/docs';
import { translate } from '../../../helpers/l10n';
import { getQualityGateUrl } from '../../../helpers/urls';
import { Component } from '../../../types/types';

interface Props {
  component: Pick<Component, 'key' | 'qualifier' | 'qualityGate' | 'organization'>;
}

function CleanAsYouCodeWarningOverCompliant({
  component,
  appState,
}: Props & WithAppStateContextProps) {
  const caycDrawbackUrl = getUrlForDoc(
    appState.version,
    '/user-guide/clean-as-you-code/#potential-drawbacks'
  );

  return (
    <>
      {component.qualityGate ? (
        <p className="sw-mb-4">
          <FormattedMessage
            id="overview.quality_gate.conditions.cayc_over_compliant.details_with_link"
            defaultMessage={translate(
              'overview.quality_gate.conditions.cayc_over_compliant.details_with_link'
            )}
            values={{
              link: (
                <DiscreetLink to={getQualityGateUrl(component.organization, component.qualityGate.name)}>
                  {translate('overview.quality_gate.conditions.cayc_over_compliant.warning.link')}
                </DiscreetLink>
              ),
            }}
          />
        </p>
      ) : (
        <p className="sw-mb-4">
          {translate('overview.quality_gate.conditions.cayc_over_compliant.details')}
        </p>
      )}

      <Link to="https://knowledgebase.autorabit.com/codescan/docs">
        {translate('overview.quality_gate.conditions.cayc_over_compliant.link')}
      </Link>
    </>
  );
}

export default withAppStateContext(CleanAsYouCodeWarningOverCompliant);
