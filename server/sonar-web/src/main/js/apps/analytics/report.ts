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
import { ReportGroup } from './governance';

/**
 * Set while the page renders the report of every tab for printing: controls are left out, lists are expanded and
 * figures stop being clickable.
 */
export const ReportContext = React.createContext(false);

export function useIsReport() {
  return React.useContext(ReportContext);
}

/** The tabs of the page; the values are what the URL keeps. */
export enum AnalyticsTab {
  Summary = 'brief',
  Projects = 'overview',
  Risk = 'quality',
  Governance = 'governance',
  Activity = 'activity',
  Value = 'value',
}

export const ANALYTICS_TABS = Object.values(AnalyticsTab);

/** Tabs that show a period. Projects shows each project as it is now. */
export function hasPeriod(tab: AnalyticsTab) {
  return tab !== AnalyticsTab.Projects;
}

/** Every tab in one PDF. */
export const FULL_REPORT = 'all';

/** What a PDF holds: one tab, every tab, or one of the ready-made reports. */
export type ReportKind = AnalyticsTab | ReportGroup | typeof FULL_REPORT;
