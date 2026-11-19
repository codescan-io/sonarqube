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
import { Button, Text } from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import * as React from 'react';
import { FlagMessage, Spinner } from '~design-system';
import DateFormatter from '../../../components/intl/DateFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { ReportGroup } from '../governance';
import { downloadPdf } from '../pdf';
import { ANALYTICS_TABS, AnalyticsTab, FULL_REPORT, ReportContext, ReportKind } from '../report';
import ActivityDashboard from './ActivityDashboard';
import AnalyticsDashboard from './AnalyticsDashboard';
import ExecutiveBrief from './ExecutiveBrief';
import GovernanceDashboard, { AdministrationReport, ComplianceReport } from './GovernanceDashboard';
import QualityDashboard from './QualityDashboard';
import { ScanSelection } from './ScanComparisonPanel';
import ValueDashboard from './ValueDashboard';

interface Props {
  /** One tab, every tab, or one of the ready-made reports. */
  kind: ReportKind;
  onDone: () => void;
  organization: string;
  organizationName: string;
  period: number;
  /** The scan comparison to show, as picked on the page. */
  scan?: ScanSelection;
}

interface ReportSection {
  content: React.ReactNode;
  key: string;
  title: string;
}

/** The content of one tab, as on screen. */
export function tabContent(
  tab: AnalyticsTab,
  organization: string,
  period: number,
  scan?: ScanSelection,
): React.ReactNode {
  switch (tab) {
    case AnalyticsTab.Summary:
      return <ExecutiveBrief organization={organization} period={period} />;
    case AnalyticsTab.Projects:
      return <AnalyticsDashboard organization={organization} />;
    case AnalyticsTab.Risk:
      return <QualityDashboard organization={organization} period={period} />;
    case AnalyticsTab.Governance:
      return <GovernanceDashboard organization={organization} period={period} scan={scan} />;
    case AnalyticsTab.Activity:
      return <ActivityDashboard organization={organization} period={period} />;
    default:
      return <ValueDashboard organization={organization} period={period} />;
  }
}

function sectionsOf(
  kind: ReportKind,
  organization: string,
  period: number,
  scan?: ScanSelection,
): ReportSection[] {
  const tab = (key: AnalyticsTab) => ({
    content: tabContent(key, organization, period, scan),
    key,
    title: translate('analytics.tab', key),
  });
  const group = (key: ReportGroup, content: React.ReactNode) => ({
    content,
    key,
    title: translate('analytics.governance.group', key),
  });
  switch (kind) {
    case FULL_REPORT:
      return ANALYTICS_TABS.map(tab);
    case ReportGroup.Compliance:
      return [
        group(kind, <ComplianceReport organization={organization} period={period} scan={scan} />),
      ];
    case ReportGroup.Security:
      return [group(kind, tabContent(AnalyticsTab.Risk, organization, period))];
    case ReportGroup.Admin:
      return [group(kind, <AdministrationReport organization={organization} period={period} />)];
    default:
      return [tab(kind)];
  }
}

function titleOf(kind: ReportKind) {
  if (kind === FULL_REPORT) {
    return translate('analytics.report.title');
  }
  const name = Object.values(ReportGroup).includes(kind as ReportGroup)
    ? translate('analytics.governance.group', kind)
    : translate('analytics.tab', kind);
  return translateWithParameters('analytics.governance.report_title', name);
}

// Everything has loaded once nothing is busy for this long.
const READY_CHECK_MS = 500;
const READY_CHECKS = 3;
// Past this, the PDF is made with whatever loaded, rather than waiting forever on a slow service.
const READY_TIMEOUT_MS = 120_000;

/**
 * A tab, every tab one after another, or a ready-made report, over the period of the page and without controls,
 * downloaded as one PDF once everything has loaded. The page comes back when the file is saved.
 */
export default function AnalyticsReport({
  kind,
  onDone,
  organization,
  organizationName,
  period,
  scan,
}: Readonly<Props>) {
  const root = React.useRef<HTMLDivElement>(null);
  const [now] = React.useState(() => new Date());
  const [ready, setReady] = React.useState(false);
  const [timedOut, setTimedOut] = React.useState(false);
  const [progress, setProgress] = React.useState<{ done: number; total: number }>();
  const [failed, setFailed] = React.useState(false);
  const [attempt, setAttempt] = React.useState(0);
  const periodLabel = translate('analytics.quality.period', String(period));
  const title = titleOf(kind);
  const filePrefix = kind === FULL_REPORT ? 'analytics-report' : `${kind}-report`;

  React.useEffect(() => {
    let quiet = 0;
    const started = Date.now();
    const timer = window.setInterval(() => {
      const busy = root.current?.querySelector('[aria-busy="true"], .it__loading') !== null;
      quiet = busy ? 0 : quiet + 1;
      const late = Date.now() - started > READY_TIMEOUT_MS;
      if (quiet >= READY_CHECKS || late) {
        window.clearInterval(timer);
        setTimedOut(late && quiet < READY_CHECKS);
        setReady(true);
      }
    }, READY_CHECK_MS);
    return () => window.clearInterval(timer);
  }, []);

  React.useEffect(() => {
    const cover = root.current?.querySelector<HTMLElement>('[data-report-cover]');
    if (!ready || !root.current || !cover) {
      return undefined;
    }
    let active = true;
    setFailed(false);
    setProgress(undefined);
    downloadPdf({
      cover,
      fileName: `${filePrefix}-${organization}-${format(now, 'yyyy-MM-dd')}.pdf`,
      header: `${title} · ${organizationName} · ${periodLabel}`,
      onProgress: (done, total) => active && setProgress({ done, total }),
      pageLabel: (page, pages) => translateWithParameters('analytics.report.page', page, pages),
      sections: Array.from(
        root.current.querySelectorAll<HTMLElement>('section[data-report-section]'),
      ),
    }).then(
      () => active && onDone(),
      () => active && setFailed(true),
    );
    return () => {
      active = false;
    };
  }, [ready, attempt, onDone, organization, organizationName, periodLabel, now, title, filePrefix]);

  const sections = React.useMemo(
    () => sectionsOf(kind, organization, period, scan),
    [kind, organization, period, scan],
  );

  let status = translate('analytics.report.preparing');
  if (failed) {
    status = translate('analytics.report.failed');
  } else if (progress) {
    status = translateWithParameters('analytics.report.generating', progress.done, progress.total);
  }

  return (
    <>
      <Banner className="sw-flex sw-items-center sw-justify-between sw-gap-4 sw-mb-6 sw-px-4 sw-py-3 sw-rounded-2">
        <span className="sw-flex sw-items-center sw-gap-3">
          <Spinner loading={!failed} />
          <Text>{status}</Text>
        </span>
        <span className="sw-flex sw-items-center sw-gap-2">
          {failed && (
            <Button onClick={() => setAttempt(attempt + 1)}>
              {translate('analytics.report.retry')}
            </Button>
          )}
          <Button onClick={onDone}>{translate('analytics.report.close')}</Button>
        </span>
      </Banner>
      {timedOut && (
        <FlagMessage className="sw-mb-4" variant="warning">
          {translate('analytics.report.incomplete')}
        </FlagMessage>
      )}

      <ReportContext.Provider value>
        <ReportRoot ref={root}>
          <header className="sw-pb-4" data-report-cover>
            <h1 className="sw-heading-xl">{title}</h1>
            <Text as="p" className="sw-mt-2">
              {translateWithParameters('analytics.report.subtitle', organizationName, periodLabel)}
            </Text>
            <Text as="p" isSubdued>
              {translate('analytics.report.generated')} <DateFormatter date={now} long />
            </Text>
          </header>
          {sections.map(({ content, key, title }) => (
            <Section aria-label={title} data-report-section={key} key={key}>
              <h2 className="sw-heading-lg sw-mb-4">{title}</h2>
              {content}
            </Section>
          ))}
        </ReportRoot>
      </ReportContext.Provider>
    </>
  );
}

// A fixed width, so the PDF looks the same whatever the size of the window. Period switches and searches have nothing
// to do in a report.
const ReportRoot = styled.div`
  width: 1200px;
  background-color: #ffffff;

  [role='radiogroup'],
  input {
    display: none !important;
  }
`;

const Section = styled.section`
  padding-bottom: 32px;
`;

const Banner = styled.div`
  border: 1px solid var(--echoes-color-border-accent);
  background-color: var(--echoes-color-background-accent-weak-default);
`;
