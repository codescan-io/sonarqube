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
import { GRADLE_SCANNER_VERSION } from '../../../../helpers/constants';
import { translate } from '../../../../helpers/l10n';
import { Component } from '../../../../types/types';
import CodeSnippet from '../../../common/CodeSnippet';
import DocLink from '../../../common/DocLink';
import InstanceMessage from '../../../common/InstanceMessage';
import GradleBuildSelection from '../../components/GradleBuildSelection';
import { GradleBuildDSL } from '../../types';
import DoneNextSteps from '../DoneNextSteps';

export interface JavaGradleProps {
  component: Component;
  baseUrl: string;
  token: string;
}

const config = {
  [GradleBuildDSL.Groovy]: `plugins {
  id "org.sonarqube" version "${GRADLE_SCANNER_VERSION}"
}`,
  [GradleBuildDSL.Kotlin]: `plugins {
  id("org.sonarqube") version "${GRADLE_SCANNER_VERSION}"
}`,
};

export default function JavaGradle(props: JavaGradleProps) {
  const { baseUrl, component, token } = props;

  const command = [
    './gradlew sonar',
    `-Dsonar.projectKey=${component.key}`,
    `-Dsonar.projectName='${component.name}'`,
    `-Dsonar.host.url=${baseUrl}`,
    `-Dsonar.token=${token}`,
  ];

  return (
    <div>
      <h4 className="spacer-bottom">{translate('onboarding.analysis.java.gradle.header')}</h4>
      <InstanceMessage message={translate('onboarding.analysis.java.gradle.text.1')}>
        {(transformedMessage) => (
          <p className="spacer-bottom markdown">
            <FormattedMessage
              defaultMessage={transformedMessage}
              id="onboarding.analysis.java.gradle.text.1"
              values={{
                plugin_code: <code>org.sonarqube</code>,
                groovy: <code>{GradleBuildDSL.Groovy}</code>,
                kotlin: <code>{GradleBuildDSL.Kotlin}</code>,
              }}
            />
          </p>
        )}
      </InstanceMessage>
      <GradleBuildSelection className="big-spacer-top big-spacer-bottom">
        {(build) => <CodeSnippet snippet={config[build]} />}
      </GradleBuildSelection>
      <p className="big-spacer-bottom markdown">
        <em className="small text-muted">
          <FormattedMessage
            defaultMessage={translate('onboarding.analysis.java.gradle.latest_version')}
            id="onboarding.analysis.java.gradle.latest_version"
            values={{
              link: (
                <DocLink to="https://knowledgebase.autorabit.com/codescan/docs">
                  {translate('here')}
                </DocLink>
              ),
            }}
          />
        </em>
      </p>
      <p className="spacer-top spacer-bottom markdown">
        {translate('onboarding.analysis.java.gradle.text.2')}
      </p>
      <CodeSnippet snippet={command} />
      <p className="big-spacer-top markdown">
        <FormattedMessage
          defaultMessage={translate('onboarding.analysis.docs')}
          id="onboarding.analysis.docs"
          values={{
            link: (
              <DocLink to="https://knowledgebase.autorabit.com/codescan/docs">
                {translate('onboarding.analysis.java.gradle.docs_link')}
              </DocLink>
            ),
          }}
        />
      </p>
      <DoneNextSteps component={component} />
    </div>
  );
}
