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

import {Checkbox, RadioButtonGroup, Spinner} from '@sonarsource/echoes-react';
import {countBy, flattenDeep, pickBy, sortBy} from 'lodash';
import * as React from 'react';
import {FormattedMessage} from 'react-intl';
import {
  addGlobalErrorMessage,
  ButtonPrimary,
  DatePicker,
  FlagMessage,
  FormField,
  Highlight,
  HelperHintIcon,
  InputTextArea,
  LightLabel,
  Modal,
  Note,
  SelectionCard,
} from '~design-system';
import HelpTooltip from '~sonar-aligned/components/controls/HelpTooltip';
import {throwGlobalError} from '~sonar-aligned/helpers/error';
import {bulkChangeIssues, searchIssueTags} from '../../../api/issues';
import withComponentContext from '../../../app/components/componentContext/withComponentContext';
import FormattingTips from '../../../components/common/FormattingTips';
import {
  issueSupportsExceptionExpiryPicker,
  isTransitionHidden,
  transitionRequiresComment,
  transitionRequiresMandatoryComment,
} from '../../../components/issue/helpers';
import {translate, translateWithParameters} from '../../../helpers/l10n';
import {withBranchStatusRefresh} from '../../../queries/branch';
import {IssueTransition} from '../../../types/issues';
import {Issue, Organization, Paging} from '../../../types/types';
import {withOrganizationContext} from "../../organizations/OrganizationContext";
import AssigneeSelect from './AssigneeSelect';
import TagsSelect from './TagsSelect';
import {getCodefixQuota, getCodefixStatus, queueCodeFix} from '../../../api/ai-codefix';
import {CreateBulkPullRequestModal} from '../../../components/rules/CreateBulkPullRequestModal';

interface Props {
  organization: Organization;
  fetchIssues: (x: {}) => Promise<{ issues: Issue[]; paging: Paging }>;
  needIssueSync?: boolean;
  onClose: () => void;
  onDone: () => void;
  refreshBranchStatus: () => void;
}

interface FormFields {
  organization: Organization;
  addTags?: Array<string>;
  assignee?: string;
  comment?: string;
  exceptionExpiryDate?: Date;
  notifications?: boolean;
  removeTags?: Array<string>;
  severity?: string;
  transition?: IssueTransition;
  type?: string;
  createPr?: boolean;
}

interface State extends FormFields {
  initialTags: Array<string>;
  issues: Issue[];
  // used for initial loading of issues
  loading: boolean;
  paging?: Paging;
  // used when submitting a form
  submitting: boolean;
  showBulkPrModal: boolean;
}

enum InputField {
  addTags = 'addTags',
  assignee = 'assignee',
  removeTags = 'removeTags',
  severity = 'severity',
  type = 'type',
}

export const MAX_PAGE_SIZE = 500;
export const MAX_AI_CODE_ASSISTANT_BULK_ASSIGN = 10;

export class BulkChangeModal extends React.PureComponent<Props, State> {
  mounted = false;

  constructor(props: Props) {
    super(props);
    const organization  = props.organization.kee;
    this.state = {
      initialTags: [],
      issues: [],
      loading: true,
      submitting: false,
      organization,
      showBulkPrModal: false,
    };
  }

  componentDidMount() {
    const { needIssueSync } = this.props;

    this.mounted = true;

    Promise.all([
      this.loadIssues(),
      needIssueSync ? Promise.resolve([]) : searchIssueTags({ organization: this.props.organization.kee }),
    ]).then(
      ([{ issues, paging }, tags]) => {
        if (this.mounted) {
          if (issues.length > MAX_PAGE_SIZE) {
            issues = issues.slice(0, MAX_PAGE_SIZE);
          }

          this.setState({
            initialTags: tags,
            issues,
            loading: false,
            paging,
          });

          // Fetch live status from the CodeScan backend for each issue so that the
          // "Create Bulk PR" checkbox appears without needing a page refresh.
          Promise.all(
            issues.map((issue) =>
              getCodefixStatus(issue.key)
                .then((res) => ({ key: issue.key, codefixStatus: res?.status }))
                .catch(() => ({ key: issue.key, codefixStatus: issue.codefixStatus })),
            ),
          ).then((freshStatuses) => {
            if (this.mounted) {
              const statusMap = new Map(freshStatuses.map((s) => [s.key, s.codefixStatus]));
              this.setState(({ issues: current }) => ({
                issues: current.map((issue) => {
                  const fresh = statusMap.get(issue.key);
                  return fresh ? { ...issue, codefixStatus: fresh } : issue;
                }),
              }));
            }
          });
        }
      },
      () => {},
    );
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  loadIssues = () => {
    return this.props.fetchIssues({ additionalFields: 'actions,transitions', ps: MAX_PAGE_SIZE });
  };

  handleAssigneeSelect = (assignee: string) => {
    this.setState({ assignee });
  };

  handleTagsSearch = (query: string): Promise<string[]> => {
    return searchIssueTags({ organization: this.props.organization.kee, q: query })
      .then((tags) => tags)
      .catch(() => []);
  };

  handleTagsSelect =
    (field: InputField.addTags | InputField.removeTags) => (options: Array<string>) => {
      this.setState<keyof FormFields>({ [field]: options });
    };

  handleFieldCheck = (field: keyof FormFields) => (checked: boolean) => {
    if (!checked) {
      this.setState<keyof FormFields>({ [field]: undefined });
    } else if (field === 'notifications') {
      this.setState<keyof FormFields>({ [field]: true });
    }
  };

  handleRadioTransitionChange = (transition: IssueTransition) => {
    this.setState({ transition, exceptionExpiryDate: undefined });
  };

  handleExceptionExpiryDateChange = (date?: Date) => {
    this.setState({ exceptionExpiryDate: date });
  };

  handleCommentChange = (event: React.SyntheticEvent<HTMLTextAreaElement>) => {
    this.setState({ comment: event.currentTarget.value });
  };

  handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const {
      addTags,
      assignee,
      comment,
      createPr,
      exceptionExpiryDate,
      issues,
      notifications,
      removeTags,
      severity,
      transition,
      type,
    } = this.state;

    if (createPr) {
      const fixGeneratedIssues = issues.filter((issue) => issue.codefixStatus === 'FIX_GENERATED');
      if (fixGeneratedIssues.length > MAX_AI_CODE_ASSISTANT_BULK_ASSIGN) {
        throwGlobalError({
          data: {
            message: `You can select a maximum of ${MAX_AI_CODE_ASSISTANT_BULK_ASSIGN} Fix Generated issues for Bulk PR creation.`,
          },
        });
        this.props.onClose();
        return;
      }
      this.setState({ showBulkPrModal: true });
      return;
    }

    if (!createPr && transition && transitionRequiresMandatoryComment(transition) && !comment?.trim()) {
      return;
    }
    const supportsBulkExceptionExpiry =
      transition === IssueTransition.Exception &&
      issues.some((issue) => issueSupportsExceptionExpiryPicker(issue));

    const expiryFields: Record<string, string> = {};
    if (supportsBulkExceptionExpiry) {
      expiryFields.issueResolutionExpiryOffsetMinutes = String(new Date().getTimezoneOffset());
      if (exceptionExpiryDate) {
        const yyyy = exceptionExpiryDate.getFullYear();
        const mm = String(exceptionExpiryDate.getMonth() + 1).padStart(2, '0');
        const dd = String(exceptionExpiryDate.getDate()).padStart(2, '0');
        expiryFields.issueResolutionExpiryDate = `${yyyy}-${mm}-${dd}`;
      }
    }

    if (assignee === 'ai-code-assistant') {
      const hasAnyAiDisabled = issues.some((issue) => issue.aiCodeFixEnabled !== true);
      if (hasAnyAiDisabled) {
        throwGlobalError({
          data: {
            message:
              'AI Agent can only be assigned to issues where AI CodeFix is available.',
          },
        });
        this.props.onClose();
        return;
      }

      if (issues.length > MAX_AI_CODE_ASSISTANT_BULK_ASSIGN) {
        throwGlobalError({
          data: {
            message:
                'You can assign a maximum of 10 issues to the AI Agent in a single bulk action.',
          },
        });
        this.props.onClose();
        return;
      }
    }

    const query = pickBy(
      {
        add_tags: addTags?.join(),
        assign: assignee,
        comment,
        do_transition: transition,
        remove_tags: removeTags?.join(),
        sendNotifications: notifications,
        set_severity: severity,
        set_type: type,
        ...expiryFields,
      },
      (x) => x !== undefined,
    );

    const issueKeys = issues.map((issue) => issue.key);

    this.setState({ submitting: true });

    bulkChangeIssues(issueKeys, query).then(
      () => {
        this.props.refreshBranchStatus();

        if (query.assign === 'ai-code-assistant') {
          const issuesToQueue = issues.filter(
            (issue) =>
              issue.codefixStatus !== 'FIX_GENERATED' &&
              issue.codefixStatus !== 'PULL_REQUEST_CREATED',
          );

          if (issuesToQueue.length === 0) {
            this.setState({ submitting: false });
            this.props.onDone();
            return;
          }

          getCodefixQuota(this.props.organization.kee)
            .then((quota) => {
              if (!quota.moduleLicensed) {
                addGlobalErrorMessage(translate('aicodefix.module_not_licensed'));
                return;
              }
              if (issuesToQueue.length > quota.remainingFixes) {
                addGlobalErrorMessage(translate('aicodefix.insufficient_credits'));
                return;
              }
              if (quota.currentUsage + issuesToQueue.length > quota.dailyLimit) {
                addGlobalErrorMessage(translate('aicodefix.daily_limit_exceeded'));
                return;
              }
              return queueCodeFix({
                organizationKey: issuesToQueue[0].organization,
                projectKey: issuesToQueue[0].projectKey,
                issueKeys: issuesToQueue.map((issue) => issue.key),
              });
            })
            .catch(throwGlobalError)
            .finally(() => {
              this.setState({ submitting: false });
              this.props.onDone();
            });
          return;
        }

        this.setState({ submitting: false });
        this.props.onDone();
      },
      (error) => {
        this.setState({ submitting: false });
        throwGlobalError(error);
      },
    );
  };

  getAvailableTransitions(issues: Issue[]) {
    const allTransitions = flattenDeep(issues.map((issue) => issue.transitions));
    const countTransitions = countBy<IssueTransition>(allTransitions);

    return sortBy(Object.keys(countTransitions)).map((transition: IssueTransition) => ({
      transition,
      count: countTransitions[transition],
    }));
  }

  canSubmit = () => {
    const { addTags, assignee, createPr, removeTags, severity, transition, type } = this.state;
    return Boolean(
      (addTags && addTags.length > 0) ||
        (removeTags && removeTags.length > 0) ||
        assignee !== undefined ||
        createPr ||
        severity ||
        transition ||
        type,
    );
  };

  renderField = (
    field: InputField,
    label: string,
    affected: number | undefined,
    input: React.ReactNode,
  ) => (
    <FormField htmlFor={`issues-bulk-change-${field}`} label={translate(label)}>
      <div className="sw-flex sw-items-center sw-justify-between">
        {input}

        {affected !== undefined && (
          <LightLabel>
            ({translateWithParameters('issue_bulk_change.x_issues', affected)})
          </LightLabel>
        )}
      </div>
    </FormField>
  );

  renderAssigneeField = () => {
    const { assignee, issues } = this.state;
    const affected = this.state.issues.filter(hasAction('assign')).length;
    const field = InputField.assignee;

    if (affected === 0) {
      return null;
    }

    return (
      <div className="sw-flex sw-items-center sw-justify-between sw-mb-6">
        <AssigneeSelect
          className="sw-max-w-abs-300"
          inputId={`issues-bulk-change-${field}`}
          organization={this.props.organization.kee}
          issues={issues}
          label={translate('issue.assign.formlink')}
          onAssigneeSelect={this.handleAssigneeSelect}
          selectedAssigneeKey={assignee}
        />
        {affected !== undefined && (
          <LightLabel>
            ({translateWithParameters('issue_bulk_change.x_issues', affected)})
          </LightLabel>
        )}
      </div>
    );
  };

  renderTagsField = (
    field: InputField.addTags | InputField.removeTags,
    label: string,
    allowCreate: boolean,
  ) => {
    const { initialTags } = this.state;
    const tags = this.state[field] ?? [];
    const affected = this.state.issues.filter(hasAction('set_tags')).length;

    if (initialTags === undefined || affected === 0) {
      return null;
    }

    const input = (
      <TagsSelect
        allowCreation={allowCreate}
        inputId={`issues-bulk-change-${field}`}
        onChange={this.handleTagsSelect(field)}
        selectedTags={tags}
        onSearch={this.handleTagsSearch}
      />
    );

    return this.renderField(field, label, affected, input);
  };

  renderTransitionsField = () => {
    const { issues, createPr } = this.state;
    const hasFixGeneratedIssues = issues.length > 0 && issues.every(issue => issue.codefixStatus === 'FIX_GENERATED');

    const transitions = this.getAvailableTransitions(issues).filter(
      (transition) => !isTransitionHidden(transition.transition),
    );

    if (transitions.length === 0 && issues.length === 0) {
      return null;
    }

    const bulkPrTooltip = 'All selected issues must have an AI-generated fix ready to enable bulk pull request creation.';

    return (
      <div className="sw-mb-6">
        {transitions.length > 0 && (
          <fieldset>
            <Highlight id="bulk-change-transition-label" as="legend" className="sw-mb-2">
              {translate('issue.change_status')}
            </Highlight>

            <RadioButtonGroup
              ariaLabelledBy="bulk-change-transition-label"
              id="bulk-change-transition"
              options={transitions.map(({ transition, count }) => ({
                label: translate('issue.transition', transition),
                value: transition,
                helpText: translateWithParameters('issue_bulk_change.x_issues', count),
              }))}
              onChange={this.handleRadioTransitionChange}
            />
          </fieldset>
        )}

        <div className="sw-mt-4 sw-flex sw-items-center sw-gap-2">
          <Checkbox
            checked={createPr || false}
            label={translate('issues.code_fix.create_bulk_pr_modal.title')}
            onCheck={(checked) => this.setState({ createPr: checked })}
            isDisabled={!hasFixGeneratedIssues}
          />
          <HelpTooltip overlay={bulkPrTooltip}>
            <HelperHintIcon aria-label={bulkPrTooltip} />
          </HelpTooltip>
        </div>
      </div>
    );
  };

  renderBulkExceptionExpiryCard = () => {
    const { issues, transition, exceptionExpiryDate } = this.state;
    if (transition !== IssueTransition.Exception) {
      return null;
    }
    if (!issues.some((issue) => issueSupportsExceptionExpiryPicker(issue))) {
      return null;
    }
    return (
      <SelectionCard
        className="sw-mb-6"
        selected
        vertical
        title={translate('issue.transition.exception')}
        onClick={() => {
          /* noop: visual parity with single-issue exception flow */
        }}
      >
        <Note className="sw-mt-1 sw-mr-12">{translate('issue.transition.exception.description')}</Note>
        <div className="sw-mt-10 sw-flex sw-flex-wrap sw-items-center sw-gap-3">
          <DatePicker
            clearButtonLabel={translate('clear')}
            minDate={(() => {
              const d = new Date();
              d.setHours(0, 0, 0, 0);
              return d;
            })()}
            maxDate={new Date(2050, 11, 31)}
            name="bulkIssueExceptionExpiry"
            onChange={this.handleExceptionExpiryDateChange}
            placeholder={translate('issue.exception.expiry.no_expiry_placeholder')}
            value={exceptionExpiryDate}
          />
        </div>
      </SelectionCard>
    );
  };

  renderCommentField = () => {
    const affectedIssuesCount = this.state.issues.filter(hasAction('comment')).length;
    if (affectedIssuesCount === 0) {
      return null;
    }

    // Selected transition does not require comment
    if (!this.state.transition || !transitionRequiresComment(this.state.transition)) {
      return null;
    }

    const exceptionWithExpiry =
      this.state.transition === IssueTransition.Exception &&
      this.state.issues.some((issue) => issueSupportsExceptionExpiryPicker(issue));
    const commentLabel = exceptionWithExpiry
      ? `${translate('issue.transition.exception.reason')} *`
      : translate('issue_bulk_change.resolution_comment');

    return (
      <FormField label={commentLabel}>
        <InputTextArea
          aria-label={commentLabel}
          onChange={this.handleCommentChange}
          placeholder={translate(
            'issue.transition.comment.placeholder',
            this.state.transition ?? '',
          )}
          rows={5}
          value={this.state.comment}
          size="auto"
          className="sw-resize-y sw-w-full"
        />
        <FormattingTips className="sw-mt-2" />
      </FormField>
    );
  };

  renderNotificationsField = () => (
    <Checkbox
      checked={this.state.notifications !== undefined}
      id="send-notifications"
      label={translate('issue.send_notifications')}
      onCheck={this.handleFieldCheck('notifications')}
    />
  );

  renderForm = () => {
    const { needIssueSync } = this.props;
    const { issues, loading, paging } = this.state;

    const limitReached = paging && paging.total > MAX_PAGE_SIZE;

    return (
      <Spinner isLoading={loading}>
        <form id="bulk-change-form" onSubmit={this.handleSubmit} className="sw-mr-4">
          {limitReached && (
            <FlagMessage className="sw-mb-4" variant="warning">
              <span>
                <FormattedMessage
                  defaultMessage={translate('issue_bulk_change.max_issues_reached')}
                  id="issue_bulk_change.max_issues_reached"
                  values={{ max: <strong>{MAX_PAGE_SIZE}</strong> }}
                />
              </span>
            </FlagMessage>
          )}

          {this.renderAssigneeField()}
          {!needIssueSync && this.renderTagsField(InputField.addTags, 'issue.add_tags', true)}

          {!needIssueSync &&
            this.renderTagsField(InputField.removeTags, 'issue.remove_tags', false)}

          {this.renderTransitionsField()}
          {this.renderBulkExceptionExpiryCard()}
          {this.renderCommentField()}
          {issues.length > 0 && this.renderNotificationsField()}

          {issues.length === 0 && (
            <FlagMessage variant="warning">{translate('issue_bulk_change.no_match')}</FlagMessage>
          )}
        </form>
      </Spinner>
    );
  };

  render() {
    const { issues, loading, submitting, showBulkPrModal } = this.state;

    const canSubmit = this.canSubmit();

    if (showBulkPrModal) {
      return (
        <CreateBulkPullRequestModal
          issueKeys={issues.map((i) => i.key)}
          onClose={() => this.setState({ showBulkPrModal: false })}
          onSuccess={() => {
            this.props.onDone();
          }}
        />
      );
    }

    return (
      <Modal
        body={this.renderForm()}
        headerTitle={
          loading
            ? translate('bulk_change')
            : translateWithParameters('issue_bulk_change.form.title', issues.length)
        }
        isScrollable
        loading={submitting}
        onClose={this.props.onClose}
        primaryButton={
          <ButtonPrimary
            disabled={!canSubmit || submitting || issues.length === 0 ||
              (!this.state.createPr && transitionRequiresMandatoryComment(this.state.transition) && !this.state.comment?.trim())}
            form="bulk-change-form"
            id="bulk-change-submit"
            type="submit"
          >
            {translate('apply')}
          </ButtonPrimary>
        }
        secondaryButtonLabel={translate('cancel')}
      />
    );
  }
}

function hasAction(action: string) {
  return (issue: Issue) => issue.actions?.includes(action);
}

export default withComponentContext(withOrganizationContext(withBranchStatusRefresh(BulkChangeModal)));
