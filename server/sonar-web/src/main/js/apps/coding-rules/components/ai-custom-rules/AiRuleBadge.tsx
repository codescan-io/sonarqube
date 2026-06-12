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
import * as React from 'react';
import Tooltip from '../../../../components/controls/Tooltip';
import { translate } from '../../../../helpers/l10n';
import AiRulesSvg from './icons/Icon-aican-make-mistakes.svg';

/**
 * Reserved system tag the backend attaches to a custom rule whose XPath was
 * generated using AI. Kept in sync with RuleCreator.AI_GENERATED_SYSTEM_TAG.
 */
export const AI_GENERATED_TAG = 'ai-generated';

/** Returns true when the rule carries the reserved AI-generated system tag. */
export function isAiGeneratedRule(rule: { sysTags?: string[] }): boolean {
  return rule.sysTags?.includes(AI_GENERATED_TAG) ?? false;
}

/**
 * Small pill ("✦ AI") shown next to rules whose XPath was generated using AI.
 * Mirrors the design tag: sparkle icon on a light-purple background.
 */
export default function AiRuleBadge({ className }: Readonly<{ className?: string }>) {
  return (
    <Tooltip content={translate('coding_rules.ai_generated.tooltip')}>
      <span
        className={className}
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: '4px',
          padding: '2.5px 8px 1.5px 8px',
          borderRadius: '4px',
          background: '#E8EBFF',
          color: '#5C6BD1',
          fontFamily: 'Inter, sans-serif',
          fontSize: '12px',
          fontStyle: 'normal',
          fontWeight: 600,
          lineHeight: '18px',
        }}
      >
        <img src={AiRulesSvg} alt="" width="12" height="12" aria-hidden />
        {translate('coding_rules.ai_generated')}
      </span>
    </Tooltip>
  );
}
