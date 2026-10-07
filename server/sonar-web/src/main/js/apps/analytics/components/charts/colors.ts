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

import { AnalyticsIssueType } from '../../utils';

/*
 * Categorical slots 1-3 of the validated reference palette (blue, orange, aqua). They pass
 * every colour-vision check on the white page surface for all pairs; aqua is under 3:1
 * contrast, so every chart using them also prints its values and a legend.
 */
export const ISSUE_TYPE_COLORS: Record<AnalyticsIssueType, string> = {
  BUG: '#2a78d6',
  CODE_SMELL: '#eb6834',
  VULNERABILITY: '#1baf7a',
};

// Magnitude-only bars (one series) use the product accent, so they never read as an issue type.
export const MAGNITUDE_COLOR = 'var(--echoes-color-background-accent-default)';

export const STATUS_COLORS = {
  success: 'var(--echoes-color-icon-success)',
  danger: 'var(--echoes-color-icon-danger)',
  warning: 'var(--echoes-color-icon-warning)',
  info: 'var(--echoes-color-icon-info)',
  neutral: 'var(--echoes-color-icon-disabled)',
};

export const TRACK_COLOR = 'var(--echoes-color-border-weaker)';

/** A light wash of a colour for backgrounds (icon badges, heatmap cells). */
export function tint(color: string, percent: number) {
  return `color-mix(in srgb, ${color} ${percent}%, white)`;
}
