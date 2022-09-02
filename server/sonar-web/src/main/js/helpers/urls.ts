/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import { getBaseUrl, Location } from 'sonar-ui-common/helpers/urls';
import { getProfilePath } from '../apps/quality-profiles/utils';
import { BranchLike, BranchParameters } from '../types/branch-like';
import { ComponentQualifier, isPortfolioLike } from '../types/component';
import { GraphType } from '../types/project-activity';
import { getBranchLikeQuery, isBranch, isMainBranch, isPullRequest } from './branch-like';

type Query = Location['query'];

export function getComponentOverviewUrl(
  componentKey: string,
  componentQualifier: ComponentQualifier | string,
  branchParameters?: BranchParameters
) {
  return isPortfolioLike(componentQualifier)
    ? getPortfolioUrl(componentKey)
    : getProjectQueryUrl(componentKey, branchParameters);
}

export function getProjectUrl(project: string, branch?: string): Location {
  return { pathname: '/dashboard', query: { id: project, branch } };
}

export function getGrcDashboardUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/dashboard', query: { id: project, branch } };
}

export function getGrcViolationsUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/violations', query: { id: project, branch } };
}


export function getGrcRerunAnalysisUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/analysis', query: { id: project, branch } };
}

export function getGrcOverviewUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/overview', query: { id: project, branch } };
}

export function getGrcActivityUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/activity', query: { 
    custom_metrics: "security_hotspots,security_hotspots_reviewed",
    graph:"custom",
    id: project, branch } };
}

export function getGrcInventoryUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/inventory', query: { id: project, branch } };
}
export function getGrcProfilesUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/profiles', query: { id: project, branch } };
}
export function getGrcRulesUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/policies', query: {
  languages:"sfmeta",
  repositories:"grc",
  id: project, branch } };
}
export function getGrcProjectSettingsUrl(project: string, branch?: string): Location {
  return { pathname: '/grc/settings', query: { id: project, branch } };
}



export function getProjectQueryUrl(project: string, branchParameters?: BranchParameters): Location {
  return { pathname: '/dashboard', query: { id: project, ...branchParameters } };
}

export function getPortfolioUrl(key: string): Location {
  return { pathname: '/portfolio', query: { id: key } };
}

export function getPortfolioAdminUrl(key: string, qualifier: string) {
  return { pathname: '/project/admin/extension/governance/console', query: { id: key, qualifier } };
}

export function getComponentBackgroundTaskUrl(componentKey: string, status?: string): Location {
  return { pathname: '/project/background_tasks', query: { id: componentKey, status } };
}

export function getBranchLikeUrl(project: string, branchLike?: BranchLike, grc?:boolean): Location {
  if (isPullRequest(branchLike)) {
    return getPullRequestUrl(project, branchLike.key);
  } else if (isBranch(branchLike) && !isMainBranch(branchLike)) {
    return getBranchUrl(project, branchLike.name);
  } else {
    return grc? getGrcDashboardUrl(project) : getProjectUrl(project);
  }
}

export function getBranchUrl(project: string, branch: string): Location {
  return { pathname: '/dashboard', query: { branch, id: project } };
}

export function getPullRequestUrl(project: string, pullRequest: string): Location {
  return { pathname: '/dashboard', query: { id: project, pullRequest } };
}

/**
 * Generate URL for a global issues page
 */
export function getIssuesUrl(query: Query, organization?: string): Location {
  const pathname = organization ? `/organizations/${organization}/issues` : '/issues';
  return { pathname, query };
}

/**
 * Generate URL for a component's issues page
 */
export function getComponentIssuesUrl(componentKey: string, query?: Query, grc?:boolean): Location {
  const path = grc? '/grc/violations':'/project/issues'
  return { pathname: path, query: { ...(query || {}), id: componentKey } };
}

/**
 * Generate URL for a component's security hotspot page
 */
export function getComponentSecurityHotspotsUrl(componentKey: string, query: Query = {}, grc?:boolean): Location {
  const { branch, pullRequest, sinceLeakPeriod, hotspots, assignedToMe, category } = query;
  const pathName = grc ? '/grc/violations' : '/security_hotspots';
  return {
    pathname: pathName,
    query: {
      id: componentKey,
      branch,
      pullRequest,
      sinceLeakPeriod,
      hotspots,
      assignedToMe,
      category
    }
  };
}

/**
 * Generate URL for a component's drilldown page
 */
export function getComponentDrilldownUrl(options: {
  componentKey: string;
  metric: string;
  branchLike?: BranchLike;
  selectionKey?: string;
  treemapView?: boolean;
  listView?: boolean;
}): Location {
  const { componentKey, metric, branchLike, selectionKey, treemapView, listView } = options;
  const query: Query = { id: componentKey, metric, ...getBranchLikeQuery(branchLike) };
  if (treemapView) {
    query.view = 'treemap';
  }
  if (listView) {
    query.view = 'list';
  }
  if (selectionKey) {
    query.selected = selectionKey;
  }
  return { pathname: '/component_measures', query };
}

export function getComponentDrilldownUrlWithSelection(
  componentKey: string,
  selectionKey: string,
  metric: string,
  branchLike?: BranchLike
): Location {
  return getComponentDrilldownUrl({ componentKey, selectionKey, metric, branchLike });
}

export function getMeasureTreemapUrl(componentKey: string, metric: string) {
  return getComponentDrilldownUrl({ componentKey, metric, treemapView: true });
}

export function getActivityUrl(component: string, branchLike?: BranchLike, graph?: GraphType) {
  return {
    pathname: '/project/activity',
    query: { id: component, graph, ...getBranchLikeQuery(branchLike) }
  };
}

/**
 * Generate URL for a component's measure history
 */
export function getMeasureHistoryUrl(component: string, metric: string, branchLike?: BranchLike) {
  return {
    pathname: '/project/activity',
    query: {
      id: component,
      graph: 'custom',
      custom_metrics: metric,
      ...getBranchLikeQuery(branchLike)
    }
  };
}

/**
 * Generate URL for a component's permissions page
 */
export function getComponentPermissionsUrl(componentKey: string): Location {
  return { pathname: '/project_roles', query: { id: componentKey } };
}

/**
 * Generate URL for a quality profile
 */
export function getQualityProfileUrl(
  name: string,
  language: string,
  organization?: string | null
): Location {
  return getProfilePath(name, language, organization);
}

export function getQualityGateUrl(key: string, organization?: string | null): Location {
  return {
    pathname: getQualityGatesUrl(organization).pathname + '/show/' + encodeURIComponent(key)
  };
}

export function getQualityGatesUrl(organization?: string | null): Location {
  return {
    pathname:
      (organization ? '/organizations/' + encodeURIComponent(organization) : '') + '/quality_gates'
  };
}

/**
 * Generate URL for the rules page
 */
export function getRulesUrl(query: Query, organization: string | null | undefined, grc:boolean = false): Location {
  let pathname = organization ? `/organizations/${organization}/rules` : '/coding_rules';
  if(grc){
    pathname = "/grc/policies";
  }
  return { pathname, query };
}

export function getGrcRulePermaLink(project: any, query: Query): Location {
    const pathname = "/grc/policies";
    query = {...query, ...{languages:"sfmeta", repositories:"grc", id: project}}
    return { pathname, query };
}

/**
 * Generate URL for the rules page filtering only active deprecated rules
 */
export function getDeprecatedActiveRulesUrl(
  query: Query = {},
  organization: string | null | undefined,
  grc:boolean = false
): Location {
  const baseQuery = { activation: 'true', statuses: 'DEPRECATED' };
  return getRulesUrl({ ...query, ...baseQuery }, organization, grc);
}

export function getRuleUrl(rule: string, organization: string | undefined,grc:boolean=false) {
  return getRulesUrl({ open: rule, rule_key: rule }, organization, grc);
}

export function getMarkdownHelpUrl(): string {
  return getBaseUrl() + '/markdown/help';
}

export function getCodeUrl(
  project: string,
  branchLike?: BranchLike,
  selected?: string,
  line?: number,
  grc?:boolean
) {
  const pathName = grc?"/grc/inventory":"/code"
  return {
    pathname: pathName,
    query: { id: project, ...getBranchLikeQuery(branchLike), selected, line }
  };
}

export function getOrganizationUrl(organization: string) {
  return `/organizations/${organization}`;
}

export function getHomePageUrl(homepage: T.HomePage) {
  switch (homepage.type) {
    case 'APPLICATION':
      return homepage.branch
        ? getProjectUrl(homepage.component, homepage.branch)
        : getProjectUrl(homepage.component);
    case 'PROJECT':
      return homepage.branch
        ? getBranchUrl(homepage.component, homepage.branch)
        : getProjectUrl(homepage.component);
    case 'ORGANIZATION':
      return getOrganizationUrl(homepage.organization);
    case 'PORTFOLIO':
      return getPortfolioUrl(homepage.component);
    case 'PORTFOLIOS':
      return '/portfolios';
    case 'MY_PROJECTS':
      return '/projects';
    case 'ISSUES':
    case 'MY_ISSUES':
      return { pathname: '/issues', query: { resolved: 'false' } };
  }

  // should never happen, but just in case...
  return '/projects';
}
