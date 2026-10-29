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

import { generateAIXPath } from '../../apps/coding-rules/components/ai-custom-rules/aiRuleService';
import { axiosToCatch, postJSON, postJSONBody } from '../../helpers/request';
import { addIssueComment, editIssueComment } from '../issues';
import { createLink } from '../projectLinks';
import { createRule, updateRule } from '../rules';

jest.mock('../../helpers/request', () => ({
  ...jest.requireActual('../../helpers/request'),
  axiosToCatch: { post: jest.fn().mockResolvedValue({}) },
  postJSON: jest.fn().mockResolvedValue({}),
  postJSONBody: jest.fn().mockResolvedValue({}),
}));

// Free text from CSN-246 that the WAF blocks in a request body.
const TEXT = "See ../../classes/AccountService.cls where Name = 'a' OR 1=1 -- é";

function decodeBase64Url(value: string) {
  const binary = window.atob(value.replace(/-/g, '+').replace(/_/g, '/'));
  return new TextDecoder().decode(Uint8Array.from(binary, (c) => c.charCodeAt(0)));
}

function lastBody(fn: unknown, argIndex = 1) {
  const { calls } = jest.mocked(fn as (...args: unknown[]) => unknown).mock;
  return calls[calls.length - 1][argIndex];
}

function expectEncodedOnly(body: Record<string, unknown>, plainKey: string, encodedKey: string) {
  expect(body[plainKey]).toBeUndefined();
  expect(body[encodedKey]).toMatch(/^[A-Za-z0-9_-]+$/);
  expect(decodeBase64Url(body[encodedKey] as string)).toBe(TEXT);
}

beforeEach(() => {
  jest.clearAllMocks();
});

it('should send issue comment text encoded', async () => {
  await addIssueComment({ issue: 'AX1', text: TEXT });
  expect(postJSON).toHaveBeenLastCalledWith('/api/issues/add_comment', expect.anything());
  expect(lastBody(postJSON)).toMatchObject({ issue: 'AX1' });
  expectEncodedOnly(lastBody(postJSON), 'text', 'encodedText');

  await editIssueComment({ comment: 'C1', text: TEXT });
  expect(postJSON).toHaveBeenLastCalledWith('/api/issues/edit_comment', expect.anything());
  expect(lastBody(postJSON)).toMatchObject({ comment: 'C1' });
  expectEncodedOnly(lastBody(postJSON), 'text', 'encodedText');
});

it('should send an empty comment text unchanged', async () => {
  await addIssueComment({ issue: 'AX1', text: '' });
  expect(lastBody(postJSON)).toEqual({ issue: 'AX1', text: '' });
});

it('should send project link url encoded', async () => {
  jest.mocked(postJSON).mockResolvedValueOnce({ link: {} });
  const url = 'https://wiki.example.com/team/../apex';
  await createLink({ name: 'Wiki', projectKey: 'p1', url });

  const body = lastBody(postJSON);
  expect(postJSON).toHaveBeenLastCalledWith('/api/project_links/create', expect.anything());
  expect(body).toMatchObject({ name: 'Wiki', projectKey: 'p1' });
  expect(body.url).toBeUndefined();
  expect(decodeBase64Url(body.encodedUrl)).toBe(url);
});

it('should send rule description encoded on create', async () => {
  await createRule({
    organization: 'org',
    impacts: [],
    key: 'apex:my_rule',
    markdownDescription: TEXT,
    name: 'My rule',
    templateKey: 'apex:XPathTemplateRule',
  });

  const body = lastBody(axiosToCatch.post);
  expect(body).toMatchObject({ key: 'apex:my_rule', name: 'My rule' });
  expectEncodedOnly(body, 'markdownDescription', 'encodedMarkdownDescription');
});

it('should send rule description encoded on update', async () => {
  jest.mocked(postJSON).mockResolvedValueOnce({ rule: {} });
  await updateRule({ key: 'apex:my_rule', markdownDescription: TEXT, name: 'My rule' });

  const body = lastBody(postJSON);
  expect(postJSON).toHaveBeenLastCalledWith('/api/rules/update', expect.anything());
  expect(body).toMatchObject({ key: 'apex:my_rule', name: 'My rule' });
  expectEncodedOnly(body, 'markdownDescription', 'encodedMarkdownDescription');
});

it('should leave an update without description unchanged', async () => {
  jest.mocked(postJSON).mockResolvedValueOnce({ rule: {} });
  await updateRule({ key: 'apex:my_rule', tags: 'a,b' });

  const body = lastBody(postJSON);
  expect(body).toMatchObject({ key: 'apex:my_rule', tags: 'a,b' });
  expect(body).not.toHaveProperty('encodedMarkdownDescription');
});

it('should send AI rule description encoded', async () => {
  await generateAIXPath({
    description: TEXT,
    organization: 'org',
    ruleKey: 'my_rule',
    templateKey: 'apex:XPathTemplateRule',
  });

  const body = lastBody(postJSONBody);
  expect(postJSONBody).toHaveBeenLastCalledWith(
    '/_codescan/ai-rules/generate-xpath',
    expect.anything(),
  );
  expect(body).toMatchObject({ organization: 'org', ruleKey: 'my_rule' });
  expectEncodedOnly(body, 'description', 'encodedDescription');
});
