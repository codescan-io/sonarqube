/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
import { Dict, Metric } from '../../types/types';

export const DEFAULT_METRICS: Dict<Metric> = {
  new_technical_debt: {
    id: 'AXJMbIl_PAOIsUIE3guE',
    key: 'new_technical_debt',
    type: 'WORK_DUR',
    name: 'Added Technical Debt',
    description: 'Added technical debt',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  blocker_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtt',
    key: 'blocker_violations',
    type: 'INT',
    name: 'Blocker Issues',
    description: 'Blocker issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  bugs: {
    id: 'AXJMbIl_PAOIsUIE3gt_',
    key: 'bugs',
    type: 'INT',
    name: 'Bugs',
    description: 'Bugs',
    domain: 'Reliability',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  classes: {
    id: 'AXJMbImPPAOIsUIE3gu5',
    key: 'classes',
    type: 'INT',
    name: 'Classes',
    description: 'Classes',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  code_smells: {
    id: 'AXJMbIl_PAOIsUIE3gt9',
    key: 'code_smells',
    type: 'INT',
    name: 'Code Smells',
    description: 'Code Smells',
    domain: 'Maintainability',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  cognitive_complexity: {
    id: 'AXJMbIl9PAOIsUIE3gtZ',
    key: 'cognitive_complexity',
    type: 'INT',
    name: 'Cognitive Complexity',
    description: 'Cognitive complexity',
    domain: 'Complexity',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  comment_lines: {
    id: 'AXJMbImPPAOIsUIE3gup',
    key: 'comment_lines',
    type: 'INT',
    name: 'Comment Lines',
    description: 'Number of comment lines',
    domain: 'Size',
    direction: 1,
    qualitative: false,
    hidden: false,
  },
  comment_lines_data: {
    id: 'AXJMbImPPAOIsUIE3guV',
    key: 'comment_lines_data',
    type: 'DATA',
    name: 'comment_lines_data',
    domain: 'Size',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  comment_lines_density: {
    id: 'AXJMbImPPAOIsUIE3guq',
    key: 'comment_lines_density',
    type: 'PERCENT',
    name: 'Comments (%)',
    description: 'Comments balanced by ncloc + comment lines',
    domain: 'Size',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  class_complexity: {
    id: 'AXJMbImPPAOIsUIE3guw',
    key: 'class_complexity',
    type: 'FLOAT',
    name: 'Complexity / Class',
    description: 'Complexity average by class',
    domain: 'Complexity',
    direction: -1,
    qualitative: true,
    hidden: true,
    decimalScale: 1,
  },
  file_complexity: {
    id: 'AXJMbImPPAOIsUIE3guu',
    key: 'file_complexity',
    type: 'FLOAT',
    name: 'Complexity / File',
    description: 'Complexity average by file',
    domain: 'Complexity',
    direction: -1,
    qualitative: true,
    hidden: true,
    decimalScale: 1,
  },
  function_complexity: {
    id: 'AXJMbImPPAOIsUIE3guy',
    key: 'function_complexity',
    type: 'FLOAT',
    name: 'Complexity / Function',
    description: 'Complexity average by function',
    domain: 'Complexity',
    direction: -1,
    qualitative: true,
    hidden: true,
    decimalScale: 1,
  },
  complexity_in_classes: {
    id: 'AXJMbImPPAOIsUIE3guv',
    key: 'complexity_in_classes',
    type: 'INT',
    name: 'Complexity in Classes',
    description: 'Cyclomatic complexity in classes',
    domain: 'Complexity',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  complexity_in_functions: {
    id: 'AXJMbImPPAOIsUIE3gux',
    key: 'complexity_in_functions',
    type: 'INT',
    name: 'Complexity in Functions',
    description: 'Cyclomatic complexity in functions',
    domain: 'Complexity',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  branch_coverage: {
    id: 'AXJMbIl9PAOIsUIE3gs-',
    key: 'branch_coverage',
    type: 'PERCENT',
    name: 'Condition Coverage',
    description: 'Condition coverage',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_branch_coverage: {
    id: 'AXJMbIl9PAOIsUIE3gs_',
    key: 'new_branch_coverage',
    type: 'PERCENT',
    name: 'Condition Coverage on New Code',
    description: 'Condition coverage of new/changed code',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  conditions_to_cover: {
    id: 'AXJMbIl9PAOIsUIE3gqt',
    key: 'conditions_to_cover',
    type: 'INT',
    name: 'Conditions to Cover',
    description: 'Conditions to cover',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  new_conditions_to_cover: {
    id: 'AXJMbIl9PAOIsUIE3gs7',
    key: 'new_conditions_to_cover',
    type: 'INT',
    name: 'Conditions to Cover on New Code',
    description: 'Conditions to cover on new code',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  confirmed_issues: {
    id: 'AXJMbIl_PAOIsUIE3gt8',
    key: 'confirmed_issues',
    type: 'INT',
    name: 'Confirmed Issues',
    description: 'Confirmed issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  coverage: {
    id: 'AXJMbIl9PAOIsUIE3gtg',
    key: 'coverage',
    type: 'PERCENT',
    name: 'Coverage',
    description: 'Coverage by tests',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_coverage: {
    id: 'AXJMbIl_PAOIsUIE3gth',
    key: 'new_coverage',
    type: 'PERCENT',
    name: 'Coverage on New Code',
    description: 'Coverage of new/changed code',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  critical_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtu',
    key: 'critical_violations',
    type: 'INT',
    name: 'Critical Issues',
    description: 'Critical issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  complexity: {
    id: 'AXJMbImPPAOIsUIE3gut',
    key: 'complexity',
    type: 'INT',
    name: 'Cyclomatic Complexity',
    description: 'Cyclomatic complexity',
    domain: 'Complexity',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  last_commit_date: {
    id: 'AXJMbImPPAOIsUIE3gua',
    key: 'last_commit_date',
    type: 'MILLISEC',
    name: 'Date of Last Commit',
    domain: 'SCM',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  development_cost: {
    id: 'AXJMbIl_PAOIsUIE3guI',
    key: 'development_cost',
    type: 'STRING',
    name: 'Development Cost',
    description: 'Development cost',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_development_cost: {
    id: 'AXJMbIl_PAOIsUIE3guJ',
    key: 'new_development_cost',
    type: 'FLOAT',
    name: 'Development Cost on New Code',
    description: 'Development cost on new code',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: true,
    decimalScale: 1,
  },
  directories: {
    id: 'AXJMbImPPAOIsUIE3gu9',
    key: 'directories',
    type: 'INT',
    name: 'Directories',
    description: 'Directories',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  duplicated_blocks: {
    id: 'AXJMbIl9PAOIsUIE3gsu',
    key: 'duplicated_blocks',
    type: 'INT',
    name: 'Duplicated Blocks',
    description: 'Duplicated blocks',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_duplicated_blocks: {
    id: 'AXJMbIl_PAOIsUIE3gto',
    key: 'new_duplicated_blocks',
    type: 'INT',
    name: 'Duplicated Blocks on New Code',
    description: 'Duplicated blocks on new code',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  duplicated_files: {
    id: 'AXJMbImPPAOIsUIE3gvA',
    key: 'duplicated_files',
    type: 'INT',
    name: 'Duplicated Files',
    description: 'Duplicated files',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  duplicated_lines: {
    id: 'AXJMbIl9PAOIsUIE3gss',
    key: 'duplicated_lines',
    type: 'INT',
    name: 'Duplicated Lines',
    description: 'Duplicated lines',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  duplicated_lines_density: {
    id: 'AXJMbIl_PAOIsUIE3gtp',
    key: 'duplicated_lines_density',
    type: 'PERCENT',
    name: 'Duplicated Lines (%)',
    description: 'Duplicated lines balanced by statements',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_duplicated_lines_density: {
    id: 'AXJMbIl_PAOIsUIE3gtq',
    key: 'new_duplicated_lines_density',
    type: 'PERCENT',
    name: 'Duplicated Lines (%) on New Code',
    description: 'Duplicated lines (%) on new code balanced by statements',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_duplicated_lines: {
    id: 'AXJMbIl9PAOIsUIE3gst',
    key: 'new_duplicated_lines',
    type: 'INT',
    name: 'Duplicated Lines on New Code',
    description: 'Duplicated Lines on New Code',
    domain: 'Duplications',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  duplications_data: {
    id: 'AXJMbIl_PAOIsUIE3gtr',
    key: 'duplications_data',
    type: 'DATA',
    name: 'Duplication Details',
    description: 'Duplications details',
    domain: 'Duplications',
    direction: 0,
    qualitative: false,
    hidden: false,
  },
  effort_to_reach_maintainability_rating_a: {
    id: 'AXJMbIl_PAOIsUIE3guM',
    key: 'effort_to_reach_maintainability_rating_a',
    type: 'WORK_DUR',
    name: 'Effort to Reach Maintainability Rating A',
    description: 'Effort to reach maintainability rating A',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  executable_lines_data: {
    id: 'AXJMbImPPAOIsUIE3guW',
    key: 'executable_lines_data',
    type: 'DATA',
    name: 'executable_lines_data',
    domain: 'Coverage',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  false_positive_issues: {
    id: 'AXJMbIl_PAOIsUIE3gt4',
    key: 'false_positive_issues',
    type: 'INT',
    name: 'False Positive Issues',
    description: 'False positive issues',
    domain: 'Issues',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  file_complexity_distribution: {
    id: 'AXJMbIl9PAOIsUIE3gtY',
    key: 'file_complexity_distribution',
    type: 'DISTRIB',
    name: 'File Distribution / Complexity',
    description: 'Files distribution /complexity',
    domain: 'Complexity',
    direction: 0,
    qualitative: true,
    hidden: true,
  },
  files: {
    id: 'AXJMbImPPAOIsUIE3gu6',
    key: 'files',
    type: 'INT',
    name: 'Files',
    description: 'Number of files',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  function_complexity_distribution: {
    id: 'AXJMbIl9PAOIsUIE3gtX',
    key: 'function_complexity_distribution',
    type: 'DISTRIB',
    name: 'Function Distribution / Complexity',
    description: 'Functions distribution /complexity',
    domain: 'Complexity',
    direction: 0,
    qualitative: true,
    hidden: true,
  },
  functions: {
    id: 'AXJMbImPPAOIsUIE3gu-',
    key: 'functions',
    type: 'INT',
    name: 'Functions',
    description: 'Functions',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  generated_lines: {
    id: 'AXJMbImPPAOIsUIE3gu0',
    key: 'generated_lines',
    type: 'INT',
    name: 'Generated Lines',
    description: 'Number of generated lines',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  generated_ncloc: {
    id: 'AXJMbImPPAOIsUIE3gu4',
    key: 'generated_ncloc',
    type: 'INT',
    name: 'Generated Lines of Code',
    description: 'Generated non Commenting Lines of Code',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  info_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtx',
    key: 'info_violations',
    type: 'INT',
    name: 'Info Issues',
    description: 'Info issues',
    domain: 'Issues',
    direction: 0,
    qualitative: true,
    hidden: false,
  },
  violations: {
    id: 'AXJMbImPPAOIsUIE3gul',
    key: 'violations',
    type: 'INT',
    name: 'Issues',
    description: 'Issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  last_change_on_maintainability_rating: {
    id: 'AXJMbImPPAOIsUIE3gud',
    key: 'last_change_on_maintainability_rating',
    type: 'DATA',
    name: 'Last Change on Maintainability Rating',
    domain: 'Maintainability',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  last_change_on_releasability_rating: {
    id: 'AXJMbImPPAOIsUIE3gue',
    key: 'last_change_on_releasability_rating',
    type: 'DATA',
    name: 'Last Change on Releasability Rating',
    domain: 'Releasability',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  last_change_on_reliability_rating: {
    id: 'AXJMbImPPAOIsUIE3guf',
    key: 'last_change_on_reliability_rating',
    type: 'DATA',
    name: 'Last Change on Reliability Rating',
    domain: 'Reliability',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  last_change_on_security_rating: {
    id: 'AXJMbImPPAOIsUIE3gug',
    key: 'last_change_on_security_rating',
    type: 'DATA',
    name: 'Last Change on Security Rating',
    domain: 'Security',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  last_change_on_security_review_rating: {
    id: 'AXJMbIl9PAOIsUIE3gs4',
    key: 'last_change_on_security_review_rating',
    type: 'DATA',
    name: 'Last Change on Security Review Rating',
    domain: 'Security',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  line_coverage: {
    id: 'AXJMbIl_PAOIsUIE3gtl',
    key: 'line_coverage',
    type: 'PERCENT',
    name: 'Line Coverage',
    description: 'Line coverage',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_line_coverage: {
    id: 'AXJMbIl_PAOIsUIE3gtm',
    key: 'new_line_coverage',
    type: 'PERCENT',
    name: 'Line Coverage on New Code',
    description: 'Line coverage of added/changed code',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  lines: {
    id: 'AXJMbImPPAOIsUIE3guz',
    key: 'lines',
    type: 'INT',
    name: 'Lines',
    description: 'Lines',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  ncloc: {
    id: 'AXJMbImPPAOIsUIE3gu1',
    key: 'ncloc',
    type: 'INT',
    name: 'Lines of Code',
    description: 'Non commenting lines of code',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  ncloc_language_distribution: {
    id: 'AXJMbImPPAOIsUIE3gu3',
    key: 'ncloc_language_distribution',
    type: 'DATA',
    name: 'Lines of Code Per Language',
    description: 'Non Commenting Lines of Code Distributed By Language',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  lines_to_cover: {
    id: 'AXJMbImPPAOIsUIE3gu_',
    key: 'lines_to_cover',
    type: 'INT',
    name: 'Lines to Cover',
    description: 'Lines to cover',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  new_lines_to_cover: {
    id: 'AXJMbIl_PAOIsUIE3gti',
    key: 'new_lines_to_cover',
    type: 'INT',
    name: 'Lines to Cover on New Code',
    description: 'Lines to cover on new code',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  leak_projects: {
    id: 'AXJMbImPPAOIsUIE3gvE',
    key: 'leak_projects',
    type: 'DATA',
    name: 'List of technical projects with their leaks',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  sqale_rating: {
    id: 'AXJMbIl_PAOIsUIE3guF',
    key: 'sqale_rating',
    type: 'RATING',
    name: 'Maintainability Rating',
    description: 'A-to-E rating based on the technical debt ratio',
    domain: 'Maintainability',
    direction: 0,
    qualitative: true,
    hidden: false,
  },
  maintainability_rating_distribution: {
    id: 'AX6QkqP7zEziun0YBqmh',
    key: 'maintainability_rating_distribution',
    type: 'DATA',
    name: 'Maintainability Rating Distribution',
    description: 'Maintainability rating distribution',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_maintainability_rating_distribution: {
    id: 'AX6QkqP8zEziun0YBqml',
    key: 'new_maintainability_rating_distribution',
    type: 'DATA',
    name: 'Maintainability Rating Distribution on New Code',
    description: 'Maintainability rating distribution on new code',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_maintainability_rating: {
    id: 'AXJMbIl_PAOIsUIE3guH',
    key: 'new_maintainability_rating',
    type: 'RATING',
    name: 'Maintainability Rating on New Code',
    description: 'Maintainability rating on new code',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  major_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtv',
    key: 'major_violations',
    type: 'INT',
    name: 'Major Issues',
    description: 'Major issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  minor_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtw',
    key: 'minor_violations',
    type: 'INT',
    name: 'Minor Issues',
    description: 'Minor issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  ncloc_data: {
    id: 'AXJMbImPPAOIsUIE3guU',
    key: 'ncloc_data',
    type: 'DATA',
    name: 'ncloc_data',
    domain: 'Size',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  new_blocker_violations: {
    id: 'AXJMbIl_PAOIsUIE3gtz',
    key: 'new_blocker_violations',
    type: 'INT',
    name: 'New Blocker Issues',
    description: 'New Blocker issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_bugs: {
    id: 'AXJMbIl_PAOIsUIE3guA',
    key: 'new_bugs',
    type: 'INT',
    name: 'New Bugs',
    description: 'New Bugs',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_code_smells: {
    id: 'AXJMbIl_PAOIsUIE3gt-',
    key: 'new_code_smells',
    type: 'INT',
    name: 'New Code Smells',
    description: 'New Code Smells',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_critical_violations: {
    id: 'AXJMbIl_PAOIsUIE3gt0',
    key: 'new_critical_violations',
    type: 'INT',
    name: 'New Critical Issues',
    description: 'New Critical issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_info_violations: {
    id: 'AXJMbIl_PAOIsUIE3gt3',
    key: 'new_info_violations',
    type: 'INT',
    name: 'New Info Issues',
    description: 'New Info issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_violations: {
    id: 'AXJMbIl_PAOIsUIE3gty',
    key: 'new_violations',
    type: 'INT',
    name: 'New Issues',
    description: 'New issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_lines: {
    id: 'AXJMbImPPAOIsUIE3gu2',
    key: 'new_lines',
    type: 'INT',
    name: 'New Lines',
    description: 'New lines',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  new_major_violations: {
    id: 'AXJMbIl_PAOIsUIE3gt1',
    key: 'new_major_violations',
    type: 'INT',
    name: 'New Major Issues',
    description: 'New Major issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_minor_violations: {
    id: 'AXJMbIl_PAOIsUIE3gt2',
    key: 'new_minor_violations',
    type: 'INT',
    name: 'New Minor Issues',
    description: 'New Minor issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_security_hotspots: {
    id: 'AXJMbIl9PAOIsUIE3gsw',
    key: 'new_security_hotspots',
    type: 'INT',
    name: 'New Security Hotspots',
    description: 'New Security Hotspots',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_vulnerabilities: {
    id: 'AXJMbIl_PAOIsUIE3guC',
    key: 'new_vulnerabilities',
    type: 'INT',
    name: 'New Vulnerabilities',
    description: 'New Vulnerabilities',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  unanalyzed_c: {
    id: 'AXTb6RMqLLQlB5osv3xN',
    key: 'unanalyzed_c',
    type: 'INT',
    name: 'Number of unanalyzed c files',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  unanalyzed_cpp: {
    id: 'AXTb6RMtLLQlB5osv3xO',
    key: 'unanalyzed_cpp',
    type: 'INT',
    name: 'Number of unanalyzed c++ files',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  open_issues: {
    id: 'AXJMbIl_PAOIsUIE3gt6',
    key: 'open_issues',
    type: 'INT',
    name: 'Open Issues',
    description: 'Open issues',
    domain: 'Issues',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  quality_profiles: {
    id: 'AXJMbImPPAOIsUIE3guZ',
    key: 'quality_profiles',
    type: 'DATA',
    name: 'Profiles',
    description: 'Details of quality profiles used during analysis',
    domain: 'General',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  projects: {
    id: 'AXJMbImPPAOIsUIE3guo',
    key: 'projects',
    type: 'INT',
    name: 'Project branches',
    description: 'Number of project branches',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  public_api: {
    id: 'AXJMbImPPAOIsUIE3gun',
    key: 'public_api',
    type: 'INT',
    name: 'Public API',
    description: 'Public API',
    domain: 'Documentation',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  public_documented_api_density: {
    id: 'AXJMbImPPAOIsUIE3gur',
    key: 'public_documented_api_density',
    type: 'PERCENT',
    name: 'Public Documented API (%)',
    description: 'Public documented classes and functions balanced by ncloc',
    domain: 'Documentation',
    direction: 1,
    qualitative: true,
    hidden: true,
    decimalScale: 1,
  },
  public_undocumented_api: {
    id: 'AXJMbImPPAOIsUIE3gus',
    key: 'public_undocumented_api',
    type: 'INT',
    name: 'Public Undocumented API',
    description: 'Public undocumented classes, functions and variables',
    domain: 'Documentation',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  quality_gate_details: {
    id: 'AXJMbImPPAOIsUIE3guY',
    key: 'quality_gate_details',
    type: 'DATA',
    name: 'Quality Gate Details',
    description: 'The project detailed status with regard to its quality gate',
    domain: 'General',
    direction: 0,
    qualitative: false,
    hidden: false,
  },
  alert_status: {
    id: 'AXJMbImPPAOIsUIE3guX',
    key: 'alert_status',
    type: 'LEVEL',
    name: 'Quality Gate Status',
    description: 'The project status with regard to its quality gate.',
    domain: 'Releasability',
    direction: 1,
    qualitative: true,
    hidden: false,
  },
  releasability_rating: {
    id: 'AXJMbImPPAOIsUIE3guc',
    key: 'releasability_rating',
    type: 'RATING',
    name: 'Releasability rating',
    domain: 'Releasability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  releasability_rating_distribution: {
    id: 'AX6QkqP7zEziun0YBqmg',
    key: 'releasability_rating_distribution',
    type: 'DATA',
    name: 'Releasability Rating Distribution',
    description: 'Releasability rating distribution',
    domain: 'Releasability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  reliability_rating: {
    id: 'AXJMbIl_PAOIsUIE3guP',
    key: 'reliability_rating',
    type: 'RATING',
    name: 'Reliability Rating',
    description: 'Reliability rating',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  reliability_rating_distribution: {
    id: 'AX6QkqP7zEziun0YBqmi',
    key: 'reliability_rating_distribution',
    type: 'DATA',
    name: 'Reliability Rating Distribution',
    description: 'Maintainability rating distribution',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_reliability_rating_distribution: {
    id: 'AX6QkqP8zEziun0YBqmm',
    key: 'new_reliability_rating_distribution',
    type: 'DATA',
    name: 'Reliability Rating Distribution on New Code',
    description: 'Maintainability rating distribution on new code',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_reliability_rating: {
    id: 'AXJMbIl_PAOIsUIE3guQ',
    key: 'new_reliability_rating',
    type: 'RATING',
    name: 'Reliability Rating on New Code',
    description: 'Reliability rating on new code',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  reliability_remediation_effort: {
    id: 'AXJMbIl_PAOIsUIE3guN',
    key: 'reliability_remediation_effort',
    type: 'WORK_DUR',
    name: 'Reliability Remediation Effort',
    description: 'Reliability Remediation Effort',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_reliability_remediation_effort: {
    id: 'AXJMbIl_PAOIsUIE3guO',
    key: 'new_reliability_remediation_effort',
    type: 'WORK_DUR',
    name: 'Reliability Remediation Effort on New Code',
    description: 'Reliability remediation effort on new code',
    domain: 'Reliability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  reopened_issues: {
    id: 'AXJMbIl_PAOIsUIE3gt7',
    key: 'reopened_issues',
    type: 'INT',
    name: 'Reopened Issues',
    description: 'Reopened issues',
    domain: 'Issues',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_hotspots: {
    id: 'AXJMbIl9PAOIsUIE3gsv',
    key: 'security_hotspots',
    type: 'INT',
    name: 'Security Hotspots',
    description: 'Security Hotspots',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  security_hotspots_reviewed: {
    id: 'AXJMbIl9PAOIsUIE3gs0',
    key: 'security_hotspots_reviewed',
    type: 'PERCENT',
    name: 'Security Hotspots Reviewed',
    description: 'Percentage of Security Hotspots Reviewed',
    domain: 'SecurityReview',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_security_hotspots_reviewed: {
    id: 'AXJMbIl9PAOIsUIE3gs1',
    key: 'new_security_hotspots_reviewed',
    type: 'PERCENT',
    name: 'Security Hotspots Reviewed on New Code',
    description: 'Percentage of Security Hotspots Reviewed on New Code',
    domain: 'SecurityReview',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  security_rating: {
    id: 'AXJMbIl_PAOIsUIE3guS',
    key: 'security_rating',
    type: 'RATING',
    name: 'Security Rating',
    description: 'Security rating',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_rating_distribution: {
    id: 'AX6QkqP7zEziun0YBqmj',
    key: 'security_rating_distribution',
    type: 'DATA',
    name: 'Security Rating Distribution',
    description: 'Security rating distribution',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_security_rating_distribution: {
    id: 'AX6QkqP8zEziun0YBqmn',
    key: 'new_security_rating_distribution',
    type: 'DATA',
    name: 'Security Rating Distribution on New Code',
    description: 'Security rating distribution on new code',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_security_rating: {
    id: 'AXJMbImPPAOIsUIE3guT',
    key: 'new_security_rating',
    type: 'RATING',
    name: 'Security Rating on New Code',
    description: 'Security rating on new code',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_remediation_effort: {
    id: 'AXJMbIl_PAOIsUIE3guG',
    key: 'security_remediation_effort',
    type: 'WORK_DUR',
    name: 'Security Remediation Effort',
    description: 'Security remediation effort',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  new_security_remediation_effort: {
    id: 'AXJMbIl_PAOIsUIE3guR',
    key: 'new_security_remediation_effort',
    type: 'WORK_DUR',
    name: 'Security Remediation Effort on New Code',
    description: 'Security remediation effort on new code',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_review_rating: {
    id: 'AXJMbIl9PAOIsUIE3gsx',
    key: 'security_review_rating',
    type: 'RATING',
    name: 'Security Review Rating',
    description: 'Security Review Rating',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_review_rating_distribution: {
    id: 'AX6QkqP8zEziun0YBqmk',
    key: 'security_review_rating_distribution',
    type: 'DATA',
    name: 'Security Review Rating Distribution',
    description: 'Security review rating distribution',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_security_review_rating_distribution: {
    id: 'AX6QkqP8zEziun0YBqmo',
    key: 'new_security_review_rating_distribution',
    type: 'DATA',
    name: 'Security Review Rating Distribution on New Code',
    description: 'Security review rating distribution on new code',
    domain: 'Security',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  new_security_review_rating: {
    id: 'AXJMbIl9PAOIsUIE3gtA',
    key: 'new_security_review_rating',
    type: 'RATING',
    name: 'Security Review Rating on New Code',
    description: 'Security Review Rating on New Code',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  security_hotspots_reviewed_status: {
    id: 'AXJMbIl9PAOIsUIE3gs2',
    key: 'security_hotspots_reviewed_status',
    type: 'INT',
    name: 'Security Review Reviewed Status',
    description: 'Security Review Reviewed Status',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  new_security_hotspots_reviewed_status: {
    id: 'AXJMbIl9PAOIsUIE3gtB',
    key: 'new_security_hotspots_reviewed_status',
    type: 'INT',
    name: 'Security Review Reviewed Status on New Code',
    description: 'Security Review Reviewed Status on New Code',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  security_hotspots_to_review_status: {
    id: 'AXJMbIl9PAOIsUIE3gs3',
    key: 'security_hotspots_to_review_status',
    type: 'INT',
    name: 'Security Review To Review Status',
    description: 'Security Review To Review Status',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  new_security_hotspots_to_review_status: {
    id: 'AXJMbIl9PAOIsUIE3gs5',
    key: 'new_security_hotspots_to_review_status',
    type: 'INT',
    name: 'Security Review To Review Status on New Code',
    description: 'Security Review To Review Status on New Code',
    domain: 'SecurityReview',
    direction: -1,
    qualitative: false,
    hidden: true,
  },
  skipped_tests: {
    id: 'AXJMbIl9PAOIsUIE3gtd',
    key: 'skipped_tests',
    type: 'INT',
    name: 'Skipped Unit Tests',
    description: 'Number of skipped unit tests',
    domain: 'Coverage',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  statements: {
    id: 'AXJMbImPPAOIsUIE3gum',
    key: 'statements',
    type: 'INT',
    name: 'Statements',
    description: 'Number of statements',
    domain: 'Size',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  sqale_index: {
    id: 'AXJMbIl_PAOIsUIE3guD',
    key: 'sqale_index',
    type: 'WORK_DUR',
    name: 'Technical Debt',
    description:
      'Total effort (in hours) to fix all the issues on the component and therefore to comply to all the requirements.',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  sqale_debt_ratio: {
    id: 'AXJMbIl_PAOIsUIE3guK',
    key: 'sqale_debt_ratio',
    type: 'PERCENT',
    name: 'Technical Debt Ratio',
    description:
      'Ratio of the actual technical debt compared to the estimated cost to develop the whole source code from scratch',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  new_sqale_debt_ratio: {
    id: 'AXJMbIl_PAOIsUIE3guL',
    key: 'new_sqale_debt_ratio',
    type: 'PERCENT',
    name: 'Technical Debt Ratio on New Code',
    description: 'Technical Debt Ratio of new/changed code.',
    domain: 'Maintainability',
    direction: -1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  maintainability_rating_effort: {
    id: 'AXJMbImPPAOIsUIE3gvD',
    key: 'maintainability_rating_effort',
    type: 'DATA',
    name: 'Total number of projects having worst maintainability rating',
    domain: 'Maintainability',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  reliability_rating_effort: {
    id: 'AXJMbImPPAOIsUIE3gvC',
    key: 'reliability_rating_effort',
    type: 'DATA',
    name: 'Total number of projects having worst reliability rating',
    domain: 'Reliability',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  security_rating_effort: {
    id: 'AXJMbImPPAOIsUIE3gvB',
    key: 'security_rating_effort',
    type: 'DATA',
    name: 'Total number of projects having worst security rating',
    domain: 'Security',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  security_review_rating_effort: {
    id: 'AXJMbIl9PAOIsUIE3gs6',
    key: 'security_review_rating_effort',
    type: 'DATA',
    name: 'Total number of projects having worst security review rating',
    domain: 'Security',
    direction: 0,
    qualitative: false,
    hidden: true,
  },
  releasability_effort: {
    id: 'AXJMbImPPAOIsUIE3gub',
    key: 'releasability_effort',
    type: 'INT',
    name: 'Total number of projects not production ready',
    domain: 'Releasability',
    direction: -1,
    qualitative: true,
    hidden: true,
  },
  uncovered_conditions: {
    id: 'AXJMbIl9PAOIsUIE3gs8',
    key: 'uncovered_conditions',
    type: 'INT',
    name: 'Uncovered Conditions',
    description: 'Uncovered conditions',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  new_uncovered_conditions: {
    id: 'AXJMbIl9PAOIsUIE3gs9',
    key: 'new_uncovered_conditions',
    type: 'INT',
    name: 'Uncovered Conditions on New Code',
    description: 'Uncovered conditions on new code',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  uncovered_lines: {
    id: 'AXJMbIl_PAOIsUIE3gtj',
    key: 'uncovered_lines',
    type: 'INT',
    name: 'Uncovered Lines',
    description: 'Uncovered lines',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  new_uncovered_lines: {
    id: 'AXJMbIl_PAOIsUIE3gtk',
    key: 'new_uncovered_lines',
    type: 'INT',
    name: 'Uncovered Lines on New Code',
    description: 'Uncovered lines on new code',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  test_execution_time: {
    id: 'AXJMbIl9PAOIsUIE3gtb',
    key: 'test_execution_time',
    type: 'MILLISEC',
    name: 'Unit Test Duration',
    description: 'Execution duration of unit tests',
    domain: 'Coverage',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  test_errors: {
    id: 'AXJMbIl9PAOIsUIE3gtc',
    key: 'test_errors',
    type: 'INT',
    name: 'Unit Test Errors',
    description: 'Number of unit test errors',
    domain: 'Coverage',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  test_failures: {
    id: 'AXJMbIl9PAOIsUIE3gte',
    key: 'test_failures',
    type: 'INT',
    name: 'Unit Test Failures',
    description: 'Number of unit test failures',
    domain: 'Coverage',
    direction: -1,
    qualitative: true,
    hidden: false,
  },
  tests: {
    id: 'AXJMbIl9PAOIsUIE3gta',
    key: 'tests',
    type: 'INT',
    name: 'Unit Tests',
    description: 'Number of unit tests',
    domain: 'Coverage',
    direction: 1,
    qualitative: false,
    hidden: false,
  },
  test_success_density: {
    id: 'AXJMbIl9PAOIsUIE3gtf',
    key: 'test_success_density',
    type: 'PERCENT',
    name: 'Unit Test Success (%)',
    description: 'Density of successful unit tests',
    domain: 'Coverage',
    direction: 1,
    qualitative: true,
    hidden: false,
    decimalScale: 1,
  },
  vulnerabilities: {
    id: 'AXJMbIl_PAOIsUIE3guB',
    key: 'vulnerabilities',
    type: 'INT',
    name: 'Vulnerabilities',
    description: 'Vulnerabilities',
    domain: 'Security',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
  wont_fix_issues: {
    id: 'AXJMbIl_PAOIsUIE3gt5',
    key: 'wont_fix_issues',
    type: 'INT',
    name: "Won't Fix Issues",
    description: "Won't fix issues",
    domain: 'Issues',
    direction: -1,
    qualitative: false,
    hidden: false,
  },
};
