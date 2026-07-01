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
import { postJSONBody } from '../../../../helpers/request';

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

export interface ValidationMatch {
  line: number;
  column: number;
  text: string;
  nodeName?: string;
  name?: string;
  image?: string;
  xpath: string;
  endLine: number;
  endColumn: number;
}

export interface ValidationError {
  message: string;
  line: number;
  column: number;
  type: string;
  details: string;
}

export interface ValidationResult {
  success: boolean;
  totalMatches: number;
  matches: ValidationMatch[];
  errors: ValidationError[];
  astPreview: string;
}

export interface ValidateXPathRequest {
  xpath: string;
  code: string;
  language: string;
}

/**
 * Generate XPath from natural language description using AI.
 * Does not create a rule — rule creation uses the standard v2 rules API.
 */
export function generateAIXPath(request: GenerateXPathRequest): Promise<GenerateXPathResponse> {
  return postJSONBody('/_codescan/ai-rules/generate-xpath', request);
}

/**
 * Validate an XPath expression against a code sample.
 * Backend expects a JSON body (XPathValidationRequest), so this uses postJSONBody.
 */
export function validateAIXPath(request: ValidateXPathRequest): Promise<ValidationResult> {
  return postJSONBody('/_codescan/ai-rules/validate-xpath', request);
}

/**
 * Split an XPath expression into multiple lines for display.
 * Tries to break long lines at logical boundaries.
 */
export function splitXPath(xpath: string, maxLength = 60): string[] {
  if (!xpath) return [''];
  
  // First, split by existing newlines
  const segments = xpath.split('\n');
  const result: string[] = [];

  for (const segment of segments) {
    if (segment.length <= maxLength) {
      result.push(segment);
      continue;
    }

    // Try to split long lines at logical boundaries
    let current = segment;
    while (current.length > maxLength) {
      // Find a good split point
      // Preferred: logical operators (and, or), separators (/, //, |, [)
      
      let splitIdx = -1;
      
      // Look for last logical marker before maxLength
      const searchPart = current.substring(0, maxLength);
      
      // Try logical operators first (surrounded by spaces)
      const logicMatch = searchPart.match(/.*\s+(and|or)\s+/i);
      if (logicMatch) {
        splitIdx = logicMatch.index! + logicMatch[0].length;
      }
      
      // Try space after some common tokens
      if (splitIdx === -1) {
        const spaceMatch = searchPart.lastIndexOf(' ');
        if (spaceMatch > maxLength * 0.6) {
          splitIdx = spaceMatch + 1;
        }
      }

      // Try logical separators / or // or [ or |
      if (splitIdx === -1) {
        const sepMatch = searchPart.match(/.*(\/\/|\/|\[|\|)/);
        if (sepMatch && sepMatch.index! > 0) {
           // Split BEFORE these
           splitIdx = sepMatch.index!;
        }
      }

      if (splitIdx > 0) {
        result.push(current.substring(0, splitIdx).trimEnd());
        current = current.substring(splitIdx).trimStart();
      } else {
        // Force split if no good point found
        result.push(current.substring(0, maxLength));
        current = current.substring(maxLength);
      }
    }
    result.push(current);
  }

  return result.filter((l, i) => l.length > 0 || i === 0);
}
