# Organization Analytics: Analysis and Plan

Status: Phase 1 implemented (see §7.1) · Date: 2026-10-12 · Owner: Arunsimha Kamalapuram

## 1. Goal

Give organization admins one page that answers:

1. **How much is being analysed?** Projects, analyses run, branches and PRs covered.
2. **How healthy is the analysis pipeline?** Background tasks completed, failed or queued, and how long they take.
3. **What is the code quality, and is it getting better?** Quality gate, issues, and how both change over time.
4. **What value is the customer getting?** Issues fixed, adoption, AI fix usage, licence (LOC) usage.

This builds on the existing Analytics page (`server/sonar-web/src/main/js/apps/analytics`). It does not replace it.

---

## 2. What exists today

### 2.1 Features

| Area | What it shows | Source |
|---|---|---|
| Summary tiles | Total projects, QG passed, QG failed, open issues, analysed in last 30 days | `api/components/search_projects`, `api/measures/search`, `api/issues/search` |
| Quality gate donut | Passed / Failed / Not available, plus pass rate | `alert_status` measure |
| Issues by type and by severity | Bar lists (Bug, Code Smell, Vulnerability × Blocker…Info) | Issues `projects` facet |
| Top 10 projects by issues | Stacked bars | Same as above |
| Type × severity heatmap | Plus "top pair" and "% high severity" insights | Same as above |
| Background tasks | **Latest** task status per project (Success / Failed / Canceled / In progress / Pending / None) | `api/ce/component` per project |
| Analysis age | Week / Month / Quarter / Older / Never | `analysisDate` |
| Table | Per-project row with all of the above | |
| Filters | Project, date preset, QG, task status, recency, type, severity; charts cross-filter each other | Client-side |

Access: organization admins and super admins (`canViewAnalytics` → `organization.actions.admin`).

### 2.2 Gaps

| # | Gap | Impact |
|---|---|---|
| G1 | **Everything is computed in the browser.** For N projects the page sends `ceil(N/100) × 15` issue searches plus **N** `api/ce/component` calls (10 at a time). | For 1,000 projects that is about 1,150 HTTP calls on every page load. Slow for large customers, and heavy on the web server and Elasticsearch. |
| G2 | **Snapshot only, no history.** | We cannot show trends such as "analyses per week" or "pass rate this month vs last". |
| G3 | **No count of background tasks.** The chart shows the latest status of each project, not how many tasks ran. | The question "how many background tasks ran and completed" cannot be answered. |
| G4 | **No timing data.** | Average analysis time, queue wait time and slowest projects are missing. |
| G5 | The date filter only checks the *last* analysis date. | "Last 7 days" means "projects whose most recent analysis was in the last 7 days", not "analyses that ran in the last 7 days". |
| G6 | Main branch only. | Branch and PR analysis activity is invisible, even though for many customers that is most of the volume. |
| G7 | Uses only the legacy taxonomy (type and severity). | Does not match the Clean Code taxonomy (software qualities / impacts) shown elsewhere in the product. |

---

## 3. Data available in the platform

### 3.1 Compute Engine (main source for the new metrics)

`CE_ACTIVITY` has one row per finished background task:

| Column | Use |
|---|---|
| `TASK_TYPE` | `REPORT` = a scanner analysis. Other types are `ISSUE_SYNC`, `AUDIT_PURGE`, provisioning and so on. |
| `ENTITY_UUID` | Project UUID. **Join to `PROJECTS.ORGANIZATION_UUID` to scope by org.** CE tables have no organization column. |
| `COMPONENT_UUID` | Branch or PR component |
| `STATUS` | `SUCCESS`, `FAILED`, `CANCELED` |
| `SUBMITTED_AT`, `STARTED_AT`, `EXECUTED_AT` | Queue wait = `STARTED_AT − SUBMITTED_AT`; turnaround = `EXECUTED_AT − SUBMITTED_AT` |
| `EXECUTION_TIME_MS` | CE processing time ("analysis time" on the server side) |
| `EXECUTION_COUNT` | Always written as 0 in this version, so retries cannot be measured |
| `ERROR_TYPE`, `ERROR_MESSAGE` | Group failures by cause |
| `SUBMITTER_UUID` | User or token that submitted the task (CI adoption, top submitters) |
| `WORKER_UUID`, `NODE_NAME` | Worker and node distribution (internal ops) |

Related tables:

- `CE_QUEUE`: tasks waiting or in progress right now. Use it for live queue depth and the oldest pending task.
- `CE_TASK_CHARACTERISTICS`: `branch`, `branchType`, `pullRequest`. Use it to split main / branch / PR.
- `CE_TASK_MESSAGE`: analysis warnings per task.

> ⚠️ **Retention: `PurgeDao.purgeOldCeActivities` deletes CE activity older than 180 days.** Any trend longer than 6 months needs our own daily rollup (see §6.3).

> ⚠️ `EXECUTION_TIME_MS` is **CE processing time only**. It does not include the time the scanner spends on the CI agent. To report full end-to-end time, the scanner would have to send its own duration, for example as a scanner-context property. That is out of scope for phase 1, and the UI should label the metric clearly.

### 3.2 Projects and quality

- `PROJECTS`: `ORGANIZATION_UUID`, `NCLOC`, `CREATED_AT`, `CONTAINS_AI_CODE`, `AI_CODE_FIX_ENABLED`, `TAGS`
- `PROJECT_BRANCHES`: branch and PR count per project
- `SNAPSHOTS` + `PROJECT_MEASURES` (history) / `LIVE_MEASURES`: QG status, ratings, coverage, duplications, `sqale_index` (debt), `ncloc_language_distribution`
- `EVENTS`: quality gate change events (passed→failed transitions)
- Issues index (Elasticsearch): open, created and resolved in a period, with facets by project, type, severity, impact and `createdAt`

### 3.3 CodeScan-specific

- `ai_call_log` (`codescanng-core`, `AiCallLogRepository`): AI fix and custom-rule calls and credits used per org.
- `ORGANIZATIONS.SUBSCRIPTION`: plan, to compare usage against entitlement.

---

## 4. Proposed metric catalogue

Priority: **P1** = what was asked for (counts, background tasks, average time). **P2** = high value, low effort once P1 exists. **P3** = later.

### 4.1 Analysis activity (P1)

| Metric | Definition | Visual |
|---|---|---|
| Analyses run | Count of `REPORT` tasks in period | KPI + daily/weekly column chart, stacked by status |
| Projects analysed | Distinct `ENTITY_UUID` with ≥1 analysis in period, out of total projects | KPI ("412 of 530") |
| Success rate | `SUCCESS / (SUCCESS + FAILED)` | KPI + trend line |
| Analyses by target | Main branch vs branch vs pull request | Stacked share bar |
| Most active projects | Top 10 by analysis count | Bar list |
| Inactive projects | Projects with 0 analyses in period, or never analysed | Count + drill-down list |

### 4.2 Background tasks (P1)

| Metric | Definition | Visual |
|---|---|---|
| Tasks completed | Count by `STATUS`, all task types, in period | KPI row |
| Tasks by type | `REPORT`, `ISSUE_SYNC`, … | Bar list |
| Live queue | `CE_QUEUE` pending and in-progress count; age of the oldest pending task | KPI with a warning above a threshold |

### 4.3 Analysis time / performance (P1)

| Metric | Definition | Visual |
|---|---|---|
| Average analysis time | `AVG(EXECUTION_TIME_MS)` of successful `REPORT` tasks | KPI |
| Median and p90 analysis time | `percentile_cont(0.5/0.9)` | Show next to the average; the average alone is skewed by outliers |
| Avg queue wait | `AVG(STARTED_AT − SUBMITTED_AT)` | KPI. A high value means the customer needs more workers, which is an upsell or ops signal |
| Time trend | Daily median and p90 | Line chart |
| Slowest projects | Top 10 by median time, with LOC | Table |
| Time per 1k LOC | `median_ms / (ncloc / 1000)` | Normalised table column. Flags projects that are slow for their size |
| Regression alert | Project whose 7-day median is more than 50% above its 30-day median | Badge in the table |
| Peak load | Submissions by weekday × hour | Heatmap (reuse `charts/Heatmap.tsx`) |

### 4.4 Reliability (P2)

- Failure rate trend, plus the **top failure reasons** (group by `ERROR_TYPE`, then by a normalised `ERROR_MESSAGE` prefix).
- Projects whose last N analyses all failed ("broken pipelines"), each linking to its background tasks page.
- Projects with analysis warnings (`CE_TASK_MESSAGE`).

### 4.5 Quality trends (P2, on top of the existing charts)

- QG pass rate over time: share of analyses that ended `OK` vs `ERROR`, per week.
- QG transitions: projects that went passed→failed in the period, from `EVENTS`.
- Issues introduced vs resolved per week (issues index `createdAt` / `closeDate` histograms).
- Technical debt trend (sum of `sqale_index`).
- Ratings distribution: count of projects at each A–E rating for Reliability, Security and Maintainability.
- Add the Clean Code taxonomy view (software quality × impact severity) next to type × severity (gap G7).

### 4.6 Adoption and customer value (P2/P3)

| Metric | Why it matters | Audience |
|---|---|---|
| LOC analysed vs licensed LOC | Shows headroom or overage before the bill does | Customer + Sales |
| Language mix (`ncloc_language_distribution`) | Which analysers matter to this customer | Product |
| PR analysis adoption (% of projects with PR analyses) | Shift-left maturity; customers who use PR analysis churn less | CSM |
| Active submitters (distinct `SUBMITTER_UUID`) | Engagement and seat usage | CSM |
| Issues fixed in period | "CodeScan helped you fix X issues", ROI story | Customer exec |
| Debt removed (hours) | ROI expressed in time | Customer exec |
| AI fix usage (calls, success, credits from `ai_call_log`) | Add-on value and upsell | Customer + Sales |
| New projects onboarded per month | Expansion trend | CSM |

### 4.7 Platform-wide view (P3, super admin only)

The same CE metrics across **all** organizations: top orgs by analysis volume and CE time, orgs with high failure rates, and orgs near their licence limit. This is internal and useful for capacity planning and customer success.

---

## 5. Proposed UI layout

Keep the page and its filters. Add tabs so the page does not become one long scroll:

```
Analytics  [ Overview | Activity & Performance | Quality | Usage ]     Period: [Last 30 days ▾]

Overview (new KPI strip on top of today's tiles)
┌─────────┬────────────┬───────────┬────────────┬──────────────┬────────────┐
│Projects │ Analysed   │ Analyses  │ Success    │ Median time  │ Queue now  │
│  530    │ 412 (78%)  │ 6,240 ▲12%│ 97.8% ▼0.4 │ 48s (p90 3m) │ 3 pending  │
└─────────┴────────────┴───────────┴────────────┴──────────────┴────────────┘
 ...existing QG / issues charts...

Activity & Performance
 [Analyses per day, stacked Success/Failed/Canceled ───────────────────────]
 [Median + p90 analysis time per day ──────]  [Main / Branch / PR share  ]
 [Slowest projects table (median, p90, LOC, ms/kLOC, trend badge)        ]
 [Top failure reasons]                        [Peak load heatmap (day×hr) ]

Quality      → today's charts + pass-rate trend, introduced vs resolved, ratings
Usage        → LOC vs licence, language mix, PR adoption, AI fix usage
```

Every KPI shows the change against the previous period of the same length. Every chart links to the page that lists the underlying items (background tasks, issues, project).

---

## 6. Technical approach

### 6.1 New server-side aggregation API (fixes G1–G5)

Add a web service controller **`api/analytics`** in `sonar-webserver-webapi`, with the same permission as the page: org admin or super admin. Each action returns pre-aggregated data with one SQL query per action.

| Action | Params | Returns |
|---|---|---|
| `api/analytics/activity_summary` | `organization`, `from`, `to`, `branchType?` | counts by status, distinct projects, success rate, avg / p50 / p90 execution and queue wait, plus the same for the previous period |
| `api/analytics/activity_timeseries` | `organization`, `from`, `to`, `interval=day\|week`, `metric` | buckets: `{date, success, failed, canceled, p50Ms, p90Ms}` |
| `api/analytics/project_performance` | `organization`, `from`, `to`, `sort`, `p`, `ps` | per project: analyses, failures, p50 / p90, ncloc, last status |
| `api/analytics/failures` | `organization`, `from`, `to` | top error groups with counts and sample task ids |
| `api/analytics/queue` | `organization` | live pending / in-progress, oldest pending age |
| `api/analytics/project_snapshot` | `organization` | per project: QG, issue matrix, latest task status. **Replaces the N+1 client fan-out** |

Example query for the activity summary (PostgreSQL; MyBatis mapper in `sonar-db-dao`):

```sql
SELECT
  COUNT(*)                                                     AS total,
  COUNT(*) FILTER (WHERE ca.status = 'SUCCESS')                AS success,
  COUNT(*) FILTER (WHERE ca.status = 'FAILED')                 AS failed,
  COUNT(*) FILTER (WHERE ca.status = 'CANCELED')               AS canceled,
  COUNT(DISTINCT ca.entity_uuid)                               AS projects_analysed,
  AVG(ca.execution_time_ms) FILTER (WHERE ca.status='SUCCESS') AS avg_ms,
  percentile_cont(0.5) WITHIN GROUP (ORDER BY ca.execution_time_ms) AS p50_ms,
  percentile_cont(0.9) WITHIN GROUP (ORDER BY ca.execution_time_ms) AS p90_ms,
  AVG(ca.started_at - ca.submitted_at)                         AS avg_queue_ms
FROM ce_activity ca
JOIN projects p ON p.uuid = ca.entity_uuid
WHERE p.organization_uuid = #{orgUuid}
  AND ca.task_type = 'REPORT'
  AND ca.submitted_at >= #{from} AND ca.submitted_at < #{to};
```

Notes:

- `percentile_cont` and `FILTER` are PostgreSQL-only. Tests run on H2, and we may support other databases. Either provide database-specific mapper statements, or compute percentiles in Java from a capped set of `execution_time_ms` values.
- **Indexing:** `CE_ACTIVITY` has no index on `SUBMITTED_AT`. Add `CE_ACTIVITY_ENTITY_SUBMITTED (ENTITY_UUID, SUBMITTED_AT)` through a DB migration, and check the query plan on a large production-like dataset.
- Splitting by branch type needs a join to `CE_TASK_CHARACTERISTICS` on `kee = 'branchType'` / `'pullRequest'`.
- Issue counts stay in Elasticsearch, but as **one** aggregation per request (terms on project × type × severity) inside `project_snapshot`, instead of 15 × N/100 calls.

### 6.2 Front end

- Add API wrappers in `api/analytics.ts`, and replace the fan-out in `utils.ts` (`fetchIssueMatrices`, `fetchBackgroundTaskStatuses`) with `project_snapshot`. Client-side cross-filtering keeps working because it runs on the returned rows.
- Change the meaning of the date filter so it selects **analyses in the period** for activity metrics (fixes G5). Recency stays as it is.
- Reuse the existing chart components (`BarList`, `StackedBars`, `Donut`, `Heatmap`, `StatTile`). Add `TimeSeries` (column and line) and `DeltaBadge`.
- Add the new l10n keys under `analytics.*`.

### 6.3 History beyond 180 days (P3)

Add a daily rollup table, filled by a scheduled CE task (or on each analysis):

```
ORG_ANALYTICS_DAILY(org_uuid, project_uuid, day, analyses, success, failed, canceled,
                    sum_exec_ms, p50_exec_ms, p90_exec_ms, sum_queue_ms,
                    qg_ok, qg_error, issues_open, issues_created, issues_resolved, ncloc)
```

Benefits: year-over-year trends, quarterly business review exports, fast queries whatever the CE size, and no dependence on the 180-day CE purge.

### 6.4 Exports and sharing (P3)

- CSV export of every table (for example through the existing `sonar-codescancsv-export-plugin` patterns).
- Scheduled weekly or monthly email digest to org admins, with KPIs and changes.
- PDF "quality report" for customer executives (possibly through `sonar-codescanreports-plugin`).

---

## 7. Delivery phases

| Phase | Scope | Result |
|---|---|---|
| **1** | `activity_summary`, `activity_timeseries`, `queue` endpoints plus index migration; new KPI strip; "Activity & Performance" tab with analyses per day, success rate, avg / median / p90 time, queue | Answers the original request: projects analysed, background tasks run and completed, average analysis time |
| **2** | `project_snapshot` (removes the N+1 calls), `project_performance` (slowest projects, ms/kLOC, regression badge), `failures`, main / branch / PR split, peak-load heatmap | Fast page for big orgs, plus troubleshooting value |
| **3** | Quality trends, Clean Code taxonomy, Usage tab (LOC vs licence, PR adoption, AI fix usage) | Story of the value customers get |
| **4** | Daily rollup table, exports, email digest, super-admin cross-org view | Long-term trends, QBR material, internal ops |

---

### 7.1 Phase 1: what was built

Phase 1 ships as **two** endpoints instead of the three listed in §6.1. `activity` returns the summary, the previous period and the timeline in one call, so the page makes a single request per period change.

| Endpoint | Returns |
|---|---|
| `GET api/analytics/activity?organization&from&to&timeZone&interval=day\|week` | `analyses` (counts by status, distinct projects, avg / median / p90 execution time, avg / median queue wait), the same for the `previous` period of equal length, `taskTypes` (all task types), `targets` (main / branch / PR), `timeline` buckets |
| `GET api/analytics/queue?organization` | pending and in-progress counts, oldest pending task and its age |
| `GET api/analytics/tasks?organization&from&to&timeZone&statuses&type&s&p&ps` | the finished tasks behind a tile: filter by status and type, sort by most recent, slowest or longest queue wait (drill-downs) |
| `GET api/analytics/queued_tasks?organization&p&ps` | tasks running or waiting right now, with how long each has waited |
| `GET api/analytics/projects?organization&from&to&timeZone&analyzed&p&ps` | per project: analyses, failures, average time, last analysis; or, with `analyzed=false`, the projects not analyzed in the period |

Each headline tile on the Activity tab opens a drill-down panel listing the tasks or projects behind it. Org admins have no organization-wide background tasks page, so the drill-down stays inside Analytics; each task row links to its project's own background tasks page.

Decisions taken on the open questions, all easy to revisit:

- **Analysis time** is CE processing time only, and the UI says so.
- **Headline KPIs count every analysis** (main + branches + PRs). The split is shown in "Analyses by target".
- **Percentiles are computed in Java** from streamed rows (`CeActivityDao.scrollForOrganizationAnalytics`), so the SQL stays portable across the supported databases and H2.
- Periods are capped at 180 days (CE retention). Days and weekly buckets (Monday-based) follow the browser's time zone.
- Access is the same as the page: org admins and root users (`checkPermission(ADMINISTER, org)`).

Main files:

- Web service: `server/sonar-webserver-webapi/src/main/java/org/sonar/server/analytics/ws/`
- DAO: `CeActivityDao`, `CeQueueDao`, `ProjectDao` + mappers
- Migration `10_8_034`: index `ce_activity_entity_submitted` on `(entity_uuid, submitted_at)`
- UI: "Activity & Performance" tab, `apps/analytics/components/ActivityDashboard.tsx`, `charts/ColumnTimeline.tsx`, `charts/LineTimeline.tsx`

---

### 7.2 Product roadmap: Waves 1 and 2 built

The backend is split by data source: SonarQube data in the fork's `api/analytics`, CodeScan data in codescanng-services under `/_codescan/analytics`.

| # | Feature | Where it shows | Backend |
|---|---|---|---|
| 1 | Project scorecard with "Ready to release: yes / no, because…" | Overview: Focus list, table "Scorecard" button | Existing APIs (quality gate status, measures, issues, CE) |
| 2 | ~~Organization health score, 12-week trend, "What changed this week"~~ Removed from the page on request; the endpoint now only feeds Top Root Causes (Quality & Risk) | n/a | `/_codescan/analytics/health` |
| 3 | Quality gate pass rate, open issues, vulnerabilities and blockers over time; new issues per week | Quality & Risk | `api/analytics/quality_trend` (main-branch measure history), issues `createdAt` facet |
| 4 | Focus this week (projects ranked by risk, with reasons) | Overview, just above the Projects table | Computed in the browser from data the page already loads |
| 5 | Open issues by technology (Apex, Visualforce, Lightning, metadata…) | Quality & Risk | Issues `languages` facet. A true metadata-type split (Flows, Profiles…) needs a file-suffix aggregation: not built |
| 6 | Security issues by age and severity, count past SLA (blocker/high > 30 days) | Quality & Risk | Issues facets |
| 7 | Exceptions expiring in 30 days, accepted / false-positive totals | Quality & Risk | `api/analytics/expiring_exceptions`, issues facet |
| 8 | AI value: issues resolved by AI, PRs merged, credits by add-on, fix job outcomes | Value & Adoption | `/_codescan/analytics/ai-value` |
| 9 | PR analysis adoption; IDE plugin users vs members | Activity; Value & Adoption | `targets[].projects` in `api/analytics/activity`; `/_codescan/analytics/ide-adoption` |
| 10 | Most frequent rules; top root causes from AI debt analysis | Quality & Risk | Issues `rules` facet; `/_codescan/analytics/health` |

| 3b | Issues introduced vs resolved; how they were resolved (fixed by the analysis, removed, accepted, false positive, reopened); issues by status now | Quality & Risk | `api/analytics/issue_resolutions` (issue changelog of main branches), issues `issueStatuses` facet |
| 9b | Pull requests by project type (Salesforce, GitHub, GitLab, Bitbucket, CI · Azure DevOps…) and by project: PRs received, PRs analyzed, PR analyses, failures | Activity & Performance | `/_codescan/analytics/pull-requests` |

Pull request definitions:
- **Project type:** the CodeScan integration type from `cs_project.config.type`. Projects analyzed from the customer's own CI fall back to their DevOps platform binding (`ci_github`, `ci_azure`…), or `ci` without one.
- **PRs received:** distinct pull requests CodeScan started an analysis job for (`cs_queue`). This only applies to projects CodeScan pulls from a repository.
- **PRs analyzed:** distinct pull requests with a successful analysis. It reads the `pullRequest` task characteristic, which outlives the pull request branch that SonarQube deletes after inactivity.

Known limits:
- **Closed issues are deleted after `sonar.dbcleaner.daysBeforeDeletingClosedIssues` days (30 by default), with their changelog.** "Fixed" and "removed" counts are complete only within that window; the response says from which date (`completeFrom`) and the UI shows a note. Accepted and false-positive counts are not affected. Longer history needs either a longer retention or the daily rollup (#14).
- **Panels built on codescanng data hide themselves** when codescanng-services does not answer.

Wave 3 (digest and export, alerts, long-history rollup, benchmarks) is not started; see the open questions below.

## 8. Open questions

1. **Definition of "analysis time":** is CE processing time (`EXECUTION_TIME_MS`) enough, or do we also want scanner-side time? Scanner-side time needs a scanner change.
2. **Branches:** should the headline KPIs count every analysis (main + branches + PRs) by default, or only the main branch?
3. **Database support:** is PostgreSQL the only production database? That decides whether percentiles can be computed in SQL.
4. **Licence LOC:** where is the licensed LOC limit per org stored (billing plugin / subscription)? It is needed for "LOC vs licence".
5. **Audience:** should project admins (not only org admins) see a project-scoped version of the Activity tab?
6. **Super-admin cross-org view:** in scope for the product, or internal only?
7. **Benchmarks (#13):** may anonymised data from all organizations be used to compare one customer with others? This needs a legal / contract check.
8. **Digest (#11):** who receives it (org admins only?), how often, and is it opt-in or opt-out?
9. **Alerts (#12):** which signals, which thresholds, and which channels (email only, or also webhooks / Slack)?
