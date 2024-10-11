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
import { cloneDeep, keyBy, times, uniqueId } from 'lodash';
import { RuleDescriptionSections } from '../../apps/coding-rules/rule';
import { mockIssueChangelog } from '../../helpers/mocks/issues';
import { mockSnippetsByComponent } from '../../helpers/mocks/sources';
import { RequestData } from '../../helpers/request';
import { getStandards } from '../../helpers/security-standard';
import {
  mockLoggedInUser,
  mockPaging,
  mockRawIssue,
  mockRule,
  mockRuleDetails,
  mockUser,
} from '../../helpers/testMocks';
import {
  ASSIGNEE_ME,
  IssueActions,
  IssueCharacteristic,
  IssueResolution,
  IssueScope,
  IssueSeverity,
  IssueStatus,
  IssueTransition,
  IssueType,
  RawFacet,
  RawIssue,
  RawIssuesResponse,
  ReferencedComponent,
} from '../../types/issues';
import { SearchRulesQuery } from '../../types/rules';
import { Standards } from '../../types/security';
import {
  Dict,
  FlowType,
  Rule,
  RuleActivation,
  RuleDetails,
  SnippetsByComponent,
} from '../../types/types';
import { LoggedInUser, NoticeType } from '../../types/users';
import {
  addIssueComment,
  bulkChangeIssues,
  deleteIssueComment,
  editIssueComment,
  getIssueChangelog,
  getIssueFlowSnippets,
  searchIssueTags,
  searchIssues,
  setIssueAssignee,
  setIssueSeverity,
  setIssueTags,
  setIssueTransition,
  setIssueType,
} from '../issues';
import { getRuleDetails, searchRules } from '../rules';
import { dismissNotice, getCurrentUser, searchUsers } from '../users';

function mockReferenceComponent(override?: Partial<ReferencedComponent>) {
  return {
    key: 'component1',
    name: 'Component1',
    uuid: 'id1',
    enabled: true,
    ...override,
  };
}

function generateReferenceComponentsForIssues(issueData: IssueData[]) {
  return issueData
    .reduce((componentKeys, response) => {
      const componentKey = response.issue.component;
      if (!componentKeys.includes(componentKey)) {
        return [...componentKeys, componentKey];
      }

      return componentKeys;
    }, [] as string[])
    .map((key) => mockReferenceComponent({ key, enabled: true }));
}

interface IssueData {
  issue: RawIssue;
  snippets: Dict<SnippetsByComponent>;
}

export default class IssuesServiceMock {
  isAdmin = false;
  currentUser: LoggedInUser;
  standards?: Standards;
  defaultList: IssueData[];
  rulesList: Rule[];
  list: IssueData[];

  constructor() {
    this.currentUser = mockLoggedInUser();
    this.defaultList = [
      {
        issue: mockRawIssue(false, {
          key: 'issue101',
          component: 'foo:test1.js',
          creationDate: '2023-01-05T09:36:01+0100',
          message: 'Issue with no location message',
          characteristic: IssueCharacteristic.Secure,
          type: IssueType.Vulnerability,
          rule: 'simpleRuleId',
          textRange: {
            startLine: 10,
            endLine: 10,
            startOffset: 0,
            endOffset: 2,
          },
          flows: [
            {
              locations: [
                {
                  component: 'foo:test1.js',
                  textRange: {
                    startLine: 1,
                    endLine: 1,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
            {
              locations: [
                {
                  component: 'foo:test2.js',
                  textRange: {
                    startLine: 20,
                    endLine: 20,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
          ],
          resolution: IssueResolution.WontFix,
          scope: IssueScope.Main,
          tags: ['tag0', 'tag1'],
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'test1.js',
              'foo',
              times(40, (i) => i + 1)
            ),
            mockSnippetsByComponent(
              'test2.js',
              'foo',
              times(40, (i) => i + 1)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          key: 'issue11',
          component: 'foo:test1.js',
          creationDate: '2022-01-01T09:36:01+0100',
          message: 'FlowIssue',
          characteristic: IssueCharacteristic.Clear,
          type: IssueType.CodeSmell,
          severity: IssueSeverity.Minor,
          rule: 'simpleRuleId',
          textRange: {
            startLine: 10,
            endLine: 10,
            startOffset: 0,
            endOffset: 2,
          },
          flows: [
            {
              type: FlowType.DATA,
              description: 'Backtracking 1',
              locations: [
                {
                  component: 'foo:test1.js',
                  msg: 'Data location 1',
                  textRange: {
                    startLine: 20,
                    endLine: 20,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
                {
                  component: 'foo:test1.js',
                  msg: 'Data location 2',
                  textRange: {
                    startLine: 21,
                    endLine: 21,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
            {
              type: FlowType.EXECUTION,
              locations: [
                {
                  component: 'foo:test2.js',
                  msg: 'Execution location 1',
                  textRange: {
                    startLine: 20,
                    endLine: 20,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
                {
                  component: 'foo:test2.js',
                  msg: 'Execution location 2',
                  textRange: {
                    startLine: 22,
                    endLine: 22,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
                {
                  component: 'foo:test2.js',
                  msg: 'Execution location 3',
                  textRange: {
                    startLine: 5,
                    endLine: 5,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
          ],
          tags: ['tag1'],
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'test1.js',
              'foo',
              times(40, (i) => i + 1)
            ),
            mockSnippetsByComponent(
              'test2.js',
              'foo',
              times(40, (i) => i + 1)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          key: 'issue0',
          component: 'foo:test1.js',
          message: 'Issue on file',
          assignee: mockLoggedInUser().login,
          characteristic: IssueCharacteristic.Clear,
          type: IssueType.CodeSmell,
          rule: 'simpleRuleId',
          textRange: undefined,
          line: undefined,
          scope: IssueScope.Test,
        }),
        snippets: {},
      },
      {
        issue: mockRawIssue(false, {
          key: 'issue1',
          component: 'foo:huge.js',
          message: 'Fix this',
          characteristic: IssueCharacteristic.Secure,
          type: IssueType.Vulnerability,
          rule: 'simpleRuleId',
          textRange: {
            startLine: 10,
            endLine: 10,
            startOffset: 0,
            endOffset: 2,
          },
          flows: [
            {
              locations: [
                {
                  component: 'foo:huge.js',
                  msg: 'location 1',
                  textRange: {
                    startLine: 1,
                    endLine: 1,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
            {
              locations: [
                {
                  component: 'foo:huge.js',
                  msg: 'location 2',
                  textRange: {
                    startLine: 50,
                    endLine: 50,
                    startOffset: 0,
                    endOffset: 1,
                  },
                },
              ],
            },
          ],
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'huge.js',
              'foo',
              times(80, (i) => i + 1)
            ),
            mockSnippetsByComponent(
              'huge.js',
              'foo',
              times(80, (i) => i + 1)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          actions: Object.values(IssueActions),
          transitions: ['confirm', 'resolve', 'falsepositive', 'wontfix'],
          key: 'issue2',
          component: 'foo:test2.js',
          message: 'Fix that',
          rule: 'advancedRuleId',
          textRange: {
            startLine: 25,
            endLine: 25,
            startOffset: 0,
            endOffset: 1,
          },
          ruleDescriptionContextKey: 'spring',
          resolution: IssueResolution.Unresolved,
          status: IssueStatus.Open,
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'test2.js',
              'foo',
              times(40, (i) => i + 20)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          key: 'issue3',
          component: 'foo:test2.js',
          message: 'Second issue',
          rule: 'other',
          textRange: {
            startLine: 28,
            endLine: 28,
            startOffset: 0,
            endOffset: 1,
          },
          resolution: IssueResolution.Fixed,
          status: IssueStatus.Confirmed,
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'test2.js',
              'foo',
              times(40, (i) => i + 20)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          actions: Object.values(IssueActions),
          transitions: ['confirm', 'resolve', 'falsepositive', 'wontfix'],
          key: 'issue4',
          component: 'foo:test2.js',
          message: 'Issue with tags',
          rule: 'other',
          textRange: {
            startLine: 25,
            endLine: 25,
            startOffset: 0,
            endOffset: 1,
          },
          ruleDescriptionContextKey: 'spring',
          ruleStatus: 'DEPRECATED',
          quickFixAvailable: true,
          tags: ['unused'],
          project: 'org.project2',
          assignee: 'email1@sonarsource.com',
          author: 'email3@sonarsource.com',
        }),
        snippets: keyBy(
          [
            mockSnippetsByComponent(
              'test2.js',
              'foo',
              times(40, (i) => i + 20)
            ),
          ],
          'component.key'
        ),
      },
      {
        issue: mockRawIssue(false, {
          key: 'issue1101',
          component: 'foo:test5.js',
          message: 'Issue on page 2',
          rule: 'simpleRuleId',
          textRange: undefined,
          line: undefined,
        }),
        snippets: {},
      },
    ];
    this.rulesList = [
      mockRule({
        key: 'simpleRuleId',
        name: 'Simple rule',
        lang: 'java',
        langName: 'Java',
        type: 'CODE_SMELL',
      }),
      mockRule({
        key: 'advancedRuleId',
        name: 'Advanced rule',
        lang: 'web',
        langName: 'HTML',
        type: 'VULNERABILITY',
      }),
      mockRule({
        key: 'cpp:S6069',
        lang: 'cpp',
        langName: 'C++',
        name: 'Security hotspot rule',
        type: 'SECURITY_HOTSPOT',
      }),
      mockRule({
        key: 'tsql:S131',
        name: '"CASE" expressions should end with "ELSE" clauses',
        lang: 'tsql',
        langName: 'T-SQL',
      }),
    ];

    this.list = cloneDeep(this.defaultList);

    jest.mocked(searchIssues).mockImplementation(this.handleSearchIssues);
    jest.mocked(getRuleDetails).mockImplementation(this.handleGetRuleDetails);
    jest.mocked(searchRules).mockImplementation(this.handleSearchRules);
    jest.mocked(getIssueFlowSnippets).mockImplementation(this.handleGetIssueFlowSnippets);
    jest.mocked(bulkChangeIssues).mockImplementation(this.handleBulkChangeIssues);
    jest.mocked(getCurrentUser).mockImplementation(this.handleGetCurrentUser);
    jest.mocked(dismissNotice).mockImplementation(this.handleDismissNotification);
    jest.mocked(setIssueType).mockImplementation(this.handleSetIssueType);
    jest.mocked(setIssueAssignee).mockImplementation(this.handleSetIssueAssignee);
    jest.mocked(setIssueSeverity).mockImplementation(this.handleSetIssueSeverity);
    jest.mocked(setIssueTransition).mockImplementation(this.handleSetIssueTransition);
    jest.mocked(setIssueTags).mockImplementation(this.handleSetIssueTags);
    jest.mocked(addIssueComment).mockImplementation(this.handleAddComment);
    jest.mocked(editIssueComment).mockImplementation(this.handleEditComment);
    jest.mocked(deleteIssueComment).mockImplementation(this.handleDeleteComment);
    jest.mocked(searchUsers).mockImplementation(this.handleSearchUsers);
    jest.mocked(searchIssueTags).mockImplementation(this.handleSearchIssueTags);
    jest.mocked(getIssueChangelog).mockImplementation(this.handleGetIssueChangelog);
  }

  reset = () => {
    this.list = cloneDeep(this.defaultList);
    this.currentUser = mockLoggedInUser();
  };

  setCurrentUser = (user: LoggedInUser) => {
    this.currentUser = user;
  };

  setIssueList = (list: IssueData[]) => {
    this.list = list;
  };

  async getStandards(): Promise<Standards> {
    if (this.standards) {
      return this.standards;
    }
    this.standards = await getStandards();
    return this.standards;
  }

  owasp2021FacetList(): RawFacet {
    return {
      property: 'owaspTop10-2021',
      values: [{ val: 'a1', count: 0 }],
    };
  }

  setIsAdmin(isAdmin: boolean) {
    this.isAdmin = isAdmin;
  }

  handleBulkChangeIssues = (issueKeys: string[], query: RequestData): Promise<void> => {
    //For now we only check for issue severity change.
    this.list
      .filter((i) => issueKeys.includes(i.issue.key))
      .forEach((data) => {
        data.issue.severity = query.set_severity;
      });
    return this.reply(undefined);
  };

  handleGetIssueFlowSnippets = (issueKey: string): Promise<Dict<SnippetsByComponent>> => {
    const issue = this.list.find((i) => i.issue.key === issueKey);
    if (issue === undefined) {
      return Promise.reject({ errors: [{ msg: `No issue has been found for id ${issueKey}` }] });
    }
    return this.reply(issue.snippets);
  };

  handleSearchRules = (req: SearchRulesQuery) => {
    const rules = this.rulesList.filter((rule) => {
      const query = req.q?.toLowerCase() || '';
      const nameMatches = rule.name.toLowerCase().includes(query);
      const keyMatches = rule.key.toLowerCase().includes(query);
      const isTypeRight = req.types?.includes(rule.type);
      return isTypeRight && (nameMatches || keyMatches);
    });
    return this.reply({
      p: 1,
      ps: 30,
      rules,
      total: rules.length,
    });
  };

  handleGetRuleDetails = (parameters: {
    actives?: boolean;
    key: string;
  }): Promise<{ actives?: RuleActivation[]; rule: RuleDetails }> => {
    if (parameters.key === 'advancedRuleId') {
      return this.reply({
        rule: mockRuleDetails({
          key: parameters.key,
          name: 'Advanced rule',
          htmlNote: '<h1>Extended Description</h1>',
          educationPrinciples: ['defense_in_depth'],
          descriptionSections: [
            { key: RuleDescriptionSections.INTRODUCTION, content: '<h1>Into</h1>' },
            { key: RuleDescriptionSections.ROOT_CAUSE, content: '<h1>Because</h1>' },
            { key: RuleDescriptionSections.HOW_TO_FIX, content: '<h1>Fix with</h1>' },
            {
              content: '<p> Context 1 content<p>',
              key: RuleDescriptionSections.HOW_TO_FIX,
              context: {
                key: 'spring',
                displayName: 'Spring',
              },
            },
            {
              content: '<p> Context 2 content<p>',
              key: RuleDescriptionSections.HOW_TO_FIX,
              context: {
                key: 'context_2',
                displayName: 'Context 2',
              },
            },
            {
              content: '<p> Context 3 content<p>',
              key: RuleDescriptionSections.HOW_TO_FIX,
              context: {
                key: 'context_3',
                displayName: 'Context 3',
              },
            },
            { key: RuleDescriptionSections.RESOURCES, content: '<h1>Link</h1>' },
          ],
        }),
      });
    }
    return this.reply({
      rule: mockRuleDetails({
        key: parameters.key,
        name: 'Simple rule',
        htmlNote: '<h1>Note</h1>',
        descriptionSections: [
          {
            key: RuleDescriptionSections.DEFAULT,
            content: '<h1>Default</h1> Default description',
          },
        ],
      }),
    });
  };

  mockFacetDetailResponse = (query: RequestData): RawFacet[] => {
    const facets = (query.facets ?? '').split(',');
    const types: Exclude<IssueType, IssueType.SecurityHotspot>[] = (
      query.types ?? 'BUG,CODE_SMELL,VULNERABILITY'
    ).split(',');
    return facets.map((name: string): RawFacet => {
      if (name === 'owaspTop10-2021') {
        return this.owasp2021FacetList();
      }
      if (name === 'tags') {
        return {
          property: name,
          values: [
            {
              val: 'unused',
              count: 12842,
            },
            {
              val: 'confusing',
              count: 124,
            },
          ],
        };
      }
      if (name === 'projects') {
        return {
          property: name,
          values: [
            { val: 'org.project1', count: 14685 },
            { val: 'org.project2', count: 3890 },
          ],
        };
      }
      if (name === 'assignees') {
        return {
          property: name,
          values: [
            { val: 'email1@sonarsource.com', count: 675 },
            { val: 'email2@sonarsource.com', count: 531 },
          ],
        };
      }
      if (name === 'author') {
        return {
          property: name,
          values: [
            { val: 'email3@sonarsource.com', count: 421 },
            { val: 'email4@sonarsource.com', count: 123 },
          ],
        };
      }
      if (name === 'rules') {
        return {
          property: name,
          values: [
            { val: 'simpleRuleId', count: 8816 },
            { val: 'advancedRuleId', count: 2060 },
            { val: 'other', count: 1324 },
          ],
        };
      }
      if (name === 'languages') {
        const counters = {
          [IssueType.Bug]: { java: 4100, ts: 500 },
          [IssueType.CodeSmell]: { java: 21000, ts: 2000 },
          [IssueType.Vulnerability]: { java: 111, ts: 674 },
        };
        return {
          property: name,
          values: [
            {
              val: 'java',
              count: types.reduce<number>((acc, type) => acc + counters[type].java, 0),
            },
            {
              val: 'ts',
              count: types.reduce<number>((acc, type) => acc + counters[type].ts, 0),
            },
          ],
        };
      }
      return {
        property: name,
        values: [],
      };
    });
  };

  handleSearchIssues = (query: RequestData): Promise<RawIssuesResponse> => {
    const facets = this.mockFacetDetailResponse(query);

    // Filter list (only supports assignee, type and severity)
    const filteredList = this.list
      .filter((item) => {
        if (!query.assignees) {
          return true;
        }
        if (query.assignees === ASSIGNEE_ME) {
          return item.issue.assignee === mockLoggedInUser().login;
        }
        return query.assignees.split(',').includes(item.issue.assignee);
      })
      .filter((item) => {
        if (!query.tags) {
          return true;
        }
        if (!item.issue.tags) {
          return false;
        }
        return item.issue.tags.some((tag) => query.tags?.split(',').includes(tag));
      })
      .filter(
        (item) =>
          !query.createdBefore || new Date(item.issue.creationDate) <= new Date(query.createdBefore)
      )
      .filter(
        (item) =>
          !query.createdAfter || new Date(item.issue.creationDate) >= new Date(query.createdAfter)
      )
      .filter(
        (item) =>
          !query.characteristics ||
          query.characteristics.split(',').includes(item.issue.characteristic)
      )
      .filter((item) => !query.types || query.types.split(',').includes(item.issue.type))
      .filter(
        (item) => !query.severities || query.severities.split(',').includes(item.issue.severity)
      )
      .filter((item) => !query.scopes || query.scopes.split(',').includes(item.issue.scope))
      .filter((item) => !query.statuses || query.statuses.split(',').includes(item.issue.status))
      .filter((item) => !query.projects || query.projects.split(',').includes(item.issue.project))
      .filter((item) => !query.rules || query.rules.split(',').includes(item.issue.rule))
      .filter(
        (item) => !query.resolutions || query.resolutions.split(',').includes(item.issue.resolution)
      )
      .filter(
        (item) =>
          !query.inNewCodePeriod || new Date(item.issue.creationDate) > new Date('2023-01-10')
      );

    // Splice list items according to paging using a fixed page size
    const pageIndex = query.p || 1;
    const pageSize = 7;
    const listItems = filteredList.slice((pageIndex - 1) * pageSize, pageIndex * pageSize);

    // Generate response
    return this.reply({
      components: generateReferenceComponentsForIssues(filteredList),
      effortTotal: 199629,
      facets,
      issues: listItems.map((line) => line.issue),
      languages: [{ name: 'java' }, { name: 'python' }, { name: 'ts' }],
      paging: mockPaging({
        pageIndex,
        pageSize,
        total: filteredList.length,
      }),
      rules: this.rulesList,
      users: [
        { login: 'login0' },
        { login: 'login1', name: 'Login 1' },
        { login: 'login2', name: 'Login 2' },
      ],
    });
  };

  handleGetCurrentUser = () => {
    return this.reply(this.currentUser);
  };

  handleDismissNotification = (noticeType: NoticeType) => {
    if (noticeType === NoticeType.EDUCATION_PRINCIPLES) {
      return this.reply(true);
    }

    return Promise.reject();
  };

  handleSetIssueType = (data: { issue: string; type: IssueType }) => {
    return this.getActionsResponse({ type: data.type }, data.issue);
  };

  handleSetIssueSeverity = (data: { issue: string; severity: string }) => {
    return this.getActionsResponse({ severity: data.severity }, data.issue);
  };

  handleSetIssueAssignee = (data: { issue: string; assignee?: string }) => {
    return this.getActionsResponse(
      { assignee: data.assignee === '_me' ? this.currentUser.login : data.assignee },
      data.issue
    );
  };

  handleSetIssueTransition = (data: { issue: string; transition: string }) => {
    const statusMap: { [key: string]: IssueStatus } = {
      [IssueTransition.Confirm]: IssueStatus.Confirmed,
      [IssueTransition.UnConfirm]: IssueStatus.Reopened,
      [IssueTransition.Resolve]: IssueStatus.Resolved,
      [IssueTransition.WontFix]: IssueStatus.Resolved,
      [IssueTransition.FalsePositive]: IssueStatus.Resolved,
    };
    const transitionMap: Dict<IssueTransition[]> = {
      [IssueStatus.Reopened]: [
        IssueTransition.Confirm,
        IssueTransition.Resolve,
        IssueTransition.FalsePositive,
        IssueTransition.WontFix,
      ],
      [IssueStatus.Open]: [
        IssueTransition.Confirm,
        IssueTransition.Resolve,
        IssueTransition.FalsePositive,
        IssueTransition.WontFix,
      ],
      [IssueStatus.Confirmed]: [
        IssueTransition.Resolve,
        IssueTransition.UnConfirm,
        IssueTransition.FalsePositive,
        IssueTransition.WontFix,
      ],
      [IssueStatus.Resolved]: [IssueTransition.Reopen],
    };

    const resolutionMap: Dict<string> = {
      [IssueTransition.WontFix]: IssueResolution.WontFix,
      [IssueTransition.FalsePositive]: IssueResolution.FalsePositive,
    };

    return this.getActionsResponse(
      {
        status: statusMap[data.transition],
        transitions: transitionMap[statusMap[data.transition]],
        resolution: resolutionMap[data.transition],
      },
      data.issue
    );
  };

  handleSetIssueTags = (data: { issue: string; tags: string }) => {
    const tagsArr = data.tags.split(',');
    return this.getActionsResponse({ tags: tagsArr }, data.issue);
  };

  handleAddComment = (data: { issue: string; text: string }) => {
    return this.getActionsResponse(
      {
        comments: [
          {
            createdAt: '2022-07-28T11:30:04+0200',
            htmlText: data.text,
            key: uniqueId(),
            login: 'admin',
            markdown: data.text,
            updatable: true,
          },
        ],
      },
      data.issue
    );
  };

  handleEditComment = (data: { comment: string; text: string }) => {
    const issueKey = this.list.find((i) => i.issue.comments?.some((c) => c.key === data.comment))
      ?.issue.key;
    if (!issueKey) {
      throw new Error(`Couldn't find issue for comment ${data.comment}`);
    }
    return this.getActionsResponse(
      {
        comments: [
          {
            createdAt: '2022-07-28T11:30:04+0200',
            htmlText: data.text,
            key: data.comment,
            login: 'admin',
            markdown: data.text,
            updatable: true,
          },
        ],
      },
      issueKey
    );
  };

  handleDeleteComment = (data: { comment: string }) => {
    const issue = this.list.find((i) =>
      i.issue.comments?.some((c) => c.key === data.comment)
    )?.issue;
    if (!issue) {
      throw new Error(`Couldn't find issue for comment ${data.comment}`);
    }
    return this.getActionsResponse(
      {
        comments: issue.comments?.filter((c) => c.key !== data.comment),
      },
      issue.key
    );
  };

  handleSearchUsers = () => {
    return this.reply({
      paging: mockPaging({ pageIndex: 1, pageSize: 5, total: 1 }),
      users: [mockUser({ login: 'luke', name: 'Skywalker' })],
    });
  };

  handleSearchIssueTags = () => {
    return this.reply(['accessibility', 'android']);
  };

  handleGetIssueChangelog = (_issue: string) => {
    return this.reply({
      changelog: [
        mockIssueChangelog({
          creationDate: '2018-09-01',
          diffs: [
            {
              key: 'status',
              newValue: IssueStatus.Reopened,
              oldValue: IssueStatus.Confirmed,
            },
          ],
        }),
        mockIssueChangelog({
          creationDate: '2018-10-01',
          diffs: [
            {
              key: 'assign',
              newValue: 'darth.vader',
              oldValue: 'luke.skywalker',
            },
          ],
        }),
      ],
    });
  };

  getActionsResponse = (overrides: Partial<RawIssue>, issueKey: string) => {
    const issueDataSelected = this.list.find((l) => l.issue.key === issueKey);

    if (!issueDataSelected) {
      throw new Error(`Coulnd't find issue for key ${issueKey}`);
    }

    issueDataSelected.issue = {
      ...issueDataSelected.issue,
      ...overrides,
    };

    return this.reply({
      issue: issueDataSelected.issue,
    });
  };

  reply<T>(response: T): Promise<T> {
    return Promise.resolve(cloneDeep(response));
  }
}
