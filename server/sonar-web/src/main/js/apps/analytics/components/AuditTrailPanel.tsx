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
  ButtonVariety,
  InputSize,
  LinkStandalone,
  Select,
  Text,
  TextSize,
} from '@sonarsource/echoes-react';
import * as React from 'react';
import { ContentCell, FlagMessage, NumericalCell, Spinner, Table, TableRow } from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  AuditChange,
  AuditRequest,
  AuditUserActivity,
  getIssueAudit,
} from '../../../api/analytics';
import DateFormatter from '../../../components/intl/DateFormatter';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getComponentIssuesUrl } from '../../../helpers/urls';
import { exportFileName, fetchAllPages } from '../csv';
import { auditTable, auditUsersTable, userLabel } from '../governance';
import { useIsReport } from '../report';
import { periodRequest, useLoaded } from '../useLoaded';
import ChartCard from './charts/ChartCard';
import ExportCsvButton from './ExportCsvButton';
import Pagination, { PAGE_SIZES } from './Pagination';

const ALL_CATEGORIES = 'STATUS,SEVERITY,COMMENT,EXCEPTION_REASON';
const CATEGORIES = ['STATUS', 'SEVERITY', 'COMMENT', 'EXCEPTION_REASON'];
const USERS_SHOWN = 10;
/** The per-user summary columns, as the audit names the actions. */
const SUMMARY_COLUMNS = [
  'accepted',
  'false_positive',
  'reopened',
  'severity_changed',
  'commented',
  'exception_reason',
];
const CHANGE_COLUMNS = ['13%', '15%', '22%', '28%', '22%'];

/** Who accepted, dismissed, reopened, reclassified or commented on which finding, and when. */
export default function AuditTrailPanel({
  organization,
  period,
}: Readonly<{ organization: string; period: number }>) {
  const isReport = useIsReport();
  const [category, setCategory] = React.useState<string | null>(null);
  const [user, setUser] = React.useState<string | null>(null);
  const [page, setPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState<number>(PAGE_SIZES[0]);

  const request = React.useMemo<AuditRequest>(() => {
    const { from, timeZone } = periodRequest(period);
    return {
      organization,
      from,
      timeZone,
      categories: category ?? ALL_CATEGORIES,
      user: user ?? undefined,
    };
  }, [organization, period, category, user]);

  React.useEffect(() => setPage(1), [request]);

  const { data, error, loading } = useLoaded(
    () => getIssueAudit({ ...request, p: page, ps: pageSize }),
    [request, page, pageSize],
  );
  // the user filter lists everyone active over the period, whichever user is picked
  const { data: everyone } = useLoaded(
    () => getIssueAudit({ ...request, user: undefined, ps: 1 }).then((r) => r.users),
    [request.organization, request.from, request.categories],
  );

  const exportAll = async () => {
    const { items } = await fetchAllPages((p, ps) =>
      getIssueAudit({ ...request, p, ps }).then((r) => ({ items: r.changes, paging: r.paging })),
    );
    return auditTable(items);
  };

  if (data === undefined) {
    return error ? <LoadError /> : <Spinner loading={loading} />;
  }

  return (
    <div
      aria-busy={loading}
      className={`sw-grid sw-grid-cols-12 sw-gap-6 ${loading ? 'sw-opacity-50' : ''}`}
    >
      {error && (
        <div className="sw-col-span-12">
          <LoadError />
        </div>
      )}
      <ChartCard
        action={
          <ExportCsvButton
            fileName={exportFileName('audit-by-user', organization)}
            getTable={() =>
              auditUsersTable(data.users, translate('analytics.governance.audit.analysis'))
            }
          />
        }
        className="sw-col-span-12"
        description={translate('analytics.governance.audit.users.description')}
        title={translate('analytics.governance.audit.users')}
      >
        <UsersTable users={data.users} />
      </ChartCard>

      <ChartCard
        action={
          <span className="sw-flex sw-flex-wrap sw-items-center sw-gap-3">
            {!isReport && (
              <>
                <Select
                  ariaLabel={translate('analytics.governance.audit.category')}
                  className="sw-w-abs-200"
                  data={CATEGORIES.map((value) => ({
                    value,
                    label: translate('analytics.governance.audit.category', value),
                  }))}
                  onChange={setCategory}
                  placeholder={translate('analytics.governance.audit.all_changes')}
                  size={InputSize.Medium}
                  value={category}
                />
                <Select
                  ariaLabel={translate('analytics.governance.audit.user')}
                  className="sw-w-abs-200"
                  data={(everyone ?? [])
                    .filter(
                      (u): u is AuditUserActivity & { login: string } => u.login !== undefined,
                    )
                    .map((u) => ({
                      value: u.login,
                      label: userLabel({ login: u.login, name: u.name }),
                    }))}
                  isSearchable
                  onChange={setUser}
                  placeholder={translate('analytics.governance.audit.all_users')}
                  size={InputSize.Medium}
                  value={user}
                />
              </>
            )}
            <ExportCsvButton
              fileName={exportFileName('audit-trail', organization)}
              getTable={exportAll}
            />
          </span>
        }
        className="sw-col-span-12"
        description={translate('analytics.governance.audit.description')}
        title={translate('analytics.governance.audit')}
      >
        {data.changes.length === 0 ? (
          <Text isSubdued>{translate('analytics.governance.audit.none')}</Text>
        ) : (
          <ChangesTable changes={data.changes} />
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
    </div>
  );
}

function LoadError() {
  return (
    <FlagMessage className="sw-mb-4" variant="error">
      {translate('analytics.load_error')}
    </FlagMessage>
  );
}

function UsersTable({ users }: Readonly<{ users: AuditUserActivity[] }>) {
  const isReport = useIsReport();
  const [expanded, setExpanded] = React.useState(false);
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const shown = expanded || isReport ? users : users.slice(0, USERS_SHOWN);
  if (users.length === 0) {
    return <Text isSubdued>{translate('analytics.governance.audit.none')}</Text>;
  }
  const widths = ['22%', '10%', ...SUMMARY_COLUMNS.map(() => `${68 / SUMMARY_COLUMNS.length}%`)];
  return (
    <>
      <Table
        columnCount={widths.length}
        columnWidths={widths}
        header={
          <TableRow>
            <ContentCell>{translate('analytics.governance.audit.user')}</ContentCell>
            <NumericalCell>{translate('analytics.governance.total')}</NumericalCell>
            {SUMMARY_COLUMNS.map((action) => (
              <NumericalCell key={action}>
                {translate('analytics.governance.audit.action', action.toUpperCase())}
              </NumericalCell>
            ))}
          </TableRow>
        }
      >
        {shown.map((user) => (
          <TableRow key={user.login ?? ''}>
            <ContentCell>
              {user.login ? (
                userLabel({ login: user.login, name: user.name })
              ) : (
                <Text isSubdued>{translate('analytics.governance.audit.analysis')}</Text>
              )}
            </ContentCell>
            <NumericalCell>{integer(user.total)}</NumericalCell>
            {SUMMARY_COLUMNS.map((action) => (
              <NumericalCell key={action}>{integer(user.actions[action] ?? 0)}</NumericalCell>
            ))}
          </TableRow>
        ))}
      </Table>
      {!isReport && users.length > USERS_SHOWN && (
        <div className="sw-flex sw-justify-center sw-mt-3">
          <Button onClick={() => setExpanded(!expanded)} variety={ButtonVariety.DefaultGhost}>
            {expanded
              ? translate('analytics.governance.show_less')
              : translateWithParameters('analytics.governance.show_all', users.length)}
          </Button>
        </div>
      )}
    </>
  );
}

function actionDetail(change: AuditChange) {
  if (change.action === 'SEVERITY_CHANGED' && change.from && change.to) {
    return `${translate('severity', change.from)} → ${translate('severity', change.to)}`;
  }
  if (change.action === 'STATUS_CHANGED' && change.to) {
    return `${change.from ?? ''} → ${change.to}`;
  }
  return change.text;
}

function ChangesTable({ changes }: Readonly<{ changes: AuditChange[] }>) {
  return (
    <Table
      columnCount={CHANGE_COLUMNS.length}
      columnWidths={CHANGE_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.governance.audit.date')}</ContentCell>
          <ContentCell>{translate('analytics.governance.audit.user')}</ContentCell>
          <ContentCell>{translate('analytics.governance.audit.change')}</ContentCell>
          <ContentCell>{translate('analytics.governance.issue')}</ContentCell>
          <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
        </TableRow>
      }
    >
      {changes.map((change) => {
        const detail = actionDetail(change);
        return (
          <TableRow key={change.id}>
            <ContentCell className="sw-whitespace-nowrap">
              <DateFormatter date={change.date} long={false} />
            </ContentCell>
            <ContentCell>
              {change.user ? (
                userLabel(change.user)
              ) : (
                <Text isSubdued>{translate('analytics.governance.audit.analysis')}</Text>
              )}
            </ContentCell>
            <ContentCell>
              <span className="sw-flex sw-flex-col sw-min-w-0">
                <span>{translate('analytics.governance.audit.action', change.action)}</span>
                {detail && (
                  <Text className="sw-line-clamp-2" isSubdued size={TextSize.Small}>
                    {detail}
                  </Text>
                )}
              </span>
            </ContentCell>
            <ContentCell>
              <span className="sw-flex sw-flex-col sw-min-w-0">
                <LinkStandalone
                  to={getComponentIssuesUrl(change.projectKey, {
                    issues: change.issueKey,
                    open: change.issueKey,
                  })}
                >
                  {change.projectName ?? change.projectKey}
                </LinkStandalone>
                {change.filePath && (
                  <span className="sw-truncate" title={change.filePath}>
                    <Text isSubdued>{change.filePath}</Text>
                  </span>
                )}
              </span>
            </ContentCell>
            <ContentCell>{change.ruleName ?? change.rule}</ContentCell>
          </TableRow>
        );
      })}
    </Table>
  );
}
