
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