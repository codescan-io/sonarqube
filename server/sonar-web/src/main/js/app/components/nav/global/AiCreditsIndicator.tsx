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
import { COLOR_UNFILLED, COLOR_WHITE } from '../../../../helpers/constants';

export interface AiCreditsIndicatorProps {
  data?: AiCreditsSummary;
}

export function AiCreditsIndicator({
  data,
}: Readonly<AiCreditsIndicatorProps>) {
  if (!data) { return null; }

  var { allocatedCredits, consumedCredits, remainingCredits } = data;

  const usedPct  = allocatedCredits > 0
    ? Math.min(100, (consumedCredits / allocatedCredits) * 100)
    : 0;

  const arcColor = getArcColor(usedPct);

  // stroke-dashoffset controls how much of the arc is drawn clockwise from top
  const filledLength   = (usedPct / 100) * CIRCUMFERENCE;
  const dashOffset     = CIRCUMFERENCE - filledLength;

  const tooltipContent = (
    <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0 }}>
      <span
        style={{
          color: COLOR_WHITE,
          fontSize: '14px',
          fontStyle: 'normal',
          fontWeight: 600,
          lineHeight: '20px',
          textAlign: 'center',
          display: 'block',
          width: '100%',
        }}
      >
        {remainingCredits} / {allocatedCredits}
      </span>
      <span
        style={{
          color: COLOR_WHITE,
          fontSize: '12px',
          fontStyle: 'normal',
          fontWeight: 400,
          lineHeight: '20px',
          textAlign: 'center',
          display: 'block',
          width: '100%',
        }}
      >
        AI Credits remaining
      </span>
    </span>
  );

  return (
    <Tooltip content={tooltipContent}>
      <button
        aria-label={`AI Credits: ${remainingCredits.toLocaleString()} of ${allocatedCredits.toLocaleString()} remaining`}
        style={{
          position: 'relative',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: SIZE,
          height: SIZE,
          background: 'none',
          border: 'none',
          padding: '0 10px',    
          cursor: 'default',
          borderRadius: '50%',
          flexShrink: 0,
        }}
        type="button"
      >
        <svg
          aria-hidden="true"
          height={SIZE}
          style={{ display: 'block' }}
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
              // Rotate -90° so the arc starts at 12 o'clock (top)
              transform={`rotate(-90, ${CX}, ${CY})`}
            />
          )}
        </svg>

        {/* "AI" label centred inside the ring */}
        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            fontFamily: 'Inter, sans-serif',
            fontSize: '12px',
            fontStyle: 'normal',
            fontWeight: 600,
            lineHeight: '16.5px',
            letterSpacing: '-0.275px',
            color: arcColor,
            userSelect: 'none',
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
          }}
        >
          AI
        </span>
      </button>
    </Tooltip>
  );
}
