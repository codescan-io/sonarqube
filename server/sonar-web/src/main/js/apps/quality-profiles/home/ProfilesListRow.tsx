/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import { Link } from 'react-router';
import Tooltip from 'sonar-ui-common/components/controls/Tooltip';
import DateFromNow from 'sonar-ui-common/components/intl/DateFromNow';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getRulesUrl } from '../../../helpers/urls';
import BuiltInQualityProfileBadge from '../components/BuiltInQualityProfileBadge';
import ProfileActions from '../components/ProfileActions';
import ProfileLink from '../components/ProfileLink';
import { Profile } from '../types';

export interface ProfilesListRowProps {
  organization: string | null;
  profile: Profile;
  updateProfiles: () => Promise<void>;
  grc?:boolean;
  componentKey?:string;
}

export function ProfilesListRow(props: ProfilesListRowProps) {
  const { componentKey, organization, profile, grc } = props;

  const offset = 25 * (profile.depth - 1);
  let queryParams:any = {
    qprofile: profile.key,
    activation: 'true'
  }
  let dQueryParams:any = {
    qprofile: profile.key,
    activation: 'true',
    statuses: 'DEPRECATED'
  };
  if(grc){
    queryParams.id = componentKey;
    queryParams.languages = 'sfmeta';
    queryParams.repositories = 'grc';

    dQueryParams.id = componentKey;
    dQueryParams.languages = 'sfmeta';
    dQueryParams.repositories = 'grc';

  }
  const activeRulesUrl = getRulesUrl(
    queryParams,
    organization,
    grc
  );
 
  const deprecatedRulesUrl = getRulesUrl(
    dQueryParams,
    organization,
    grc
  );

  return (
    <tr
      className="quality-profiles-table-row text-middle"
      data-key={profile.key}
      data-name={profile.name}>
      <td className="quality-profiles-table-name text-middle">
        <div className="display-flex-center" style={{ paddingLeft: offset }}>
          <div>
            <ProfileLink
              componentKey={componentKey}
              grc={grc}
              language={profile.language}
              name={profile.name}
              organization={organization}>
              {profile.name}
            </ProfileLink>
          </div>
          {profile.isBuiltIn && <BuiltInQualityProfileBadge className="spacer-left" />}
        </div>
      </td>

      <td className="quality-profiles-table-projects thin nowrap text-middle text-right">
        {profile.isDefault ? (
          <Tooltip overlay={translate('quality_profiles.list.default.help')}>
            <span className="badge">{translate('default')}</span>
          </Tooltip>
        ) : (
          <span>{profile.projectCount}</span>
        )}
      </td>

      <td className="quality-profiles-table-rules thin nowrap text-middle text-right">
        <div>
          {profile.activeDeprecatedRuleCount > 0 && (
            <span className="spacer-right">
              <Tooltip overlay={translate('quality_profiles.deprecated_rules')}>
                <Link className="badge badge-error" to={deprecatedRulesUrl}>
                  {profile.activeDeprecatedRuleCount}
                </Link>
              </Tooltip>
            </span>
          )}

          <Link to={activeRulesUrl}>{profile.activeRuleCount}</Link>
        </div>
      </td>

      <td className="quality-profiles-table-date thin nowrap text-middle text-right">
        <DateFromNow date={profile.rulesUpdatedAt} />
      </td>

      <td className="quality-profiles-table-date thin nowrap text-middle text-right">
        <DateFromNow date={profile.lastUsed} />
      </td>{
        grc ? (<td></td>) : (
          <td className="quality-profiles-table-actions thin nowrap text-middle text-right">
            <ProfileActions
              fromList={true}
              organization={organization}
              profile={profile}
              updateProfiles={props.updateProfiles}
            />
          </td>
        )
      }

      
    </tr>
  );
}

export default React.memo(ProfilesListRow);
