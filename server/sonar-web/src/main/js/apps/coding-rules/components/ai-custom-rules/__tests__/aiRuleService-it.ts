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

import { postJSONBody } from '../../../../../helpers/request';
import { generateAIXPath, parseCodescanErrorMessage } from '../aiRuleService';

jest.mock('../../../../../helpers/request', () => ({
  ...jest.requireActual('../../../../../helpers/request'),
  postJSONBody: jest.fn().mockResolvedValue({}),
  get: jest.fn(),
  parseJSON: jest.fn(),
}));

const postJSONBodyMock = jest.mocked(postJSONBody);

beforeEach(() => {
  jest.clearAllMocks();
  postJSONBodyMock.mockResolvedValue({});
});

describe('generateAIXPath', () => {
  it('sends the rule key and template key so the api can reject a duplicate before spending credits', async () => {
    await generateAIXPath({
      description: 'Flows must have a description',
      organization: 'my-org',
      ruleKey: 'No_SOQL_In_Loops',
      templateKey: 'sfmeta:FlowTemplate',
    });

    expect(postJSONBodyMock).toHaveBeenCalledWith('/_codescan/ai-rules/generate-xpath', {
      description: 'Flows must have a description',
      organization: 'my-org',
      ruleKey: 'No_SOQL_In_Loops',
      templateKey: 'sfmeta:FlowTemplate',
    });
  });
});

describe('parseCodescanErrorMessage', () => {
  it('extracts the message from a codescanng error body', async () => {
    const response = new Response(
      JSON.stringify({ error: "A rule with the key 'No_SOQL_In_Loops' already exists" }),
      { status: 400 },
    );

    await expect(parseCodescanErrorMessage(response)).resolves.toBe(
      "A rule with the key 'No_SOQL_In_Loops' already exists",
    );
  });

  it('leaves the response body readable for other consumers', async () => {
    const response = new Response(JSON.stringify({ error: 'boom' }), { status: 400 });

    await parseCodescanErrorMessage(response);

    await expect(response.json()).resolves.toEqual({ error: 'boom' });
  });

  it('returns undefined for a SonarQube-shaped error body', async () => {
    const response = new Response(JSON.stringify({ errors: [{ msg: 'nope' }] }), { status: 400 });

    await expect(parseCodescanErrorMessage(response)).resolves.toBeUndefined();
  });

  it('returns undefined for a non-JSON body', async () => {
    const response = new Response('<html>502</html>', { status: 502 });

    await expect(parseCodescanErrorMessage(response)).resolves.toBeUndefined();
  });

  it('returns undefined for a thrown Error rather than a Response', async () => {
    await expect(parseCodescanErrorMessage(new Error('offline'))).resolves.toBeUndefined();
  });
});
