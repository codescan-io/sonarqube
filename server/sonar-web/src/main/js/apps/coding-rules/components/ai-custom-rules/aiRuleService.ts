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
import { postJSON } from '../../../../helpers/request';

export interface GenerateXPathRequest {
  description: string;
  organization: string;
}

export interface GenerateXPathResponse {
  generatedXPath: string;
  description: string;
  status: string;
  aiSummary: string;
}

/**
 * Generate XPath from natural language description using AI.
 * Does not create a rule — rule creation uses the standard v2 rules API.
 */
export function generateAIXPath(request: GenerateXPathRequest): Promise<GenerateXPathResponse> {
  return postJSON('/api/rules/generate_ai_xpath', request);
}
