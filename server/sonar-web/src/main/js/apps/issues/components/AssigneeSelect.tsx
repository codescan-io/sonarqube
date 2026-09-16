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

import { SelectAsync, SelectOption } from '@sonarsource/echoes-react';
import * as React from 'react';
import { CurrentUserContext } from '../../../app/components/current-user/CurrentUserContext';
import Avatar from '../../../components/ui/Avatar';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { isDefined } from '../../../helpers/types';
import { Issue } from '../../../types/types';
import { RestUser, UserActive, isLoggedIn, isUserActive } from '../../../types/users';
import { searchAssignees } from '../utils';
import { isAiAssistantEnabled } from '../../../api/settings';
import { useAreAllAiCodefixSupportedIntegrations } from '../../../queries/ai-codefix';

// exported for test
export const MIN_QUERY_LENGTH = 2;

const UNASSIGNED: Option = { value: '', label: translate('unassigned') };

const AI_CODE_ASSISTANT_LOGIN = 'ai-code-assistant';

interface Option extends SelectOption {
  Icon?: React.JSX.Element;
}

export interface AssigneeSelectProps {
  organization: string;
  className?: string;
  inputId: string;
  issues: Issue[];
  label: string;
  onAssigneeSelect: (assigneeKey: string) => void;
  selectedAssigneeKey?: string;
}

function userToOption(user: RestUser | UserActive) {
  const userInfo = user.name ?? user.login;
  return {
    value: user.login,
    label: isUserActive(user) ? userInfo : translateWithParameters('user.x_deleted', userInfo),
    Icon: <Avatar hash={user.avatar} name={user.name} size="xs" className="sw-my-1" />,
  };
}

export default function AssigneeSelect(props: Readonly<AssigneeSelectProps>) {
  const { className, issues, inputId, label, selectedAssigneeKey } = props;

  const { currentUser } = React.useContext(CurrentUserContext);
  const [aiEnabled, setAiEnabled] = React.useState(false);
  const [options, setOptions] = React.useState<Option[]>();
  React.useEffect(() => {
    async function checkAiEnabled() {
      const projectKey = issues?.[0]?.projectKey || "";
      const enabled = await isAiAssistantEnabled(projectKey);
      const allIssuesSupport = issues.every(issue => issue.aiCodeFixEnabled === true);
      setAiEnabled(enabled && allIssuesSupport);
    }

    checkAiEnabled();
  }, [issues]);

  const allIssuesAiCodeFixEnabled = issues.every((issue) => issue.aiCodeFixEnabled === true);
  const mayOfferAiAssistant = aiEnabled && allIssuesAiCodeFixEnabled;
  const allIntegrationsSupported = useAreAllAiCodefixSupportedIntegrations(
      mayOfferAiAssistant ? issues.map((issue) => issue.project) : [],
  );
  const canAssignAiAssistant = mayOfferAiAssistant && allIntegrationsSupported;
  const defaultOptions = React.useMemo((): Option[] => {
    const allowCurrentUserSelection =
      isLoggedIn(currentUser) && issues.some((issue) => currentUser.login !== issue.assignee);

    return allowCurrentUserSelection ? [UNASSIGNED, userToOption(currentUser)] : [UNASSIGNED];
  }, [currentUser, issues]);
  

const defaultOptionsWithAi = React.useMemo((): Option[] => {
  return [
    ...defaultOptions,
    ...(canAssignAiAssistant
      ? [
          {
            value: AI_CODE_ASSISTANT_LOGIN,
            label: "AI Code Assistant",
            Icon: <img className="ai-assistant-icon" src='/images/ai-assistant.svg' alt="AI Code Assistant" />
          }
        ]
      : [])
  ];
}, [defaultOptions, canAssignAiAssistant]);

React.useEffect(() => {
  setOptions(undefined); // Clear search results when AI status changes
}, [canAssignAiAssistant]);

  const handleAssigneeSearch = React.useCallback(
    async (query: string) => {
      if (query.length < MIN_QUERY_LENGTH) {
        setOptions(undefined);
        return;
      }
      const assignees = await searchAssignees(query, props.organization).then(({ results }) =>
        results
          .filter((user) => canAssignAiAssistant || user.login !== AI_CODE_ASSISTANT_LOGIN)
          .map(userToOption),
      );

      setOptions(assignees);
    },
    [props.issues[0]?.project, canAssignAiAssistant],
  );

  return (
    <SelectAsync
      ariaLabel={translate('issue_bulk_change.assignee.change')}
      className={className}
      id={inputId}
      data={options ?? defaultOptionsWithAi}
      helpText={translateWithParameters('select.search.tooShort', MIN_QUERY_LENGTH)}
      label={label}
      labelNotFound={translate('select.search.noMatches')}
      onChange={props.onAssigneeSelect}
      onSearch={handleAssigneeSearch}
      optionComponent={AssigneeOption}
      value={selectedAssigneeKey}
    />
  );
}

function AssigneeOption(props: Readonly<Option>) {
  const { Icon, label } = props;

  return (
    <div className="sw-flex sw-flex-nowrap sw-items-center sw-overflow-hidden">
      {isDefined(Icon) && <span className="sw-mr-2">{Icon}</span>}
      <span className="sw-whitespace-nowrap sw-text-ellipsis">{label}</span>
    </div>
  );
}
