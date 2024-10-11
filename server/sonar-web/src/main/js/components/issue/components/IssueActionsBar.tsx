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
import classNames from 'classnames';
import * as React from 'react';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import {
  IssueActions,
  IssueResolution,
  IssueResponse,
  IssueType as IssueTypeEnum,
} from '../../../types/issues';
import { Issue, RawQuery } from '../../../types/types';
import IssueTypeIcon from '../../icons/IssueTypeIcon';
import { updateIssue } from '../actions';
import IssueAssign from './IssueAssign';
import IssueCommentAction from './IssueCommentAction';
import IssueSeverity from './IssueSeverity';
import IssueTags from './IssueTags';
import IssueTransition from './IssueTransition';

interface Props {
  issue: Issue;
  currentPopup?: string;
  onAssign: (login: string) => void;
  onChange: (issue: Issue) => void;
  togglePopup: (popup: string, show?: boolean) => void;
  className?: string;
  showCommentsInPopup?: boolean;
}

interface State {
  commentAutoTriggered: boolean;
  commentPlaceholder: string;
}

export default class IssueActionsBar extends React.PureComponent<Props, State> {
  state: State = {
    commentAutoTriggered: false,
    commentPlaceholder: '',
  };

  setIssueProperty = (
    property: keyof Issue,
    popup: string,
    apiCall: (query: RawQuery) => Promise<IssueResponse>,
    value: string
  ) => {
    const { issue } = this.props;
    if (issue[property] !== value) {
      const newIssue = { ...issue, [property]: value };
      updateIssue(
        this.props.onChange,
        apiCall({ issue: issue.key, [property]: value }),
        issue,
        newIssue
      );
    }
    this.props.togglePopup(popup, false);
  };

  toggleComment = (open: boolean | undefined, placeholder = '', autoTriggered = false) => {
    this.setState({
      commentPlaceholder: placeholder,
      commentAutoTriggered: autoTriggered,
    });
    this.props.togglePopup('comment', open);
  };

  handleTransition = (issue: Issue) => {
    this.props.onChange(issue);
    if (
      issue.resolution === IssueResolution.FalsePositive ||
      (issue.resolution === IssueResolution.WontFix && issue.type !== IssueTypeEnum.SecurityHotspot)
    ) {
      this.toggleComment(true, translate('issue.comment.explain_why'), true);
    }
  };

  render() {
    const { issue, className, showCommentsInPopup } = this.props;
    const canAssign = issue.actions.includes(IssueActions.Assign);
    const canComment = issue.actions.includes(IssueActions.Comment);
    const canSetSeverity = issue.actions.includes(IssueActions.SetSeverity);
    const canSetTags = issue.actions.includes(IssueActions.SetTags);
    const hasTransitions = issue.transitions.length > 0;

    return (
      <div className={classNames(className, 'issue-actions')}>
        <div className="issue-meta-list">
          <div className="issue-meta display-flex-center disabled">
            <IssueTypeIcon className="little-spacer-right" query={issue.type} />
            {translate('issue.type', issue.type)}
          </div>
          <div className="issue-meta">
            <IssueSeverity
              canSetSeverity={canSetSeverity}
              isOpen={this.props.currentPopup === 'set-severity' && canSetSeverity}
              issue={issue}
              setIssueProperty={this.setIssueProperty}
              togglePopup={this.props.togglePopup}
            />
          </div>
          <div className="issue-meta">
            <IssueTransition
              hasTransitions={hasTransitions}
              isOpen={this.props.currentPopup === 'transition' && hasTransitions}
              issue={issue}
              onChange={this.handleTransition}
              togglePopup={this.props.togglePopup}
            />
          </div>
          <div className="issue-meta">
            <IssueAssign
              canAssign={canAssign}
              isOpen={this.props.currentPopup === 'assign' && canAssign}
              issue={issue}
              onAssign={this.props.onAssign}
              togglePopup={this.props.togglePopup}
            />
          </div>
          {issue.effort && (
            <div className="issue-meta">
              <span className="issue-meta-label">
                {translateWithParameters('issue.x_effort', issue.effort)}
              </span>
            </div>
          )}
          {(canComment || showCommentsInPopup) && (
            <IssueCommentAction
              commentAutoTriggered={this.state.commentAutoTriggered}
              commentPlaceholder={this.state.commentPlaceholder}
              currentPopup={this.props.currentPopup}
              issueKey={issue.key}
              onChange={this.props.onChange}
              toggleComment={this.toggleComment}
              comments={issue.comments}
              canComment={canComment}
              showCommentsInPopup={showCommentsInPopup}
            />
          )}
        </div>
        <div className="list-inline">
          <div className="issue-meta js-issue-tags">
            <IssueTags
              canSetTags={canSetTags}
              isOpen={this.props.currentPopup === 'edit-tags' && canSetTags}
              issue={issue}
              onChange={this.props.onChange}
              togglePopup={this.props.togglePopup}
            />
          </div>
        </div>
      </div>
    );
  }
}
