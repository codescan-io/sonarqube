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
import { getMessages } from './l10nBundle';

export const SEVERITIES_PROP_KEYS_SET = new Set(
  SEVERITIES.map((severity) => `codescan.severity.masking.${severity}`),
);
const severityLabelCache = new Map<string, string>();

/** Severities that share the same enum value in standard and impact modes. */
const IMPACT_ALIGNED_SEVERITIES = new Set(['BLOCKER', 'INFO']);

type SeverityLabelsListener = () => void;
const listeners = new Set<SeverityLabelsListener>();

export function onSeverityLabelsChanged(listener: SeverityLabelsListener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notifySeverityLabelsChanged() {
  listeners.forEach((listener) => listener());
}

export function setSeverityLabel(severity: string, label: string) {
  severityLabelCache.set(severity, label);
}

export function getCachedSeverityLabel(severity: string): string | undefined {
  return severityLabelCache.get(severity);
}

function applyLabelToMessages(messageKey: string, label: string) {
  setSeverityLabel(messageKey, label);
  const messages = getMessages();
  if (messages) {
    messages[messageKey] = label;
  }
}

export async function loadSeverityLabelsToCache() {
  try {
    const settings = await getValues({ keys: [...SEVERITIES_PROP_KEYS_SET] });
    settings.forEach((setting) => {
      const severity = setting.key.split('.').at(-1);
      if (severity && setting.value) {
        applyLabelToMessages(`severity.${severity}`, setting.value);
        // MQR / impact pills use severity_impact.*; INFO and BLOCKER share names with standard.
        if (IMPACT_ALIGNED_SEVERITIES.has(severity)) {
          applyLabelToMessages(`severity_impact.${severity}`, setting.value);
        }
      }
    });
    notifySeverityLabelsChanged();
  } catch (err) {
    console.error('Error fetching severity labels:', err);
  }
}
