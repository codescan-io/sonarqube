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

import { Tooltip } from '@sonarsource/echoes-react';
import { AiCreditsSummary } from '../../../../api/ai-codefix';
import { getArcColor, SIZE, STROKE_WIDTH, RADIUS, CX, CY, CIRCUMFERENCE } from '../helpers';
import { COLOR_UNFILLED } from '../../../../helpers/constants';
import './AiCreditsIndicator.css';

export interface AiCreditsIndicatorProps {
  data?: AiCreditsSummary;
}

export function AiCreditsIndicator({
  data,
}: Readonly<AiCreditsIndicatorProps>) {
  if (!data) { return null; }

  const { allocatedCredits, consumedCredits, remainingCredits } = data;

  const usedPct = allocatedCredits > 0
    ? Math.min(100, (consumedCredits / allocatedCredits) * 100)
    : 0;

  const arcColor     = getArcColor(usedPct);
  const filledLength = (usedPct / 100) * CIRCUMFERENCE;
  const dashOffset   = CIRCUMFERENCE - filledLength;

  const tooltipContent = (
    <span className="ai-credits-indicator-tooltip">
      <span className="ai-credits-indicator-tooltip-count">
        {remainingCredits} / {allocatedCredits}
      </span>
      <span className="ai-credits-indicator-tooltip-label">
        AI Credits remaining
      </span>
    </span>
  );

  return (
    <Tooltip content={tooltipContent}>
      <button
        aria-label={`AI Credits: ${remainingCredits.toLocaleString()} of ${allocatedCredits.toLocaleString()} remaining`}
        className="ai-credits-indicator-button"
        style={{ width: SIZE, height: SIZE }}
        type="button"
      >
        <svg
          aria-hidden="true"
          className="ai-credits-indicator-svg"
          height={SIZE}
          width={SIZE}
        >
          {/* Grey unfilled track — full circle */}
          <circle
            cx={CX}
            cy={CY}
            fill="none"
            r={RADIUS}
            stroke={COLOR_UNFILLED}
            strokeWidth={STROKE_WIDTH}
          />

          {/* Coloured filled arc — clockwise from 12 o'clock */}
          {usedPct > 0 && (
            <circle
              cx={CX}
              cy={CY}
              fill="none"
              r={RADIUS}
              stroke={arcColor}
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={dashOffset}
              strokeLinecap="round"
              strokeWidth={STROKE_WIDTH}
              transform={`rotate(-90, ${CX}, ${CY})`}
            />
          )}
        </svg>

        {/* "AI" label centred inside the ring */}
        <span
          aria-hidden="true"
          className="ai-credits-indicator-label"
          style={{ color: arcColor }}
        >
          AI
        </span>
      </button>
    </Tooltip>
  );
}
