type Setting = { key: string; value?: string | null };
type SettingsResponse = { settings: Setting[] };

const MSA_CONSENT_DISPLAY_KEY = 'codescan.cloud.msaConsent.displayMessage';

function readBooleanSetting(settings: Setting[] | undefined, key: string): boolean {
  return settings?.find((s) => s.key === key)?.value === 'true';
}

export function isMsaConsentPopupEnabled(resp: SettingsResponse): boolean {
  return readBooleanSetting(resp.settings, MSA_CONSENT_DISPLAY_KEY);
}