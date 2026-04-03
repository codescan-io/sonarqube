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

const TICK_MS = 60_000;

/**
 * Returns a fresh {@link Date#now} whenever the hook's component should repaint the exception
 * countdown (interval + tab visibility). Avoids stale "N days left" when the issues UI stays mounted.
 */
export function useExceptionExpiryClock(enabled: boolean): number {
  const [nowMs, setNowMs] = React.useState(() => Date.now());
  React.useEffect(() => {
    if (!enabled) {
      return undefined;
    }
    const bump = () => {
      setNowMs(Date.now());
    };
    bump();
    const id = window.setInterval(bump, TICK_MS);
    document.addEventListener('visibilitychange', bump);
    return () => {
      window.clearInterval(id);
      document.removeEventListener('visibilitychange', bump);
    };
  }, [enabled]);
  return nowMs;
}
