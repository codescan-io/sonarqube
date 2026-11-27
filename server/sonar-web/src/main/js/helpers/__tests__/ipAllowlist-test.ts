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

import { dismissGlobalMessage } from '~design-system';
import {
  IP_ALLOWLIST_TOAST_ID,
  getIpAllowlistBlockedMessage,
  isIpAllowlistToastSuppressed,
  markIpAllowlistBlockedPageMounted,
  parseIpAllowlistBlockedMessage,
  showIpAllowlistBlockedPage,
} from '../ipAllowlist';
import { HttpStatus } from '../request';

jest.mock('~design-system', () => ({
  dismissGlobalMessage: jest.fn(),
}));

const MSG =
  "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

function response(status: number, body: object) {
  return new Response(JSON.stringify(body), { status });
}

it('returns the message of an allowlist 403', async () => {
  expect(
    await getIpAllowlistBlockedMessage(response(HttpStatus.Forbidden, { errors: [{ msg: MSG }] })),
  ).toBe(MSG);
});

it('leaves the body readable for the next handler', async () => {
  const res = response(HttpStatus.Forbidden, { errors: [{ msg: MSG }] });
  await getIpAllowlistBlockedMessage(res);
  expect(await res.json()).toEqual({ errors: [{ msg: MSG }] });
});

it('ignores other 403s', async () => {
  expect(
    await getIpAllowlistBlockedMessage(
      response(HttpStatus.Forbidden, { errors: [{ msg: 'Insufficient privileges' }] }),
    ),
  ).toBeUndefined();
});

it('ignores a 403 whose body is not JSON', async () => {
  expect(
    await getIpAllowlistBlockedMessage(new Response('<html>Forbidden</html>', { status: 403 })),
  ).toBeUndefined();
});

it('ignores a 403 with an unexpected body shape', async () => {
  expect(await getIpAllowlistBlockedMessage(response(403, { errors: [] }))).toBeUndefined();
  expect(
    await getIpAllowlistBlockedMessage(response(403, { errors: [{ msg: 42 }] })),
  ).toBeUndefined();
  expect(await getIpAllowlistBlockedMessage(response(403, { message: MSG }))).toBeUndefined();
});

it('ignores non-403 and non-Response values', async () => {
  expect(
    await getIpAllowlistBlockedMessage(response(HttpStatus.NotFound, { errors: [{ msg: MSG }] })),
  ).toBeUndefined();
  expect(await getIpAllowlistBlockedMessage(new Error('x'))).toBeUndefined();
  expect(await getIpAllowlistBlockedMessage(undefined)).toBeUndefined();
});

describe('reading the blocked message', () => {
  it('finds the organization key and the caller IP', () => {
    expect(parseIpAllowlistBlockedMessage(MSG)).toEqual({ organization: 'acme', ip: '192.0.2.1' });
  });

  it('reads an IPv6 address', () => {
    expect(
      parseIpAllowlistBlockedMessage(
        "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 2001:db8::1.",
      ),
    ).toEqual({ organization: 'acme', ip: '2001:db8::1' });
  });

  it('gives no IP when the server could not tell it', () => {
    expect(
      parseIpAllowlistBlockedMessage(
        "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is unknown.",
      ),
    ).toEqual({ organization: 'acme', ip: undefined });
  });

  it('gives nothing for other wording', () => {
    expect(parseIpAllowlistBlockedMessage('Access denied')).toBeUndefined();
    expect(parseIpAllowlistBlockedMessage('')).toBeUndefined();
  });
});

describe('suppressing the allowlist toast', () => {
  afterEach(() => {
    jest.restoreAllMocks();
    jest.clearAllMocks();
  });

  it('does not suppress anything by default', () => {
    expect(isIpAllowlistToastSuppressed()).toBe(false);
  });

  it('suppresses while the blocked page is shown, and stops when it goes away', () => {
    const unmount = markIpAllowlistBlockedPageMounted();
    expect(isIpAllowlistToastSuppressed()).toBe(true);

    unmount();

    expect(isIpAllowlistToastSuppressed()).toBe(false);
  });

  it('suppresses while a redirect to the blocked page is under way, but not forever', () => {
    const now = jest.spyOn(Date, 'now').mockReturnValue(1_000_000);
    showIpAllowlistBlockedPage(jest.fn(), MSG);
    expect(isIpAllowlistToastSuppressed()).toBe(true);

    now.mockReturnValue(1_000_000 + 60_000);

    expect(isIpAllowlistToastSuppressed()).toBe(false);
  });

  it('stops suppressing as soon as the blocked page is left, even within the redirect window', () => {
    showIpAllowlistBlockedPage(jest.fn(), MSG);
    const unmount = markIpAllowlistBlockedPageMounted();

    unmount();

    expect(isIpAllowlistToastSuppressed()).toBe(false);
  });

  it('removes only the allowlist toast when redirecting', () => {
    const navigate = jest.fn();
    window.history.pushState({}, '', '/');

    showIpAllowlistBlockedPage(navigate, MSG);

    expect(dismissGlobalMessage).toHaveBeenCalledTimes(1);
    expect(dismissGlobalMessage).toHaveBeenCalledWith(IP_ALLOWLIST_TOAST_ID);
    expect(navigate).toHaveBeenCalledWith('/sessions/ip-blocked', {
      replace: true,
      state: { message: MSG, from: '/' },
    });
  });
});

describe('remembering the blocked page', () => {
  const originalBaseUrl = (window as unknown as { baseUrl: string }).baseUrl;

  afterEach(() => {
    (window as unknown as { baseUrl: string }).baseUrl = originalBaseUrl;
    window.history.pushState({}, '', '/');
  });

  it('passes the address that was blocked, so the user can try it again', () => {
    const navigate = jest.fn();
    window.history.pushState({}, '', '/organizations/acme/projects?sort=name#top');

    showIpAllowlistBlockedPage(navigate, MSG);

    expect(navigate).toHaveBeenCalledWith('/sessions/ip-blocked', {
      replace: true,
      state: { message: MSG, from: '/organizations/acme/projects?sort=name#top' },
    });
  });

  it('passes the address without the context path the app is served under', () => {
    const navigate = jest.fn();
    (window as unknown as { baseUrl: string }).baseUrl = '/codescan';
    window.history.pushState({}, '', '/codescan/organizations/acme');

    showIpAllowlistBlockedPage(navigate, MSG);

    expect(navigate).toHaveBeenCalledWith('/sessions/ip-blocked', {
      replace: true,
      state: { message: MSG, from: '/organizations/acme' },
    });
  });
});
