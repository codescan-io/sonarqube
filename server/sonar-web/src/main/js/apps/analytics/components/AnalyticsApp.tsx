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
import { useSearchParams } from 'react-router-dom';
import {
  LargeCenteredLayout,
  PageContentFontWrapper,
  Title,
  ToggleButton,
  getTabId,
  getTabPanelId,
} from '~design-system';
import handleRequiredAuthorization from '../../../app/utils/handleRequiredAuthorization';
import { translate } from '../../../helpers/l10n';
import { Organization } from '../../../types/types';
import { withOrganizationContext } from '../../organizations/OrganizationContext';
import { canViewAnalytics } from '../utils';
import ActivityDashboard from './ActivityDashboard';
import AnalyticsDashboard from './AnalyticsDashboard';
import QualityDashboard from './QualityDashboard';
import ValueDashboard from './ValueDashboard';

enum AnalyticsTab {
  Overview = 'overview',
  Quality = 'quality',
  Activity = 'activity',
  Value = 'value',
}

const TABS = Object.values(AnalyticsTab);

const TAB_PARAM = 'tab';

interface Props {
  organization?: Organization;
}

export function AnalyticsApp({ organization }: Readonly<Props>) {
  const allowed = canViewAnalytics(organization);
  const [searchParams, setSearchParams] = useSearchParams();
  // The tab lives in the URL so a link to the Activity tab can be shared.
  const tab = TABS.find((value) => value === searchParams.get(TAB_PARAM)) ?? AnalyticsTab.Overview;
  const changeTab = (value: AnalyticsTab) =>
    setSearchParams(
      (params) => {
        if (value === AnalyticsTab.Overview) {
          params.delete(TAB_PARAM);
        } else {
          params.set(TAB_PARAM, value);
        }
        return params;
      },
      { replace: true },
    );

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
        <header className="sw-mb-6 sw-flex sw-items-center sw-justify-between sw-gap-4">
          <Title className="sw-mb-0">{translate('analytics.page')}</Title>
          <ToggleButton
            onChange={changeTab}
            options={[
              { label: translate('analytics.tab.overview'), value: AnalyticsTab.Overview },
              { label: translate('analytics.tab.quality'), value: AnalyticsTab.Quality },
              { label: translate('analytics.tab.activity'), value: AnalyticsTab.Activity },
              { label: translate('analytics.tab.value'), value: AnalyticsTab.Value },
            ]}
            role="tablist"
            value={tab}
          />
        </header>
        <div aria-labelledby={getTabId(tab)} id={getTabPanelId(tab)} role="tabpanel">
          {tab === AnalyticsTab.Overview && <AnalyticsDashboard organization={organization.kee} />}
          {tab === AnalyticsTab.Quality && <QualityDashboard organization={organization.kee} />}
          {tab === AnalyticsTab.Activity && <ActivityDashboard organization={organization.kee} />}
          {tab === AnalyticsTab.Value && <ValueDashboard organization={organization.kee} />}
        </div>
      </PageContentFontWrapper>
    </LargeCenteredLayout>
  );
}

export default withOrganizationContext(AnalyticsApp);
