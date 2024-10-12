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
  ClipboardIconButton,
  IssueMessageHighlighting,
  Link,
  LinkIcon,
  Note,
  PageContentFontWrapper,
} from 'design-system';
import * as React from 'react';
import { setIssueAssignee } from '../../../api/issues';
import { updateIssue } from '../../../components/issue/actions';
import IssueActionsBar from '../../../components/issue/components/IssueActionsBar';
import IssueTags from '../../../components/issue/components/IssueTags';
import { getBranchLikeQuery } from '../../../helpers/branch-like';
import { isInput, isShortcut } from '../../../helpers/keyboardEventHelpers';
import { KeyboardKeys } from '../../../helpers/keycodes';
import { translate } from '../../../helpers/l10n';
import { getKeyboardShortcutEnabled } from '../../../helpers/preferences';
import { getComponentIssuesUrl, getPathUrlAsString, getRuleUrl } from '../../../helpers/urls';
import { BranchLike } from '../../../types/branch-like';
import { IssueActions, IssueType } from '../../../types/issues';
import { Issue, RuleDetails } from '../../../types/types';

interface Props {
  organization: string | undefined;
  issue: Issue;
  ruleDetails: RuleDetails;
  branchLike?: BranchLike;
  onIssueChange: (issue: Issue) => void;
}

interface State {
  issuePopupName?: string;
}

export default class IssueHeader extends React.PureComponent<Props, State> {
  state = { issuePopupName: undefined };

  componentDidMount() {
    document.addEventListener('keydown', this.handleKeyDown, { capture: true });
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.issue.key !== this.props.issue.key) {
      this.setState({ issuePopupName: undefined });
    }
  }

  componentWillUnmount() {
    document.removeEventListener('keydown', this.handleKeyDown, { capture: true });
  }

  handleIssuePopupToggle = (popupName: string, open?: boolean) => {
    this.setState(({ issuePopupName }) => {
      const samePopup = popupName && issuePopupName === popupName;
      if (open !== false && !samePopup) {
        return { issuePopupName: popupName };
      } else if (open !== true && samePopup) {
        return { issuePopupName: undefined };
      }
      return { issuePopupName };
    });
  };

  handleAssignement = (login: string) => {
    const { issue } = this.props;
    if (issue.assignee !== login) {
      updateIssue(
        this.props.onIssueChange,
        setIssueAssignee({ issue: issue.key, assignee: login })
      );
    }
    this.handleIssuePopupToggle('assign', false);
  };

  handleKeyDown = (event: KeyboardEvent) => {
    if (isInput(event) || isShortcut(event) || !getKeyboardShortcutEnabled()) {
      return true;
    } else if (event.key === KeyboardKeys.KeyF) {
      event.preventDefault();
      return this.handleIssuePopupToggle('transition');
    } else if (event.key === KeyboardKeys.KeyA) {
      event.preventDefault();
      return this.handleIssuePopupToggle('assign');
    } else if (event.key === KeyboardKeys.KeyM && this.props.issue.actions.includes('assign')) {
      event.preventDefault();
      return this.handleAssignement('_me');
    } else if (event.key === KeyboardKeys.KeyI) {
      event.preventDefault();
      return this.handleIssuePopupToggle('set-severity');
    } else if (event.key === KeyboardKeys.KeyC) {
      event.preventDefault();
      return this.handleIssuePopupToggle('comment');
    } else if (event.key === KeyboardKeys.KeyT) {
      event.preventDefault();
      return this.handleIssuePopupToggle('edit-tags');
    }
    return true;
  };

  render() {
    const {
      issue,
      ruleDetails: { key, name, isExternal },
      branchLike,
    } = this.props;
    const { issuePopupName } = this.state;
    const issueUrl = getComponentIssuesUrl(issue.project, {
      ...getBranchLikeQuery(branchLike),
      issues: issue.key,
      open: issue.key,
      types: issue.type === IssueType.SecurityHotspot ? issue.type : undefined,
    });
    const canSetTags = issue.actions.includes(IssueActions.SetTags);

    return (
      <header className="sw-flex sw-flex-col sw-gap-3 sw-my-6">
        <div className="sw-flex sw-items-center">
          <PageContentFontWrapper className="sw-body-md-highlight" as="h1">
            <IssueMessageHighlighting
              message={issue.message}
              messageFormattings={issue.messageFormattings}
            />
          </PageContentFontWrapper>
          <ClipboardIconButton
            Icon={LinkIcon}
            aria-label={translate('permalink')}
            className="sw-ml-1 sw-align-bottom"
            copyValue={getPathUrlAsString(issueUrl, false)}
            discreet
          />
        </div>
        <div className="sw-flex sw-items-center sw-justify-between">
          <Note>
            <span className="sw-pr-1">{name}</span>
            {isExternal ? (
              <span>({key})</span>
            ) : (
              <Link to={getRuleUrl(key, this.props.organization)} target="_blank">
                {key}
              </Link>
            )}
          </Note>
          <IssueTags
            canSetTags={canSetTags}
            issue={issue}
            onChange={this.props.onIssueChange}
            open={issuePopupName === 'edit-tags' && canSetTags}
            togglePopup={this.handleIssuePopupToggle}
          />
        </div>
        <IssueActionsBar
          currentPopup={issuePopupName}
          issue={issue}
          onAssign={this.handleAssignement}
          onChange={this.props.onIssueChange}
          togglePopup={this.handleIssuePopupToggle}
          showCommentsInPopup
        />
      </header>
    );
  }
}
