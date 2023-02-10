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
import { fetchL10nBundle } from '../api/l10n';
import { L10nBundle, L10nBundleRequestParams } from '../types/l10nBundle';
import { toNotSoISOString } from './dates';

const DEFAULT_LOCALE = 'en';
const DEFAULT_MESSAGES = {
  // eslint-disable-next-line camelcase
  default_error_message: 'The request cannot be processed. Try again later.',
};

export function getMessages() {
  return getL10nBundleFromCache().messages ?? DEFAULT_MESSAGES;
}

export function getCurrentLocale() {
  return getL10nBundleFromCache().locale;
}

export function getCurrentL10nBundle() {
  return getL10nBundleFromCache();
}

export async function loadL10nBundle() {
  const browserLocale = getPreferredLanguage();
  const cachedBundle = getL10nBundleFromCache();

  const params: L10nBundleRequestParams = {};

  if (browserLocale) {
    params.locale = browserLocale;

    if (
      cachedBundle.locale &&
      browserLocale.startsWith(cachedBundle.locale) &&
      cachedBundle.timestamp &&
      cachedBundle.messages
    ) {
      params.ts = cachedBundle.timestamp;
    }
  }

  const { effectiveLocale, messages } = await fetchL10nBundle(params).catch((response) => {
    if (response && response.status === 304) {
      return {
        effectiveLocale: cachedBundle.locale || browserLocale || DEFAULT_LOCALE,
        messages: cachedBundle.messages ?? {},
      };
    }
    throw new Error(`Unexpected status code: ${response.status}`);
  });

  const bundle = {
    timestamp: toNotSoISOString(new Date()),
    locale: effectiveLocale,
    messages,
  };

  persistL10nBundleInCache(bundle);

  return bundle;
}

function getPreferredLanguage() {
  return window.navigator.languages ? window.navigator.languages[0] : window.navigator.language;
}

function getL10nBundleFromCache(): L10nBundle {
  return (window as unknown as any).sonarQubeL10nBundle ?? {};
}

function persistL10nBundleInCache(bundle: L10nBundle) {
  (window as unknown as any).sonarQubeL10nBundle = bundle;
}
