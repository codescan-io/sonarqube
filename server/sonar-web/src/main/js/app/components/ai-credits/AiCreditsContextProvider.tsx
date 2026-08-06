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
import { AiCreditsSummary, fetchCredits, fetchUserAiCredits } from '../../../api/ai-codefix';
import { useCurrentOrg } from '../nav/organization/CurrentOrgContext';
import { useCurrentUser } from '../current-user/CurrentUserContext';
import { AiCreditsContext } from './AiCreditsContext';

interface Props {
  children: React.ReactNode;
}

export function AiCreditsContextProvider({ children }: Props) {
  const { pathname } = useLocation();
  const [searchParams] = useSearchParams();
  const { orgKee: contextOrgKee } = useCurrentOrg();
  const { userOrganizations } = useCurrentUser();

  const [creditsData, setCreditsData] = React.useState<AiCreditsSummary | null>(null);
  const [userCreditsData, setUserCreditsData] = React.useState<AiCreditsSummary | null>(null);
  const [showCredits, setShowCredits] = React.useState(false);
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

  const isOrgAdmin = React.useMemo(
    () => userOrganizations?.find((o) => o.kee === orgKee)?.actions?.admin === true,
    [userOrganizations, orgKee]
  );

  const isOrgMember = React.useMemo(
    () => userOrganizations?.some((o) => o.kee === orgKee) === true,
    [userOrganizations, orgKee]
  );

  React.useEffect(() => {
    if (!orgKee || !isOrgMember) {
      setCreditsData(null);
      setUserCreditsData(null);
      setShowCredits(false);
      setIsLoading(false);
      return undefined;
    }

    // Ignore stale responses when orgKee changes before a request resolves.
    let cancelled = false;
    setIsLoading(true);

    // Top-nav pie indicator always shows the CURRENT user's own credits.
    const userPromise = fetchUserAiCredits(orgKee)
      .then((user) => {
        if (cancelled) return;
        setUserCreditsData({
          allocatedCredits: user.creditLimit,
          consumedCredits: user.creditsUsed,
          remainingCredits: user.creditsRemaining,
          resetDate: '',
        });
        // show the pie only when the user has access AND a positive allocation,
        setShowCredits(user.hasAiAccess && user.creditLimit > 0);
      })
      .catch(() => {
        if (cancelled) return;
        setUserCreditsData(null);
        setShowCredits(false);
      });

    let orgPromise: Promise<unknown> = Promise.resolve();
    if (isOrgAdmin) {
      orgPromise = fetchCredits(orgKee)
        .then((data) => {
          if (!cancelled) setCreditsData(data);
        })
        .catch(() => {
          if (!cancelled) setCreditsData(null);
        });
    } else {
      setCreditsData(null);
    }
    Promise.all([userPromise, orgPromise]).finally(() => {
      if (!cancelled) setIsLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [orgKee, isOrgAdmin, isOrgMember]);

  const value = React.useMemo(() => ({
    creditsData,
    userCreditsData,
    isLoading,
    showCredits,
  }), [creditsData, userCreditsData, isLoading, showCredits]);

  return (
    <AiCreditsContext.Provider value={value}>
      {children}
    </AiCreditsContext.Provider>
  );
}
