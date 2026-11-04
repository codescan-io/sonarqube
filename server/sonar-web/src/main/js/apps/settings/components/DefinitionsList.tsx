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
import { themeBorder } from '~design-system';
import { useGetValueQuery } from '../../../queries/settings';
import { SettingDefinitionAndValue, SettingsKey } from '../../../types/settings';
import { Component } from '../../../types/types';
import Definition from './Definition';

interface Props {
  component?: Component;
  scrollToDefinition: (element: HTMLLIElement) => void;
  settings: SettingDefinitionAndValue[];
}

function DefinitionListItem(
  props: Readonly<{
    component?: Component;
    scrollToDefinition: (element: HTMLLIElement) => void;
    setting: SettingDefinitionAndValue;
  }>,
) {
  const { component, setting, scrollToDefinition } = props;
  const isProjectExtraction =
    setting.definition.key === SettingsKey.CodescanStaticResourceExtraction;

  const { data: extractionGate, isLoading: extractionGateLoading } = useGetValueQuery(
    { key: SettingsKey.CodescanStaticResourceExtractionEnabled },
    { enabled: isProjectExtraction },
  );

  // Hide project extraction (and its list row) when the instance gate is off.
  if (
    isProjectExtraction &&
    (extractionGateLoading || extractionGate?.value !== 'true')
  ) {
    return null;
  }

  return (
    <StyledListItem
      className="sw-p-6"
      data-scroll-key={setting.definition.key}
      ref={scrollToDefinition}
    >
      <Definition
        component={component}
        definition={setting.definition}
        initialSettingValue={setting.settingValue}
      />
    </StyledListItem>
  );
}

export default function DefinitionsList(props: Readonly<Props>) {
  const { component, settings } = props;
  return (
    <ul>
      {settings.map((setting) => (
        <DefinitionListItem
          key={setting.definition.key}
          component={component}
          scrollToDefinition={props.scrollToDefinition}
          setting={setting}
        />
      ))}
    </ul>
  );
}

const StyledListItem = styled.li`
  & + & {
    border-top: ${themeBorder('default')};
  }
`;
