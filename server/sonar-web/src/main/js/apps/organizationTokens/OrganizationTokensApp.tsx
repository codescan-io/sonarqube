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
import { Button, ButtonVariety, Heading, Spinner } from '@sonarsource/echoes-react';
import * as React from 'react';
import { Helmet } from 'react-helmet-async';
import { FormattedMessage } from 'react-intl';
import {
  Checkbox,
  ContentCell,
  InputSearch,
  LargeCenteredLayout,
  PageContentFontWrapper,
  Table,
  TableRow,
} from '~design-system';
import { revokeOrganizationToken, searchOrganizationTokens } from '../../api/user-tokens';
import ConfirmButton from '../../components/controls/ConfirmButton';
import ListFooter from '../../components/controls/ListFooter';
import DateFromNow from '../../components/intl/DateFromNow';
import DateTimeFormatter from '../../components/intl/DateTimeFormatter';
import { translate, translateWithParameters } from '../../helpers/l10n';
import { OrganizationUserToken } from '../../types/token';
import { Organization, Paging } from '../../types/types';
import { withOrganizationContext } from '../organizations/OrganizationContext';

const PAGE_SIZE = 50;
const OLD_TOKEN_DAYS = 30;
const COLUMN_WIDTHS = ['auto', 'auto', 'auto', 'auto', 'auto', 'auto', 'auto', '1%'];

interface Props {
  organization?: Organization;
}

/** yyyy-MM-dd of the day OLD_TOKEN_DAYS ago, sent as the createdBefore filter. */
function oldTokenThreshold() {
  const date = new Date();
  date.setDate(date.getDate() - OLD_TOKEN_DAYS);
  return date.toISOString().slice(0, 10);
}

export function OrganizationTokensApp({ organization }: Readonly<Props>) {
  const organizationKey = organization?.kee;
  const [tokens, setTokens] = React.useState<OrganizationUserToken[]>([]);
  const [paging, setPaging] = React.useState<Paging>();
  const [loading, setLoading] = React.useState(true);
  const [query, setQuery] = React.useState('');
  const [onlyOld, setOnlyOld] = React.useState(false);
  const [revoking, setRevoking] = React.useState<string>();

  const fetchPage = React.useCallback(
    (page: number) => {
      if (!organizationKey) {
        return Promise.resolve();
      }
      setLoading(true);
      return searchOrganizationTokens({
        organization: organizationKey,
        p: page,
        ps: PAGE_SIZE,
        ...(query.length >= 2 ? { q: query } : {}),
        ...(onlyOld ? { createdBefore: oldTokenThreshold() } : {}),
      })
        .then((response) => {
          setTokens((prev) => (page === 1 ? response.userTokens : [...prev, ...response.userTokens]));
          setPaging(response.paging);
        })
        .finally(() => setLoading(false));
    },
    [organizationKey, query, onlyOld],
  );

  React.useEffect(() => {
    fetchPage(1).catch(() => {
      /* error already shown by throwGlobalError */
    });
  }, [fetchPage]);

  const handleLoadMore = () => {
    if (paging) {
      fetchPage(paging.pageIndex + 1).catch(() => {});
    }
  };

  const handleRevoke = async (token: OrganizationUserToken) => {
    if (!organizationKey) {
      return;
    }
    const key = tokenKey(token);
    setRevoking(key);
    try {
      await revokeOrganizationToken({
        organization: organizationKey,
        login: token.login,
        name: token.name,
      });
      setTokens((prev) => prev.filter((t) => tokenKey(t) !== key));
      setPaging((prev) => (prev ? { ...prev, total: Math.max(0, prev.total - 1) } : prev));
    } finally {
      setRevoking(undefined);
    }
  };

  const header = (
    <TableRow>
      <ContentCell>{translate('user')}</ContentCell>
      <ContentCell>{translate('name')}</ContentCell>
      <ContentCell>{translate('my_account.token_type')}</ContentCell>
      <ContentCell>{translate('my_account.project_name')}</ContentCell>
      <ContentCell>{translate('my_account.tokens_last_usage')}</ContentCell>
      <ContentCell>{translate('created')}</ContentCell>
      <ContentCell>{translate('my_account.tokens.expiration')}</ContentCell>
      <ContentCell />
    </TableRow>
  );

  return (
    <LargeCenteredLayout id="organization-tokens">
      <PageContentFontWrapper className="sw-my-8 sw-typo-default">
        <Helmet defer={false} title={translate('organization.tokens.page')} />
        <header className="sw-mb-4">
          <Heading as="h1" className="sw-mb-4">
            {translate('organization.tokens.page')}
          </Heading>
          <p>{translate('organization.tokens.page.description')}</p>
        </header>

        <div className="sw-flex sw-items-center sw-gap-6 sw-mb-4">
          <InputSearch
            minLength={2}
            onChange={setQuery}
            placeholder={translate('organization.tokens.search')}
            size="large"
            value={query}
          />
          <Checkbox checked={onlyOld} onCheck={setOnlyOld}>
            <span className="sw-ml-2">
              {translateWithParameters('organization.tokens.older_than_x_days', OLD_TOKEN_DAYS)}
            </span>
          </Checkbox>
        </div>

        <Spinner isLoading={loading && tokens.length === 0}>
          <Table
            className="sw-w-full"
            columnCount={COLUMN_WIDTHS.length}
            columnWidths={COLUMN_WIDTHS}
            header={header}
            noHeaderTopBorder
          >
            {tokens.length === 0 ? (
              <TableRow>
                <ContentCell colSpan={COLUMN_WIDTHS.length}>{translate('users.no_tokens')}</ContentCell>
              </TableRow>
            ) : (
              tokens.map((token) => (
                <TableRow key={tokenKey(token)}>
                  <ContentCell title={token.login}>
                    <div>
                      <div>{token.userName ?? token.login}</div>
                      {token.userName && <div className="sw-text-gray-500">{token.login}</div>}
                    </div>
                  </ContentCell>
                  <ContentCell title={token.name}>
                    <div className="sw-max-w-64 sw-truncate">{token.name}</div>
                  </ContentCell>
                  <ContentCell title={translate('users.tokens', token.type)}>
                    {translate('users.tokens', token.type, 'short')}
                  </ContentCell>
                  <ContentCell title={token.project?.name}>
                    <div className="sw-max-w-32 sw-truncate">{token.project?.name}</div>
                  </ContentCell>
                  <ContentCell>
                    <DateFromNow date={token.lastConnectionDate} hourPrecision />
                  </ContentCell>
                  <ContentCell>
                    <DateTimeFormatter date={token.createdAt} short />
                  </ContentCell>
                  <ContentCell>
                    {token.expirationDate ? (
                      <span>
                        <DateTimeFormatter date={token.expirationDate} short />
                        {token.isExpired && ` (${translate('my_account.tokens.expired')})`}
                      </span>
                    ) : (
                      '–'
                    )}
                  </ContentCell>
                  <ContentCell>
                    <ConfirmButton
                      confirmButtonText={translate('yes')}
                      isDestructive
                      modalBody={
                        <FormattedMessage
                          defaultMessage={translate('organization.tokens.sure_X_Y')}
                          id="organization.tokens.sure_X_Y"
                          values={{
                            token: <strong>{token.name}</strong>,
                            user: <strong>{token.userName ?? token.login}</strong>,
                          }}
                        />
                      }
                      modalHeader={translateWithParameters('users.tokens.revoke_label', token.name)}
                      onConfirm={() => handleRevoke(token)}
                    >
                      {({ onClick }) => (
                        <Button
                          aria-label={translateWithParameters('users.tokens.revoke_label', token.name)}
                          isDisabled={revoking === tokenKey(token)}
                          isLoading={revoking === tokenKey(token)}
                          onClick={onClick}
                          variety={ButtonVariety.DangerOutline}
                        >
                          {translate('users.tokens.revoke')}
                        </Button>
                      )}
                    </ConfirmButton>
                  </ContentCell>
                </TableRow>
              ))
            )}
          </Table>
        </Spinner>

        {paging && tokens.length > 0 && (
          <ListFooter
            count={tokens.length}
            loadMore={handleLoadMore}
            loading={loading}
            ready={!loading}
            total={paging.total}
          />
        )}
      </PageContentFontWrapper>
    </LargeCenteredLayout>
  );
}

function tokenKey(token: OrganizationUserToken) {
  return `${token.login}::${token.name}`;
}

export default withOrganizationContext(OrganizationTokensApp);
