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
import { COLOR_BG, COLOR_HEALTHY } from '../../../../helpers/constants';
import { getFillColor, snapToDisplayPct, buildPiePath } from '../helpers';
import { SIZE, CX, CY, R } from '../helpers';

export interface AiCreditsIndicatorProps {
  data?: any
}

export function AiCreditsIndicator({
  data,
}: Readonly<AiCreditsIndicatorProps>) {

  const { totalCredits, usedCredits } = data;

  const remaining      = Math.max(0, totalCredits - usedCredits);
  const remainingPct   = totalCredits > 0 ? (remaining / totalCredits) * 100 : 0;
  const fillColor      = getFillColor(remainingPct);

  // Snap to one of 4 fixed visual states
  const displayPct     = snapToDisplayPct(remainingPct);
  const isFullCircle   = displayPct >= 100;
  const piePath        = buildPiePath(displayPct);

  const textColor = displayPct >= 75 ? COLOR_BG : fillColor;
  const tooltipLabel = `${remaining.toLocaleString()} / ${totalCredits.toLocaleString()}`;

  return (
    <Tooltip content={tooltipLabel}>
      <button
        aria-label={tooltipLabel}
        style={{
          position: 'relative',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: SIZE,
          height: SIZE,
          background: 'none',
          border: 'none',
          padding: 0,
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
          {/* White circular background */}
          <circle cx={CX} cy={CY} fill={COLOR_BG} r={R + 1} />

          {/* Filled portion */}
          {isFullCircle
            ? <circle cx={CX} cy={CY} fill={fillColor} r={R} />
            : piePath && <path d={piePath} fill={fillColor} />
          }

          {/* Outer ring */}
          <circle
            cx={CX}
            cy={CY}
            fill="none"
            r={R}
            stroke={fillColor}
            strokeWidth={1.5}
          />
        </svg>

        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            fontSize: 9,
            fontWeight: 800,
            lineHeight: 1,
            color: textColor,
            letterSpacing: '-0.4px',
            userSelect: 'none',
            pointerEvents: 'none',
            ...(displayPct === 75 && {
              WebkitTextStroke: `0.6px ${COLOR_HEALTHY}`,
              paintOrder: 'stroke fill',
            }),
          }}
        >
          AI
        </span>
      </button>
    </Tooltip> 
  );
}
