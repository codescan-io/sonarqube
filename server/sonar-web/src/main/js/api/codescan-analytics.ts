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

import { get, parseJSON } from '../helpers/request';

/*
 * Organization analytics served by codescanng-services from CodeScan's own data: debt health, AI add-ons and IDE plugins.
 * The SonarQube-side analytics live in ./analytics.ts.
 */

const BASE = '/_codescan/analytics';

export interface OrganizationProjectHealth {
  analyzedAt: number;
  grade?: string;
  previous?: number;
  projectKey: string;
  projectName?: string;
  score: number;
}

export interface OrganizationHealth {
  lowest: OrganizationProjectHealth[];
  movers: OrganizationProjectHealth[];
  projectsScored: number;
  qualityGateChanges: Array<{
    date: number;
    projectKey: string;
    projectName?: string;
    /** As stored by the platform, e.g. "Failed" or "Passed". */
    status: string;
  }>;
  rootCauses: Array<{
    effortMinutes: number;
    issueCount: number;
    projectKey: string;
    severity: string;
    title: string;
  }>;
  /** Average latest health score, 0-100; missing until a project has a debt analysis. */
  score?: number;
  trend: Array<{ projects: number; score?: number; weekStart: number }>;
}

export interface AiValue {
  codefixJobs: Record<string, number>;
  credits: Array<{ addon: string; calls: number; credits: number }>;
  days: number;
  fixJobs: {
    failed: number;
    inProgress: number;
    issuesResolved: number;
    issuesSelected: number;
    merged: number;
    pullRequests: number;
    succeeded: number;
    total: number;
  };
  totalCredits: number;
}

export interface IdeAdoption {
  activeUsers: number;
  byIde: Array<{ ideType: string; lines: number; sessions: number; users: number }>;
  days: number;
  members: number;
}

export function getOrganizationHealth(
  organizationKey: string,
  weeks = 12,
): Promise<OrganizationHealth> {
  return get(`${BASE}/health`, { organizationKey, weeks }).then(parseJSON);
}

export function getAiValue(organizationKey: string, days = 90): Promise<AiValue> {
  return get(`${BASE}/ai-value`, { organizationKey, days }).then(parseJSON);
}

export function getIdeAdoption(organizationKey: string, days = 30): Promise<IdeAdoption> {
  return get(`${BASE}/ide-adoption`, { organizationKey, days }).then(parseJSON);
}

export interface PullRequestTypeStats {
  analyses: number;
  failedAnalyses: number;
  failedJobs: number;
  projects: number;
  projectsWithPullRequests: number;
  pullRequestsAnalyzed: number;
  pullRequestsReceived: number;
  /** CodeScan integration type ("metadata_api", "github"…), "ci_" + DevOps platform, or "ci". */
  type: string;
}

export interface PullRequestProjectStats {
  analyses: number;
  failedAnalyses: number;
  failedJobs: number;
  lastAnalysisAt?: number;
  projectKey: string;
  projectName?: string;
  pullRequestsAnalyzed: number;
  /** Pull requests CodeScan started an analysis job for; 0 for projects analyzed from customer CI. */
  pullRequestsReceived: number;
  type: string;
}

export interface PullRequestAnalytics {
  byType: PullRequestTypeStats[];
  days: number;
  projects: PullRequestProjectStats[];
  total: number;
}

export function getPullRequestAnalytics(data: {
  days: number;
  organizationKey: string;
  page?: number;
  pageSize?: number;
  type?: string;
}): Promise<PullRequestAnalytics> {
  return get(`${BASE}/pull-requests`, data).then(parseJSON);
}
