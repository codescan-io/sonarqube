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
import {
  ButtonPrimary,
  Checkbox,
  DeferredSpinner,
  FlagMessage,
  FormField,
  HelperHintIcon,
  Highlight,
  InputSelect,
  LabelValueSelectOption,
  LightLabel,
  Modal,
  RadioButton,
} from 'design-system';
import { pickBy, sortBy } from 'lodash';
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import { SingleValue } from 'react-select';
import { bulkChangeIssues, searchIssueTags } from '../../../api/issues';
import FormattingTips from '../../../components/common/FormattingTips';
import IssueSeverityIcon from '../../../components/icon-mappers/IssueSeverityIcon';
import IssueTypeIcon from '../../../components/icon-mappers/IssueTypeIcon';
import { SEVERITIES } from '../../../helpers/constants';
import { throwGlobalError } from '../../../helpers/error';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { IssueSeverity } from '../../../types/issues';
import { Dict, Issue, IssueType, Paging } from '../../../types/types';
import AssigneeSelect from './AssigneeSelect';
import TagsSelect from './TagsSelect';
import { withOrganizationContext } from "../../organizations/OrganizationContext";

const DEBOUNCE_DELAY = 250;

interface Props {
  fetchIssues: (x: {}) => Promise<{ issues: Issue[]; paging: Paging }>;
  onClose: () => void;
  onDone: () => void;
  organization: Organization;
}

interface FormFields {
  addTags?: Array<string>;
  assignee?: SingleValue<LabelValueSelectOption<string>>;
  comment?: string;
  notifications?: boolean;
  organization: Organization;
  removeTags?: Array<string>;
  severity?: string;
  transition?: string;
  type?: string;
}

interface State extends FormFields {
  initialTags: Array<string>;
  issues: Issue[];
  // used for initial loading of issues
  loading: boolean;
  paging?: Paging;
  // used when submitting a form
  submitting: boolean;
}

enum InputField {
  addTags = 'addTags',
  assignee = 'assignee',
  removeTags = 'removeTags',
  severity = 'severity',
  type = 'type',
}

export const MAX_PAGE_SIZE = 500;

export class BulkChangeModal extends React.PureComponent<Props, State> {
  mounted = false;

  constructor(props: Props) {
    super(props);
    this.state = { initialTags: [], issues: [], loading: true, submitting: false };
  }

  componentDidMount() {
    this.mounted = true;

    Promise.all([this.loadIssues(), searchIssueTags({ organization: this.props.organization.kee })]).then(
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
        }
      },
      () => {}
    );
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  loadIssues = () => {
    return this.props.fetchIssues({ additionalFields: 'actions,transitions', ps: MAX_PAGE_SIZE });
  };

  handleAssigneeSelect = (assignee: SingleValue<LabelValueSelectOption<string>>) => {
    this.setState({ assignee });
  };

  handleTagsSearch = (query: string): Promise<string[]> => {
    return searchIssueTags({ q: query, organization: this.props.organization.kee })
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

  handleRadioTransitionChange = (transition: string) => {
    this.setState({ transition });
  };

  handleCommentChange = (event: React.SyntheticEvent<HTMLTextAreaElement>) => {
    this.setState({ comment: event.currentTarget.value });
  };

  handleSelectFieldChange =
    (field: 'severity' | 'type') => (data: LabelValueSelectOption<string> | null) => {
      if (data) {
        this.setState<keyof FormFields>({ [field]: data.value });
      } else {
        this.setState<keyof FormFields>({ [field]: undefined });
      }
    };

  handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const query = pickBy(
      {
        add_tags: this.state.addTags?.join(),
        assign: this.state.assignee ? this.state.assignee.value : null,
        comment: this.state.comment,
        do_transition: this.state.transition,
        remove_tags: this.state.removeTags?.join(),
        sendNotifications: this.state.notifications,
        set_severity: this.state.severity,
        set_type: this.state.type,
      },
      (x) => x !== undefined
    );

    const issueKeys = this.state.issues.map((issue) => issue.key);

    this.setState({ submitting: true });
    bulkChangeIssues(issueKeys, query).then(
      () => {
        this.setState({ submitting: false });
        this.props.onDone();
      },
      (error) => {
        this.setState({ submitting: false });
        throwGlobalError(error);
      }
    );
  };

  getAvailableTransitions(issues: Issue[]) {
    const transitions: Dict<number> = {};
    issues.forEach((issue) => {
      if (issue.transitions) {
        issue.transitions.forEach((t) => {
          if (transitions[t] !== undefined) {
            transitions[t]++;
          } else {
            transitions[t] = 1;
          }
        });
      }
    });
    return sortBy(Object.keys(transitions)).map((transition) => ({
      transition,
      count: transitions[transition],
    }));
  }

  canSubmit = () => {
    const { addTags, assignee, removeTags, severity, transition, type } = this.state;

    return Boolean(
      (addTags && addTags.length > 0) ||
        (removeTags && removeTags.length > 0) ||
        assignee ||
        severity ||
        transition ||
        type
    );
  };

  renderField = (
    field: InputField,
    label: string,
    affected: number | undefined,
    input: React.ReactNode
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

    const input = (
      <AssigneeSelect
        assignee={assignee}
        className="sw-max-w-abs-300"
        inputId={`issues-bulk-change-${field}`}
        issues={issues}
        organization={this.props.organization.kee}
        onAssigneeSelect={this.handleAssigneeSelect}
      />
    );

    return this.renderField(field, 'issue.assign.formlink', affected, input);
  };

  renderTypeField = () => {
    const affected = this.state.issues.filter(hasAction('set_type')).length;
    const field = InputField.type;

    if (affected === 0) {
      return null;
    }

    const types: IssueType[] = ['BUG', 'VULNERABILITY', 'CODE_SMELL'];
    const options: LabelValueSelectOption<IssueType>[] = types.map((type) => ({
      label: translate('issue.type', type),
      value: type,
      Icon: <IssueTypeIcon height={16} type={type} />,
    }));

    const input = (
      <InputSelect
        className="sw-w-abs-300"
        inputId={`issues-bulk-change-${field}`}
        isClearable
        isSearchable={false}
        onChange={this.handleSelectFieldChange('type')}
        options={options}
        size="full"
      />
    );

    return this.renderField(field, 'issue.set_type', affected, input);
  };

  renderSeverityField = () => {
    const affected = this.state.issues.filter(hasAction('set_severity')).length;
    const field = InputField.severity;

    if (affected === 0) {
      return null;
    }

    const options: LabelValueSelectOption<IssueSeverity>[] = SEVERITIES.map((severity) => ({
      label: translate('severity', severity),
      value: severity,
      Icon: <IssueSeverityIcon height={16} severity={severity} />,
    }));

    const input = (
      <InputSelect
        className="sw-w-abs-300"
        inputId={`issues-bulk-change-${field}`}
        isClearable
        isSearchable={false}
        onChange={this.handleSelectFieldChange('severity')}
        options={options}
        size="full"
      />
    );

    return this.renderField(field, 'issue.set_severity', affected, input);
  };

  renderTagsField = (
    field: InputField.addTags | InputField.removeTags,
    label: string,
    allowCreate: boolean
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
    const transitions = this.getAvailableTransitions(this.state.issues);

    if (transitions.length === 0) {
      return null;
    }

    return (
      <div className="sw-mb-6">
        <fieldset>
          <Highlight as="legend" className="sw-mb-2">
            {translate('issue.transition')}
          </Highlight>
          {transitions.map((transition) => (
            <div
              className="sw-mb-1 sw-flex sw-items-center sw-justify-between"
              key={transition.transition}
            >
              <RadioButton
                checked={this.state.transition === transition.transition}
                onCheck={this.handleRadioTransitionChange}
                value={transition.transition}
              >
                {translate('issue.transition', transition.transition)}
              </RadioButton>
              <LightLabel>
                ({translateWithParameters('issue_bulk_change.x_issues', transition.count)})
              </LightLabel>
            </div>
          ))}
        </fieldset>
      </div>
    );
  };

  renderCommentField = () => {
    const affected = this.state.issues.filter(hasAction('comment')).length;

    if (affected === 0) {
      return null;
    }

    return (
      <FormField
        label={translate('issue.comment.formlink')}
        htmlFor="comment"
        help={
          <div className="-sw-mt-1" title={translate('issue_bulk_change.comment.help')}>
            <HelperHintIcon />
          </div>
        }
      >
        <textarea
          id="comment"
          onChange={this.handleCommentChange}
          rows={4}
          value={this.state.comment ?? ''}
        />
        <FormattingTips className="sw-text-right" />
      </FormField>
    );
  };

  renderNotificationsField = () => (
    <div>
      <Checkbox
        checked={this.state.notifications !== undefined}
        className="sw-my-2 sw-gap-1/2"
        id="send-notifications"
        onCheck={this.handleFieldCheck('notifications')}
        right
      >
        {translate('issue.send_notifications')}
      </Checkbox>
    </div>
  );

  renderForm = () => {
    const { issues, loading, paging } = this.state;

    const limitReached = paging && paging.total > MAX_PAGE_SIZE;

    return (
      <DeferredSpinner loading={loading}>
        <form id="bulk-change-form" onSubmit={this.handleSubmit}>
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
          {this.renderTypeField()}
          {this.renderSeverityField()}
          {this.renderTagsField(InputField.addTags, 'issue.add_tags', true)}
          {this.renderTagsField(InputField.removeTags, 'issue.remove_tags', false)}
          {this.renderTransitionsField()}
          {this.renderCommentField()}
          {issues.length > 0 && this.renderNotificationsField()}
          {issues.length === 0 && (
            <FlagMessage variant="warning">{translate('issue_bulk_change.no_match')}</FlagMessage>
          )}
        </form>
      </DeferredSpinner>
    );
  };

  render() {
    const { issues, loading, submitting } = this.state;

    const canSubmit = this.canSubmit();

    return (
      <Modal
        onClose={this.props.onClose}
        headerTitle={
          loading
            ? translate('bulk_change')
            : translateWithParameters('issue_bulk_change.form.title', issues.length)
        }
        isScrollable
        loading={submitting}
        body={this.renderForm()}
        primaryButton={
          <ButtonPrimary
            id="bulk-change-submit"
            form="bulk-change-form"
            type="submit"
            disabled={!canSubmit || submitting || issues.length === 0}
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
  return (issue: Issue) => issue.actions && issue.actions.includes(action);
}

export default withOrganizationContext(BulkChangeModal);
