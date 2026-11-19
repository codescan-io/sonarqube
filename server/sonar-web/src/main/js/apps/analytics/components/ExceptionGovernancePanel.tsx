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
  IconCalendar,
  IconCheckCircle,
  IconError,
  IconSecurityFinding,
  IconWarning,
  LinkStandalone,
  Text,
  TextSize,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { ContentCell, FlagMessage, Spinner, Table, TableRow, ToggleButton } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import { GovernedException, getExceptions } from '../../../api/analytics';
import DateFormatter from '../../../components/intl/DateFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getComponentIssuesUrl } from '../../../helpers/urls';
import { exportFileName, fetchAllPages } from '../csv';
import { exceptionsTable, userLabel } from '../governance';
import { useLoaded } from '../useLoaded';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import ExportCsvButton from './ExportCsvButton';
import Pagination, { PAGE_SIZES } from './Pagination';
import StatTile from './StatTile';

const ALL = 'ACCEPTED,FALSE_POSITIVE';
const SOON_DAYS = 30;
const COLUMNS = ['24%', '18%', '12%', '16%', '20%', '10%'];

/**
 * Exception and false-positive governance: what was accepted or dismissed, by whom, why, and when it expires; and what
 * still waits for a review.
 */
export default function ExceptionGovernancePanel({
  organization,
}: Readonly<{ organization: string }>) {
  const [statuses, setStatuses] = React.useState(ALL);
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState<number>(PAGE_SIZES[0]);
  const { data, error, loading } = useLoaded(
    () => getExceptions({ organization, statuses, p: page, ps: pageSize, soonDays: SOON_DAYS }),
    [organization, statuses, page, pageSize],
  );

  const exportAll = async () => {
    const { items } = await fetchAllPages((p, ps) =>
      getExceptions({ organization, statuses, p, ps }).then((r) => ({
        items: r.exceptions,
        paging: r.paging,
      })),
    );
    return exceptionsTable(items);
  };

  if (data === undefined) {
    return error ? <LoadError /> : <Spinner loading={loading} />;
  }
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const { summary } = data;

  return (
    <section aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
      {error && <LoadError />}
      <div className="sw-grid sw-grid-cols-6 sw-gap-4 sw-mb-6">
        <StatTile
          color={MAGNITUDE_COLOR}
          icon={<IconCheckCircle />}
          label={translate('issue.issue_status.ACCEPTED')}
        >
          {integer(summary.accepted)}
        </StatTile>
        <StatTile
          color={MAGNITUDE_COLOR}
          icon={<IconCheckCircle />}
          label={translate('issue.issue_status.FALSE_POSITIVE')}
        >
          {integer(summary.falsePositive)}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.warning}
          footnote={translate('analytics.governance.exceptions.without_expiry.footnote')}
          icon={<IconCalendar />}
          label={translate('analytics.governance.exceptions.without_expiry')}
        >
          {integer(summary.withoutExpiry)}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.warning}
          icon={<IconWarning />}
          label={translateWithParameters(
            'analytics.governance.exceptions.expiring',
            summary.soonDays,
          )}
        >
          {integer(summary.expiringSoon)}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.danger}
          icon={<IconError />}
          label={translate('analytics.governance.exceptions.expired')}
        >
          {integer(summary.expired)}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.danger}
          footnote={translate('analytics.governance.exceptions.awaiting_review.footnote')}
          icon={<IconSecurityFinding />}
          label={translate('analytics.governance.exceptions.awaiting_review')}
        >
          {integer(summary.hotspotsToReview + summary.expired)}
        </StatTile>
      </div>

      <ChartCard
        action={
          <span className="sw-flex sw-items-center sw-gap-3">
            <ToggleButton
              label={translate('analytics.governance.exceptions.status')}
              onChange={(value: string) => {
                setStatuses(value);
                setPage(1);
              }}
              options={[
                { label: translate('all'), value: ALL },
                { label: translate('issue.issue_status.ACCEPTED'), value: 'ACCEPTED' },
                { label: translate('issue.issue_status.FALSE_POSITIVE'), value: 'FALSE_POSITIVE' },
              ]}
              value={statuses}
            />
            <ExportCsvButton
              fileName={exportFileName('exceptions', organization)}
              getTable={exportAll}
            />
          </span>
        }
        description={translate('analytics.governance.exceptions.description')}
        title={translate('analytics.governance.exceptions')}
      >
        {data.exceptions.length === 0 ? (
          <Text isSubdued>{translate('analytics.governance.exceptions.none')}</Text>
        ) : (
          <ExceptionsTable exceptions={data.exceptions} />
        )}
        <Pagination
          onChange={(p, ps) => {
            setPage(p);
            setPageSize(ps);
          }}
          page={page}
          pageSize={pageSize}
          total={data.paging.total}
        />
      </ChartCard>
    </section>
  );
}

function LoadError() {
  return (
    <FlagMessage className="sw-mb-4" variant="error">
      {translate('analytics.load_error')}
    </FlagMessage>
  );
}

function ExceptionsTable({ exceptions }: Readonly<{ exceptions: GovernedException[] }>) {
  return (
    <Table
      columnCount={COLUMNS.length}
      columnWidths={COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.governance.issue')}</ContentCell>
          <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
          <ContentCell>{translate('analytics.quality.exception')}</ContentCell>
          <ContentCell>{translate('analytics.governance.exceptions.marked_by')}</ContentCell>
          <ContentCell>{translate('analytics.governance.exceptions.rationale')}</ContentCell>
          <ContentCell>{translate('analytics.quality.expires')}</ContentCell>
        </TableRow>
      }
    >
      {exceptions.map((exception) => (
        <TableRow key={exception.issueKey}>
          <ContentCell>
            <span className="sw-flex sw-flex-col sw-min-w-0">
              <LinkStandalone
                to={getComponentIssuesUrl(exception.projectKey, {
                  issues: exception.issueKey,
                  open: exception.issueKey,
                })}
              >
                {exception.projectName ?? exception.projectKey}
              </LinkStandalone>
              {exception.filePath && (
                <span className="sw-truncate" title={exception.filePath}>
                  <Text isSubdued>{exception.filePath}</Text>
                </span>
              )}
            </span>
          </ContentCell>
          <ContentCell>
            <span className="sw-flex sw-flex-col">
              <span>{exception.ruleName ?? exception.rule}</span>
              {exception.severity && (
                <Text isSubdued size={TextSize.Small}>
                  {translate('severity', exception.severity)}
                  {exception.type && ` · ${translate('issue.type', exception.type)}`}
                </Text>
              )}
            </span>
          </ContentCell>
          <ContentCell>{translate('issue.issue_status', exception.issueStatus)}</ContentCell>
          <ContentCell>
            <span className="sw-flex sw-flex-col">
              <span>{exception.markedBy ? userLabel(exception.markedBy) : '-'}</span>
              {exception.markedAt && (
                <Text isSubdued size={TextSize.Small}>
                  <DateFormatter date={exception.markedAt} long={false} />
                </Text>
              )}
            </span>
          </ContentCell>
          <ContentCell>
            {exception.rationale?.text ? (
              <span className="sw-flex sw-flex-col sw-min-w-0">
                <span className="sw-line-clamp-3" title={exception.rationale.text}>
                  {exception.rationale.text}
                </span>
                <Text isSubdued size={TextSize.Small}>
                  {translate(
                    'analytics.governance.exceptions.rationale',
                    exception.rationale.source,
                  )}
                  {exception.comments > 1 &&
                    ` · ${translateWithParameters('analytics.governance.exceptions.comments', exception.comments)}`}
                </Text>
              </span>
            ) : (
              <Text isSubdued>{translate('analytics.governance.exceptions.no_rationale')}</Text>
            )}
          </ContentCell>
          <ContentCell className="sw-whitespace-nowrap">
            {exception.expired && <Text>{translate('analytics.quality.expired')}</Text>}
            {!exception.expired && exception.expiresAt && (
              <DateFormatter date={exception.expiresAt} long={false} />
            )}
            {!exception.expiresAt && (
              <Text isSubdued>{translate('analytics.governance.exceptions.never')}</Text>
            )}
          </ContentCell>
        </TableRow>
      ))}
    </Table>
  );
}
