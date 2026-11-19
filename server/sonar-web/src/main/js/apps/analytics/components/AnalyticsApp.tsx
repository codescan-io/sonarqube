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

import {
  Button,
  DropdownMenu,
  DropdownMenuAlign,
  IconDownload,
  Text,
} from '@sonarsource/echoes-react';
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
import { REPORT_GROUPS } from '../governance';
import { ANALYTICS_PERIODS, AnalyticsPeriod, parsePeriod } from '../period';
import { ANALYTICS_TABS, AnalyticsTab, FULL_REPORT, ReportKind, hasPeriod } from '../report';
import { canViewAnalytics } from '../utils';
import AnalyticsReport, { tabContent } from './AnalyticsReport';
import ExecutiveBrief from './ExecutiveBrief';
import GovernanceDashboard from './GovernanceDashboard';
import { NO_SCAN_SELECTION, ScanSelection } from './ScanComparisonPanel';

const TAB_PARAM = 'tab';
const PERIOD_PARAM = 'period';
// Links made before the governance reports joined the dashboard keep working.
const LEGACY_TABS: Record<string, AnalyticsTab> = { reports: AnalyticsTab.Governance };

/** What each tab says about its period, next to the period picker. */
const PERIOD_HINTS: Partial<Record<AnalyticsTab, string>> = {
  [AnalyticsTab.Risk]: 'analytics.quality.main_branch_hint',
  [AnalyticsTab.Governance]: 'analytics.quality.main_branch_hint',
  [AnalyticsTab.Activity]: 'analytics.activity.compared_hint',
};

interface Props {
  organization?: Organization;
}

export function AnalyticsApp({ organization }: Readonly<Props>) {
  const allowed = canViewAnalytics(organization);
  const [searchParams, setSearchParams] = useSearchParams();
  // The tab and the period live in the URL, so a link shows the same page.
  // The summary is the landing tab: the one-page overview first, the detail one click away.
  const requested = searchParams.get(TAB_PARAM) ?? '';
  const tab =
    ANALYTICS_TABS.find((value) => value === requested) ??
    LEGACY_TABS[requested] ??
    AnalyticsTab.Summary;
  const period = parsePeriod(searchParams.get(PERIOD_PARAM));

  const setParam = (key: string, value: string | undefined) =>
    setSearchParams(
      (params) => {
        if (value === undefined) {
          params.delete(key);
        } else {
          params.set(key, value);
        }
        return params;
      },
      { replace: true },
    );
  const changeTab = (value: AnalyticsTab) =>
    setParam(TAB_PARAM, value === AnalyticsTab.Summary ? undefined : value);
  const changePeriod = (value: AnalyticsPeriod) => setParam(PERIOD_PARAM, String(value));

  const [report, setReport] = React.useState<ReportKind>();
  const closeReport = React.useCallback(() => setReport(undefined), []);
  // Kept by the page, so the comparison picked on screen is the one in the PDF.
  const [scan, setScan] = React.useState<ScanSelection>(NO_SCAN_SELECTION);

  React.useEffect(() => {
    if (!allowed) {
      handleRequiredAuthorization();
    }
  }, [allowed]);

  if (!allowed || !organization) {
    return null;
  }

  const hint = PERIOD_HINTS[tab];

  return (
    <LargeCenteredLayout id="analytics-page">
      <PageContentFontWrapper className="sw-my-4 sw-typo-default">
        <Helmet defer={false} title={translate('analytics.page')} />
        <header className="sw-mb-6 sw-flex sw-items-center sw-justify-between sw-gap-4">
          <Title className="sw-mb-0"></Title>
          {report === undefined && (
            <span className="sw-flex sw-items-center sw-gap-4">
              <ToggleButton
                onChange={changeTab}
                options={ANALYTICS_TABS.map((value) => ({
                  label: translate('analytics.tab', value),
                  value,
                }))}
                role="tablist"
                value={tab}
              />
              <DropdownMenu.Root
                align={DropdownMenuAlign.End}
                items={
                  <>
                    <DropdownMenu.ItemButton onClick={() => setReport(tab)}>
                      {translate('analytics.report.download.tab')}
                    </DropdownMenu.ItemButton>
                    <DropdownMenu.ItemButton onClick={() => setReport(FULL_REPORT)}>
                      {translate('analytics.report.download.all')}
                    </DropdownMenu.ItemButton>
                    <DropdownMenu.Separator />
                    <DropdownMenu.GroupLabel>
                      {translate('analytics.report.download.reports')}
                    </DropdownMenu.GroupLabel>
                    {REPORT_GROUPS.map((group) => (
                      <DropdownMenu.ItemButton key={group} onClick={() => setReport(group)}>
                        {translate('analytics.governance.group', group)}
                      </DropdownMenu.ItemButton>
                    ))}
                  </>
                }
              >
                <Button prefix={<IconDownload />}>{translate('analytics.report.download')}</Button>
              </DropdownMenu.Root>
            </span>
          )}
        </header>
        {report !== undefined ? (
          <AnalyticsReport
            kind={report}
            onDone={closeReport}
            organization={organization.kee}
            organizationName={organization.name}
            period={period}
            scan={scan}
          />
        ) : (
          <div aria-labelledby={getTabId(tab)} id={getTabPanelId(tab)} role="tabpanel">
            {hasPeriod(tab) && (
              <div className="sw-flex sw-items-center sw-gap-4 sw-mb-6">
                <ToggleButton
                  label={translate('analytics.activity.period')}
                  onChange={changePeriod}
                  options={ANALYTICS_PERIODS.map((days) => ({
                    label: translate('analytics.quality.period', String(days)),
                    value: days,
                  }))}
                  value={period}
                />
                {hint && <Text isSubdued>{translate(hint)}</Text>}
              </div>
            )}
            {tab === AnalyticsTab.Summary && (
              <ExecutiveBrief onOpen={changeTab} organization={organization.kee} period={period} />
            )}
            {tab === AnalyticsTab.Governance && (
              <GovernanceDashboard
                onScanChange={setScan}
                organization={organization.kee}
                period={period}
                scan={scan}
              />
            )}
            {tab !== AnalyticsTab.Summary &&
              tab !== AnalyticsTab.Governance &&
              tabContent(tab, organization.kee, period)}
          </div>
        )}
      </PageContentFontWrapper>
    </LargeCenteredLayout>
  );
}

export default withOrganizationContext(AnalyticsApp);
