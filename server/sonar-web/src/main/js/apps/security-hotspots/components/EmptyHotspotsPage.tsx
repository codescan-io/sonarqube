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
import { connect } from 'react-redux';
import { Link } from 'react-router';
import { translate } from 'sonar-ui-common/helpers/l10n';
import { getBaseUrl } from 'sonar-ui-common/helpers/urls';
import { getAppState, Store } from '../../../store/rootReducer';

export interface EmptyHotspotsPageProps {
  filtered: boolean;
  isStaticListOfHotspots: boolean;
  appState: T.AppState | undefined;
}

function EmptyHotspotsPage(props: EmptyHotspotsPageProps) {
  const { filtered, isStaticListOfHotspots } = props;

  let translationRoot;
  if (isStaticListOfHotspots) {
    translationRoot = 'no_hotspots_for_keys';
  } else if (filtered) {
    translationRoot = 'no_hotspots_for_filters';
  } else {
    translationRoot = 'no_hotspots';
  }

  return (
    <div className="display-flex-column display-flex-center huge-spacer-top">
      <img
        alt={translate('hotspots.page')}
        className="huge-spacer-top"
        height={100}
        src={`${getBaseUrl()}/images/${filtered ? 'filter-large' : 'hotspot-large'}.svg`}
      />
      <h1 className="huge-spacer-top">{translate(props.appState?.grc ? `grc.hotspots.${translationRoot}.title` : `hotspots.${translationRoot}.title`)}</h1>
      <div className="abs-width-400 text-center big-spacer-top">
        {translate(`hotspots.${translationRoot}.description`)}
      </div>
      {!(filtered || isStaticListOfHotspots) && (
        <Link
          className="big-spacer-top"
          target="_blank"
          to={{ pathname: '/documentation/user-guide/security-hotspots/' }}>
          {translate(props.appState?.grc ? 'grc.hotspots.learn_more' : 'hotspots.learn_more')}
        </Link>
      )}
    </div>
  );
}

const mapStateToProps = (state: Store) => ({
  appState: getAppState(state)
});

export default connect(mapStateToProps)(EmptyHotspotsPage);
