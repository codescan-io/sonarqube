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

import styled from '@emotion/styled';
import {
  Button,
  ButtonVariety,
  Heading,
  HeadingSize,
  IconSecurityFinding,
  Text,
  TextSize,
} from '@sonarsource/echoes-react';
import { useEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { FormattedMessage } from 'react-intl';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  BasicSeparator,
  Card,
  CenteredLayout,
  PageContentFontWrapper,
  Pill,
  PillVariant,
} from '~design-system';
import { useCurrentUser } from '../../../app/components/current-user/CurrentUserContext';
import Avatar from '../../../components/ui/Avatar';
import {
  IP_ALLOWLIST_BLOCKED_PATH,
  markIpAllowlistBlockedPageMounted,
  parseIpAllowlistBlockedMessage,
} from '../../../helpers/ipAllowlist';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { isLoggedIn } from '../../../types/users';

// Not "/": the landing page can send the user to their home page, which may be the blocked organization.
const ORGANIZATIONS_PATH = '/account/organizations';

interface BlockedState {
  from?: unknown;
  message?: unknown;
}

// Only an address inside this app can be retried; anything else in the history state is ignored.
function retryPath(from: unknown): string | undefined {
  if (
    typeof from !== 'string' ||
    !from.startsWith('/') ||
    from.startsWith('//') ||
    from.startsWith(IP_ALLOWLIST_BLOCKED_PATH)
  ) {
    return undefined;
  }
  return from;
}

export default function IpAllowlistBlocked() {
  const location = useLocation();
  const navigate = useNavigate();
  const { currentUser } = useCurrentUser();
  // While this page is shown, requests that fail with the same 403 must not add a toast of their own.
  useEffect(markIpAllowlistBlockedPageMounted, []);

  const state = (location.state ?? {}) as BlockedState;
  const message = typeof state.message === 'string' ? state.message : '';
  const details = parseIpAllowlistBlockedMessage(message);
  const retry = retryPath(state.from);
  const user = isLoggedIn(currentUser) ? currentUser : undefined;

  return (
    <CenteredLayout id="bd">
      <Helmet defer={false} title={translate('ip_allowlist.blocked.title')} />
      <PageContentFontWrapper className="sw-flex sw-justify-center" id="nonav">
        <Card className="sw-w-abs-600 sw-my-14 sw-px-10 sw-py-10 sw-text-center">
          <IconTile aria-hidden>
            <IconSecurityFinding color="echoes-color-icon-danger" />
          </IconTile>
          <Text
            as="p"
            className="sw-mt-6 sw-uppercase sw-tracking-wider"
            colorOverride="echoes-color-text-danger"
            size={TextSize.Small}
          >
            {translate('ip_allowlist.blocked.eyebrow')}
          </Text>
          <Heading as="h1" className="sw-mt-2" size={HeadingSize.ExtraLarge}>
            {translate('ip_allowlist.blocked.heading')}
          </Heading>
          <Text as="p" className="sw-mt-4" isSubdued size={TextSize.Large}>
            <FormattedMessage
              id={user ? 'ip_allowlist.blocked.intro.signed_in' : 'ip_allowlist.blocked.intro'}
              defaultMessage={translate(
                user ? 'ip_allowlist.blocked.intro.signed_in' : 'ip_allowlist.blocked.intro',
              )}
              values={{ product: <strong>{translate('ip_allowlist.blocked.product')}</strong> }}
            />
          </Text>

          {user && (
            <Panel className="sw-mt-8 sw-flex sw-items-center sw-gap-4 sw-text-left">
              <Avatar hash={user.avatar} name={user.name} size="md" />
              <div className="sw-flex-1 sw-min-w-0">
                <Text as="div" className="sw-truncate" isHighlighted>
                  {user.email ?? user.login}
                </Text>
                <Text as="div" isSubdued size={TextSize.Small}>
                  {translateWithParameters(
                    'ip_allowlist.blocked.user_description',
                    user.name ?? user.login,
                  )}
                </Text>
              </div>
              <Pill variant={PillVariant.Success}>
                {translate('ip_allowlist.blocked.signed_in')}
              </Pill>
            </Panel>
          )}

          <Panel as="dl" className={user ? 'sw-mt-4' : 'sw-mt-8'}>
            {details && (
              <>
                <DetailRow>
                  <dt>{translate('ip_allowlist.blocked.organization')}</dt>
                  <dd className="sw-typo-semibold">{details.organization}</dd>
                </DetailRow>
                <DetailRow>
                  <dt>{translate('ip_allowlist.blocked.ip')}</dt>
                  <dd className="sw-typo-semibold">
                    {details.ip === undefined ? (
                      translate('ip_allowlist.blocked.ip_unknown')
                    ) : (
                      <span className="sw-font-mono">{details.ip}</span>
                    )}
                  </dd>
                </DetailRow>
              </>
            )}
            {details === undefined && message !== '' && (
              <DetailRow>
                <dd className="sw-text-left">{message}</dd>
              </DetailRow>
            )}
            <DetailRow>
              <dt>{translate('ip_allowlist.blocked.reason')}</dt>
              <dd className="sw-typo-semibold">
                <Text colorOverride="echoes-color-text-danger">
                  {translate('ip_allowlist.blocked.reason.not_listed')}
                </Text>
              </dd>
            </DetailRow>
          </Panel>

          <div className="sw-mt-8 sw-flex sw-flex-wrap sw-justify-center sw-gap-3">
            <Button onClick={() => navigate(ORGANIZATIONS_PATH)} variety={ButtonVariety.Primary}>
              {translate('ip_allowlist.blocked.go_to_organizations')}
            </Button>
            {retry !== undefined && (
              <Button onClick={() => navigate(retry, { replace: true })}>
                {translate('ip_allowlist.blocked.retry')}
              </Button>
            )}
            {user && (
              <Button onClick={() => navigate('/sessions/logout')}>
                {translate('ip_allowlist.blocked.sign_out')}
              </Button>
            )}
          </div>

          <BasicSeparator className="sw-my-8" />
          <Text as="p" isSubdued size={TextSize.Small}>
            {translate(
              user ? 'ip_allowlist.blocked.footnote.signed_in' : 'ip_allowlist.blocked.footnote',
            )}
          </Text>
        </Card>
      </PageContentFontWrapper>
    </CenteredLayout>
  );
}

const IconTile = styled.div`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  font-size: 1.75rem;
  border-radius: var(--echoes-border-radius-400);
  background-color: var(--echoes-color-background-danger-weak-default);
`;

const Panel = styled.div`
  margin-left: 0;
  margin-right: 0;
  padding: var(--echoes-dimension-space-200) var(--echoes-dimension-space-300);
  border: 1px solid var(--echoes-color-border-weak);
  border-radius: var(--echoes-border-radius-200);
  background-color: var(--echoes-color-background-page);
`;

const DetailRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--echoes-dimension-space-300);
  padding: var(--echoes-dimension-space-150) 0;
  text-align: right;

  & + & {
    border-top: 1px solid var(--echoes-color-border-weak);
  }

  dt {
    color: var(--echoes-color-text-subdued);
    text-align: left;
  }

  dd {
    margin: 0;
    overflow-wrap: anywhere;
  }
`;
