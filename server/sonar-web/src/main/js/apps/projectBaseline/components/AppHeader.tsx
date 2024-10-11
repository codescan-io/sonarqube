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
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import DocLink from '../../../components/common/DocLink';
import Link from '../../../components/common/Link';
import { translate } from '../../../helpers/l10n';

export interface AppHeaderProps {
  canAdmin: boolean;
}

export default function AppHeader(props: AppHeaderProps) {
  const { canAdmin } = props;

  return (
    <header className="page-header">
      <h1 className="sw-mb-4">{translate('project_baseline.page')}</h1>
      <p className="sw-mb-2">{translate('project_baseline.page.description')}</p>
      <p className="sw-mb-2">
        <FormattedMessage
          defaultMessage={translate('settings.new_code_period.description1')}
          id="settings.new_code_period.description1"
          values={{
            link: (
              <DocLink to="https://knowledgebase.autorabit.com/codescan/docs">
                {translate('settings.new_code_period.description1.link')}
              </DocLink>
            ),
          }}
        />
      </p>
      <p className="sw-mb-2">
        {canAdmin && (
          <FormattedMessage
            defaultMessage={translate('project_baseline.page.description2')}
            id="project_baseline.page.description2"
            values={{
              link: (
                <Link to="/admin/settings?category=new_code_period">
                  {translate('project_baseline.page.description2.link')}
                </Link>
              ),
            }}
          />
        )}
      </p>

      <p className="sw-mb-2">
        <FormattedMessage
          defaultMessage={translate('settings.new_code_period.description3')}
          id="settings.new_code_period.description3"
          values={{
            link: (
              <DocLink to="/project-administration/defining-new-code/">
                {translate('settings.new_code_period.description3.link')}
              </DocLink>
            ),
          }}
        />
      </p>

      <p className="sw-mt-4">
        <strong>{translate('project_baseline.page.question')}</strong>
      </p>
    </header>
  );
}
