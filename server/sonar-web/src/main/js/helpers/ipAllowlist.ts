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

import { HttpStatus } from './request';

// Must match IpAllowlistBlockedException.MESSAGE_MARKER on the server.
const MESSAGE_MARKER = 'is blocked by its IP allowlist';

/**
 * If the error is the server's "blocked by the organization's IP allowlist" 403, return its message.
 * Any other value (other status, other 403, non-JSON body, not a Response) gives undefined.
 * The message contains the organization key and the caller's IP, so only ever render it as text.
 */
export async function getIpAllowlistBlockedMessage(e: unknown): Promise<string | undefined> {
  if (!(e instanceof Response) || e.status !== HttpStatus.Forbidden) {
    return undefined;
  }
  try {
    const body = await e.clone().json();
    const msg: unknown = body?.errors?.[0]?.msg;
    return typeof msg === 'string' && msg.includes(MESSAGE_MARKER) ? msg : undefined;
  } catch {
    return undefined;
  }
}
