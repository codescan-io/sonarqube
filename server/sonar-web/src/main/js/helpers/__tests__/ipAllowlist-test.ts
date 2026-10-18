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

import { getIpAllowlistBlockedMessage } from '../ipAllowlist';
import { HttpStatus } from '../request';

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
