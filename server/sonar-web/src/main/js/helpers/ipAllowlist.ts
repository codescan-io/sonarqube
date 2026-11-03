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

import { NavigateFunction } from 'react-router-dom';
import { dismissGlobalMessage } from '~design-system';
import { HttpStatus } from './request';
import { getBaseUrl } from './system';

export const IP_ALLOWLIST_BLOCKED_PATH = '/sessions/ip-blocked';

// Fixed id of the error toast for this 403, so it can be removed without touching any other toast.
export const IP_ALLOWLIST_TOAST_ID = 'ip-allowlist-blocked';

// How long after starting a redirect the toast stays suppressed if the blocked page never shows.
const REDIRECT_GRACE_MS = 10_000;

let redirectingUntil = 0;
let blockedPagesShown = 0;

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

// Matches IpAllowlistBlockedException on the server: the organization key, then the caller's IP or "unknown".
const MESSAGE_PATTERN =
  /^Access to organization '(.*)' is blocked by its IP allowlist\. Your IP address is (.+)\.$/;

export interface IpAllowlistBlockedDetails {
  /** Undefined when the server could not tell the caller's IP. */
  ip?: string;
  organization: string;
}

/** Read the organization key and the caller's IP out of the blocked message, or undefined for other wording. */
export function parseIpAllowlistBlockedMessage(
  message: string,
): IpAllowlistBlockedDetails | undefined {
  const match = MESSAGE_PATTERN.exec(message);
  if (!match) {
    return undefined;
  }
  return { organization: match[1], ip: match[2] === 'unknown' ? undefined : match[2] };
}

// The address of the page being shown, as the router knows it: without the context path the app is served under.
function currentAppPath(): string {
  const { pathname, search, hash } = window.location;
  const baseUrl = getBaseUrl();
  const path =
    baseUrl !== '' && pathname.startsWith(baseUrl) ? pathname.slice(baseUrl.length) : pathname;
  return `${path === '' ? '/' : path}${search}${hash}`;
}

/**
 * Show the blocked page in place of the page that was loading. It replaces the current history entry:
 * going back would only load the blocked page again.
 *
 * A request that fails with the same 403 while the redirect happens, or after it, must not put its
 * toast on top of the blocked page (see isIpAllowlistToastSuppressed). A toast that is already
 * showing is removed, and only that one.
 */
export function showIpAllowlistBlockedPage(navigate: NavigateFunction, message: string) {
  redirectingUntil = Date.now() + REDIRECT_GRACE_MS;
  dismissGlobalMessage(IP_ALLOWLIST_TOAST_ID);
  // `from` lets the blocked page offer to try the same address again.
  navigate(IP_ALLOWLIST_BLOCKED_PATH, {
    replace: true,
    state: { message, from: currentAppPath() },
  });
}

/**
 * Called by the blocked page while it is on screen. Returns the function that says it has gone, which
 * also ends the redirect window, so that toasts come back as soon as the user is on another page.
 */
export function markIpAllowlistBlockedPageMounted(): () => void {
  blockedPagesShown += 1;
  return () => {
    blockedPagesShown = Math.max(0, blockedPagesShown - 1);
    redirectingUntil = 0;
  };
}

/** True when the user is on the blocked page, or is being sent there right now. */
export function isIpAllowlistToastSuppressed(): boolean {
  return blockedPagesShown > 0 || Date.now() < redirectingUntil;
}
