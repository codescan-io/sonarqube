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

import { getValues } from '../api/settings';
import { SEVERITIES } from './constants';

export const SEVERITIES_PROP_KEYS_SET = new Set(SEVERITIES.map(severity => `codescan.severity.masking.${severity}`));
const severityLabelCache = new Map<string, string>();

export function setSeverityLabel(severity: string, label: string) {
  severityLabelCache.set(severity, label);
}

export function getCachedSeverityLabel(severity: string): string | undefined {
  return severityLabelCache.get(severity);
}

export async function loadSeverityLabelsToCache() {

  try {
    const settings = await getValues({ keys: [...SEVERITIES_PROP_KEYS_SET] });
    settings.forEach(setting => {
      const severity = setting.key.split('.').at(-1);
      if (severity && setting.value) {
        setSeverityLabel(`severity.${severity}`, setting.value);
      }
    });
  } catch (err) {
      console.error('Error fetching severity labels:', err);
  }
}
