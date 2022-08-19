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
import { IndexLink, Link } from 'react-router';
import DateFromNow from 'sonar-ui-common/components/intl/DateFromNow';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getGrcProfilesUrl } from '../../../../js/helpers/urls';
import BuiltInQualityProfileBadge from '../components/BuiltInQualityProfileBadge';
import ProfileActions from '../components/ProfileActions';
import ProfileLink from '../components/ProfileLink';
import { Profile } from '../types';
import { getProfileChangelogPath, getProfilesForLanguagePath, getProfilesPath } from '../utils';

interface Props {
  profile: Profile;
  organization: string | null;
  updateProfiles: () => Promise<void>;
  grc?:boolean;
  componentKey?:string
}

export default class ProfileHeader extends React.PureComponent<Props> {
  render() {
    const {componentKey, organization, profile, grc } = this.props;
    let description = translate('quality_profiles.built_in.description');
    let pageTitle = translate('quality_profiles.page');
    if(grc){
      description =  translate('grc.quality_profiles.built_in.description');
      pageTitle = translate('grc.quality_profiles.page');
    }

    return (
      <header className="page-header quality-profile-header">
        <div className="note spacer-bottom">
        {
            grc && componentKey ? (
              <>
              <IndexLink className="text-muted" to={getGrcProfilesUrl(componentKey)}>
                {pageTitle}
              </IndexLink>
              </>
            ) :(
              <>
              <IndexLink className="text-muted" to={getProfilesPath(organization)}>
                {pageTitle}
              </IndexLink>
              {' / '}
              <Link
                className="text-muted"
                to={getProfilesForLanguagePath(profile.language, organization)}>
                {profile.languageName}
              </Link>
              </>
            )
          }
        </div>

        <h1 className="page-title">
        { grc ? (<span>{profile.name}</span>):(
           <ProfileLink
           className="link-base-color"
           language={profile.language}
           name={profile.name}
           organization={organization}>
           <span>{profile.name}</span>
         </ProfileLink>
        )}{profile.isBuiltIn && (
            <BuiltInQualityProfileBadge className="spacer-left" tooltip={false} />
          )}
        </h1>

        <div className="pull-right">
          <ul className="list-inline" style={{ lineHeight: '24px' }}>
            <li className="small spacer-right">
              {translate('quality_profiles.updated_')} <DateFromNow date={profile.rulesUpdatedAt} />
            </li>
            <li className="small big-spacer-right">
              {translate('quality_profiles.used_')} <DateFromNow date={profile.lastUsed} />
            </li>
            {
              grc ? (<></>):(<>
              <li>
              <Link
                className="button"
                to={getProfileChangelogPath(profile.name, profile.language, organization)}>
                {translate('changelog')}
              </Link>
            </li>
            <li>
              <ProfileActions
                className="pull-left"
                organization={organization}
                profile={profile}
                updateProfiles={this.props.updateProfiles}
              />
            </li></>)
            }
          </ul>
        </div>

        {profile.isBuiltIn && (
          <div className="page-description">
            {description}
          </div>
        )}
      </header>
    );
  }
}
