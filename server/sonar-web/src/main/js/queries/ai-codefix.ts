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

import { queryOptions, useQueries, useQuery } from '@tanstack/react-query';
import { uniq } from 'lodash';
import { getCodefixIntegration } from '../api/ai-codefix';
import { StaleTime } from './common';

export function aiCodefixIntegrationQueryOptions(projectKey: string) {
  return queryOptions({
    queryKey: ['codefix', 'integration', projectKey],
    queryFn: () => getCodefixIntegration(projectKey),
    staleTime: StaleTime.LONG,
    refetchOnWindowFocus: false,
  });
}

export function useIsAiCodefixSupportedIntegration(projectKey?: string): boolean {
  const {data} = useQuery({
    ...aiCodefixIntegrationQueryOptions(projectKey ?? ''),
    enabled: Boolean(projectKey),
  });

  return data?.supported === true;
}

export function useAreAllAiCodefixSupportedIntegrations(projectKeys: string[]): boolean {
  const keys = uniq(projectKeys);
  const results = useQueries({
    queries: keys.filter(Boolean).map((key) => aiCodefixIntegrationQueryOptions(key)),
  });

  const everyProjectIdentified = keys.length > 0 && keys.every(Boolean);

  return everyProjectIdentified && results.every(({ data }) => data?.supported === true);
}
