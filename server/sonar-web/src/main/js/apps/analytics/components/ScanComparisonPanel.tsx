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
import {
  Button,
  ButtonVariety,
  IconArrowDownRight,
  IconArrowRight,
  IconArrowUpRight,
  IconBug,
  IconCheckCircle,
  IconProject,
  IconWarning,
  InputSize,
  LinkStandalone,
  Select,
  SelectAsync,
  Text,
  TextSize,
} from '@sonarsource/echoes-react';
import { format } from 'date-fns';
import * as React from 'react';
import {
  Card,
  ContentCell,
  FlagMessage,
  NumericalCell,
  Spinner,
  Table,
  TableRow,
  ToggleButton,
} from '~design-system';
import { formatMeasure } from '~sonar-aligned/helpers/measures';
import { MetricType } from '~sonar-aligned/types/metrics';
import {
  AnalyticsIssue,
  AnalyticsPaging,
  ScanChange,
  ScanComparison,
  ScanComparisonRequest,
  ScanList,
  getScanComparison,
  getScanComparisonChanges,
  getScanComparisonIssues,
} from '../../../api/analytics';
import { searchProjects } from '../../../api/components';
import { getProjectActivity } from '../../../api/projectActivity';
import DateFormatter from '../../../components/intl/DateFormatter';
import { parseDate } from '../../../helpers/dates';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { getComponentIssuesUrl } from '../../../helpers/urls';
import { Analysis } from '../../../types/project-activity';
import { CsvTable, exportFileName, fetchAllPages } from '../csv';
import { scanChangesTable, scanIssuesTable, userLabel } from '../governance';
import { useIsReport } from '../report';
import { SEVERITIES } from '../utils';
import ChartCard from './charts/ChartCard';
import { MAGNITUDE_COLOR, STATUS_COLORS } from './charts/colors';
import ExportCsvButton from './ExportCsvButton';
import Pagination, { PAGE_SIZES } from './Pagination';
import StatTile from './StatTile';

const ANALYSES_SHOWN = 100;
const PROJECTS_SHOWN = 20;
/** Rows of each list in the PDF, where nothing can be paged: the CSV export has all of them. */
const REPORT_ROWS = 25;
const ISSUE_COLUMNS = ['36%', '30%', '11%', '11%', '12%'];
const CHANGE_COLUMNS = ['32%', '26%', '16%', '14%', '12%'];
/** Counts where a lower value is better: a drop shows in green. */
const LOWER_IS_BETTER = new Set([
  'violations',
  'bugs',
  'vulnerabilities',
  'code_smells',
  'security_hotspots',
  'blocker_violations',
  'critical_violations',
  'major_violations',
  'minor_violations',
  'info_violations',
]);

const SCAN_LISTS = [
  ScanList.New,
  ScanList.Remediated,
  ScanList.SeverityChanges,
  ScanList.ExceptionChanges,
];

// What scanners send when the build gives no version.
const NO_VERSION = 'not provided';

function versionOf(analysis: { projectVersion?: string }) {
  return analysis.projectVersion && analysis.projectVersion !== NO_VERSION
    ? analysis.projectVersion
    : undefined;
}

function analysisLabel(analysis: Analysis) {
  const version = versionOf(analysis);
  return `${format(parseDate(analysis.date), 'PPp')}${version ? ` · ${version}` : ''}`;
}

/** A column title for an analysis: its version when it has one, with its date. */
function columnLabel(analysis: ScanComparison['base']) {
  const date = format(parseDate(analysis.date), 'PPp');
  const version = versionOf(analysis);
  return version ? `${version} · ${date}` : date;
}

/** Which project and analyses are compared; null until picked. No current analysis means the latest one. */
export interface ScanSelection {
  base: string | null;
  current: string | null;
  project: string | null;
}

export const NO_SCAN_SELECTION: ScanSelection = { base: null, current: null, project: null };

interface Props {
  onSelectionChange?: (selection: ScanSelection) => void;
  organization: string;
  /** The selection to start from, such as the one on screen when the PDF report is made. */
  selection?: ScanSelection;
}

/**
 * Compares the latest analysis of a project, or any analysis, with any earlier one: what moved, which findings are new,
 * which were remediated, and which were reclassified or excepted in between. Each list is paged by the server, so a
 * comparison of thousands of findings stays quick, and exports in full as CSV.
 */
export default function ScanComparisonPanel({
  onSelectionChange,
  organization,
  selection: initialSelection = NO_SCAN_SELECTION,
}: Readonly<Props>) {
  const isReport = useIsReport();
  const [projects, setProjects] = React.useState<Array<{ label: string; value: string }>>([]);
  const [selection, setSelection] = React.useState(initialSelection);
  const [analyses, setAnalyses] = React.useState<Analysis[]>([]);
  const [comparison, setComparison] = React.useState<ScanComparison>();
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState(false);
  const { base, current, project } = selection;

  const update = (changes: Partial<ScanSelection>) => {
    const next = { ...selection, ...changes };
    setSelection(next);
    onSelectionChange?.(next);
  };

  const search = React.useCallback(
    (query: string) => {
      searchProjects({
        organization,
        filter: query.length >= 2 ? `query = "${query.replace(/"/g, '')}"` : undefined,
        ps: PROJECTS_SHOWN,
      }).then(
        ({ components }) => setProjects(components.map((c) => ({ label: c.name, value: c.key }))),
        () => setProjects([]),
      );
    },
    [organization],
  );

  React.useEffect(() => {
    if (!isReport) {
      search('');
    }
  }, [isReport, search]);

  React.useEffect(() => {
    setAnalyses([]);
    if (!project) {
      return;
    }
    getProjectActivity({ project, ps: ANALYSES_SHOWN }).then(
      (result) => {
        setAnalyses(result.analyses);
        // the analysis before the latest one is the usual question: what did the last scan change
        setSelection((sel) => {
          if (sel.project !== project || sel.base !== null) {
            return sel;
          }
          const next = { ...sel, base: result.analyses[1]?.key ?? null };
          onSelectionChange?.(next);
          return next;
        });
      },
      () => setAnalyses([]),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reload only when the project changes
  }, [project]);

  React.useEffect(() => {
    if (!project || !base) {
      setComparison(undefined);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setError(false);
    getScanComparison({
      organization,
      project,
      baseAnalysis: base,
      analysis: current ?? undefined,
    }).then(
      (result) => {
        if (active) {
          setComparison(result);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
  }, [organization, project, base, current]);

  const currentDate = analyses.find((a) => a.key === current)?.date ?? analyses[0]?.date;
  const olderAnalyses = analyses.filter((a) => !currentDate || a.date < currentDate);

  return (
    <section>
      {!isReport && (
        <Card className="sw-flex sw-flex-wrap sw-items-end sw-gap-4 sw-mb-6">
          <SelectAsync
            className="sw-w-abs-300"
            data={projects}
            label={translate('analytics.governance.scan.project')}
            onChange={(value) => update({ project: value, base: null, current: null })}
            onSearch={search}
            placeholder={translate('analytics.governance.scan.project.placeholder')}
            size={InputSize.Large}
            value={project}
            valueIcon={<IconProject />}
          />
          <Select
            className="sw-w-abs-300"
            data={olderAnalyses.map((a) => ({ label: analysisLabel(a), value: a.key }))}
            isDisabled={olderAnalyses.length === 0}
            isNotClearable
            label={translate('analytics.governance.scan.base')}
            onChange={(value) => update({ base: value })}
            size={InputSize.Large}
            value={base}
          />
          <Arrow aria-hidden>
            <IconArrowRight />
          </Arrow>
          <Select
            className="sw-w-abs-300"
            data={analyses.map((a, i) => ({
              label:
                i === 0
                  ? translateWithParameters('analytics.governance.scan.latest', analysisLabel(a))
                  : analysisLabel(a),
              value: a.key,
            }))}
            isDisabled={analyses.length === 0}
            label={translate('analytics.governance.scan.current')}
            onChange={(value) => update({ current: value })}
            placeholder={translate('analytics.governance.scan.latest_placeholder')}
            size={InputSize.Large}
            value={current}
          />
        </Card>
      )}

      {error && (
        <FlagMessage className="sw-mb-4" variant="error">
          {translate('analytics.governance.scan.error')}
        </FlagMessage>
      )}
      {project && analyses.length === 1 && (
        <Text isSubdued>{translate('analytics.governance.scan.single_analysis')}</Text>
      )}
      {!project && (
        <Text isSubdued>
          {translate(
            isReport
              ? 'analytics.governance.scan.none_selected'
              : 'analytics.governance.scan.pick_project',
          )}
        </Text>
      )}
      {comparison === undefined ? (
        <Spinner loading={loading} />
      ) : (
        <div aria-busy={loading} className={loading ? 'sw-opacity-50' : undefined}>
          <ComparisonView
            comparison={comparison}
            organization={organization}
            request={{
              organization,
              project: comparison.project.key,
              baseAnalysis: comparison.base.key,
              analysis: comparison.analysis.key,
            }}
          />
        </div>
      )}
    </section>
  );
}

function ComparisonView({
  comparison,
  organization,
  request,
}: Readonly<{ comparison: ScanComparison; organization: string; request: ScanComparisonRequest }>) {
  const isReport = useIsReport();
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  const { exceptionChanges, newIssues, remediated, severityChanges } = comparison;
  const [list, setList] = React.useState(ScanList.New);
  const findings = React.useRef<HTMLDivElement>(null);
  const counts: Record<ScanList, number> = {
    [ScanList.New]: newIssues.total,
    [ScanList.Remediated]: remediated.total,
    [ScanList.SeverityChanges]: severityChanges.total,
    [ScanList.ExceptionChanges]:
      exceptionChanges.accepted + exceptionChanges.falsePositive + exceptionChanges.reopened,
  };
  const open = (value: ScanList) => () => {
    setList(value);
    findings.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  };

  return (
    <>
      <Text as="p" className="sw-mb-4">
        {translateWithParameters(
          'analytics.governance.scan.summary',
          comparison.project.name,
          format(parseDate(comparison.base.date), 'PPp'),
          format(parseDate(comparison.analysis.date), 'PPp'),
        )}
      </Text>
      <div className="sw-grid sw-grid-cols-4 sw-gap-4 sw-mb-6">
        <StatTile
          color={STATUS_COLORS.danger}
          footnote={translateWithParameters(
            'analytics.governance.scan.new.footnote',
            integer(newIssues.open),
          )}
          icon={<IconBug />}
          label={translate('analytics.governance.scan.new')}
          onClick={isReport ? undefined : open(ScanList.New)}
        >
          {integer(counts[ScanList.New])}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.success}
          footnote={
            remediated.complete
              ? undefined
              : translate('analytics.governance.scan.remediated.incomplete')
          }
          icon={<IconCheckCircle />}
          label={translate('analytics.governance.scan.remediated')}
          onClick={isReport ? undefined : open(ScanList.Remediated)}
        >
          {integer(counts[ScanList.Remediated])}
        </StatTile>
        <StatTile
          color={STATUS_COLORS.warning}
          footnote={translateWithParameters(
            'analytics.governance.scan.severity.footnote',
            integer(severityChanges.raised),
            integer(severityChanges.lowered),
          )}
          icon={<IconArrowUpRight />}
          label={translate('analytics.governance.scan.severity')}
          onClick={isReport ? undefined : open(ScanList.SeverityChanges)}
        >
          {integer(counts[ScanList.SeverityChanges])}
        </StatTile>
        <StatTile
          color={MAGNITUDE_COLOR}
          footnote={translateWithParameters(
            'analytics.governance.scan.exceptions.footnote',
            integer(exceptionChanges.accepted),
            integer(exceptionChanges.falsePositive),
            integer(exceptionChanges.reopened),
          )}
          icon={<IconWarning />}
          label={translate('analytics.governance.scan.exceptions')}
          onClick={isReport ? undefined : open(ScanList.ExceptionChanges)}
        >
          {integer(counts[ScanList.ExceptionChanges])}
        </StatTile>
      </div>

      <MeasuresCard comparison={comparison} organization={organization} />

      <div className="sw-mt-6" ref={findings}>
        {isReport ? (
          SCAN_LISTS.map((value) => (
            <FindingsCard
              className="sw-mb-6"
              count={counts[value]}
              key={value}
              list={value}
              note={value === ScanList.Remediated ? remediatedNote(comparison) : undefined}
              organization={organization}
              request={request}
            />
          ))
        ) : (
          <FindingsCard
            count={counts[list]}
            list={list}
            note={list === ScanList.Remediated ? remediatedNote(comparison) : undefined}
            onListChange={setList}
            organization={organization}
            request={request}
            tabs={SCAN_LISTS.map((value) => ({
              label: `${translate('analytics.governance.scan.list', value)} (${integer(counts[value])})`,
              value,
            }))}
          />
        )}
      </div>
    </>
  );
}

function remediatedNote(comparison: ScanComparison) {
  return comparison.remediated.complete
    ? undefined
    : translateWithParameters(
        'analytics.governance.scan.remediated.note',
        comparison.remediated.retentionDays,
      );
}

function MeasuresCard({
  comparison,
  organization,
}: Readonly<{ comparison: ScanComparison; organization: string }>) {
  const isReport = useIsReport();
  const [showAll, setShowAll] = React.useState(false);
  // The quality gate always shows; the counts only when they moved, unless asked for.
  const changed = comparison.measures.filter(
    ({ change, metric }) => metric === 'alert_status' || (change !== undefined && change !== 0),
  );
  const unchanged = comparison.measures.length - changed.length;
  const measures = showAll || isReport ? comparison.measures : changed;

  return (
    <ChartCard
      action={
        <ExportCsvButton
          fileName={exportFileName(
            `scan-comparison-${comparison.project.key}-net-changes`,
            organization,
          )}
          getTable={() => measuresTable(comparison)}
        />
      }
      description={translate('analytics.governance.scan.net.description')}
      title={translate('analytics.governance.scan.net')}
    >
      <MeasuresTable comparison={{ ...comparison, measures }} />
      {!isReport && unchanged > 0 && (
        <Button
          className="sw-mt-3"
          onClick={() => setShowAll(!showAll)}
          variety={ButtonVariety.PrimaryGhost}
        >
          {showAll
            ? translate('analytics.governance.scan.hide_unchanged')
            : translateWithParameters('analytics.governance.scan.show_unchanged', unchanged)}
        </Button>
      )}
    </ChartCard>
  );
}

function measuresTable(comparison: ScanComparison): CsvTable {
  return {
    header: ['Metric', 'Base analysis', 'Compared analysis', 'Change'],
    rows: comparison.measures.map((m) => [
      translate('metric', m.metric, 'name'),
      m.base,
      m.analysis,
      m.change,
    ]),
  };
}

function MeasuresTable({ comparison }: Readonly<{ comparison: ScanComparison }>) {
  const integer = (value: number) => formatMeasure(value, MetricType.Integer);
  return (
    <Table
      columnCount={4}
      columnWidths={['40%', '20%', '20%', '20%']}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.governance.scan.metric')}</ContentCell>
          <NumericalCell>
            {translateWithParameters(
              'analytics.governance.scan.from',
              columnLabel(comparison.base),
            )}
          </NumericalCell>
          <NumericalCell>
            {translateWithParameters(
              'analytics.governance.scan.to',
              columnLabel(comparison.analysis),
            )}
          </NumericalCell>
          <NumericalCell>{translate('analytics.governance.scan.change')}</NumericalCell>
        </TableRow>
      }
    >
      {comparison.measures.map(({ analysis, base, change, metric }) => {
        let changeColor: string | undefined;
        if (change !== undefined && change !== 0 && LOWER_IS_BETTER.has(metric)) {
          changeColor = change < 0 ? STATUS_COLORS.success : STATUS_COLORS.danger;
        }
        return (
          <TableRow key={metric}>
            <ContentCell>{translate('metric', metric, 'name')}</ContentCell>
            <NumericalCell>
              {typeof base === 'number' ? integer(base) : (base ?? '-')}
            </NumericalCell>
            <NumericalCell>
              {typeof analysis === 'number' ? integer(analysis) : (analysis ?? '-')}
            </NumericalCell>
            <NumericalCell>
              {change === undefined ? (
                ''
              ) : (
                <span
                  className="sw-inline-flex sw-items-center sw-gap-1"
                  style={{ color: changeColor }}
                >
                  {change > 0 && <IconArrowUpRight />}
                  {change < 0 && <IconArrowDownRight />}
                  {change > 0 ? `+${integer(change)}` : integer(change)}
                </span>
              )}
            </NumericalCell>
          </TableRow>
        );
      })}
    </Table>
  );
}

function IssueLink({
  issue,
}: Readonly<{
  issue: Pick<AnalyticsIssue, 'filePath' | 'issueKey' | 'line'> & { projectKey?: string };
}>) {
  const where = `${issue.filePath ?? issue.issueKey}${issue.line ? `:${issue.line}` : ''}`;
  return issue.projectKey ? (
    <LinkStandalone
      className="sw-break-all"
      to={getComponentIssuesUrl(issue.projectKey, { issues: issue.issueKey, open: issue.issueKey })}
    >
      {where}
    </LinkStandalone>
  ) : (
    <span className="sw-break-all">{where}</span>
  );
}

function RuleCell({ name, rule }: Readonly<{ name?: string; rule: string }>) {
  return (
    <span className="sw-flex sw-flex-col sw-min-w-0">
      <Text>{name ?? rule}</Text>
      {name && (
        <Text className="sw-break-all" isSubdued size={TextSize.Small}>
          {rule}
        </Text>
      )}
    </span>
  );
}

const isIssueList = (list: ScanList): list is ScanList.New | ScanList.Remediated =>
  list === ScanList.New || list === ScanList.Remediated;

type Page =
  | { changes: ScanChange[]; issues?: undefined; paging: AnalyticsPaging }
  | { changes?: undefined; issues: AnalyticsIssue[]; paging: AnalyticsPaging };

function loadPage(request: ScanComparisonRequest, list: ScanList, p: number, ps: number) {
  return isIssueList(list)
    ? getScanComparisonIssues({ ...request, list, p, ps })
    : getScanComparisonChanges({ ...request, list, p, ps });
}

/**
 * One list of the comparison, a page at a time. The PDF shows the first rows of it; the CSV export has every row.
 */
function FindingsCard({
  className,
  count,
  list,
  note,
  onListChange,
  organization,
  request,
  tabs,
}: Readonly<{
  className?: string;
  count: number;
  list: ScanList;
  note?: string;
  onListChange?: (list: ScanList) => void;
  organization: string;
  request: ScanComparisonRequest;
  tabs?: Array<{ label: string; value: ScanList }>;
}>) {
  const isReport = useIsReport();
  const [paging, setPaging] = React.useState({
    page: 1,
    pageSize: isReport ? REPORT_ROWS : (PAGE_SIZES[0] as number),
  });
  const [data, setData] = React.useState<Page>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  // Back to the first page when the list or the comparison changes.
  React.useEffect(() => {
    setPaging((current) => ({ ...current, page: 1 }));
  }, [list, request.baseAnalysis, request.analysis, request.project]);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    (loadPage(request, list, paging.page, paging.pageSize) as Promise<Page>).then(
      (result) => {
        if (active) {
          setData(result);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- the request is rebuilt on every render
  }, [list, paging, request.organization, request.project, request.baseAnalysis, request.analysis]);

  const exportAll = async (): Promise<CsvTable> => {
    if (isIssueList(list)) {
      const { items } = await fetchAllPages((p, ps) =>
        getScanComparisonIssues({ ...request, list, p, ps }).then((r) => ({
          items: r.issues,
          paging: r.paging,
        })),
      );
      return scanIssuesTable(items);
    }
    const { items } = await fetchAllPages((p, ps) =>
      getScanComparisonChanges({ ...request, list, p, ps }).then((r) => ({
        items: r.changes,
        paging: r.paging,
      })),
    );
    return scanChangesTable(items);
  };

  return (
    <ChartCard
      action={
        <ExportCsvButton
          fileName={exportFileName(`scan-comparison-${request.project}-${list}`, organization)}
          getTable={exportAll}
        />
      }
      className={className}
      description={note}
      loading={loading && data !== undefined}
      title={translate(
        tabs ? 'analytics.governance.scan.lists' : `analytics.governance.scan.list.${list}`,
      )}
    >
      {tabs && onListChange && (
        <ToggleButton
          label={translate('analytics.governance.scan.lists')}
          onChange={onListChange}
          options={tabs}
          value={list}
        />
      )}
      <div className={tabs ? 'sw-mt-4' : undefined}>
        {error && (
          <FlagMessage className="sw-mb-4" variant="error">
            {translate('analytics.governance.scan.error')}
          </FlagMessage>
        )}
        {data === undefined && <Spinner loading={loading} />}
        {data !== undefined && count === 0 && data.paging.total === 0 && (
          <Text isSubdued>{translate('analytics.governance.scan.none')}</Text>
        )}
        {data?.issues && data.issues.length > 0 && <IssueTable issues={data.issues} />}
        {data?.changes && data.changes.length > 0 && (
          <ChangeTable changes={data.changes} projectKey={request.project} />
        )}
        {data !== undefined && data.paging.total > 0 && !isReport && (
          <Pagination
            onChange={(page, pageSize) => setPaging({ page, pageSize })}
            page={paging.page}
            pageSize={paging.pageSize}
            total={data.paging.total}
          />
        )}
        {data !== undefined && isReport && data.paging.total > REPORT_ROWS && (
          <Text as="p" className="sw-mt-2" isSubdued size={TextSize.Small}>
            {translateWithParameters(
              'analytics.governance.scan.report_rows',
              REPORT_ROWS,
              formatMeasure(data.paging.total, MetricType.Integer),
            )}
          </Text>
        )}
      </div>
    </ChartCard>
  );
}

function IssueTable({ issues }: Readonly<{ issues: AnalyticsIssue[] }>) {
  return (
    <Table
      columnCount={ISSUE_COLUMNS.length}
      columnWidths={ISSUE_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.governance.issue')}</ContentCell>
          <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
          <ContentCell>{translate('analytics.governance.type')}</ContentCell>
          <ContentCell>{translate('analytics.quality.severity')}</ContentCell>
          <ContentCell>{translate('status')}</ContentCell>
        </TableRow>
      }
    >
      {issues.map((issue) => (
        <TableRow key={issue.issueKey}>
          <ContentCell>
            <IssueLink issue={issue} />
          </ContentCell>
          <ContentCell>
            <RuleCell name={issue.ruleName} rule={issue.rule} />
          </ContentCell>
          <ContentCell>{issue.type ? translate('issue.type', issue.type) : '-'}</ContentCell>
          <ContentCell>{issue.severity ? translate('severity', issue.severity) : '-'}</ContentCell>
          <ContentCell>
            {issue.resolution
              ? translate('issue.resolution', issue.resolution)
              : translate('issue.status', issue.status ?? 'OPEN')}
          </ContentCell>
        </TableRow>
      ))}
    </Table>
  );
}

function ChangeTable({
  changes,
  projectKey,
}: Readonly<{ changes: ScanChange[]; projectKey: string }>) {
  const label = (value?: string) => {
    if (!value) {
      return translate('issue.status.OPEN');
    }
    if (value === 'ACCEPTED' || value === 'FALSE_POSITIVE') {
      return translate('issue.issue_status', value);
    }
    return (SEVERITIES as readonly string[]).includes(value)
      ? translate('severity', value)
      : translate('issue.resolution', value);
  };
  return (
    <Table
      columnCount={CHANGE_COLUMNS.length}
      columnWidths={CHANGE_COLUMNS}
      header={
        <TableRow>
          <ContentCell>{translate('analytics.governance.issue')}</ContentCell>
          <ContentCell>{translate('analytics.quality.rule')}</ContentCell>
          <ContentCell>{translate('analytics.governance.scan.change')}</ContentCell>
          <ContentCell>{translate('analytics.governance.audit.user')}</ContentCell>
          <ContentCell>{translate('analytics.governance.audit.date')}</ContentCell>
        </TableRow>
      }
    >
      {changes.map((change) => (
        <TableRow key={`${change.issueKey}-${change.date}`}>
          <ContentCell>
            <IssueLink issue={{ ...change, projectKey }} />
          </ContentCell>
          <ContentCell>
            <RuleCell name={change.ruleName} rule={change.rule} />
          </ContentCell>
          <ContentCell>
            {label(change.from)} → {label(change.to)}
          </ContentCell>
          <ContentCell>{change.user ? userLabel(change.user) : '-'}</ContentCell>
          <ContentCell className="sw-whitespace-nowrap">
            <DateFormatter date={change.date} long={false} />
          </ContentCell>
        </TableRow>
      ))}
    </Table>
  );
}

const Arrow = styled.span`
  display: inline-flex;
  align-items: center;
  height: 40px;
  color: var(--echoes-color-icon-subdued);
`;
