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
import { useLocation, useSearchParams } from 'react-router-dom';
import { AiCreditsSummary, fetchCredits } from '../../../api/ai-codefix';
import { useCurrentOrg } from '../nav/organization/CurrentOrgContext';
import { AiCreditsContext } from './AiCreditsContext';

interface Props {
  children: React.ReactNode;
}

export function AiCreditsContextProvider({ children }: Props) {
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const { orgKee: contextOrgKee } = useCurrentOrg();

  const [creditsData, setCreditsData] = React.useState<AiCreditsSummary | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);

  const orgKee = React.useMemo(() => {
    // /organizations/beforetwo/extension/billing
    const orgMatch = pathname.match(/\/organizations\/([^/]+)/);
    const orgKey = orgMatch?.[1] ?? null;

    if (orgKey) {
      return orgKey;
    }

    // ?id=123
    const id = searchParams.get('id');
    if (!id) {
      return null;
    }

    return contextOrgKee;
  }, [pathname, searchParams, contextOrgKee]);

  React.useEffect(() => {
    if (!orgKee) {
      setCreditsData(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    fetchCredits(orgKee)
      .then(setCreditsData)
      .catch(() => setCreditsData(null))
      .finally(() => setIsLoading(false));
  }, [orgKee]);

  const value = React.useMemo(() => ({
    creditsData,
    isLoading,
  }), [creditsData, isLoading]);

  return (
    <AiCreditsContext.Provider value={value}>
      {children}
    </AiCreditsContext.Provider>
  );
}
