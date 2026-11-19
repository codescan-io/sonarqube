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

import { format, subDays } from 'date-fns';
import * as React from 'react';
import { AnalyticsInterval } from '../../api/analytics';

/**
 * Loads data again whenever a dependency changes. The previous data stays while the next loads, so panels dim instead of
 * jumping; a load that a newer one replaced is ignored.
 */
export function useLoaded<T>(load: () => Promise<T>, deps: React.DependencyList) {
  const [data, setData] = React.useState<T>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  React.useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);
    load().then(
      (result) => {
        if (active) {
          setData(result);
          setLoading(false);
        }
      },
      () => {
        if (active) {
          setError(true);
          setLoading(false);
        }
      },
    );
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- the caller lists what the load depends on
  }, deps);

  return { data, error, loading };
}

/** The last {@code days} days up to today, in the browser's time zone, with day buckets up to a month and weeks after. */
export function periodRequest(days: number, now = new Date()) {
  return {
    from: format(subDays(now, days - 1), 'yyyy-MM-dd'),
    interval: (days > 31 ? 'week' : 'day') as AnalyticsInterval,
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  };
}
