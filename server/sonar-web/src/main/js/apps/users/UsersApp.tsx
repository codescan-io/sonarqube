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
import { subDays, subSeconds } from 'date-fns';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Helmet } from 'react-helmet-async';
import { getIdentityProviders, searchUsers } from '../../api/users';
import HelpTooltip from '../../components/controls/HelpTooltip';
import ListFooter from '../../components/controls/ListFooter';
import SearchBox from '../../components/controls/SearchBox';
import Select, { LabelValueSelectOption } from '../../components/controls/Select';
import Suggestions from '../../components/embed-docs-modal/Suggestions';
import DeferredSpinner from '../../components/ui/DeferredSpinner';
import { now, toNotSoISOString } from '../../helpers/dates';
import { translate } from '../../helpers/l10n';
import { IdentityProvider, Paging } from '../../types/types';
import { User } from '../../types/users';
import Header from './Header';
import UsersList from './UsersList';
import { USERS_ACTIVITY_OPTIONS, USER_INACTIVITY_DAYS_THRESHOLD } from './constants';
import { UserActivity } from './types';
import withCurrentUserContext from "../../app/components/current-user/withCurrentUserContext";

function UsersApp({ currentUser }) {
  const [identityProviders, setIdentityProviders] = useState<IdentityProvider[]>([]);

  const [loading, setLoading] = useState(true);
  const [paging, setPaging] = useState<Paging>();
  const [users, setUsers] = useState<User[]>([]);

  const [search, setSearch] = useState('');
  const [usersActivity, setUsersActivity] = useState<UserActivity>(UserActivity.AnyActivity);

  const usersActivityParams = useMemo(() => {
    const nowDate = now();
    const nowDateMinus30Days = subDays(nowDate, USER_INACTIVITY_DAYS_THRESHOLD);
    const nowDateMinus30DaysAnd1Second = subSeconds(nowDateMinus30Days, 1);

    switch (usersActivity) {
      case UserActivity.ActiveSonarLintUser:
        return {
          slLastConnectedAfter: toNotSoISOString(nowDateMinus30Days),
        };
      case UserActivity.ActiveSonarQubeUser:
        return {
          lastConnectedAfter: toNotSoISOString(nowDateMinus30Days),
          slLastConnectedBefore: toNotSoISOString(nowDateMinus30DaysAnd1Second),
        };
      case UserActivity.InactiveUser:
        return {
          lastConnectedBefore: toNotSoISOString(nowDateMinus30DaysAnd1Second),
        };
      default:
        return {};
    }
  }, [usersActivity]);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const { paging, users } = await searchUsers({
        q: search,
        ...usersActivityParams,
      });
      setPaging(paging);
      setUsers(users);
    } finally {
      setLoading(false);
    }
  }, [search, usersActivityParams]);

  const fetchMoreUsers = useCallback(async () => {
    if (!paging) {
      return;
    }
    setLoading(true);
    try {
      const { paging: nextPage, users: nextUsers } = await searchUsers({
        q: search,
        ...usersActivityParams,
        p: paging.pageIndex + 1,
      });
      setPaging(nextPage);
      setUsers([...users, ...nextUsers]);
    } finally {
      setLoading(false);
    }
  }, [search, usersActivityParams, paging, users]);

  useEffect(() => {
    (async () => {
      const { identityProviders } = await getIdentityProviders();
      setIdentityProviders(identityProviders);
    })();
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  return (
    <main className="page page-limited" id="users-page">
      <Suggestions suggestions="users" />
      <Helmet defer={false} title={translate('users.page')} />
      <Header onUpdateUsers={fetchUsers} />
      <div className="display-flex-justify-start big-spacer-bottom big-spacer-top">
        <SearchBox
          id="users-search"
          minLength={2}
          onChange={(search: string) => setSearch(search)}
          placeholder={translate('search.search_by_login_or_name')}
          value={search}
        />
        <div className="sw-ml-4">
          <Select
            id="users-activity-filter"
            className="input-large"
            isDisabled={loading}
            onChange={(userActivity: LabelValueSelectOption<UserActivity>) => {
              setUsersActivity(userActivity.value);
            }}
            options={USERS_ACTIVITY_OPTIONS}
            isSearchable={false}
            placeholder={translate('users.activity_filter.placeholder')}
            aria-label={translate('users.activity_filter.label')}
            value={USERS_ACTIVITY_OPTIONS.find((option) => option.value === usersActivity) ?? null}
          />
          <HelpTooltip
            className="sw-ml-1"
            overlay={
              <>
                <p>{translate('users.activity_filter.helptext.sonarqube')}</p>
                <p>{translate('users.activity_filter.helptext.sonarlint')}</p>
              </>
            }
          />
        </div>
      </div>
      <DeferredSpinner loading={loading}>
        <UsersList
          currentUser={currentUser}
          identityProviders={identityProviders}
          onUpdateUsers={fetchUsers}
          updateTokensCount={fetchUsers}
          users={users}
        />
      </DeferredSpinner>
      {paging !== undefined && (
        <ListFooter
          count={users.length}
          loadMore={fetchMoreUsers}
          ready={!loading}
          total={paging.total}
        />
      )}
    </main>
  );
}

export default withCurrentUserContext(UsersApp);
