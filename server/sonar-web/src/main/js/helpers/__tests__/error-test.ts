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

import { addGlobalErrorMessage } from '~design-system';
import { throwGlobalError } from '~sonar-aligned/helpers/error';

jest.mock('~design-system', () => ({
  addGlobalErrorMessage: jest.fn(),
}));

beforeEach(() => {
  jest.useFakeTimers();
  jest.clearAllMocks();
});

afterEach(() => {
  jest.runOnlyPendingTimers();
  jest.useRealTimers();
});

it('should display the error message', async () => {
  const response = new Response();
  response.json = jest.fn().mockResolvedValue({ errors: [{ msg: 'error 1' }] });

  // We need to catch because throwGlobalError rethrows after displaying the message
  await throwGlobalError(response)
    .then(() => {
      throw new Error('Should throw');
    })
    .catch(() => {});

  expect(addGlobalErrorMessage).toHaveBeenCalledWith('error 1');
});

it('should display the default error messsage', async () => {
  const response = new Response();
  response.json = jest.fn().mockResolvedValue({});

  // We need to catch because throwGlobalError rethrows after displaying the message
  await throwGlobalError(response)
    .then(() => {
      throw new Error('Should throw');
    })
    .catch(() => {});

  expect(addGlobalErrorMessage).toHaveBeenCalledWith('default_error_message');
});

it('should handle weird response types', async () => {
  const response = { weird: 'response type' };

  await expect(
    throwGlobalError(response).then(() => {
      throw new Error('Should throw');
    }),
  ).rejects.toBe(response);
});

it('should unwrap response if necessary', async () => {
  const response = new Response();
  response.json = jest.fn().mockResolvedValue({});

  /* eslint-disable-next-line no-console */
  console.warn = jest.fn();

  // We need to catch because throwGlobalError rethrows after displaying the message
  await throwGlobalError({ response })
    .then(() => {
      throw new Error('Should throw');
    })
    .catch(() => {});

  /* eslint-disable-next-line no-console */
  expect(console.warn).toHaveBeenCalled();
});

describe('IP allowlist 403', () => {
  const MSG =
    "Access to organization 'acme' is blocked by its IP allowlist. Your IP address is 192.0.2.1.";

  function jsonResponse(status: number, msg: string) {
    return new Response(JSON.stringify({ errors: [{ msg }] }), { status });
  }

  it('rejects with the response but shows no message', async () => {
    const response = jsonResponse(403, MSG);

    await expect(throwGlobalError(response)).rejects.toBe(response);

    expect(addGlobalErrorMessage).not.toHaveBeenCalled();
  });

  it('still shows the message of any other 403', async () => {
    await throwGlobalError(jsonResponse(403, 'Insufficient privileges')).catch(() => {});

    expect(addGlobalErrorMessage).toHaveBeenCalledWith('Insufficient privileges');
  });

  it('still shows the message of a 500 that carries the same words', async () => {
    await throwGlobalError(jsonResponse(500, MSG)).catch(() => {});

    expect(addGlobalErrorMessage).toHaveBeenCalledWith(MSG);
  });
});
