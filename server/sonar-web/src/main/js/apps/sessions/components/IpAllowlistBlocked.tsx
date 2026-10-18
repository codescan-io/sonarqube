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

import { Helmet } from 'react-helmet-async';
import { useLocation } from 'react-router-dom';
import { Card, CenteredLayout, Link, PageContentFontWrapper } from '~design-system';
import { translate } from '../../../helpers/l10n';

export default function IpAllowlistBlocked() {
  const location = useLocation();
  const message = (location.state as { message?: unknown } | null)?.message;

  return (
    <CenteredLayout id="bd">
      <Helmet defer={false} title={translate('ip_allowlist.blocked.title')} />
      <PageContentFontWrapper className="sw-typo-lg sw-flex sw-justify-center" id="nonav">
        <Card className="sw-w-abs-500 sw-my-14 sw-text-center">
          <h1 className="sw-mb-4">{translate('ip_allowlist.blocked.title')}</h1>
          <p id="ip-allowlist-blocked">
            {typeof message === 'string' && message
              ? message
              : translate('ip_allowlist.blocked.generic')}
          </p>
          <p className="sw-mt-4">{translate('ip_allowlist.blocked.contact_admin')}</p>
          <div className="sw-mt-8">
            {/* Not "/": the landing page can send the user to their home page, which may be the blocked organization. */}
            <Link to="/projects">{translate('layout.home')}</Link>
          </div>
        </Card>
      </PageContentFontWrapper>
    </CenteredLayout>
  );
}
