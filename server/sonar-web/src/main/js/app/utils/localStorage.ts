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

import { AlmConnection } from '../../types/alm-connection';

export function encrypt(text: string): string {
  if (text.length <= 5) {
    return text;
  }

  const first5 = text.slice(0, 5);
  const rest = text.slice(5);

  const randLetters = Array.from({ length: 5 })
    .map(() => String.fromCharCode(97 + Math.floor(Math.random() * 26)))
    .join('');

  const randDigits = Array.from({ length: 5 })
    .map(() => Math.floor(Math.random() * 10).toString())
    .join('');

  return first5 + randLetters + randDigits + rest;
}

export function decrypt(encryptedText: string): string {
  const first5 = encryptedText.slice(0, 5);
  const after = encryptedText.slice(5 + 10);
  return first5 + after;
}

export const storeAlmConnectionInLocalStorage = (almConnection: AlmConnection): void => {
  const encryptedAlmConnection = {
    ...almConnection,
    url: almConnection.url,
    clientId: encrypt(almConnection.clientId),
    clientSecret: encrypt(almConnection.clientSecret),
  };

  localStorage.setItem('almConnectionTemp', JSON.stringify(encryptedAlmConnection));
};

export const getAlmConnectionFromLocalStorage = (): AlmConnection | null => {
  const stored = localStorage.getItem('almConnectionTemp');
  if (!stored) {
    return null;
  }

  const encryptedAlmConnection = JSON.parse(stored);

  const decryptedAlmConnection: AlmConnection = {
    ...encryptedAlmConnection,
    url: encryptedAlmConnection.url,
    clientId: decrypt(encryptedAlmConnection.clientId),
    clientSecret: decrypt(encryptedAlmConnection.clientSecret),
  };

  return decryptedAlmConnection;
};

export const removeAlmConnectionFromLocalStorage = (): void => {
  localStorage.removeItem('almConnectionTemp');
};
