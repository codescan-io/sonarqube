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

import * as React from 'react';
import { Helmet } from 'react-helmet-async';
import { LargeCenteredLayout, PageContentFontWrapper, Title } from '~design-system';
import handleRequiredAuthorization from '../../../app/utils/handleRequiredAuthorization';
import { translate } from '../../../helpers/l10n';
import { Organization } from '../../../types/types';
import { withOrganizationContext } from '../../organizations/OrganizationContext';
import { canViewAnalytics } from '../utils';
import AnalyticsDashboard from './AnalyticsDashboard';

interface Props {
  organization?: Organization;
}

export function AnalyticsApp({ organization }: Readonly<Props>) {
  const allowed = canViewAnalytics(organization);

  React.useEffect(() => {
    if (!allowed) {
      handleRequiredAuthorization();
    }
  }, [allowed]);

  if (!allowed || !organization) {
    return null;
  }

  return (
    <LargeCenteredLayout id="analytics-page">
      <PageContentFontWrapper className="sw-my-4 sw-typo-default">
        <Helmet defer={false} title={translate('analytics.page')} />
        <header className="sw-mb-6">
          <Title>{translate('analytics.page')}</Title>
        </header>
        <AnalyticsDashboard organization={organization.kee} />
      </PageContentFontWrapper>
    </LargeCenteredLayout>
  );
}

export default withOrganizationContext(AnalyticsApp);
