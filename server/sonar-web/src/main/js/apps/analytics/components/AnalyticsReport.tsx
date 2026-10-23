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
import { downloadPdf } from '../pdf';
import { ReportContext, ReportPeriod } from '../report';
import ActivityDashboard from './ActivityDashboard';
import AnalyticsDashboard from './AnalyticsDashboard';
import ExecutiveBrief from './ExecutiveBrief';
import QualityDashboard from './QualityDashboard';
import ValueDashboard from './ValueDashboard';

interface Props {
  onDone: () => void;
  organization: string;
  organizationName: string;
  period: ReportPeriod;
}

// Everything has loaded once nothing is busy for this long.
const READY_CHECK_MS = 500;
const READY_CHECKS = 3;
// Past this, the PDF is made with whatever loaded, rather than waiting forever on a slow service.
const READY_TIMEOUT_MS = 120_000;

/**
 * Every tab, one after another and over the same period, without their controls, downloaded as one PDF once every tab
 * has loaded. The page comes back when the file is saved.
 */
export default function AnalyticsReport({
  onDone,
  organization,
  organizationName,
  period,
}: Readonly<Props>) {
  const root = React.useRef<HTMLDivElement>(null);
  const [now] = React.useState(() => new Date());
  const [ready, setReady] = React.useState(false);
  const [timedOut, setTimedOut] = React.useState(false);
  const [progress, setProgress] = React.useState<{ done: number; total: number }>();
  const [failed, setFailed] = React.useState(false);
  const [attempt, setAttempt] = React.useState(0);
  const periodLabel = translate('analytics.quality.period', String(period));

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
      fileName: `analytics-report-${organization}-${format(now, 'yyyy-MM-dd')}.pdf`,
      header: `${translate('analytics.report.title')} · ${organizationName} · ${periodLabel}`,
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
  }, [ready, attempt, onDone, organization, organizationName, periodLabel, now]);

  const sections = [
    {
      key: 'brief',
      title: translate('analytics.tab.brief'),
      content: <ExecutiveBrief defaultPeriod={period} organization={organization} />,
    },
    {
      key: 'overview',
      title: translate('analytics.tab.overview'),
      content: <AnalyticsDashboard organization={organization} />,
    },
    {
      key: 'quality',
      title: translate('analytics.tab.quality'),
      content: <QualityDashboard defaultPeriod={period} organization={organization} />,
    },
    {
      key: 'activity',
      title: translate('analytics.tab.activity'),
      content: <ActivityDashboard defaultPeriod={period} organization={organization} />,
    },
    {
      key: 'value',
      title: translate('analytics.tab.value'),
      content: <ValueDashboard defaultPeriod={period} organization={organization} />,
    },
  ];

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
            <h1 className="sw-heading-xl">{translate('analytics.report.title')}</h1>
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
