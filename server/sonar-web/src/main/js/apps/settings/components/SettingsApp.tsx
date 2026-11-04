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

import * as React from 'react';
import { getDefinitions, getValues } from '../../../api/settings';
import withComponentContext from '../../../app/components/componentContext/withComponentContext';
import { ExtendedSettingDefinition, SettingsKey, SettingValue } from '../../../types/settings';
import { Component } from '../../../types/types';
import '../styles.css';
import SettingsAppRenderer from './SettingsAppRenderer';

interface Props {
  component?: Component;
}

interface State {
  definitions: ExtendedSettingDefinition[];
  loading: boolean;
}

class SettingsApp extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { definitions: [], loading: true };

  componentDidMount() {
    this.mounted = true;
    this.fetchSettings();
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.component !== this.props.component) {
      this.fetchSettings();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  fetchSettings = async () => {
    const { component } = this.props;

    const definitions: ExtendedSettingDefinition[] = await getDefinitions(component?.key).catch(
      () => [],
    );

    const filteredDefinitions = component
      ? await this.filterProjectDefinitions(definitions)
      : definitions;

    if (this.mounted) {
      this.setState({ definitions: filteredDefinitions, loading: false });
    }
  };

  /**
   * Hide the project-level static resource toggle when the instance gate is off.
   * Definition still disables the control as a fallback if the setting is shown.
   */
  filterProjectDefinitions = async (
    definitions: ExtendedSettingDefinition[],
  ): Promise<ExtendedSettingDefinition[]> => {
    const hasProjectToggle = definitions.some(
      (definition) => definition.key === SettingsKey.CodescanStaticResourceExtraction,
    );
    if (!hasProjectToggle) {
      return definitions;
    }

    const instanceEnabled = await this.isStaticResourceExtractionInstanceEnabled();
    if (instanceEnabled) {
      return definitions;
    }

    return definitions.filter(
      (definition) => definition.key !== SettingsKey.CodescanStaticResourceExtraction,
    );
  };

  isStaticResourceExtractionInstanceEnabled = async (): Promise<boolean> => {
    try {
      const values: (SettingValue | undefined)[] = await getValues({
        keys: [SettingsKey.CodescanStaticResourceExtractionEnabled],
      });
      const setting = values.find(
        (value) => value?.key === SettingsKey.CodescanStaticResourceExtractionEnabled,
      );
      return setting?.value === 'true';
    } catch {
      return false;
    }
  };

  render() {
    const { component } = this.props;
    return <SettingsAppRenderer component={component} {...this.state} />;
  }
}

export default withComponentContext(SettingsApp);
