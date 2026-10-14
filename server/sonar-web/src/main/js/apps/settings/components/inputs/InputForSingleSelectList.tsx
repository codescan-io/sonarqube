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

import { InputSize, Select } from '@sonarsource/echoes-react';
import * as React from 'react';
import { SEVERITIES_PROP_KEYS_SET, getCachedSeverityLabel } from '../../../../helpers/severityMasking';
import { useGetValuesQuery } from '../../../../queries/settings';
import { ExtendedSettingDefinition, SettingsKey } from '../../../../types/settings';
import { DefaultSpecializedInputProps, getPropertyName } from '../../utils';

type Props = DefaultSpecializedInputProps & Pick<ExtendedSettingDefinition, 'options'>;

function InputForSingleSelectList(
  props: Readonly<Props>,
  ref: React.ForwardedRef<HTMLInputElement>,
) {
  const { name, options: opts, value, setting, disabled } = props;
  const isStaticResourceSeverity =
    setting.definition.key === SettingsKey.CodescanStaticResourceCustomSeverity;

  const { data: maskingSettings } = useGetValuesQuery([...SEVERITIES_PROP_KEYS_SET], {
    enabled: isStaticResourceSeverity,
  });

  const options = React.useMemo(() => {
    const maskingBySeverity = new Map<string, string>();
    maskingSettings?.forEach((settingValue) => {
      const severity = settingValue.key.split('.').at(-1);
      if (severity && settingValue.value) {
        maskingBySeverity.set(severity, settingValue.value);
      }
    });

    return opts.map((option) => {
      if (!isStaticResourceSeverity) {
        return { label: option, value: option };
      }
      const label =
        maskingBySeverity.get(option) ||
        getCachedSeverityLabel(`severity.${option}`) ||
        option;
      return { label, value: option };
    });
  }, [opts, isStaticResourceSeverity, maskingSettings]);

  return (
    <Select
      ariaLabel={getPropertyName(setting.definition)}
      data={options}
      isDisabled={disabled}
      isNotClearable
      name={name}
      onChange={props.onChange}
      ref={ref}
      size={InputSize.Large}
      value={value}
    />
  );
}

export default React.forwardRef(InputForSingleSelectList);
